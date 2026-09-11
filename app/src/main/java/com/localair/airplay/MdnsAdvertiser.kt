package com.localair.airplay

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log

/**
 * Advertises the two services an AirPlay 2 mirroring receiver must expose:
 *   _airplay._tcp  — control + video
 *   _raop._tcp     — audio (RAOP = Remote Audio Output Protocol)
 *
 * The TXT records here are the minimum an iOS sender looks for; see
 * RPiPlay's dnssd.c for the full set we'll mirror once the handshake runs.
 */
class MdnsAdvertiser(context: Context) {

    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var airplayListener: NsdManager.RegistrationListener? = null
    private var raopListener: NsdManager.RegistrationListener? = null

    fun register(port: Int, deviceId: String = "AA:BB:CC:DD:EE:FF", name: String = "localair") {

        val airplay = NsdServiceInfo().apply {
            serviceName = name
            serviceType = "_airplay._tcp"
            setPort(port)
            setAttribute("deviceid", deviceId)
            setAttribute("features", "0x5A7FFFF7,0x1E")
            setAttribute("srcvers", "377.25.06")
            setAttribute("model", "AppleTV5,3")
            setAttribute("flags", "0x4")
            setAttribute("vv", "2")
            setAttribute("pk", "b07727d6f6cd6e08b58ede525ec3cdeaa252ad9f683feb212ef8a205246554e7")
        }
        airplayListener = loggingListener("airplay")
        nsd.registerService(airplay, NsdManager.PROTOCOL_DNS_SD, airplayListener)

        val raop = NsdServiceInfo().apply {
            serviceName = "${deviceId.replace(":", "")}@$name"
            serviceType = "_raop._tcp"
            setPort(port)
            // Match the audio format discovery fields published by RPiPlay.
            setAttribute("txtvers", "1")
            setAttribute("ch", "2")
            setAttribute("sr", "44100")
            setAttribute("ss", "16")
            setAttribute("pw", "false")
            setAttribute("sv", "false")
            setAttribute("rhd", "5.6.0.0")
            setAttribute("cn", "0,1,2,3")
            setAttribute("da", "true")
            setAttribute("et", "0,3,5")
            setAttribute("ft", "0x5A7FFFF7,0x1E")
            setAttribute("sf", "0x4")
            setAttribute("md", "0,1,2")
            setAttribute("am", "AppleTV5,3")
            setAttribute("pk", "b07727d6f6cd6e08b58ede525ec3cdeaa252ad9f683feb212ef8a205246554e7")
            setAttribute("tp", "UDP")
            setAttribute("vn", "65537")
            setAttribute("vs", "377.25.06")
            setAttribute("vv", "2")
        }
        raopListener = loggingListener("raop")
        nsd.registerService(raop, NsdManager.PROTOCOL_DNS_SD, raopListener)
    }

    fun unregister() {
        airplayListener?.let { runCatching { nsd.unregisterService(it) } }
        raopListener?.let { runCatching { nsd.unregisterService(it) } }
        airplayListener = null
        raopListener = null
    }

    private fun loggingListener(tag: String) = object : NsdManager.RegistrationListener {
        override fun onRegistrationFailed(info: NsdServiceInfo, err: Int) {
            Log.e(TAG, "$tag registration failed: $err")
        }
        override fun onUnregistrationFailed(info: NsdServiceInfo, err: Int) {
            Log.e(TAG, "$tag unregistration failed: $err")
        }
        override fun onServiceRegistered(info: NsdServiceInfo) {
            Log.i(TAG, "$tag registered: ${info.serviceName}")
        }
        override fun onServiceUnregistered(info: NsdServiceInfo) {
            Log.i(TAG, "$tag unregistered")
        }
    }

    companion object { private const val TAG = "MdnsAdvertiser" }
}
