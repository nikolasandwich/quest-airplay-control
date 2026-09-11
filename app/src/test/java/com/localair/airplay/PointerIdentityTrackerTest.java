package com.localair.airplay;

import org.junit.Test;
import static org.junit.Assert.*;
import java.util.List;

public class PointerIdentityTrackerTest {
    private ArrowPointerDetector.Candidate c(int x,double score){return new ArrowPointerDetector.Candidate(x,40,7,10,score);}
    @Test public void staticPageArrowCannotAcquireIdentity(){
        PointerIdentityTracker t=new PointerIdentityTracker();
        for(int i=0;i<10;i++)assertFalse(t.update(List.of(c(40,.99)),i*10,40,i*150));
    }
    @Test public void lowScoreFalsePositiveNeverAcquires(){
        PointerIdentityTracker t=new PointerIdentityTracker();
        for(int i=0;i<10;i++)assertFalse(t.update(List.of(c(40+i*5,.732)),i*10,40,i*150));
    }
    @Test public void correlatedMovementAcquiresAndStationaryArrowStaysLocked(){
        PointerIdentityTracker t=new PointerIdentityTracker();
        assertFalse(t.update(List.of(c(40,.95)),40,40,0));
        assertFalse(t.update(List.of(c(45,.95)),45,40,150));
        assertTrue(t.update(List.of(c(50,.95)),50,40,300));
        assertTrue(t.update(List.of(c(50,.95)),50,40,450));
        assertFalse(t.update(List.of(c(50,.95),c(80,.95)),50,40,600));
        assertFalse(t.update(List.of(c(50,.95)),50,40,750));
    }
    @Test public void oppositeMotionAndOldFramesDoNotAcquire(){
        PointerIdentityTracker t=new PointerIdentityTracker();
        for(int i=0;i<4;i++)assertFalse(t.update(List.of(c(40+i*5,.95)),80-i*5,40,i*150));
        assertFalse(t.update(List.of(c(60,.95)),60,40,100));
    }
}
