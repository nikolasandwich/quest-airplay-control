package com.localair.airplay;

/** One visibility policy for frame notifications and all window transitions. */
public final class VideoUiState {
    public static boolean parkOnPause(boolean inPip){return !inPip;}
    public static boolean showWaiting(boolean inPip,boolean surfaceReady,boolean currentOwnerHasFrames){
        return !inPip && !(surfaceReady&&currentOwnerHasFrames);
    }
}
