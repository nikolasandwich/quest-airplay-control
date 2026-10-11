package com.localair.airplay

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothDevice
import org.robolectric.Shadows.shadowOf
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
    @Test fun pairingCompletionDoesNotRestoreStaleSubscriptionOrAutoArm(){
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_ADVERTISE)
        val hid=HidController(RuntimeEnvironment.getApplication())
        fun field(name:String)=HidController::class.java.getDeclaredField(name).apply {isAccessible=true}
        val peer=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("02:00:00:00:00:02")
        val owner=Any()
        try {
            hid.attachUi(owner){};hid.setFocused(owner,true);hid.setInputContext(owner){true}
            field("server").set(hid,Shadow.newInstanceOf(BluetoothGattServer::class.java))
            field("serviceReady").setBoolean(hid,true);field("host").set(hid,peer)
            field("subscribed").setBoolean(hid,true)
            shadowOf(peer).setBondState(BluetoothDevice.BOND_BONDING)
            // An explicit Enable must wait for pairing, even with a stale subscription.
            hid.toggleArmed()
            assertSame(peer,field("pairingPeer").get(hid))
            assertFalse(field("subscribed").getBoolean(hid));assertFalse(hid.isArmed)
            shadowOf(peer).setBondState(BluetoothDevice.BOND_BONDED)
            (field("pairingPoll").get(hid) as Runnable).run()
            assertNull(field("pairingPeer").get(hid))
            assertFalse(hid.canTrackPointer());assertFalse(hid.isArmed)
            hid.toggleArmed();assertFalse(hid.isArmed)
            field("subscribed").setBoolean(hid,true)
            hid.toggleArmed();assertTrue(hid.isArmed)
        } finally {field("server").set(hid,null);hid.close();AppText.resetForTests()}
    }
    @Test fun pairingTimeoutAndStaleConnectionCannotEnableInput(){
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT,android.Manifest.permission.BLUETOOTH_ADVERTISE)
        val hid=HidController(RuntimeEnvironment.getApplication())
        fun field(name:String)=HidController::class.java.getDeclaredField(name).apply {isAccessible=true}
        val peer=BluetoothAdapter.getDefaultAdapter().getRemoteDevice("02:00:00:00:00:03")
        val owner=Any()
        try {
            hid.attachUi(owner){};hid.setFocused(owner,true);hid.setInputContext(owner){true}
            field("server").set(hid,Shadow.newInstanceOf(BluetoothGattServer::class.java))
            field("serviceReady").setBoolean(hid,true);field("host").set(hid,peer)
            shadowOf(peer).setBondState(BluetoothDevice.BOND_BONDING)
            hid.toggleArmed()
            assertSame(peer,field("pairingPeer").get(hid))
            field("pairingDeadline").setLong(hid,0)
            (field("pairingPoll").get(hid) as Runnable).run()
            assertNull(field("pairingPeer").get(hid));assertFalse(hid.isArmed)
            hid.toggleArmed()
            field("host").set(hid,null)
            (field("pairingPoll").get(hid) as Runnable).run()
            assertNull(field("pairingPeer").get(hid));assertFalse(hid.canTrackPointer())
        } finally {field("server").set(hid,null);hid.close();AppText.resetForTests()}
    }
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
