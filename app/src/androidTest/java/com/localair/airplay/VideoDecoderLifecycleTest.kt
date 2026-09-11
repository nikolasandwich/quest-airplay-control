package com.localair.airplay

import android.graphics.SurfaceTexture
import android.media.MediaCodec
import android.os.Handler
import junit.framework.TestCase
import org.junit.Assert.*
import android.view.Surface
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real Android codec/handler tests; no sender, Activity, HID or input injection. */
@Suppress("DEPRECATION")
class VideoDecoderLifecycleTest : TestCase() {
    private lateinit var decoder: VideoDecoder
    private lateinit var handler: Handler

    override fun setUp() {
        super.setUp()
        decoder = VideoDecoder()
        handler = field("codecHandler") as Handler
    }

    private fun field(name: String): Any? = VideoDecoder::class.java.getDeclaredField(name).apply {
        isAccessible = true
    }.get(decoder)

    private fun setField(name: String, value: Any?) {
        VideoDecoder::class.java.getDeclaredField(name).apply { isAccessible = true }.set(decoder, value)
    }

    private fun onDecoder(block: () -> Unit) {
        val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable?>()
        assertTrue(handler.post {
            try { block() } catch (t: Throwable) { failure.set(t) } finally { done.countDown() }
        })
        assertTrue("decoder handler timed out", done.await(10, TimeUnit.SECONDS))
        failure.get()?.let { throw AssertionError("decoder action failed", it) }
    }

    override fun tearDown() {
        decoder.release()
        (field("codecThread") as Thread).join(10_000)
        super.tearDown()
    }

    fun testReleasedPlatformCodecRetiresInsteadOfCrashingHandler() {
        val c = MediaCodec.createDecoderByType("video/avc")
        c.release()
        onDecoder {
            setField("codec", c)
            setField("sps", byteArrayOf(7))
            setField("pps", byteArrayOf(8))
            @Suppress("UNCHECKED_CAST")
            (field("availableInputs") as java.util.ArrayDeque<Int>).add(3)
            (field("callback") as MediaCodec.Callback).onOutputBufferAvailable(c, 0, MediaCodec.BufferInfo())
            assertNull(field("codec"))
            assertTrue((field("availableInputs") as java.util.ArrayDeque<*>).isEmpty())
            assertEquals(true, field("waitingForKeyFrame"))
            assertNotNull("recovery must retain current stream config", field("sps"))
        }
        onDecoder { assertFalse(decoder.hasFrames) }
    }

    fun testStaleCallbacksCannotTouchReplacementCodec() {
        val old = MediaCodec.createDecoderByType("video/avc")
        old.release()
        val current = MediaCodec.createDecoderByType("video/avc")
        onDecoder {
            setField("codec", current)
            val callback = field("callback") as MediaCodec.Callback
            repeat(100) {
                callback.onInputBufferAvailable(old, it)
                callback.onOutputBufferAvailable(old, it, MediaCodec.BufferInfo())
            }
            assertSame(current, field("codec"))
            assertTrue((field("availableInputs") as java.util.ArrayDeque<*>).isEmpty())
        }
    }

    fun testLateOldWindowDetachDoesNotDetachNewWindow() {
        val texture = SurfaceTexture(false)
        val surface = Surface(texture)
        try {
            val oldOwner = Any()
            val newOwner = Any()
            decoder.attachSurface(oldOwner, surface)
            decoder.attachSurface(newOwner, surface)
            decoder.detachSurface(oldOwner)
            onDecoder { assertNotNull(field("target")) }
            decoder.detachSurface(newOwner)
            onDecoder { assertNull(field("target")) }
        } finally { surface.release(); texture.release() }
    }

    fun testDetachQueuedBeforeAttachExecutesCannotResurrectSurface() {
        val texture = SurfaceTexture(false)
        val surface = Surface(texture)
        try {
            // Queue both operations from the codec thread to force the race ordering.
            onDecoder {
                val owner = Any()
                decoder.attachSurface(owner, surface)
                decoder.detachSurface(owner)
            }
            onDecoder { assertNull(field("target")) }
        } finally { surface.release(); texture.release() }
    }

    fun testSessionEndDropsQueuedBuffersAndOldParameters() {
        decoder.onNalUnit(byteArrayOf(0, 0, 0, 1, 0x67, 1, 2), 0)
        onDecoder { assertNotNull(field("sps")) }
        decoder.onSessionEnd()
        onDecoder {
            assertNull(field("sps"))
            assertNull(field("codec"))
            assertTrue((field("pending") as java.util.ArrayDeque<*>).isEmpty())
            assertEquals(true, field("waitingForKeyFrame"))
        }
    }

    fun testOldFrameConfirmationCannotHideNewSurfaceWaitingState() {
        val texture = SurfaceTexture(false)
        val surface = Surface(texture)
        val oldOwner = Any()
        val newOwner = Any()
        try {
            decoder.attachSurface(oldOwner, surface)
            onDecoder {
                setField("hasFrames", true)
                setField("confirmedSurfaceOwner", oldOwner)
                assertTrue(decoder.hasFramesFor(oldOwner))
                decoder.attachSurface(newOwner, surface)
                // Even before the queued rebind executes, old confirmation is invalid for new UI.
                assertFalse(decoder.hasFramesFor(newOwner))
                assertFalse(decoder.hasFramesFor(null))
            }
            onDecoder {
                assertFalse(decoder.hasFramesFor(newOwner))
                assertFalse(decoder.hasFramesFor(oldOwner))
            }
        } finally { surface.release(); texture.release() }
    }

    fun testReleaseIsTerminalEvenWithQueuedData() {
        onDecoder {
            decoder.release()
            decoder.onNalUnit(byteArrayOf(0, 0, 0, 1, 0x67, 1, 2), 0)
            decoder.onSessionEnd()
            decoder.release()
        }
        (field("codecThread") as Thread).join(10_000)
        assertFalse((field("codecThread") as Thread).isAlive)
        assertNull(field("codec"))
        assertNull(field("sps"))
    }
}
