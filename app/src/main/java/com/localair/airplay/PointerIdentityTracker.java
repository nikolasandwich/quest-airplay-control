package com.localair.airplay;

import java.util.List;

/** Require a unique high-score arrow and user-correlated movement before locking.
 * This conservative heuristic can still fail on matching animated page content. */
public final class PointerIdentityTracker {
    private ArrowPointerDetector.Candidate previous,anchor;
    private double rayX,rayY;
    private int frames;
    private long last=-1;
    private boolean locked;
    private ArrowPointerDetector.Candidate recent,recovery;
    private long recentAt=-1;
    private long observedAt=-1;
    public String reason=AppText.get(R.string.waiting_for_initial_detection);
    public void reset(){previous=anchor=recent=recovery=null;frames=0;last=recentAt=observedAt=-1;locked=false;reason=AppText.get(R.string.waiting_for_initial_detection);}
    private boolean lost(String why,boolean keepRecent){
        previous=anchor=recovery=null;frames=0;last=-1;locked=false;
        if(!keepRecent){recent=null;recentAt=-1;}
        reason=why;return false;
    }
    public boolean update(List<ArrowPointerDetector.Candidate> candidates,double rx,double ry,long time){
        if(observedAt>=0&&time<=observedAt)return lost(AppText.get(R.string.duplicate_or_out_of_order_observation),false);
        observedAt=time;
        if(!Double.isFinite(rx)||!Double.isFinite(ry))return lost(AppText.get(R.string.ray_position_unavailable),false);
        if(last>=0&&(time<=last||time-last>400))return lost(AppText.get(R.string.observation_interval_expired),false);
        if(recentAt>=0&&(time<recentAt||time-recentAt>500)){recent=null;recovery=null;recentAt=-1;}
        if(candidates.size()!=1)return lost(candidates.isEmpty()?AppText.get(R.string.pointer_not_visible_reacquiring):AppText.get(R.string.multiple_candidates_alignment_paused),candidates.isEmpty());
        var c=candidates.get(0);
        if(previous!=null&&c.circular!=previous.circular)return lost(AppText.get(R.string.position_jumped_detecting_again),false);
        if(recent!=null&&c.circular!=recent.circular)return lost(AppText.get(R.string.position_jumped_detecting_again),false);
        if(c.score<(locked?.80:.86))return lost(AppText.get(R.string.outline_unclear_alignment_paused),true);
        if(previous!=null&&Math.hypot(c.x-previous.x,c.y-previous.y)>100)return lost(AppText.get(R.string.position_jumped_detecting_again),false);
        // A recent established identity may recover only after two unique, strong,
        // spatially consistent observations. No command is permitted during the gap.
        if(!locked&&recent!=null){
            if(c.score>=.90&&Math.hypot(c.x-recent.x,c.y-recent.y)<=12){
                if(recovery!=null&&Math.hypot(c.x-recovery.x,c.y-recovery.y)<=3){
                    locked=true;reason=AppText.get(R.string.reacquired);
                }else {recovery=c;previous=c;last=time;reason=AppText.get(R.string.confirming_reacquisition_1_2);return false;}
            }else {recent=recovery=null;recentAt=-1;}
        }
        if(anchor==null){anchor=c;rayX=rx;rayY=ry;}
        previous=c;last=time;frames++;
        double dx=c.x-anchor.x,dy=c.y-anchor.y,drx=rx-rayX,dry=ry-rayY;
        if(frames>=3&&Math.hypot(dx,dy)>=4&&Math.hypot(drx,dry)>=4&&dx*drx+dy*dry>0)locked=true;
        if(locked){recent=c;recentAt=time;recovery=null;reason=AppText.get(R.string.pointer_confirmed);}
        else reason=AppText.get(R.string.pointer_not_identified_direct_control_still_works);
        return locked;
    }
}
