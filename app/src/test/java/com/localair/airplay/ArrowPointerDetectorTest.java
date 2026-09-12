package com.localair.airplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class ArrowPointerDetectorTest {
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
