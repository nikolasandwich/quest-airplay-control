package com.localair.airplay;

/** Recognizes one dominant horizontal ACTION_SCROLL burst, not a raw joystick.
 * The idle interval is checked only on a new event; it never emits by timer. */
public final class HorizontalGestureGate {
    private long previous=-1;
    private boolean latched;
    private double accumulated;
    private int direction;
    public void reset(){previous=-1;latched=false;accumulated=0;direction=0;}
    public int event(float horizontal,float vertical,long now,boolean allowed){
        if(!allowed||!Float.isFinite(horizontal)||!Float.isFinite(vertical)){reset();return 0;}
        if(previous>=0 && (now<previous||now-previous>350))reset();
        previous=now;
        if(Math.abs(horizontal)<.008){latched=false;accumulated=0;direction=0;return 0;}
        if(latched||Math.abs(horizontal)<Math.abs(vertical)*1.3||Math.abs(horizontal)<.02)return 0;
        int sign=horizontal>0?1:-1;
        if(sign!=direction){accumulated=0;direction=sign;}
        accumulated+=Math.abs(horizontal);
        if(accumulated<.15)return 0;
        latched=true;return sign;
    }
}
