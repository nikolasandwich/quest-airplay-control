package com.localair.airplay
import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Collections
import java.util.UUID

/** Informational page only; the removed pursuit wizard has no public entry point. */
class CalibrationBoardServer(context:Context):AutoCloseable {
    private val session=UUID.randomUUID().toString()
    private data class State(val payload:String,val at:Long)
    @Volatile private var state=State(JSONObject().put("session",session).put("status","直接控制，无需校准").toString(),SystemClock.uptimeMillis())
    private val server=LocalBoardHttpServer(8765,context.assets.open("calibration-board.html").use {it.readBytes()},{
        val current=state
        JSONObject(current.payload).put("ageMs",SystemClock.uptimeMillis()-current.at).toString().toByteArray(Charsets.UTF_8)
    })
    init {Log.i("CalibrationBoard","Listening on ${address()}")}
    fun address():String {
        val interfaces=Collections.list(NetworkInterface.getNetworkInterfaces())
        val ip=interfaces.sortedBy {if(it.name=="wlan0")0 else 1}.flatMap {Collections.list(it.inetAddresses)}
            .firstOrNull {it is Inet4Address&&it.isSiteLocalAddress}?.hostAddress ?: "127.0.0.1"
        return "http://$ip:8765/"
    }
    fun update(calibration:PointerCalibration,status:String){
        state=State(JSONObject().put("session",session).put("status",status).put("active",calibration.isActive).toString(),SystemClock.uptimeMillis())
    }
    override fun close(){server.close()}
}
