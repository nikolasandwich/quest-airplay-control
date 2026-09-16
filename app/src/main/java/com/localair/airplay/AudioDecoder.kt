package com.localair.airplay

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.localair.airplay.nativebridge.AudioSink
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Async MediaCodec AAC-ELD decoder → AudioTrack. The Mac sends 44.1kHz
 * stereo AAC-ELD in the mirroring audio stream (audio_format=101, which
 * RPiPlay advertises in /info).
 *
 * Note: RPiPlay's raop_buffer_decrypt strips the 12-byte RTP header before
 * handing us the payload (see aes_cbc_decrypt(&data[12], ...) in raop_buffer.c)
 * — we must NOT strip it again, the bytes we receive are the raw encrypted
 * AAC-ELD frame, already decrypted by the time we get here.
 */
class AudioDecoder : AudioSink {

    private var codec: MediaCodec? = null
    private var track: AudioTrack? = null
    private var received = 0L
    private var rendered = 0L
    private val closed=AtomicBoolean(false)
    private val generation=AtomicInteger(0)
    private var appliedGeneration=0
    private var codecGeneration=-1
    private var retryAt=0L
    private var pumpQueued=false

    private val availableInputs = java.util.ArrayDeque<Int>()
    private val pending = java.util.ArrayDeque<Pair<ByteArray, Long>>()
    private val codecThread = HandlerThread("AudioDecoder").apply { start() }
    private val codecHandler = Handler(codecThread.looper)

    private fun lazyStart() {
        if (closed.get() || appliedGeneration!=generation.get() || codec != null) return
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_OBJECT_ELD)
            setByteBuffer("csd-0", ByteBuffer.wrap(AAC_ELD_CSD0_441_STEREO))
            setInteger(MediaFormat.KEY_IS_ADTS, 0)
        }
        val created=MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec=created;codecGeneration=appliedGeneration
        created.setCallback(callback, codecHandler)
        created.configure(fmt, null, null, 0)
        created.start()

        val bufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build())
            .setBufferSizeInBytes(bufSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track!!.play()

        Log.i(TAG, "audio decoder lazy-started (AAC-ELD 44.1k stereo, async), AudioTrack buffer=${bufSize * 2}B")
    }

    private fun resetResources(){
        check(android.os.Looper.myLooper()===codecHandler.looper)
        val oldCodec=codec;val oldTrack=track
        codec=null;track=null;codecGeneration=-1;availableInputs.clear()
        oldCodec?.let {runCatching {it.stop()};runCatching {it.release()}}
        oldTrack?.let {runCatching {it.stop()};runCatching {it.release()}}
        received=0;rendered=0
    }
    fun resetSession(){
        synchronized(pending){
            if(closed.get())return
            val next=generation.incrementAndGet();pending.clear()
            codecHandler.post {if(!closed.get()){resetResources();appliedGeneration=next;retryAt=0;schedulePump()}}
        }
    }
    fun release() {
        synchronized(pending){
            if(!closed.compareAndSet(false,true))return
            generation.incrementAndGet();pending.clear()
            codecHandler.post {resetResources();codecThread.quitSafely()}
        }
    }
    private fun current(c:MediaCodec)=!closed.get()&&c===codec&&codecGeneration==generation.get()
    private fun failed(c:MediaCodec?,e:Exception){
        if(c!==codec)return
        Log.e(TAG,"audio decoder retired; retrying on later frames",e)
        resetResources();synchronized(pending){pending.clear()};retryAt=android.os.SystemClock.uptimeMillis()+1000
    }
    private fun schedulePump(){
        synchronized(pending){
            if(closed.get()||pumpQueued)return
            pumpQueued=true
            codecHandler.post {
                synchronized(pending){pumpQueued=false}
                if(!closed.get()&&appliedGeneration==generation.get()&&synchronized(pending){pending.isNotEmpty()}){
                    try {if(android.os.SystemClock.uptimeMillis()>=retryAt){lazyStart();drainInputs()}}
                    catch(e:Exception){failed(codec,e)}
                }
            }
        }
    }

    override fun onAacFrame(data: ByteArray, ptsUs: Long) {
        synchronized(pending){
            if(closed.get())return
            if(pending.size>=120)pending.removeFirst()
            pending.offer(data to ptsUs)
            schedulePump()
        }
    }

    private fun drainInputs() {
        val c = codec ?: return
        if(!current(c))return
        while (availableInputs.isNotEmpty()) {
            val (frame, pts) = synchronized(pending){pending.poll()} ?: return
            val idx = availableInputs.removeFirst()
            try {
                val buf = c.getInputBuffer(idx) ?: error("Missing audio buffer")
                require(frame.size <= buf.capacity()) { "Audio packet exceeds input capacity" }
                buf.clear(); buf.put(frame)
                c.queueInputBuffer(idx, 0, frame.size, pts, 0)
            } catch (e: Exception) {
                Log.e(TAG, "audio input failed", e)
                failed(c,e)
                return
            }
        }
    }

    private val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(c: MediaCodec, idx: Int) {
            if (!current(c)) return
            availableInputs.addLast(idx)
            drainInputs()
        }

        override fun onOutputBufferAvailable(c: MediaCodec, idx: Int, info: MediaCodec.BufferInfo) {
            if(!current(c))return
            try {
            val outBuf = c.getOutputBuffer(idx)
            val t = track
            if (outBuf != null && info.size > 0 && t != null) {
                val pcm = ByteArray(info.size)
                outBuf.position(info.offset)
                outBuf.get(pcm, 0, info.size)
                t.write(pcm, 0, info.size)
                rendered++
                if (rendered == 1L || rendered % 200 == 0L) Log.i(TAG, "played $rendered PCM chunks")
            }
            c.releaseOutputBuffer(idx, false)
            }catch(e:Exception){failed(c,e)}
        }

        override fun onOutputFormatChanged(c: MediaCodec, fmt: MediaFormat) {
            if(!current(c))return
            Log.i(TAG, "audio output format: $fmt")
        }

        override fun onError(c: MediaCodec, e: MediaCodec.CodecException) {
            if(current(c))failed(c,e)
        }
    }

    companion object {
        private const val TAG = "AudioDecoder"
        private const val SAMPLE_RATE = 44100
        private const val CHANNELS = 2
        /** android.media.MediaCodecInfo.CodecProfileLevel.AACObjectELD */
        private const val AAC_OBJECT_ELD = 39
        /** ASC for AAC-ELD 44.1kHz stereo, 480-sample frames, no SBR. */
        private val AAC_ELD_CSD0_441_STEREO = byteArrayOf(
            0xF8.toByte(), 0xE8.toByte(), 0x50.toByte(), 0x00.toByte(),
        )
    }
}
