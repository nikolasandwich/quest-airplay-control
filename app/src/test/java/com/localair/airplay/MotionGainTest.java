package com.localair.airplay;

import org.junit.Test;
import static org.junit.Assert.*;

public class MotionGainTest {
    @Test public void dragGainScalesDistanceAndAlwaysReleases(){
        for(double gain:new double[]{.25,1,2})for(int direction:new int[]{-1,1}){
            PointerAction action=new PointerAction();
            assertTrue(action.start(direction,gain));
            int distance=0,releases=0,packets=0;
            while(action.active()&&packets++<20){
                PointerAction.Packet packet=action.next();distance+=packet.x;
                if(packet.buttons==0)releases++;
                action.attempted(packet);action.acknowledged(packet,true);
            }
            assertFalse(action.active());assertEquals(1,releases);
            assertEquals((int)(240*gain)*direction,distance);
        }
    }
    @Test public void higherScrollGainIncreasesOutputAndStopDropsRemainder(){
        int previous=0;
        for(double gain:new double[]{.25,1,2}){
            ScrollDeltaEngine engine=new ScrollDeltaEngine();engine.configure(false,gain,6);
            int total=0;
            for(int i=0;i<20;i++)total+=engine.event(.25f,i*45,true,true);
            assertTrue(total>previous);previous=total;
            engine.stop();assertEquals(0,engine.event(0,1000,true,true));
            assertEquals(0,engine.event(.5f,1045,false,true));
        }
    }
}
