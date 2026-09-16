package com.localair.airplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class RayDeltaEngineTest {
    private void zero(RayDeltaEngine.Delta d) { assertEquals(0,d.x); assertEquals(0,d.y); }
    @Test public void entryAndReentryNeverWarp() {
        RayDeltaEngine ray=new RayDeltaEngine();
        zero(ray.event(800,500,1000,600,0,true));
        assertEquals(8,ray.event(810,500,1000,600,16,true).x);
        ray.reset(); zero(ray.event(10,10,1000,600,32,true));
    }
    @Test public void blackBarsInvalidNumbersAndEdgesResetBaseline() {
        RayDeltaEngine ray=new RayDeltaEngine();
        for(float invalid:new float[]{-1,1000,Float.NaN,Float.POSITIVE_INFINITY}) {
            ray.event(200,200,1000,600,0,true);
            zero(ray.event(invalid,200,1000,600,16,true));
            zero(ray.event(300,200,1000,600,32,true));
            ray.reset();
        }
        zero(ray.event(0,0,0,0,48,true));
    }
    @Test public void resizeTimeGapAndBackwardsTimeCannotJump() {
        RayDeltaEngine ray=new RayDeltaEngine();
        ray.event(100,100,1000,600,0,true);
        zero(ray.event(300,300,600,1000,16,true));
        zero(ray.event(400,400,600,1000,200,true));
        zero(ray.event(100,100,600,1000,10,true));
    }
    @Test public void backpressureDropsMotionWithoutReplaying() {
        RayDeltaEngine ray=new RayDeltaEngine();
        ray.event(100,100,1000,600,0,true);
        zero(ray.event(500,300,1000,600,16,false));
        zero(ray.event(500,300,1000,600,32,true));
        assertEquals(8,ray.event(510,300,1000,600,48,true).x);
    }
    @Test public void jumpIsBoundedAndHasNoDeferredRemainder() {
        RayDeltaEngine ray=new RayDeltaEngine();
        ray.event(0,0,1000,600,0,true);
        RayDeltaEngine.Delta d=ray.event(900,500,1000,600,16,true);
        assertEquals(32,d.x); assertEquals(32,d.y);
        zero(ray.event(900,500,1000,600,32,true));
    }
    @Test public void axesUseSameScaleAndPreserveSmallMotion() {
        RayDeltaEngine ray=new RayDeltaEngine();
        ray.event(10,10,800,400,0,true);
        zero(ray.event(10.5f,10.5f,800,400,16,true));
        RayDeltaEngine.Delta d=ray.event(11,11,800,400,32,true);
        assertEquals(1,d.x); assertEquals(1,d.y);
        d=ray.event(9,9,800,400,48,true);
        assertEquals(-2,d.x); assertEquals(-2,d.y);
    }
}
