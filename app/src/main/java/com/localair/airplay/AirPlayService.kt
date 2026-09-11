package com.localair.airplay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.localair.airplay.nativebridge.AirPlayNative

class AirPlayService : Service() {
    lateinit var hid: HidController; private set

    private var multicastLock: WifiManager.MulticastLock? = null
    private lateinit var mdns: MdnsAdvertiser
    private val audio = AudioDecoder()
    @Volatile var videoAspectRatio = 0f; private set
    val video = VideoDecoder(onFramesChanged = { hasFrames ->
        // Broadcast to any listening Activity
        val intent = Intent(ACTION_FRAMES_CHANGED).putExtra("hasFrames", hasFrames)
        sendBroadcast(intent.setPackage(packageName))
    }, onAspectRatioChanged = { ratio ->
        videoAspectRatio = ratio
        sendBroadcast(Intent(ACTION_FRAMES_CHANGED).setPackage(packageName).putExtra("aspectRatio", ratio))
    })
    private val handler = Handler(Looper.getMainLooper())
    private var port = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            if (it.hasExtra("surface_attached")) {
                // Activity is telling us about its surface — handled via attachSurface/detachSurface
            }
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        hid = HidController(this)
        instance = this
        startInForeground(hid.permitted())
        acquireMulticastLock()
        startReceiver()
        scheduleHealthCheck()
        if (hid.permitted()) hid.start()
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacksAndMessages(null)
        hid.stop()
        if (::mdns.isInitialized) mdns.unregister()
        AirPlayNative.setVideoSink(null)
        AirPlayNative.setAudioSink(null)
        AirPlayNative.stop()
        video.release()
        audio.release()
        multicastLock?.release()
        super.onDestroy()
    }

    fun attachSurface(owner: Any, s: Surface) { video.attachSurface(owner, s) }
    fun detachSurface(owner: Any) { video.detachSurface(owner) }

    fun enableHid() {
        if (!hid.permitted()) return
        startInForeground(true)
        hid.start()
    }

    private fun startReceiver() {
        port = AirPlayNative.start(DeviceIdentity.deviceName(this), DeviceIdentity.macBytes(this))
        AirPlayNative.setVideoSink(video)
        AirPlayNative.setAudioSink(audio)
        AirPlayNative.connectionListener = { Log.i(TAG, "Sender connected") }
        val name = DeviceIdentity.deviceName(this)
        mdns = MdnsAdvertiser(this)
        if (port > 0) {
            mdns.register(port, DeviceIdentity.macAddress(this), name)
            Log.i(TAG, "receiver up: $name on port $port (mac=${DeviceIdentity.macAddress(this)})")
        } else {
            Log.w(TAG, "native start returned 0 — skipping mDNS")
        }
    }

    private fun scheduleHealthCheck() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (port > 0 && !AirPlayNative.isRunning()) {
                    Log.w(TAG, "raop died — restarting receiver")
                    AirPlayNative.stop()
                    if (::mdns.isInitialized) mdns.unregister()
                    audio.release()
                    startReceiver()
                }
                handler.postDelayed(this, HEALTH_INTERVAL_MS)
            }
        }, HEALTH_INTERVAL_MS)
    }

    private fun acquireMulticastLock() {
        val wifi = getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock(TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun startInForeground(withHid: Boolean = false) {
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "AirPlay", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val name = DeviceIdentity.deviceName(this)
        val n: Notification = Notification.Builder(this, CHANNEL)
            .setContentTitle("Quest 投屏与鼠标")
            .setContentText("$name · 接收服务运行中")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val types = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                if (withHid) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
            startForeground(1, n, types)
        } else startForeground(1, n)
    }

    companion object {
        const val ACTION_FRAMES_CHANGED = "com.localair.airplay.FRAMES_CHANGED"
        private const val TAG = "AirPlayService"
        private const val CHANNEL = "airplay"
        private const val HEALTH_INTERVAL_MS = 10_000L
        @Volatile var instance: AirPlayService? = null; private set
    }
}
