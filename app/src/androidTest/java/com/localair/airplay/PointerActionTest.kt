package com.localair.airplay

import org.junit.Test
import org.junit.Assert.*

/** Pure state tests only: no Bluetooth, input injection or Activity clicks. */
class PointerActionTest {
    @Test fun clickPairsPressAndRelease() {
        val action = PointerAction()
        assertTrue(action.start(0))
        val down = action.next()!!
        assertEquals(1, down.buttons); assertEquals(0, down.x)
        action.attempted(down); action.acknowledged(down, true)
        val up = action.next()!!
        assertEquals(0, up.buttons); assertEquals(0, up.x)
        action.acknowledged(up, true)
        assertFalse(action.active())
    }

    @Test fun dragIsBoundedAndEndsWithRelease() {
        for (direction in intArrayOf(-1, 1)) {
            val action = PointerAction()
            action.start(direction)
            var packets = 0; var distance = 0
            while (action.active()) {
                val report = action.next()!!
                assertTrue(kotlin.math.abs(report.x) <= 24)
                if (report.x != 0) assertEquals(1, report.buttons)
                distance += report.x; packets++
                action.attempted(report); action.acknowledged(report, true)
                assertTrue(packets <= 12)
            }
            assertEquals(12, packets); assertEquals(direction * 240, distance)
        }
    }

    @Test fun focusLossAtEveryDragPhaseOnlyAllowsRelease() {
        for (cancelAfter in 0..10) {
            val action = PointerAction(); action.start(1)
            repeat(cancelAfter + 1) {
                val packet = action.next()!!
                action.attempted(packet); action.acknowledged(packet, true)
            }
            action.cancel()
            val neutral = action.next()!!
            assertEquals(0, neutral.buttons); assertEquals(0, neutral.x)
            action.acknowledged(neutral, true)
            assertFalse(action.active())
        }
    }

    @Test fun latePressAckCannotSatisfyCancelledRelease() {
        val action = PointerAction(); action.start(1)
        val down = action.next()!!; action.attempted(down)
        action.cancel(); action.acknowledged(down, true)
        assertTrue(action.releasing())
        val neutral = action.next()!!
        action.acknowledged(neutral, false)
        assertTrue(action.releasing())
        action.acknowledged(neutral, true)
        assertFalse(action.active())
    }

    @Test fun disconnectCannotReplayAndOldAckCannotAdvanceNewAction() {
        val action = PointerAction(); action.start(1)
        val stale = action.next()!!; action.attempted(stale)
        action.disconnected(); assertNull(action.next())
        action.start(0); action.acknowledged(stale, true)
        assertEquals(1, action.next()!!.buttons)
        action.cancel(); assertFalse(action.active())
    }

    @Test fun failedSubmissionStillRequiresNeutral() {
        val action = PointerAction(); action.start(0)
        val down = action.next()!!; action.attempted(down)
        action.acknowledged(down, false)
        assertEquals(0, action.next()!!.buttons)
        assertFalse(action.start(1))
    }

    @Test fun horizontalBurstTriggersOnceAndVerticalRemainsIndependent() {
        val gate = HorizontalGestureGate()
        var triggered = 0
        for (t in 0L..2000L step 14) triggered += gate.event(.3f, .01f, t, true)
        assertEquals(1, triggered)
        assertEquals(0, gate.event(0f, 0f, 2014, true))
        assertEquals(-1, gate.event(-.3f, 0f, 2028, true))
        gate.reset()
        assertEquals(0, gate.event(.1f, .3f, 0, true))
        assertEquals(0, gate.event(.3f, 0f, 14, false))
        assertEquals(0, gate.event(Float.NaN, 0f, 28, true))
    }

    @Test fun interruptedInputHasNoTimedOutputOrBacklog() {
        val gate = HorizontalGestureGate()
        assertEquals(0, gate.event(.03f, 0f, 0, true))
        assertEquals(0, gate.event(.03f, 0f, 500, true))
        gate.reset()
        assertEquals(1, gate.event(.3f, 0f, 600, true))
        assertEquals(0, gate.event(-.3f, 0f, 620, true))
    }
}
