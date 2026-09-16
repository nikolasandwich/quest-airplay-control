package com.localair.airplay;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MultiPointerTrackerTest {
    private ArrowPointerDetector.Candidate circle(int x,int y){return new ArrowPointerDetector.Candidate(x,y,12,12,.99,true);}
    @Test public void movingPointerWinsAgainstStationaryMenuAndStaysWhenStopped(){
        MultiPointerTracker tracker=new MultiPointerTracker();
        for(int i=0;i<4;i++){
            boolean trusted=tracker.update(List.of(circle(20+i*3,20),circle(100,100)),20+i*3,20,i*50);
            assertEquals(i==3,trusted);
        }
        assertEquals(29,tracker.selected().x);
        assertTrue(tracker.update(List.of(circle(29,20),circle(100,100)),29,20,200));
    }
    @Test public void sharedPageMotionRemainsAmbiguous(){
        MultiPointerTracker tracker=new MultiPointerTracker();
        for(int i=0;i<4;i++)assertFalse(tracker.update(List.of(circle(20+i*3,20),circle(100+i*3,100)),20+i*3,20,i*50));
        assertNull(tracker.selected());
    }
    @Test public void briefLossRequiresTwoConsistentObservations(){
        MultiPointerTracker tracker=new MultiPointerTracker();
        for(int i=0;i<4;i++)tracker.update(List.of(circle(20+i*3,20)),20+i*3,20,i*50);
        assertFalse(tracker.update(List.of(),29,20,200));
        assertFalse(tracker.update(List.of(circle(29,20)),29,20,250));
        assertTrue(tracker.update(List.of(circle(29,20)),29,20,300));
    }
    @Test public void stationaryTargetsCannotAcquireAndStaleHistoryIsDiscarded(){
        MultiPointerTracker tracker=new MultiPointerTracker();
        for(int i=0;i<5;i++)assertFalse(tracker.update(List.of(circle(20,20)),20+i*3,20,i*50));
        assertFalse(tracker.update(List.of(circle(23,20)),23,20,1000));
        assertNull(tracker.selected());
    }
    @Test public void competingNearbyCirclesNeverChooseArbitrarily(){
        MultiPointerTracker tracker=new MultiPointerTracker();
        tracker.update(List.of(circle(20,20)),20,20,0);
        assertFalse(tracker.update(List.of(circle(22,20),circle(24,20)),23,20,50));
        assertNull(tracker.selected());
    }
}
