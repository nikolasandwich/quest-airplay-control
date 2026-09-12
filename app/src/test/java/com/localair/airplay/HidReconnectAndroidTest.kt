package com.localair.airplay

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattServer
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadow.api.Shadow

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class HidReconnectAndroidTest {
    @Test fun earlyHostSurvivesServiceRegistrationWithoutReconnectionGate(){
        val hid=HidController(RuntimeEnvironment.getApplication())
        fun field(name:String)=HidController::class.java.getDeclaredField(name).apply {isAccessible=true}
        val peer=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("02:00:00:00:00:01")
        try {
            field("server").set(hid,Shadow.newInstanceOf(BluetoothGattServer::class.java))
            field("host").set(hid,peer)
            field("subscribed").setBoolean(hid,true)
            HidController::class.java.getDeclaredMethod("addNextService").apply {isAccessible=true}.invoke(hid)
            assertTrue(field("serviceReady").getBoolean(hid))
            assertFalse(field("needsReconnect").getBoolean(hid))
            assertSame(peer,field("host").get(hid))
            assertTrue(field("subscribed").getBoolean(hid))
            assertFalse(hid.isArmed)
            assertFalse(hid.canTrackPointer())
        } finally {field("server").set(hid,null);hid.close();AppText.resetForTests()}
    }
}
