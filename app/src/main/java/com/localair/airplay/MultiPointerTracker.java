package com.localair.airplay;

import java.util.ArrayList;
import java.util.List;

/** Shape-constrained temporal tracks. No colour dependency or input generation.
 * Similar moving targets remain ambiguous; this is not a general optical-flow model. */
public final class MultiPointerTracker {
    private static final class Track {
        ArrowPointerDetector.Candidate last,anchor;
        double rayX,rayY;
        long seen;
        int observations;
        boolean confirmed;
        boolean missing;
        int recovery;
        Track(ArrowPointerDetector.Candidate c,double x,double y,long time){last=anchor=c;rayX=x;rayY=y;seen=time;observations=1;}
    }
    private final List<Track> tracks=new ArrayList<>();
    private long lastTime=-1;
    private Track locked;
    private ArrowPointerDetector.Candidate selected;
    public String reason=AppText.get(R.string.waiting_for_initial_detection);
    public ArrowPointerDetector.Candidate selected(){return selected;}
    public void reset(){tracks.clear();locked=null;selected=null;lastTime=-1;reason=AppText.get(R.string.waiting_for_initial_detection);}
    private boolean compatible(Track t,ArrowPointerDetector.Candidate c){
        return t.last.circular==c.circular&&c.width/(double)t.last.width>=.7&&c.width/(double)t.last.width<=1.4&&
            c.height/(double)t.last.height>=.7&&c.height/(double)t.last.height<=1.4;
    }
    public boolean update(List<ArrowPointerDetector.Candidate> candidates,double rx,double ry,long time){
        selected=null;
        if(!Double.isFinite(rx)||!Double.isFinite(ry)||lastTime>=0&&(time<=lastTime||time-lastTime>400)){
            reset();reason=AppText.get(R.string.observation_interval_expired);return false;
        }
        lastTime=time;
        tracks.removeIf(t->time-t.seen>250);
        if(!tracks.contains(locked))locked=null;
        if(candidates.size()>8){reset();reason=AppText.get(R.string.multiple_candidates_alignment_paused);return false;}
        List<Track> updated=new ArrayList<>();
        List<Track> old=new ArrayList<>(tracks);
        boolean ambiguous=false;
        for(ArrowPointerDetector.Candidate c:candidates){
            if(c.score<(c.circular?.90:.86))continue;
            Track best=null;double first=Double.POSITIVE_INFINITY,second=Double.POSITIVE_INFINITY;
            for(Track t:old){
                if(!compatible(t,c))continue;
                double distance=Math.hypot(c.x-t.last.x,c.y-t.last.y);
                if(distance>50)continue;
                if(distance<first){second=first;first=distance;best=t;}else second=Math.min(second,distance);
            }
            if(best!=null){
                // Never assign two detections to one identity or guess at a crossing.
                if(second-first<6||updated.contains(best)){ambiguous=true;continue;}
                final Track target=best;
                long competitors=candidates.stream().filter(other->other!=c&&compatible(target,other)&&Math.hypot(other.x-target.last.x,other.y-target.last.y)<=firstDistance(c,target)+6).count();
                if(competitors>0){ambiguous=true;continue;}
                if(best.missing){best.recovery=1;best.missing=false;}
                else if(best.recovery==1){
                    if(first<=3)best.recovery=2;
                    else {best.confirmed=false;best.anchor=c;best.rayX=rx;best.rayY=ry;best.observations=0;best.recovery=0;}
                }
                best.last=c;best.seen=time;best.observations++;
                double dx=c.x-best.anchor.x,dy=c.y-best.anchor.y,ux=rx-best.rayX,uy=ry-best.rayY;
                double movement=Math.hypot(dx,dy),input=Math.hypot(ux,uy);
                if(best.observations>=4&&movement>=6&&input>=6&&movement/input>=.15&&movement/input<=6&&
                    (dx*ux+dy*uy)/(movement*input)>=.85)best.confirmed=true;
            }else{
                best=new Track(c,rx,ry,time);tracks.add(best);
            }
            updated.add(best);
        }
        if(ambiguous){reset();reason=AppText.get(R.string.multiple_candidates_alignment_paused);return false;}
        List<Track> trusted=new ArrayList<>();
        for(Track t:tracks)if(!updated.contains(t)){t.missing=true;t.recovery=0;}
        for(Track t:updated)if(t.confirmed&&t.recovery!=1)trusted.add(t);
        if(trusted.size()>1){reset();reason=AppText.get(R.string.multiple_candidates_alignment_paused);return false;}
        if(locked!=null&&!updated.contains(locked)){
            reason=AppText.get(R.string.pointer_not_visible_reacquiring);return false;
        }
        if(trusted.size()==1){
            Track winner=trusted.get(0);
            if(locked!=null&&locked!=winner){reset();return false;}
            locked=winner;selected=winner.last;reason=AppText.get(R.string.pointer_confirmed);return true;
        }
        reason=AppText.get(candidates.isEmpty()?R.string.pointer_not_visible_reacquiring:R.string.pointer_not_identified_direct_control_still_works);
        return false;
    }
    private static double firstDistance(ArrowPointerDetector.Candidate c,Track t){return Math.hypot(c.x-t.last.x,c.y-t.last.y);}
}
