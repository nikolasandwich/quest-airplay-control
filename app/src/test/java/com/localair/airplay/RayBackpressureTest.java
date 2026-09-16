package com.localair.airplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class RayBackpressureTest {
    private RayDeltaEngine.Delta sample(RayDeltaEngine r,float x,long t,boolean eligible,boolean ready){
        return r.event(x,100,800,600,t,eligible,ready,null);
    }
    @Test public void busyMotionIsCombinedOnlyWithNewMotion(){
        RayDeltaEngine r=new RayDeltaEngine();sample(r,100,0,true,true);
        assertEquals(0,sample(r,104,8,true,false).x);
        assertEquals(0,sample(r,108,16,true,false).x);
        assertEquals(12,sample(r,112,24,true,true).x);
        assertEquals(0,sample(r,112,32,true,true).x);
    }
    @Test public void stopAndReversalDoNotReplayOldDirection(){
        RayDeltaEngine r=new RayDeltaEngine();sample(r,100,0,true,true);
        sample(r,108,8,true,false);
        assertEquals(0,sample(r,108,16,true,true).x);
        assertEquals(1,sample(r,109,24,true,true).x);
        sample(r,120,32,true,false);
        assertEquals(-2,sample(r,118,40,true,true).x);
    }
    @Test public void expiryAndSaturationHaveNoUnboundedTail(){
        RayDeltaEngine r=new RayDeltaEngine();sample(r,100,0,true,true);
        sample(r,120,8,true,false);
        assertEquals(1,sample(r,121,57,true,true).x);
        sample(r,400,65,true,false);
        assertEquals(32,sample(r,500,73,true,true).x);
        assertEquals(0,sample(r,500,81,true,true).x);
    }
    @Test public void ineligibleResetAndEdgesDiscardPending(){
        RayDeltaEngine r=new RayDeltaEngine();sample(r,100,0,true,true);
        sample(r,108,8,true,false);sample(r,112,16,false,false);
        assertEquals(1,sample(r,113,24,true,true).x);
        sample(r,120,32,true,false);r.reset();
        assertEquals(0,sample(r,400,40,true,true).x);
        sample(r,420,48,true,false);sample(r,800,56,true,true);
        assertEquals(0,sample(r,100,64,true,true).x);
    }
    @Test public void equalAxesAndNormalizedWindowScaleRemainStable(){
        for(int width:new int[]{705,1120,2240}){
            RayDeltaEngine r=new RayDeltaEngine();r.event(0,0,width,1008,0,true,true,null);
            RayDeltaEngine.Delta d=r.event(width/100f,width/100f,width,1008,16,true,true,null);
            assertTrue(Math.abs(d.x-8)<=1);assertEquals(d.x,d.y);
        }
    }
    private int simulate(int hz,int ackMs,boolean revised){
        RayDeltaEngine r=new RayDeltaEngine();long readyAt=0;int total=0;
        for(int i=0;i<=hz;i++){
            long t=Math.round(i*1000.0/hz);float x=100+i*240f/hz;
            boolean ready=t>=readyAt;
            RayDeltaEngine.Delta d=revised?r.event(x,100,800,600,t,true,ready,null):r.event(x,100,800,600,t,ready);
            assertTrue(Math.abs(d.x)<=32);
            if(d.x!=0){total+=d.x;readyAt=t+ackMs;}
        }
        // The caller has no ACK/timer drain. A stationary event cannot release residual motion.
        assertEquals(0,r.event(340,100,800,600,1100,true,true,null).x);
        return total;
    }
    @Test public void eventRateAndAckDelayMatrix(){
        for(int hz:new int[]{30,60,90,120})for(int delay:new int[]{0,8,16,32,80}){
            int old=simulate(hz,delay,false), candidate=simulate(hz,delay,true);
            System.out.println("RAY_MATRIX hz="+hz+" ackMs="+delay+" old="+old+" candidate="+candidate+" ideal=240");
            assertTrue(candidate>=old);
            if(delay<=32)assertTrue("hz="+hz+" delay="+delay,candidate>=224);
            assertTrue(candidate<=240);
        }
    }
}
