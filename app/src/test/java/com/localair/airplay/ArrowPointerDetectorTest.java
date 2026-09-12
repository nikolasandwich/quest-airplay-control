package com.localair.airplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArrowPointerDetectorTest {
    @Test public void circleNeedsCorrelatedMovementBeforeCorrection(){
        PointerIdentityTracker tracker=new PointerIdentityTracker();
        for(int i=0;i<5;i++)assertFalse(tracker.update(java.util.List.of(new ArrowPointerDetector.Candidate(20,20,12,12,.99,true)),20+i*3,20,i*50));
        tracker.reset();
        assertFalse(tracker.update(java.util.List.of(new ArrowPointerDetector.Candidate(20,20,12,12,.99,true)),20,20,0));
        assertFalse(tracker.update(java.util.List.of(new ArrowPointerDetector.Candidate(23,20,12,12,.99,true)),23,20,50));
        assertTrue(tracker.update(java.util.List.of(new ArrowPointerDetector.Candidate(26,20,12,12,.99,true)),26,20,100));
        assertFalse(tracker.update(java.util.List.of(new ArrowPointerDetector.Candidate(27,20,7,10,.99)),27,20,150));
    }
    @Test public void detectsSmallDisksAndRingsAtTheirCenter(){
        for(int size:new int[]{8,12,18,24})for(double inner:new double[]{0,.65}){
            int[] pixels=new int[80*80];java.util.Arrays.fill(pixels,0xffaaaaaa);
            for(int y=0;y<size;y++)for(int x=0;x<size;x++){
                double dx=(x+.5-size/2.0)/(size/2.0),dy=(y+.5-size/2.0)/(size/2.0);
                double r=dx*dx+dy*dy;
                if(r<=1&&r>=inner*inner)pixels[(y+20)*80+x+20]=0xff111111;
            }
            var candidates=new ArrowPointerDetector().detect(pixels,80,80);
            assertTrue("circle size="+size+" inner="+inner,candidates.stream().anyMatch(c->c.circular&&Math.abs(c.x-(20+size/2))<=1&&Math.abs(c.y-(20+size/2))<=1));
        }
    }
    @Test public void squareDoesNotBecomeCircularPointer(){
        int[] pixels=new int[80*80];java.util.Arrays.fill(pixels,0xffaaaaaa);
        for(int y=20;y<32;y++)for(int x=20;x<32;x++)pixels[y*80+x]=0xff111111;
        assertTrue(new ArrowPointerDetector().detect(pixels,80,80).stream().noneMatch(c->c.circular));
    }
    @Test public void reusedBuffersMatchFreshDetectorAcrossFramesAndResizes(){
        ArrowPointerDetector reused=new ArrowPointerDetector();
        String[] shape={"##.....","###....","####...","#####..","######.","#######","#######","####...","###....","#......"};
        for(int pass=0;pass<8;pass++){
            int w=pass%3==0?60:80,h=pass%3==0?80:60;
            int[] pixels=new int[w*h];java.util.Arrays.fill(pixels,0xffaaaaaa);
            if(pass%2==0)for(int y=0;y<10;y++)for(int x=0;x<7;x++)
                if(shape[y].charAt(x)=='#')pixels[(y+20)*w+x+20]=0xff000000;
            var actual=reused.detect(pixels,w,h);
            var expected=new ArrowPointerDetector().detect(pixels,w,h);
            assertEquals(expected.size(),actual.size());
            if(pass%2==0)assertFalse(actual.isEmpty());else assertTrue(actual.isEmpty());
            for(int i=0;i<actual.size();i++){
                assertEquals(expected.get(i).x,actual.get(i).x);
                assertEquals(expected.get(i).y,actual.get(i).y);
                assertEquals(expected.get(i).score,actual.get(i).score,0);
            }
        }
    }
}
