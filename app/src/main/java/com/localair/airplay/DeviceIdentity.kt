package com.localair.airplay

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import java.net.NetworkInterface

object DeviceIdentity {
    const val PRODUCT_NAME = "Mirror and Mouse"

    fun macAddress(context: Context): String {
        networkInterfaceMac("wlan0")?.let { return it }
        networkInterfaceMac("eth0")?.let { return it }
        wifiManagerMac(context)?.let { return it }
        return "AA:BB:CC:DD:EE:FF"
    }

    fun macBytes(context: Context): ByteArray {
        return macAddress(context).split(":").map { it.toInt(16).toByte() }.toByteArray()
    }

    fun deviceName(context: Context): String {
        val prefs = context.getSharedPreferences("localair", Context.MODE_PRIVATE)
        val saved = prefs.getString("device_name", null)
        if (saved.isNullOrBlank() || saved == "Quest AirPlay Lab") {
            prefs.edit().putString("device_name", PRODUCT_NAME).apply()
            return PRODUCT_NAME
        }
        return saved
    }

    private fun networkInterfaceMac(ifName: String): String? = runCatching {
        val ni = NetworkInterface.getByName(ifName) ?: return null
        val mac = ni.hardwareAddress ?: return null
        if (mac.size != 6 || mac.all { it == 0.toByte() }) return null
        mac.joinToString(":") { "%02X".format(it) }
    }.getOrNull()

    @Suppress("DEPRECATION")
    private fun wifiManagerMac(context: Context): String? = runCatching {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val info = wm.connectionInfo ?: return null
        val mac = info.macAddress ?: return null
        if (mac == "02:00:00:00:00:00") return null
        mac.uppercase()
    }.getOrNull()
}
