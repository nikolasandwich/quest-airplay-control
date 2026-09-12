package com.localair.airplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class SettleControllerTest {
    @Test public void convergesInBoundedSimulationWithDifferentHostGains(){
        for(double gain:new double[]{.5,1,2,3}) {
            SettleController c=new SettleController();c.target(120,120,0);double x=80,y=85;int count=0;
            for(long t=0;t<10500;t+=150){var step=c.observe(x,y,t,t,true,true,true);if(step!=null){assertTrue(Math.abs(step.x)<=24);assertTrue(Math.abs(step.y)<=24);x+=step.x*gain;y+=step.y*gain;count++;}}
            assertTrue("gain="+gain,Math.hypot(120-x,120-y)<=3);assertTrue(count<=24);
        }
    }
    @Test public void focusLossStopsUntilFreshRayMovement(){
        SettleController c=new SettleController();c.target(100,100,0);
        assertNull(c.observe(50,50,500,500,true,false,true));
        assertNull(c.observe(50,50,1000,1000,true,true,true));
        c.target(120,100,1100);assertNotNull(c.observe(50,50,1600,1600,true,true,true));
    }
    @Test public void staleAmbiguousOrMissingPositionNeverCommands(){
        for(int mode=0;mode<3;mode++){
            SettleController c=new SettleController();c.target(100,100,0);
            assertNull(c.observe(mode==2?Double.NaN:50,50,mode==0?0:500,500,mode!=1,true,true));
        }
    }
    @Test public void noBacklogDuringBusyTransport(){
        SettleController c=new SettleController();c.target(100,100,0);
        assertNull(c.observe(50,50,500,500,true,true,false));
        var first=c.observe(50,50,650,650,true,true,true);assertNotNull(first);
        assertNull(c.observe(50,50,700,700,true,true,true));
    }
    @Test public void frozenOrWrongDirectionHostStops(){
        SettleController c=new SettleController();c.target(100,100,0);int count=0;
        for(long t=500;t<5000;t+=400)if(c.observe(50,50,t,t,true,true,true)!=null)count++;
        assertEquals(3,count);
    }
    @Test public void duplicateFramesCannotAdvance(){
        SettleController c=new SettleController();c.target(100,100,0);
        assertNotNull(c.observe(50,50,500,500,true,true,true));
        assertNull(c.observe(80,80,500,650,true,true,true));
    }
    @Test public void failedSubmissionIsNotRetried(){
        SettleController c=new SettleController();c.target(100,100,0);c.rejected();
        assertNull(c.observe(50,50,500,500,true,true,true));
    }
    @Test public void resetCancelsTargetAndNoImplicitClickExists(){
        SettleController c=new SettleController();c.target(100,100,0);c.reset();
        assertNull(c.observe(50,50,500,500,true,true,true));
    }
    @Test public void quarterSecondFeedbackDelayStillConvergesInSimulation(){
        SettleController c=new SettleController();c.target(120,120,0);
        double x=80,y=85;java.util.ArrayList<double[]> history=new java.util.ArrayList<>();int count=0;
        for(long t=0;t<=10500;t+=50){
            history.add(new double[]{t,x,y});double ox=80,oy=85;
            for(double[] h:history){if(h[0]>t-250)break;ox=h[1];oy=h[2];}
            if(t%150==0){var step=c.observe(ox,oy,t,t,true,true,true);if(step!=null){x+=step.x;y+=step.y;count++;}}
        }
        assertTrue(Math.hypot(120-x,120-y)<=3);assertTrue(count<=24);
    }
    @Test public void largeErrorCannotRunForever(){
        SettleController c=new SettleController();c.target(700,700,0);double x=0,y=0;int count=0;
        for(long t=0;t<20000;t+=400){var step=c.observe(x,y,t,t,true,true,true);if(step!=null){x+=step.x*.1;y+=step.y*.1;count++;}}
        assertTrue(count>0);assertTrue(count<=24);
        assertNull(c.observe(x,y,20500,20500,true,true,true));
    }
    @Test public void firstCorrectionStartsAfterOneHundredMilliseconds(){
        SettleController c=new SettleController();c.target(200,200,0);
        assertNull(c.observe(50,50,99,99,true,true,true));
        var step=c.observe(50,50,100,100,true,true,true);
        assertNotNull(step);assertEquals(24,step.x);assertEquals(24,step.y);
    }
    @Test public void visibleFeedbackCanAdvanceBeforeOldFixedDelay(){
        SettleController c=new SettleController();c.target(200,200,0);
        assertNotNull(c.observe(50,50,100,100,true,true,true));
        assertNull(c.observe(51,50,200,200,true,true,true));
        assertNotNull(c.observe(74,74,220,220,true,true,true));
    }
    @Test public void delayedFeedbackAndHostGainGridConverges(){
        for(int delay:new int[]{0,100,250,400})for(double gain:new double[]{.5,1,2,3}){
            SettleController c=new SettleController();c.target(200,200,0);
            double x=80,y=85;int count=0;
            java.util.ArrayList<double[]> history=new java.util.ArrayList<>();
            for(long t=0;t<=10500;t+=20){
                history.add(new double[]{t,x,y});double ox=80,oy=85;
                for(double[] h:history){if(h[0]>t-delay)break;ox=h[1];oy=h[2];}
                if(t%60==0){var step=c.observe(ox,oy,t,t,true,true,true);if(step!=null){x+=step.x*gain;y+=step.y*gain;count++;}}
            }
            assertTrue("delay="+delay+" gain="+gain+" error="+Math.hypot(200-x,200-y),Math.hypot(200-x,200-y)<=3);
            assertTrue(count<=24);
        }
    }
    @Test public void observationGapDoesNotResetTargetBudgetOrTerminalStop(){
        SettleController c=new SettleController();c.target(100,100,0);
        assertNotNull(c.observe(50,50,100,100,true,true,true));
        // No observations are submitted while identity is uncertain.
        assertNull(c.observe(60,60,10500,10500,true,true,true));
        assertTrue(c.status.contains("上限"));
        assertNull(c.observe(70,70,10600,10600,true,true,true));
    }
    @Test public void fastModeWithDelayedFeedbackDroppedObservationsAndTargetChange(){
        for(int delay:new int[]{0,100,250,400})for(double gain:new double[]{.5,1,2,3}){
            SettleController c=new SettleController();c.setFast(true);c.target(190,190,0);
            double x=80,y=85;java.util.ArrayList<double[]> history=new java.util.ArrayList<>();int count=0;
            for(long t=0;t<=12000;t+=20){
                if(t==1000)c.target(200,200,t);
                history.add(new double[]{t,x,y});double ox=80,oy=85;
                for(double[] h:history){if(h[0]>t-delay)break;ox=h[1];oy=h[2];}
                if(t%60==0&&t%420!=0){var s=c.observe(ox,oy,t,t,true,true,true);if(s!=null){assertTrue(Math.abs(s.x)<=32&&Math.abs(s.y)<=32);x+=s.x*gain;y+=s.y*gain;count++;}}
            }
            assertTrue("delay="+delay+" gain="+gain+" error="+Math.hypot(200-x,200-y),Math.hypot(200-x,200-y)<=3);assertTrue(count<=48);
        }
    }
    @Test public void fastStillWaitsForFeedbackAndStopsOnUntrustedInput(){
        SettleController c=new SettleController();c.setFast(true);c.target(200,200,0);
        assertNull(c.observe(50,50,59,59,true,true,true));assertNotNull(c.observe(50,50,60,60,true,true,true));
        assertNull(c.observe(50,50,120,120,true,true,true));assertNull(c.observe(80,80,180,180,false,true,true));
        assertNull(c.observe(80,80,600,600,true,true,true));
    }
}
