package com.localair.airplay;

/** Relative view input boundary; transport admission remains owned by HID. */
public interface RayInputTransport {
    boolean canTrackPointer();
    boolean canMovePointer();
    boolean movePointer(int x,int y,HidController.ReportCompletion completion);
    void clickPointer();
}
