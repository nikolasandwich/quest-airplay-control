package com.localair.airplay;
import org.junit.Test;
import static org.junit.Assert.*;
public class VideoUiStateTest {
    @Test public void pipKeepsOutputAndHidesWaitingEvenDuringNotifications(){
        assertFalse(VideoUiState.parkOnPause(true));assertTrue(VideoUiState.parkOnPause(false));
        for(boolean ready:new boolean[]{false,true})for(boolean frames:new boolean[]{false,true})assertFalse(VideoUiState.showWaiting(true,ready,frames));
    }
    @Test public void fullWindowRequiresItsOwnReadySurfaceAndConfirmedFrames(){
        assertFalse(VideoUiState.showWaiting(false,true,true));
        assertTrue(VideoUiState.showWaiting(false,true,false));assertTrue(VideoUiState.showWaiting(false,false,true));
    }
}
