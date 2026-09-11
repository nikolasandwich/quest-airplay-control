package com.localair.airplay.nativebridge

interface VideoSink {
    fun onNalUnit(data: ByteArray, ptsUs: Long)
    fun onSessionEnd()
}

interface AudioSink {
    fun onAacFrame(data: ByteArray, ptsUs: Long)
}

object AirPlayNative {
    init { System.loadLibrary("airplay_native") }

    fun start(name: String, address: ByteArray): Int = nativeStart(name, address)
    fun stop() = nativeStop()
    fun isRunning(): Boolean = nativeIsRunning()
    fun setVideoSink(sink: VideoSink?) = nativeSetSink(sink)
    fun setAudioSink(sink: AudioSink?) = nativeSetAudioSink(sink)

    var connectionListener: (() -> Unit)? = null

    @JvmStatic fun onConnectionInit() {
        connectionListener?.invoke()
    }

    @JvmStatic private external fun nativeStart(name: String, address: ByteArray): Int
    @JvmStatic private external fun nativeStop()
    @JvmStatic private external fun nativeIsRunning(): Boolean
    @JvmStatic private external fun nativeSetSink(sink: VideoSink?)
    @JvmStatic private external fun nativeSetAudioSink(sink: AudioSink?)
}
