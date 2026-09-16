package com.localair.airplay;

/** User-driven calibration. This class cannot emit HID input. All coordinates use
 * the 720-long-edge sampling plane. Never reset position merely because time elapsed. */
public final class PointerCalibration {
    private static final int[] GUIDE={R.string.move_slowly_right_then_stop,R.string.move_slowly_left_then_stop,R.string.move_slowly_down_then_stop,R.string.move_slowly_up_then_stop,
        R.string.move_right_at_your_usual_faster_speed,R.string.move_left_at_your_usual_faster_speed,R.string.move_down_at_your_usual_faster_speed,R.string.move_up_at_your_usual_faster_speed,
        R.string.move_slowly_down_right_then_stop_validation,R.string.move_up_right_at_normal_speed_then};
    static final class Sample {
        final double hx,hy,px,py,speed;
        Sample(double hx,double hy,double px,double py,double speed){this.hx=hx;this.hy=hy;this.px=px;this.py=py;this.speed=speed;}
    }
    public static final class Gain {
        public final double slowX,slowY,fastX,fastY,threshold;
        Gain(double sx,double sy,double fx,double fy,double t){slowX=sx;slowY=sy;fastX=fx;fastY=fy;threshold=t;}
        public double x(double speed){return speed>threshold?fastX:slowX;}
        public double y(double speed){return speed>threshold?fastY:slowY;}
    }
    private final Sample[] samples=new Sample[10],passive=new Sample[10];
    private final long[] passiveAt=new long[10];
    private Gain gain,candidate;
    private double validationError;
    private int step,width,height;
    private boolean active,baseline,positionValid;
    private double startX,startY,hx,hy,stableX,stableY,latestX,latestY,estimateX,estimateY;
    private long firstInput=-1,lastInput=-1,lastSignificantInput=-1,stableSince=-1,latestAt=-1,started,lastUpdate,interval=300000;
    public String status=AppText.get(R.string.not_calibrated);
    public boolean isActive(){return active;}
    public int stage(){return step;}
    public boolean hasBaseline(){return baseline;}
    public boolean awaitingReference(){return active&&step==10;}
    public boolean hasGain(){return gain!=null;}
    public Gain gain(){return active?null:gain;}
    public boolean hasPosition(){return positionValid;}
    public int intervalMinutes(){return (int)(interval/60000);}
    public void intervalMinutes(int minutes){if(minutes!=3&&minutes!=5)throw new IllegalArgumentException();interval=minutes*60000L;}
    public void begin(long now){active=true;step=0;started=now;candidate=null;validationError=0;positionValid=false;java.util.Arrays.fill(samples,null);invalidateSegment();status=AppText.get(R.string.move_the_ray_gently_to_identify_the);}
    public void cancel(){active=false;candidate=null;invalidateSegment();status=gain==null?AppText.get(R.string.calibration_canceled_direct_control_retained):AppText.get(R.string.calibration_canceled_previous_sensitivity_retained);}
    public void tick(long now){if(active&&now-started>=180000){cancel();status=AppText.get(R.string.calibration_timed_out_direct_control_retained);}}
    public void resetForDirectControl(){cancel();gain=null;positionValid=false;java.util.Arrays.fill(passive,null);status=AppText.get(R.string.direct_control_no_calibration_needed_2);}
    public void invalidateSegment(){baseline=false;hx=hy=0;firstInput=-1;stableSince=-1;}
    public void geometry(int w,int h){
        if(width>0&&(Math.abs(width-w)>2||Math.abs(height-h)>2)){gain=null;positionValid=false;if(active)cancel();status=AppText.get(R.string.video_size_changed_recalibrate);java.util.Arrays.fill(passive,null);}
        width=w;height=h;
    }
    public void externalAction(){invalidateSegment();positionValid=false;java.util.Arrays.fill(passive,null);if(gain!=null&&!active)status=AppText.get(R.string.position_reference_needs_recalibration_sensitivity_is_still);}
    public void report(int dx,int dy,long now,boolean ok){
        if(!ok){invalidateSegment();positionValid=false;return;}
        if(firstInput<0)firstInput=now;
        double speed=lastInput>=0&&now-lastInput>0?Math.hypot(dx,dy)*1000/Math.max(16,now-lastInput):0;
        if(gain!=null&&positionValid){estimateX+=dx*gain.x(speed*gain.slowX);estimateY+=dy*gain.y(speed*gain.slowY);}
        hx+=dx;hy+=dy;lastInput=now;
        if(Math.hypot(dx,dy)>=2){lastSignificantInput=now;stableSince=-1;}
    }
    public void observe(double x,double y,long now,boolean trusted,boolean idle){
        tick(now);
        if(!active&&gain!=null&&now-lastUpdate>=interval&&status.startsWith(AppText.get(R.string.calibration_complete)))status=AppText.get(R.string.review_due_waiting_for_reliable_natural_movement);
        if(!trusted||!Double.isFinite(x)||!Double.isFinite(y)){latestAt=-1;invalidateSegment();return;}
        latestX=x;latestY=y;latestAt=now;
        if(awaitingReference()){status=AppText.get(R.string.point_at_the_actual_pointer_tip_and);return;}
        if(!idle){stableSince=-1;return;}
        if(now-lastSignificantInput<350)return;
        if(stableSince<0||Math.hypot(x-stableX,y-stableY)>1.5){stableSince=now;stableX=x;stableY=y;return;}
        if(now-stableSince<450)return;
        if(positionValid&&Math.hypot(x-estimateX,y-estimateY)>12){positionValid=false;status=AppText.get(R.string.position_reference_drifted_realign_from_calibration);}
        if(x<24||y<24||x>width-24||y>height-24){invalidateSegment();if(active)status=AppText.get(R.string.pointer_too_close_to_an_edge_move);return;}
        if(!baseline){baseline=true;startX=x;startY=y;hx=hy=0;firstInput=-1;if(active)status=guide();return;}
        if(Math.hypot(hx,hy)<12||Math.hypot(x-startX,y-startY)<6)return;
        Sample s=new Sample(hx,hy,x-startX,y-startY,Math.hypot(x-startX,y-startY)*1000/Math.max(150,lastInput-firstInput));
        startX=x;startY=y;hx=hy=0;firstInput=-1;
        if(active)acceptGuided(s);else if(gain!=null)acceptPassive(s,now);
        if(!active&&gain!=null&&now-lastUpdate>=interval&&status.startsWith(AppText.get(R.string.calibration_complete)))status=AppText.get(R.string.review_due_waiting_for_reliable_natural_movement);
    }
    private String guide(){return AppText.get(R.string.calibration)+(step+1)+"/10："+AppText.get(GUIDE[step])+AppText.get(R.string.about_60_120_pixels_suggested);}
    private void acceptGuided(Sample s){
        if(!matches(s,step)){status=guide()+AppText.get(R.string.direction_or_distance_insufficient_try_again);return;}
        samples[step++]=s;
        if(step==8){candidate=fit(samples);if(candidate==null){cancel();status=AppText.get(R.string.direction_speed_data_inconsistent_recalibrate);return;}}
        if(step>=9&&!validates(candidate,s,step==9?0:1)){cancel();status=AppText.get(R.string.validation_error_too_large_new_calibration_not);return;}
        if(step>=9)validationError=Math.max(validationError,residual(candidate,s,step==9?0:1));
        status=step==10?AppText.get(R.string.movement_validated_point_at_the_actual_pointer):guide();
    }
    public boolean confirm(double rayX,double rayY,long now){
        if(!awaitingReference())return false;
        if(latestAt<0||!Double.isFinite(rayX+rayY)||now-latestAt<0||now-latestAt>250||Math.hypot(rayX-latestX,rayY-latestY)>6){status=AppText.get(R.string.point_precisely_at_the_detected_pointer_tip);return false;}
        gain=candidate;active=false;positionValid=true;estimateX=latestX;estimateY=latestY;lastUpdate=now;
        invalidateSegment();java.util.Arrays.fill(passive,null);status=AppText.get(R.string.calibration_complete_reference_error)+Math.round(Math.hypot(rayX-latestX,rayY-latestY))+AppText.get(R.string.validation_error)+Math.round(validationError)+AppText.get(R.string.sample_pixels_interval)+intervalMinutes()+AppText.get(R.string.minutes);return true;
    }
    private void acceptPassive(Sample s,long now){
        int bin=s.speed>gain.threshold?1:0;
        for(int i=bin*4;i<bin*4+4;i++)if(matches(s,i)){passive[i]=s;passiveAt[i]=now;break;}
        if(matches(s,8+bin)){passive[8+bin]=s;passiveAt[8+bin]=now;}
        if(now-lastUpdate<interval)return;
        for(long time:passiveAt)if(time<now-interval){status=AppText.get(R.string.review_due_waiting_for_reliable_natural_movement);return;}
        Gain proposed=fit(passive);
        if(proposed==null||passive[8]==null||passive[9]==null||!validates(proposed,passive[8],0)||!validates(proposed,passive[9],1)){
            status=AppText.get(R.string.review_due_waiting_for_reliable_natural_movement);return;
        }
        gain=new Gain(limit(gain.slowX,proposed.slowX),limit(gain.slowY,proposed.slowY),limit(gain.fastX,proposed.fastX),limit(gain.fastY,proposed.fastY),proposed.threshold);
        // No assignment to estimateX/Y or positionValid: gain updates do not recenter.
        lastUpdate=now;java.util.Arrays.fill(passive,null);status=AppText.get(R.string.sensitivity_reviewed_position_reference)+(positionValid?AppText.get(R.string.retained):AppText.get(R.string.needs_realignment));
    }
    private static double limit(double old,double value){return Math.max(old*.9,Math.min(old*1.1,value));}
    static boolean matches(Sample s,int index){
        if(!Double.isFinite(s.hx+s.hy+s.px+s.py+s.speed)||s.speed<=0||Math.hypot(s.hx,s.hy)<12)return false;
        if(index>=8)return s.hx>6&&(index==8?s.hy>6:s.hy< -6)&&s.px>3&&(index==8?s.py>3:s.py< -3);
        int direction=index%4;boolean horizontal=direction<2;double a=horizontal?s.hx:s.hy,b=horizontal?s.hy:s.hx,p=horizontal?s.px:s.py,q=horizontal?s.py:s.px;
        return (direction%2==0?a>12:a< -12)&&a*p>0&&Math.abs(p)>=6&&Math.abs(b)<=Math.abs(a)*.15&&Math.abs(q)<=Math.max(3,Math.abs(p)*.15);
    }
    static Gain fit(Sample[] s){
        for(int i=0;i<8;i++)if(s[i]==null||!matches(s[i],i))return null;
        double[] g=new double[4];
        for(int pair=0;pair<4;pair++){
            int i=pair*2;boolean x=pair%2==0;
            double a=x?s[i].px/s[i].hx:s[i].py/s[i].hy,b=x?s[i+1].px/s[i+1].hx:s[i+1].py/s[i+1].hy;
            if(a<.2||a>5||b<.2||b>5||Math.max(a,b)/Math.min(a,b)>1.5)return null;
            g[pair]=(a+b)/2;
        }
        double slow=0,fast=0;for(int i=0;i<4;i++){slow+=s[i].speed/4;fast+=s[i+4].speed/4;}
        if(fast<slow*1.2)return null;
        return new Gain(g[0],g[1],g[2],g[3],(slow+fast)/2);
    }
    static boolean validates(Gain g,Sample s,int bin){
        if(g==null||s==null)return false;
        double error=residual(g,s,bin);
        return error<=Math.min(8,Math.max(3,Math.hypot(s.px,s.py)*.1));
    }
    private static double residual(Gain g,Sample s,int bin){return Math.hypot(s.px-s.hx*(bin==0?g.slowX:g.fastX),s.py-s.hy*(bin==0?g.slowY:g.fastY));}
}
