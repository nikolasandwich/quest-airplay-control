package com.localair.airplay

import android.bluetooth.BluetoothAdapter
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class DeviceIdentityAndroidTest {
    @Test fun migratesLegacyReceiverNameButPreservesCustomNames() {
        val context=RuntimeEnvironment.getApplication()
        val prefs=context.getSharedPreferences("localair",Context.MODE_PRIVATE)
        for (old in listOf(null,"","Quest AirPlay Lab")) {
            prefs.edit().putString("device_name",old).commit()
            assertEquals("Mirror and Mouse",DeviceIdentity.deviceName(context))
            assertEquals("Mirror and Mouse",prefs.getString("device_name",null))
        }
        prefs.edit().putString("device_name","Living room").commit()
        assertEquals("Living room",DeviceIdentity.deviceName(context))
    }

    @Test fun bluetoothNameChangesWithoutEnablingInputOrDroppingBonds() {
        val context=RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(android.Manifest.permission.BLUETOOTH_CONNECT)
        val adapter=BluetoothAdapter.getDefaultAdapter()
        shadowOf(adapter).setState(BluetoothAdapter.STATE_ON)
        adapter.name="Quest 3"
        val before=adapter.bondedDevices.toSet()
        val hid=HidController(context)
        try {
            HidController::class.java.getDeclaredMethod("prepareBluetoothName").apply {isAccessible=true}.invoke(hid)
            assertEquals("Mirror and Mouse",adapter.name)
            assertEquals(before,adapter.bondedDevices)
            assertFalse(hid.isArmed)
        } finally {hid.close();AppText.resetForTests()}
    }
}
