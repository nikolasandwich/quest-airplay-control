package com.localair.airplay;

/** Horizon 2D scroll-delta adapter, NOT a raw thumbstick axis reader.
 * Event-driven only: no timer, replay queue, or output after events stop. */
public final class ScrollDeltaEngine {
    private double sensitivity=1, pending;
    private long previous=-1, emitted=-1;
    private boolean calibration, latched;
    private int budget=60, remaining=60, direction;
    public void configure(boolean singleFlick,double gain,int steps){
        if(!Double.isFinite(gain)||gain<.25||gain>2||steps<1||steps>12)throw new IllegalArgumentException();
        calibration=singleFlick;sensitivity=gain;budget=steps*10;stop();
    }
    public void stop(){pending=0;previous=-1;emitted=-1;latched=false;remaining=budget;direction=0;}
    public int event(float delta,long now,boolean allowed,boolean transportReady){
        if(!allowed||!Float.isFinite(delta)){stop();return 0;}
        if(previous>=0&&(now<previous||now-previous>100))stop();
        previous=now;
        // Observed 2D deltas include zero; this is a scroll noise threshold,
        // not a physical thumbstick deadzone.
        if(Math.abs(delta)<=.004){stop();return 0;}
        int sign=delta>0?1:-1;
        if(direction!=0&&direction!=sign){pending=0;if(calibration&&latched)return 0;}
        direction=sign;
        if(calibration&&!latched){latched=true;remaining=budget;}
        if(calibration&&remaining==0)return 0;
        if(!transportReady){pending=0;return 0;}
        double magnitude=Math.min(1,Math.abs(delta));
        pending=Math.min(10,pending+magnitude*6*sensitivity*Math.pow(Math.min(1,magnitude/.34),.3));
        if(emitted>=0&&now-emitted<40)return 0;
        int amount=Math.min(10,(int)pending);
        if(calibration)amount=Math.min(amount,remaining);
        if(amount==0)return 0;
        pending-=amount;emitted=now;
        if(calibration)remaining-=amount;
        return sign*amount;
    }
}
