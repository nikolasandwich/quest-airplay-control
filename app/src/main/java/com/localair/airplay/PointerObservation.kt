package com.localair.airplay

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.Surface
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** User-triggered video-only observation. This class has no HID/input dependency. */
class PointerObservation(context: Context) {
    private val root = File(context.cacheDir, "pointer-observation")
    private val thread = HandlerThread("PointerObservation").apply { start() }
    private val worker = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())
    private val active = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)
    private var closed = false
    private var rays = StringBuilder()
    private var rayCount = 0
    fun recordRay(x: Float, y: Float, width: Int, height: Int, eventTime: Long) {
        if (!active.get()) return
        worker.post {
            if (active.get() && rayCount++ < 2000) rays.append("$eventTime,$x,$y,$width,$height\n")
        }
    }
    fun start(surface: Surface, width: Int, height: Int, done: (String) -> Unit): Boolean {
        if (closed || !surface.isValid || width <= 0 || height <= 0 || !busy.compareAndSet(false,true)) return false
        active.set(true)
        val started = SystemClock.uptimeMillis()
        val scale = minOf(1f, 720f / maxOf(width,height))
        val w = (width*scale).toInt().coerceAtLeast(1)
        val h = (height*scale).toInt().coerceAtLeast(1)
        worker.post {
            // Bound stored data without deleting previous evidence or collecting indefinitely.
            root.mkdirs()
            if ((root.listFiles()?.size ?: 0) >= 4) {
                active.set(false); busy.set(false); main.post { done("已有四次采样，请先导出诊断数据") }; return@post
            }
            val folder = File(root, "run-$started")
            var count = 0
            val frames = StringBuilder("index,request_uptime_ms,callback_uptime_ms,pixelcopy_status,width,height\n")
            rays = StringBuilder("event_uptime_ms,x,y,view_width,view_height\n"); rayCount = 0
            var finished = false
            fun finish(reason: String) {
                if (finished) return
                finished = true; active.set(false)
                try {
                    File(folder,"frames.csv").writeText(frames.toString())
                    File(folder,"rays.csv").writeText(rays.toString())
                    File(folder,"result.txt").writeText("$reason\nPixelCopy times are request/callback times, not source capture timestamps.\n")
                } catch (_: Exception) { }
                busy.set(false)
                main.post { done("只读采样结束：$count 帧（$reason）") }
            }
            try { check(folder.mkdirs()) } catch (_: Exception) { finish("无法创建采样目录"); return@post }
            val capture = object : Runnable {
                override fun run() {
                    if (!active.get() || !surface.isValid) { finish("已停止"); return }
                    if (count >= 30 || SystemClock.uptimeMillis()-started > 5000) { finish("完成"); return }
                    val bitmap = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                    val requested = SystemClock.uptimeMillis()
                    try {
                        PixelCopy.request(surface,bitmap,{ status ->
                            val received = SystemClock.uptimeMillis()
                            try {
                                if (!active.get()) { finish("已停止"); return@request }
                                frames.append("$count,$requested,$received,$status,$w,$h\n")
                                if (status == PixelCopy.SUCCESS) File(folder,"frame-${count.toString().padStart(2,'0')}.png").outputStream().use {
                                    check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))
                                }
                                count++
                                worker.postDelayed(this, maxOf(0L,100-(SystemClock.uptimeMillis()-requested)))
                            } catch (_: Exception) { finish("保存失败") }
                            finally { bitmap.recycle() }
                        },worker)
                    } catch (_: Exception) { bitmap.recycle(); finish("视频取帧失败") }
                }
            }
            capture.run()
        }
        return true
    }
    fun cancel() { active.set(false) }
    fun close() { closed = true; cancel(); thread.quitSafely() }
}
