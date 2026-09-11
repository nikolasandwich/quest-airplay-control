package com.localair.airplay

import android.bluetooth.BluetoothGattServerCallback
import android.view.MotionEvent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.Assert.*

/** Gate tests only: never opens GATT, advertises, connects or sends input. */
class HidIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var hid: HidController
    @Before fun setUp() {
        instrumentation.runOnMainSync { hid = HidController(instrumentation.targetContext) }
    }
    private fun field(name: String) = HidController::class.java.getDeclaredField(name).apply { isAccessible = true }
    @After fun tearDown() { instrumentation.runOnMainSync { hid.detachUi(owner) } }
    private val owner = Any()

    @Test fun testFocusLossCancelsArmingAndPendingGesture() {
        instrumentation.runOnMainSync {
            hid.attachUi(owner) {}
            hid.setFocused(owner, true)
            field("armed").setBoolean(hid, true)
            field("gestureRemaining").setInt(hid, 6)
            hid.setFocused(owner, false)
            assertFalse(hid.isArmed)
            assertEquals(0, field("gestureRemaining").getInt(hid))
            assertEquals(0, field("sent").getInt(hid))
        }
    }

    @Test fun testOldActivityCannotDisarmNewActivity() {
        instrumentation.runOnMainSync {
            val old = Any()
            hid.attachUi(old) {}
            hid.attachUi(owner) {}
            hid.setFocused(owner, true)
            field("armed").setBoolean(hid, true)
            hid.detachUi(old)
            assertTrue(hid.isArmed)
            hid.detachUi(owner)
            assertFalse(hid.isArmed)
        }
    }

    @Test fun testUnarmedScrollNeverSendsAndDoesNotKeepBacklog() {
        instrumentation.runOnMainSync {
            hid.attachUi(owner) {}
            hid.setFocused(owner, true)
            val coords = MotionEvent.PointerCoords().apply { setAxisValue(MotionEvent.AXIS_VSCROLL, 1f) }
            val props = MotionEvent.PointerProperties().apply { id = 0 }
            val event = MotionEvent.obtain(0, 0, MotionEvent.ACTION_SCROLL, 1, arrayOf(props), arrayOf(coords), 0, 0, 1f, 1f, 0, 0, 2, 0)
            try { repeat(100) { assertFalse(hid.onMotion(event)) } } finally { event.recycle() }
            assertEquals(0, field("sent").getInt(hid))
        }
    }

    @Test fun testStaleGattGenerationIsIgnoredBeforeAccessingHost() {
        instrumentation.runOnMainSync {
            field("generation").setInt(hid, 2)
            val callback = HidController::class.java.getDeclaredMethod("createCallback", Int::class.javaPrimitiveType).apply { isAccessible = true }.invoke(hid, 1) as BluetoothGattServerCallback
            // Null would fail if this old callback got through its generation gate.
            callback.onConnectionStateChange(null, 0, 2)
        }
        instrumentation.runOnMainSync { assertNull(field("host").get(hid)) }
    }

    @Test fun testProgressiveMappingAndBackpressureRemainBounded() {
        val engine = ScrollDeltaEngine()
        var total = 0
        for (time in 0L..2000L step 14) {
            val delta = engine.event(.3f, time, true, true)
            assertTrue(delta in 0..10)
            total += delta
        }
        assertTrue(total > 0)
        assertEquals(0, engine.event(.3f, 2014, true, false))
        assertEquals(0, engine.event(0f, 2028, true, true))
        assertEquals(0, engine.event(.3f, 2042, false, true))
    }
}
