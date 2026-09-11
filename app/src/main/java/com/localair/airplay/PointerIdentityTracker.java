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
    public void reset(){previous=anchor=null;frames=0;last=-1;locked=false;}
    public boolean update(List<ArrowPointerDetector.Candidate> candidates,double rx,double ry,long time){
        if(candidates.size()!=1||!Double.isFinite(rx)||!Double.isFinite(ry)){reset();return false;}
        var c=candidates.get(0);
        if(c.score<(locked?.80:.86)||last>=0&&(time<=last||time-last>400)||previous!=null&&Math.hypot(c.x-previous.x,c.y-previous.y)>100){reset();return false;}
        if(anchor==null){anchor=c;rayX=rx;rayY=ry;}
        previous=c;last=time;frames++;
        double dx=c.x-anchor.x,dy=c.y-anchor.y,drx=rx-rayX,dry=ry-rayY;
        if(frames>=3&&Math.hypot(dx,dy)>=4&&Math.hypot(drx,dry)>=4&&dx*drx+dy*dry>0)locked=true;
        return locked;
    }
}
