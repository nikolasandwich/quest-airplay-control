package com.localair.airplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class PointerCalibrationTest {
    @Test public void missingIdentityStillExpiresAndIndependentClockWorksWithoutObservations(){
        PointerCalibration c=new PointerCalibration();c.begin(1000);
        c.observe(Double.NaN,Double.NaN,181000,false,true);assertFalse(c.isActive());
        c.begin(200000);c.tick(379999);assertTrue(c.isActive());c.tick(380000);assertFalse(c.isActive());
        assertFalse(c.hasGain());assertFalse(c.hasPosition());
    }
    static PointerCalibration.Sample[] dataset(){
        PointerCalibration.Sample[] s=new PointerCalibration.Sample[10];
        for(int i=0;i<8;i++){
            boolean x=i%4<2;double sign=i%2==0?1:-1,rate=i<4?40:180,gx=i<4?1:1.5,gy=i<4?1.2:1.8;
            s[i]=new PointerCalibration.Sample(x?sign*40:0,x?0:sign*40,x?sign*40*gx:0,x?0:sign*40*gy,rate);
        }
        s[8]=new PointerCalibration.Sample(40,40,40,48,60);
        s[9]=new PointerCalibration.Sample(40,-40,60,-72,240);return s;
    }
    @Test public void fitsDirectionalSlowFastGainsAndChecksIndependentResidual(){
        var s=dataset();var g=PointerCalibration.fit(s);assertNotNull(g);
        assertEquals(1,g.slowX,0);assertEquals(1.2,g.slowY,0);assertEquals(1.5,g.fastX,0);assertEquals(1.8,g.fastY,0);
        assertTrue(PointerCalibration.validates(g,s[8],0));assertTrue(PointerCalibration.validates(g,s[9],1));
        assertFalse(PointerCalibration.validates(g,new PointerCalibration.Sample(40,40,80,48,60),0));
    }
    @Test public void rejectsInsufficientCoverageDisagreementAndNoSpeedSeparation(){
        var s=dataset();s[0]=null;assertNull(PointerCalibration.fit(s));
        s=dataset();s[1]=new PointerCalibration.Sample(-40,0,-100,0,40);assertNull(PointerCalibration.fit(s));
        s=dataset();for(int i=4;i<8;i++){var v=s[i];s[i]=new PointerCalibration.Sample(v.hx,v.hy,v.px,v.py,40);}assertNull(PointerCalibration.fit(s));
    }
    static class Run {
        PointerCalibration c=new PointerCalibration();long now=1000;double x=300,y=300;
        Run(){c.geometry(720,720);c.begin(now);settle();}
        void settle(){now+=500;c.observe(x,y,now,true,true);now+=500;c.observe(x,y,now,true,true);}
        void segment(int dx,int dy,double gx,double gy,boolean fast){
            for(int i=0;i<4;i++){now+=fast?100:400;c.report(dx,dy,now,true);x+=dx*gx;y+=dy*gy;}
            settle();
        }
        void completeMoves(){
            for(int i=0;i<8;i++){boolean h=i%4<2;int sign=i%2==0?1:-1;segment(h?sign*10:0,h?0:sign*10,i<4?1:1.5,i<4?1.2:1.8,i>=4);}
            segment(10,10,1,1.2,false);segment(10,-10,1.5,1.8,true);
        }
    }
    @Test public void oneFlowNeedsVerifiedMovesAndFreshReferenceBeforeApplying(){
        Run r=new Run();r.completeMoves();assertTrue(r.c.awaitingReference());assertNull(r.c.gain());
        assertFalse(r.c.confirm(r.x+30,r.y,r.now));assertFalse(r.c.confirm(r.x,r.y,r.now+300));
        r.c.observe(r.x,r.y,r.now+400,true,true);assertTrue(r.c.confirm(r.x,r.y,r.now+400));
        assertTrue(r.c.hasGain());assertTrue(r.c.hasPosition());assertFalse(r.c.isActive());
    }
    @Test public void timerAndUntrustedOrBusyObservationsCannotCalibrateOrRecenter(){
        Run r=new Run();r.completeMoves();assertTrue(r.c.confirm(r.x,r.y,r.now));var original=r.c.gain();
        r.c.externalAction();r.now+=400000;
        r.c.observe(r.x,r.y,r.now,false,true);r.c.observe(r.x,r.y,r.now+500,true,false);
        assertSame(original,r.c.gain());assertFalse(r.c.hasPosition());
        r.c.intervalMinutes(3);assertEquals(3,r.c.intervalMinutes());r.c.intervalMinutes(5);assertEquals(5,r.c.intervalMinutes());
    }
    @Test public void periodicReliableCoverageUpdatesGainWithoutResettingLostPosition(){
        Run r=new Run();r.completeMoves();assertTrue(r.c.confirm(r.x,r.y,r.now));
        r.c.externalAction();r.now+=300001;r.settle();
        for(int i=0;i<8;i++){boolean h=i%4<2;int sign=i%2==0?1:-1;r.segment(h?sign*10:0,h?0:sign*10,(i<4?1:1.5)*1.05,(i<4?1.2:1.8)*1.05,i>=4);}
        r.segment(10,10,1.05,1.26,false);r.segment(10,-10,1.575,1.89,true);
        assertEquals(1.05,r.c.gain().slowX,.0001);assertFalse(r.c.hasPosition());
    }
    @Test public void cancellationAndGeometryChangeDoNotApplyPartialData(){
        Run r=new Run();r.segment(10,0,1,1.2,false);r.c.cancel();assertFalse(r.c.hasGain());
        r=new Run();r.completeMoves();assertTrue(r.c.confirm(r.x,r.y,r.now));r.c.geometry(500,720);assertFalse(r.c.hasGain());assertFalse(r.c.hasPosition());
    }
    @Test public void currentIdentityLossOrInvalidRayCannotConfirmReference(){
        Run r=new Run();r.completeMoves();assertFalse(r.c.confirm(Double.NaN,r.y,r.now));
        r.c.observe(r.x,r.y,r.now+10,false,true);assertFalse(r.c.confirm(r.x,r.y,r.now+20));assertFalse(r.c.hasGain());
    }
}
