package com.localair.airplay;

/** Pure bounded convergence policy. Transport is deliberately outside this class. */
public final class SettleController {
    public static final class Step {
        public final int x,y;
        Step(int x,int y){this.x=x;this.y=y;}
    }
    private double tx,ty,lastError=Double.NaN;
    private long changedAt=-1,started=-1,lastCommand=-1,lastFrame=-1;
    private int steps,stalled,alignedFrames;
    private boolean blocked;
    private boolean responseEvaluated;
    private double commandX,commandY,gain=1;
    private int commandDx,commandDy;
    public String status=AppText.get(R.string.move_the_ray);
    public int targetRevision;
    private boolean fast;
    public boolean isFast(){return fast;}
    public void setFast(boolean value){fast=value;reset();}
    public double targetX(){return tx;}
    public double targetY(){return ty;}
    public void reset(){changedAt=started=lastCommand=lastFrame=-1;steps=stalled=alignedFrames=0;blocked=false;responseEvaluated=false;lastError=Double.NaN;gain=1;status=AppText.get(R.string.move_the_ray);}
    public void target(double x,double y,long now){
        if(!Double.isFinite(x)||!Double.isFinite(y)){reset();return;}
        if(changedAt<0||Math.hypot(x-tx,y-ty)>3){
            // A changed target must not forget a command still travelling through AirPlay.
            steps=stalled=alignedFrames=0;blocked=false;started=-1;
            targetRevision++;tx=x;ty=y;changedAt=now;
            if(lastCommand>=0)lastError=Math.hypot(tx-commandX,ty-commandY);
            status=AppText.get(R.string.waiting_for_the_ray_to_settle);
        }
    }
    public Step observe(double x,double y,long frameTime,long now,boolean trusted,boolean allowed,boolean transportReady){
        if(changedAt<0||blocked)return null;
        if(!allowed||!trusted||!Double.isFinite(x)||!Double.isFinite(y)||frameTime>now||now-frameTime>250){block(AppText.get(R.string.detection_lost_or_input_paused_move_the));return null;}
        if(frameTime<=lastFrame)return null;
        lastFrame=frameTime;
        if(now-changedAt<(fast?60:100)){status=AppText.get(R.string.waiting_for_the_ray_to_settle);return null;}
        if(started<0)started=now;
        if(now-started>10000||steps>=24){block(AppText.get(R.string.correction_limit_reached_move_the_ray_again));return null;}
        // PixelCopy timestamps are local observations, not remote presentation times.
        // Wait for visible displacement before issuing another command; a fresh copy alone
        // does not establish that AirPlay has carried the previous command back.
        if(lastCommand>=0){
            if(now-lastCommand<(fast?60:100)){status=AppText.get(R.string.waiting_for_previous_action_feedback);return null;}
            if(Math.hypot(x-commandX,y-commandY)<2 && now-lastCommand<450){status=AppText.get(R.string.waiting_for_visible_pointer_movement);return null;}
        }
        double error=Math.hypot(tx-x,ty-y);
        if(error<=3){status=AppText.get(R.string.confirming_the_nearby_position);if(++alignedFrames>=2){blocked=true;status=AppText.get(R.string.within_tolerance_3_sample_pixels);}return null;}
        alignedFrames=0;
        if(lastCommand>=0&&!responseEvaluated&&Double.isFinite(lastError)) {
            responseEvaluated=true;
            stalled=error>=lastError-.3?stalled+1:0;
            if(stalled>=3){block(AppText.get(R.string.not_approaching_the_target_correction_stopped));return null;}
            double observedGain=((x-commandX)*commandDx+(y-commandY)*commandDy)/
                    (commandDx*commandDx+commandDy*commandDy);
            if(observedGain>=.25&&observedGain<=4)gain=.5*gain+.5*observedGain;
        }
        if(!transportReady){status=AppText.get(R.string.waiting_for_bluetooth_input);return null;}
        int cap=fast?(error>60?32:error>20?16:4):(error>80?24:error>20?12:4);
        double proportion=fast?.5:.35;
        int dx=quantize((tx-x)*proportion/gain,cap),dy=quantize((ty-y)*proportion/gain,cap);
        if(dx==0&&dy==0){
            if(Math.abs(tx-x)>=Math.abs(ty-y))dx=tx>x?1:-1;
            else dy=ty>y?1:-1;
        }
        commandX=x;commandY=y;commandDx=dx;commandDy=dy;lastError=error;lastCommand=now;responseEvaluated=false;steps++;status=AppText.get(R.string.approaching_target)+steps+"/24";
        return new Step(dx,dy);
    }
    private int quantize(double v,int cap){return (int)Math.max(-cap,Math.min(cap,Math.round(v)));}
    public void rejected(){block(AppText.get(R.string.transmission_rejected_correction_stopped));}
    private void block(String text){blocked=true;status=text;}
}
