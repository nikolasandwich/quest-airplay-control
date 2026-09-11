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
    public void reset() { previousTime=-1; remainderX=remainderY=0; }
    public Delta event(float x, float y, int width, int height, long time, boolean ready) {
        if (!Float.isFinite(x)||!Float.isFinite(y)||width<=0||height<=0||
                x<0||y<0||x>=width||y>=height) { reset(); return new Delta(0,0); }
        boolean baseline=previousTime<0||time<=previousTime||time-previousTime>150||
                width!=previousWidth||height!=previousHeight;
        double dx=(x-previousX)*800.0/width, dy=(y-previousY)*800.0/width;
        previousX=x; previousY=y; previousWidth=width; previousHeight=height; previousTime=time;
        if (baseline||!ready) { remainderX=remainderY=0; return new Delta(0,0); }
        // A single discontinuity cannot fling the remote cursor; excess is discarded.
        double totalX=Math.max(-32,Math.min(32,dx+remainderX));
        double totalY=Math.max(-32,Math.min(32,dy+remainderY));
        int outX=(int)totalX, outY=(int)totalY;
        remainderX=totalX-outX; remainderY=totalY-outY;
        return new Delta(outX,outY);
    }
}
