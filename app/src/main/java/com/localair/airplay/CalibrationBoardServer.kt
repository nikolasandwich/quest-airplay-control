package com.localair.airplay

import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.json.JSONObject
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Read-only LAN board: no input-injection or filesystem endpoints. */
class CalibrationBoardServer(context: Context) : AutoCloseable {
    private val page=context.assets.open("calibration-board.html").use {it.readBytes()}
    private val server=ServerSocket(8765)
    private val clients=ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,ArrayBlockingQueue(16))
    private val session=UUID.randomUUID().toString()
    @Volatile private var snapshot=JSONObject().put("session",session).put("active",false).put("stage",0).put("baseline",false).put("done",false).put("status","请在 Quest 点校准开始；网页不会自动推动步骤。").toString()
    @Volatile private var updatedAt=SystemClock.uptimeMillis()
    @Volatile private var closed=false
    init {
        Thread({
            while(!closed)try{
                val socket=server.accept()
                try{clients.execute {serve(socket)}}catch(_:RuntimeException){socket.close()}
            }catch(e:Exception){if(!closed)Log.w("CalibrationBoard","Accept failed",e)}
        },"CalibrationBoard").apply {isDaemon=true;start()}
        Log.i("CalibrationBoard","Listening on ${address()}")
    }
    fun address():String {
        val interfaces=Collections.list(NetworkInterface.getNetworkInterfaces())
        val ip=interfaces.sortedBy {if(it.name=="wlan0")0 else 1}.flatMap {Collections.list(it.inetAddresses)}
            .firstOrNull {it is Inet4Address && it.isSiteLocalAddress}?.hostAddress ?: "127.0.0.1"
        return "http://$ip:8765/"
    }
    fun update(calibration: PointerCalibration,status:String){
        snapshot=JSONObject().put("session",session).put("active",calibration.isActive)
            .put("stage",calibration.stage()).put("baseline",calibration.hasBaseline())
            .put("done",calibration.status.startsWith("校准完成"))
            .put("status",status).toString()
        updatedAt=SystemClock.uptimeMillis()
    }
    private fun serve(socket:Socket){
        socket.use {s ->
            try{
                s.soTimeout=2000
                val input=s.getInputStream()
                val header=StringBuilder()
                while(header.length<8192){val b=input.read();if(b<0)return;header.append(b.toChar());if(header.endsWith("\r\n\r\n"))break}
                val request=header.toString().substringBefore("\r\n").split(' ')
                val path=request.getOrNull(1)?.substringBefore('?')
                val ok=request.firstOrNull()=="GET" && path in listOf("/","/state","/favicon.ico")
                val body=when {
                    !ok -> "Not found".toByteArray()
                    path=="/state" -> JSONObject(snapshot).put("ageMs",SystemClock.uptimeMillis()-updatedAt).toString().toByteArray(Charsets.UTF_8)
                    path=="/favicon.ico" -> ByteArray(0)
                    else -> page
                }
                val type=if(path=="/state")"application/json" else "text/html"
                val response="HTTP/1.1 ${if(ok)"200 OK" else "404 Not Found"}\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${body.size}\r\nCache-Control: no-store\r\nConnection: close\r\nContent-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'\r\n\r\n"
                s.getOutputStream().apply {write(response.toByteArray(Charsets.US_ASCII));write(body);flush()}
            }catch(e:Exception){Log.d("CalibrationBoard","Client ended: ${e.javaClass.simpleName}")}
        }
    }
    override fun close(){closed=true;server.close();clients.shutdownNow()}
}
