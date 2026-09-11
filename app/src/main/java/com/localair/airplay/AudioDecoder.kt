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
import java.util.concurrent.ConcurrentLinkedQueue

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

    private val availableInputs = java.util.ArrayDeque<Int>()
    private val pending = ConcurrentLinkedQueue<Pair<ByteArray, Long>>()
    private val codecThread = HandlerThread("AudioDecoder").apply { start() }
    private val codecHandler = Handler(codecThread.looper)

    private fun lazyStart() {
        if (codec != null) return
        val fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, AAC_OBJECT_ELD)
            setByteBuffer("csd-0", ByteBuffer.wrap(AAC_ELD_CSD0_441_STEREO))
            setInteger(MediaFormat.KEY_IS_ADTS, 0)
        }
        codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            setCallback(callback, codecHandler)
            configure(fmt, null, null, 0)
            start()
        }

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
            .apply { play() }

        Log.i(TAG, "audio decoder lazy-started (AAC-ELD 44.1k stereo, async), AudioTrack buffer=${bufSize * 2}B")
    }

    fun release() {
        codecHandler.post {
            availableInputs.clear()
            codec?.runCatching { stop(); release() }
            codec = null
        }
        codecThread.quitSafely()
        track?.runCatching { stop(); release() }
        track = null
        pending.clear()
        received = 0; rendered = 0
    }

    override fun onAacFrame(data: ByteArray, ptsUs: Long) {
        received++
        if (received == 1L) {
            Log.i(TAG, "first AAC frame: ${data.size}B")
            codecHandler.post {
                runCatching { lazyStart() }.onFailure { Log.e(TAG, "audio initialization failed", it) }
            }
        }
        pending.offer(data to ptsUs)
        codecHandler.post { drainInputs() }
    }

    private fun drainInputs() {
        val c = codec ?: return
        while (availableInputs.isNotEmpty()) {
            val (frame, pts) = pending.poll() ?: return
            val idx = availableInputs.removeFirst()
            try {
                val buf = c.getInputBuffer(idx) ?: error("Missing audio buffer")
                require(frame.size <= buf.capacity()) { "Audio packet exceeds input capacity" }
                buf.clear(); buf.put(frame)
                c.queueInputBuffer(idx, 0, frame.size, pts, 0)
            } catch (e: Exception) {
                Log.e(TAG, "audio input failed", e)
                availableInputs.clear()
                return
            }
        }
    }

    private val callback = object : MediaCodec.Callback() {
        override fun onInputBufferAvailable(c: MediaCodec, idx: Int) {
            if (c !== codec) return
            availableInputs.addLast(idx)
            drainInputs()
        }

        override fun onOutputBufferAvailable(c: MediaCodec, idx: Int, info: MediaCodec.BufferInfo) {
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
        }

        override fun onOutputFormatChanged(c: MediaCodec, fmt: MediaFormat) {
            Log.i(TAG, "audio output format: $fmt")
        }

        override fun onError(c: MediaCodec, e: MediaCodec.CodecException) {
            Log.e(TAG, "audio codec error", e)
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
