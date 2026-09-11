package com.localair.airplay

import android.media.MediaCodec
import android.media.MediaFormat
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.PixelCopy
import com.localair.airplay.nativebridge.VideoSink
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Async MediaCodec AVC decoder that lives in the Service, independent of
 * Activity lifecycle. Always set as the video sink so SPS+PPS are never
 * missed. The Activity provides/revokes a Surface — codec is only created
 * when SPS+PPS and a Surface are present. Hidden windows retain the
 * decoder reference state; only the display Surface is replaced on return.
 */
class VideoDecoder(
    private val onFramesChanged: (Boolean) -> Unit = {},
    private val onAspectRatioChanged: (Float) -> Unit = {},
) : VideoSink {

    @Volatile private var codec: MediaCodec? = null
    private data class Target(val owner: Any, val surface: Surface)
    private val requestedTarget = AtomicReference<Target?>(null)
    private var target: Target? = null
    private val closed = AtomicBoolean(false)
    private var waitingForKeyFrame = true
    private val frameTargets = LinkedHashMap<Long, Target>()
    private var pixelCheckPending = false
    private var lastPixelCheck = 0L
    private var frameCallbacks = 0L
    private var sps: ByteArray? = null
    private var pps: ByteArray? = null
    @Volatile var hasFrames = false; private set
    @Volatile private var rendered = 0L
    @Volatile private var fed = 0L

    private val availableInputs = java.util.ArrayDeque<Int>()
    private val pending = java.util.ArrayDeque<Pair<ByteArray, Long>>()
    private val codecThread = HandlerThread("VideoDecoder").apply { start() }
    private val codecHandler = Handler(codecThread.looper)

    fun attachSurface(owner: Any, s: Surface) {
        if (closed.get()) return
        val next = Target(owner, s)
        requestedTarget.set(next)
        codecHandler.post {
            if (closed.get() || requestedTarget.get() !== next) return@post
            if (!s.isValid) { Log.w(TAG, "ignore expired surface"); return@post }
            target = next
            frameTargets.clear()
            setHasFrames(false)
            try {
                val c = codec
                if (c != null) {
                    c.setOutputSurface(s)
                    observeFrames(c, next)
                    Log.i(TAG, "surface rebound; decoder preserved fed=$fed rendered=$rendered")
                }
                Log.i(TAG, "surface attached")
            } catch (e: Exception) {
                failCodec(codec, "surface rebind", e)
            }
            maybeStartCodec()
            drainInputs()
        }
    }

    fun detachSurface(owner: Any) {
        // Stop rendering immediately, but retain decoded reference pictures.
        // Audio and the AirPlay session continue while the window is hidden.
        val old = requestedTarget.get() ?: return
        if (old.owner !== owner || !requestedTarget.compareAndSet(old, null)) return
        codecHandler.post {
            if (target !== old) return@post
            target = null
            frameTargets.clear()
            Log.i(TAG, "surface detached; decoder retained fed=$fed rendered=$rendered")
            setHasFrames(false)
        }
    }

    private fun setHasFrames(value: Boolean) {
        if (hasFrames != value) { hasFrames = value; onFramesChanged(value) }
    }

    private fun canRender() = target?.let {
        requestedTarget.get() === it && it.surface.isValid
    } == true

    private fun observeFrames(c: MediaCodec, bound: Target?) {
        c.setOnFrameRenderedListener({ active, pts, _ ->
            frameCallbacks++
            if (frameCallbacks <= 3) Log.i(TAG, "frame callback pts=$pts tracked=${frameTargets.containsKey(pts)}")
            if (!closed.get() && active === codec && target === bound &&
                frameTargets.remove(pts) === bound && canRender() && !hasFrames) {
                setHasFrames(true)
                Log.i(TAG, "frame presented on current surface")
            }
        }, codecHandler)
    }

    private fun confirmSurfaceBuffer(c: MediaCodec) {
        // Some codec/Surface combinations do not deliver usable frame-rendered
        // callbacks. A successful PixelCopy confirms an actual producer buffer,
        // unlike connection status or merely queueing an output buffer.
        val bound = target ?: return
        val now = SystemClock.uptimeMillis()
        if (hasFrames || !canRender() || pixelCheckPending || now - lastPixelCheck < 500) return
        lastPixelCheck = now; pixelCheckPending = true
        val pixel = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(bound.surface, pixel, { result ->
                pixelCheckPending = false
                pixel.recycle()
                if (!closed.get() && codec === c && target === bound && canRender() && result == PixelCopy.SUCCESS) {
                    setHasFrames(true)
                    Log.i(TAG, "surface buffer confirmed by PixelCopy")
                } else if (result != PixelCopy.SUCCESS) Log.d(TAG, "surface confirmation pending result=$result")
            }, codecHandler)
        } catch (e: IllegalArgumentException) {
            pixelCheckPending = false; pixel.recycle()
            Log.d(TAG, "surface confirmation unavailable", e)
        }
    }

    private fun resetCodec(clearParams: Boolean = true) {
        check(Looper.myLooper() === codecHandler.looper)
        val c = codec
        // Revoke ownership before any platform operation can enqueue callbacks.
        codec = null
        availableInputs.clear()
        frameTargets.clear()
        c?.let { disposeCodec(it) }
        if (clearParams) { sps = null; pps = null }
        pending.clear()
        waitingForKeyFrame = true
        rendered = 0; fed = 0
        setHasFrames(false)
    }

    private fun disposeCodec(c: MediaCodec) {
        // A failed stop must never skip release (e.g. a reclaimed codec).
        try { c.stop() } catch (e: Exception) { Log.w(TAG, "codec stop failed", e) }
        try { c.release() } catch (e: Exception) { Log.w(TAG, "codec release failed", e) }
    }

    private fun failCodec(c: MediaCodec?, operation: String, e: Exception) {
        if (c !== codec) return
        Log.e(TAG, "$operation failed; retiring decoder, waiting for IDR", e)
        resetCodec(clearParams = false)
    }

    fun release() {
        if (!closed.compareAndSet(false, true)) return
        requestedTarget.set(null)
        codecHandler.post {
            target = null
            resetCodec()
            codecThread.quitSafely()
        }
    }

    override fun onSessionEnd() {
        if (closed.get()) return
        codecHandler.post {
            if (closed.get()) return@post
            Log.i(TAG, "session ended — resetting")
            resetCodec()
        }
    }

    override fun onNalUnit(data: ByteArray, ptsUs: Long) {
        if (closed.get()) return
        codecHandler.post {
            if (closed.get()) return@post
            extractParams(data)
            // Never restart from dependent P/B pictures after a codec failure.
            if (waitingForKeyFrame) {
                if (!findStartCodes(data).any { it + 4 < data.size && (data[it + 4].toInt() and 0x1f) == 5 }) return@post
                if (!canRender() || sps == null || pps == null) return@post
                waitingForKeyFrame = false
            }
            pending.offer(data to ptsUs)
            while (pending.size > 120) pending.poll()
            maybeStartCodec()
            drainInputs()
        }
    }

    private fun extractParams(buf: ByteArray) {
        val starts = findStartCodes(buf)
        for (i in starts.indices) {
            val begin = starts[i]
            val end = if (i + 1 < starts.size) starts[i + 1] else buf.size
            if (begin + 4 >= end) continue
            when (buf[begin + 4].toInt() and 0x1F) {
                7 -> { sps = buf.copyOfRange(begin, end); Log.i(TAG, "SPS ${sps!!.size}B") }
                8 -> { pps = buf.copyOfRange(begin, end); Log.i(TAG, "PPS ${pps!!.size}B") }
            }
        }
    }

    private fun findStartCodes(buf: ByteArray): List<Int> {
        val out = ArrayList<Int>(4)
        var i = 0
        while (i + 3 < buf.size) {
            if (buf[i] == 0.toByte() && buf[i + 1] == 0.toByte() &&
                buf[i + 2] == 0.toByte() && buf[i + 3] == 1.toByte()) {
                out.add(i); i += 4
            } else i++
        }
        return out
    }

    private fun dumpNalTypes(buf: ByteArray): String {
        val starts = findStartCodes(buf)
        return starts.joinToString(",") { (buf[it + 4].toInt() and 0x1F).toString() }
    }

    private fun maybeStartCodec() {
        val s = sps ?: return
        val p = pps ?: return
        if (codec != null || closed.get() || waitingForKeyFrame || !canRender()) return
        val surf = target?.surface ?: return
        var created: MediaCodec? = null
        try {
            val fmt = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 1920, 1080).apply {
                setByteBuffer("csd-0", ByteBuffer.wrap(s))
                setByteBuffer("csd-1", ByteBuffer.wrap(p))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
            }
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            created = c
            Log.i(TAG, "decoder=${c.name}" + if (Build.VERSION.SDK_INT >= 29) " hardware=${c.codecInfo.isHardwareAccelerated}" else "")
            c.setCallback(callback, codecHandler)
            observeFrames(c, target)
            c.configure(fmt, surf, null, 0)
            c.start()
            codec = c
            Log.i(TAG, "codec started (async)")
        } catch (e: Exception) {
            Log.e(TAG, "codec init failed", e)
            created?.let { disposeCodec(it) }
            pending.clear()
            waitingForKeyFrame = true
        }
    }

    // Retain codec-owned input slots until real compressed data is ready.
    // Submitting empty buffers here produces a continuous invalid-work loop.
    private fun drainInputs() {
        val c = codec ?: return
        while (availableInputs.isNotEmpty()) {
            val entry = pending.poll() ?: return
            val idx = availableInputs.removeFirst()
            try {
                val buf = c.getInputBuffer(idx) ?: error("Missing input buffer")
                val (nal, pts) = entry
                require(nal.size <= buf.capacity()) { "NAL exceeds codec input capacity" }
                buf.clear(); buf.put(nal)
                c.queueInputBuffer(idx, 0, nal.size, pts, 0)
                fed++
                if (fed == 1L || fed == 10L || fed % 120 == 0L)
                    Log.i(TAG, "fed=$fed rendered=$rendered queue=${pending.size}")
            } catch (e: Exception) {
                failCodec(c, "input submission", e)
                return
            }
        }
    }

    private val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(c: MediaCodec, idx: Int) {
            if (closed.get() || c !== codec) return
            availableInputs.addLast(idx)
            drainInputs()
        }

        override fun onOutputBufferAvailable(c: MediaCodec, idx: Int, info: MediaCodec.BufferInfo) {
            if (closed.get() || c !== codec) return
            val visible = canRender()
            try {
                if (visible) {
                    target?.let { frameTargets[info.presentationTimeUs] = it }
                    while (frameTargets.size > 120) frameTargets.remove(frameTargets.keys.first())
                }
                c.releaseOutputBuffer(idx, visible)
            } catch (e: IllegalStateException) {
                // Framework codec failure can precede onError on this handler.
                // Retire the entire generation; never reuse its buffer indices.
                failCodec(c, "output release", e)
                return
            }
            if (!visible) return
            rendered++
            confirmSurfaceBuffer(c)
            if (rendered == 1L || rendered % 60 == 0L) Log.i(TAG, "rendered $rendered frames")
        }

        override fun onOutputFormatChanged(c: MediaCodec, fmt: MediaFormat) {
            if (closed.get() || c !== codec) return
            Log.i(TAG, "output format: $fmt")
            fun value(key: String, fallback: Int) = if (fmt.containsKey(key)) fmt.getInteger(key) else fallback
            val width = value("width", 1)
            val height = value("height", 1)
            val visibleWidth = value("crop-right", width - 1) - value("crop-left", 0) + 1
            val visibleHeight = value("crop-bottom", height - 1) - value("crop-top", 0) + 1
            val sarWidth = value("sar-width", 1).coerceAtLeast(1)
            val sarHeight = value("sar-height", 1).coerceAtLeast(1)
            if (visibleWidth > 0 && visibleHeight > 0) {
                var ratio = visibleWidth.toFloat() * sarWidth / (visibleHeight.toFloat() * sarHeight)
                val rotation = value("rotation-degrees", 0)
                if ((rotation % 180 + 180) % 180 == 90) ratio = 1f / ratio
                Log.i(TAG, "display=${visibleWidth}x${visibleHeight} sar=$sarWidth:$sarHeight rotation=$rotation aspect=$ratio")
                onAspectRatioChanged(ratio)
            }
        }

        override fun onError(c: MediaCodec, e: MediaCodec.CodecException) {
            if (closed.get() || c !== codec) return
            failCodec(c, "codec error: ${e.diagnosticInfo}", e)
        }
    }

    companion object { private const val TAG = "VideoDecoder" }
}
