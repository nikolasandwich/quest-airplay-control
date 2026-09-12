package com.localair.airplay;

/** Relative ray motion, with no assumption about the iPad cursor position.
 * Coordinates belong to the fitted video rectangle, excluding letterboxing. */
public final class RayDeltaEngine {
    public static final class Delta {
        public final int x, y;
        Delta(int x, int y) { this.x=x; this.y=y; }
    }
    private float previousX, previousY;
    private int previousWidth, previousHeight;
    private long previousTime=-1;
    private double remainderX, remainderY;
    private double pendingX, pendingY;
    private long pendingSince=-1;
    private double discardedUnits, mergedUnits;
    public double discardedUnits(){return discardedUnits;}
    public double mergedUnits(){return mergedUnits;}
    public static final long MAX_PENDING_MS=48;
    public void reset() { previousTime=-1; clearMotion(); }
    private void discardPending(){discardedUnits+=Math.abs(pendingX)+Math.abs(pendingY);pendingX=pendingY=0;pendingSince=-1;}
    private void clearMotion(){discardPending();remainderX=remainderY=0;}
    public Delta event(float x, float y, int width, int height, long time, boolean ready) {
        return event(x,y,width,height,time,ready,null);
    }
    public Delta event(float x,float y,int width,int height,long time,boolean ready,PointerCalibration.Gain gain){
        return event(x,y,width,height,time,ready,ready,gain);
    }
    public Delta event(float x,float y,int width,int height,long time,boolean eligible,boolean ready,PointerCalibration.Gain gain){
        if (!Float.isFinite(x)||!Float.isFinite(y)||width<=0||height<=0||
                x<0||y<0||x>=width||y>=height) { reset(); return new Delta(0,0); }
        boolean baseline=previousTime<0||time<=previousTime||time-previousTime>150||
                width!=previousWidth||height!=previousHeight;
        double dx=(x-previousX)*800.0/width, dy=(y-previousY)*800.0/width;
        if(gain!=null){
            double scale=Math.min(1,720.0/Math.max(width,height));
            double speed=Math.hypot(x-previousX,y-previousY)*scale*1000/Math.max(1,time-previousTime);
            dx=(x-previousX)*scale/gain.x(speed);dy=(y-previousY)*scale/gain.y(speed);
        }
        previousX=x; previousY=y; previousWidth=width; previousHeight=height; previousTime=time;
        if (baseline||!eligible) { clearMotion(); return new Delta(0,0); }
        // Never drain on an ACK or timer. A stationary event discards busy-period motion.
        if(dx==0&&dy==0){discardPending();return new Delta(0,0);}
        if(pendingSince>=0&&time-pendingSince>MAX_PENDING_MS)discardPending();
        // Do not make a reversal first pay back motion in the old direction.
        if(dx*pendingX<0){discardedUnits+=Math.abs(pendingX);pendingX=0;}
        if(dy*pendingY<0){discardedUnits+=Math.abs(pendingY);pendingY=0;}
        if(!ready){
            if(pendingSince<0)pendingSince=time;
            double xSum=pendingX+dx,ySum=pendingY+dy;
            pendingX=Math.max(-32,Math.min(32,xSum));
            pendingY=Math.max(-32,Math.min(32,ySum));
            discardedUnits+=Math.abs(xSum-pendingX)+Math.abs(ySum-pendingY);
            return new Delta(0,0);
        }
        // A single discontinuity cannot fling the remote cursor; excess is discarded.
        double totalX=Math.max(-32,Math.min(32,dx+pendingX+remainderX));
        double totalY=Math.max(-32,Math.min(32,dy+pendingY+remainderY));
        discardedUnits+=Math.abs(dx+pendingX+remainderX-totalX)+Math.abs(dy+pendingY+remainderY-totalY);
        mergedUnits+=Math.abs(pendingX)+Math.abs(pendingY);
        pendingX=pendingY=0;pendingSince=-1;
        int outX=(int)totalX, outY=(int)totalY;
        remainderX=totalX-outX; remainderY=totalY-outY;
        return new Delta(outX,outY);
    }
}
