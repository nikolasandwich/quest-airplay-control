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
    private PointerIdentityTracker locked(){
        PointerIdentityTracker t=new PointerIdentityTracker();
        t.update(List.of(c(40,.95)),40,40,0);
        t.update(List.of(c(45,.95)),45,40,100);
        assertTrue(t.update(List.of(c(50,.95)),50,40,200));return t;
    }
    @Test public void briefMissingFrameRecoversOnlyAfterTwoStrongConsistentFrames(){
        var t=locked();
        assertFalse(t.update(List.of(),50,40,280));
        assertFalse(t.update(List.of(c(51,.95)),50,40,360));
        assertTrue(t.update(List.of(c(51,.95)),50,40,440));
    }
    @Test public void ambiguityErasesRecoveryEvenIfNearbyCandidateReturns(){
        var t=locked();t.update(List.of(c(50,.95),c(80,.95)),50,40,280);
        for(int i=0;i<5;i++)assertFalse(t.update(List.of(c(50,.95)),50,40,360+i*80));
    }
    @Test public void expiredOrDistantOrWeakCandidateCannotUseRecovery(){
        for(int mode=0;mode<3;mode++){
            var t=locked();assertFalse(t.update(List.of(),50,40,280));
            long start=mode==0?800:360;
            for(int i=0;i<3;i++)assertFalse(t.update(List.of(c(mode==1?90:50,mode==2?.88:.95)),50,40,start+i*80));
        }
    }
    @Test public void duplicateRecoveryObservationDoesNotCountAsSecondFrame(){
        var t=locked();t.update(List.of(),50,40,280);
        assertFalse(t.update(List.of(c(50,.95)),50,40,360));
        assertFalse(t.update(List.of(c(50,.95)),50,40,360));
        assertFalse(t.update(List.of(c(50,.95)),50,40,440));
    }
}
