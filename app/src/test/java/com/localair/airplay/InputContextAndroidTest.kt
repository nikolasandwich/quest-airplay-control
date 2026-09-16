package com.localair.airplay

import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class InputContextAndroidTest {
    @Test fun contextLossDisarmsAndReturningNeverRearms(){
        val hid=HidController(RuntimeEnvironment.getApplication())
        val owner=Any()
        val armed=HidController::class.java.getDeclaredField("armed").apply {isAccessible=true}
        try {
            hid.attachUi(owner){}
            hid.setInputContext(owner){true}
            armed.setBoolean(hid,true)
            hid.setInputContext(owner){false}
            assertFalse(hid.isArmed)
            hid.clickPointer();hid.dragPointer(1);hid.scrollPage(1)
            assertFalse(hid.movePointer(1,1))
            hid.toggleArmed();assertFalse(hid.isArmed)
            hid.setInputContext(owner){true}
            assertFalse(hid.isArmed)
            hid.detachUi(owner)
            armed.setBoolean(hid,true)
            assertFalse(hid.canTrackPointer())
            assertFalse(hid.isArmed)
        } finally {hid.close();AppText.resetForTests()}
    }
}
