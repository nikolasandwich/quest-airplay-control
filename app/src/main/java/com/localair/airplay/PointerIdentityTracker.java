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
    public String reason="等待首次识别";
    public void reset(){previous=anchor=recent=recovery=null;frames=0;last=recentAt=observedAt=-1;locked=false;reason="等待首次识别";}
    private boolean lost(String why,boolean keepRecent){
        previous=anchor=recovery=null;frames=0;last=-1;locked=false;
        if(!keepRecent){recent=null;recentAt=-1;}
        reason=why;return false;
    }
    public boolean update(List<ArrowPointerDetector.Candidate> candidates,double rx,double ry,long time){
        if(observedAt>=0&&time<=observedAt)return lost("重复或倒序观察",false);
        observedAt=time;
        if(!Double.isFinite(rx)||!Double.isFinite(ry))return lost("射线位置不可用",false);
        if(last>=0&&(time<=last||time-last>400))return lost("观察间隔失效",false);
        if(recentAt>=0&&(time<recentAt||time-recentAt>500)){recent=null;recovery=null;recentAt=-1;}
        if(candidates.size()!=1)return lost(candidates.isEmpty()?"暂未看到指针，正在重捕获":"多个候选，暂停对齐",candidates.isEmpty());
        var c=candidates.get(0);
        if(c.score<(locked?.80:.86))return lost("轮廓不清，暂停对齐",true);
        if(previous!=null&&Math.hypot(c.x-previous.x,c.y-previous.y)>100)return lost("位置跳变，重新识别",false);
        // A recent established identity may recover only after two unique, strong,
        // spatially consistent observations. No command is permitted during the gap.
        if(!locked&&recent!=null){
            if(c.score>=.90&&Math.hypot(c.x-recent.x,c.y-recent.y)<=12){
                if(recovery!=null&&Math.hypot(c.x-recovery.x,c.y-recovery.y)<=3){
                    locked=true;reason="已重捕获";
                }else {recovery=c;previous=c;last=time;reason="正在确认重捕获（1/2）";return false;}
            }else {recent=recovery=null;recentAt=-1;}
        }
        if(anchor==null){anchor=c;rayX=rx;rayY=ry;}
        previous=c;last=time;frames++;
        double dx=c.x-anchor.x,dy=c.y-anchor.y,drx=rx-rayX,dry=ry-rayY;
        if(frames>=3&&Math.hypot(dx,dy)>=4&&Math.hypot(drx,dry)>=4&&dx*drx+dy*dry>0)locked=true;
        if(locked){recent=c;recentAt=time;recovery=null;reason="已确认指针";}
        else reason="尚未认出鼠标指针；直接控制仍可用";
        return locked;
    }
}
