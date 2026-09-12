package com.localair.airplay

import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@LooperMode(LooperMode.Mode.PAUSED)
class RayInputAndroidTest {
    private class Transport:RayInputTransport {
        var eligible=true
        var ready=true
        val moves=mutableListOf<Int>()
        var ack:HidController.ReportCompletion?=null
        override fun canTrackPointer()=eligible
        override fun canMovePointer()=eligible&&ready
        override fun movePointer(x:Int,y:Int,completion:HidController.ReportCompletion?):Boolean {
            if(!canMovePointer())return false
            moves.add(x);ack=completion;ready=false;return true
        }
        override fun clickPointer() { error("Unexpected click") }
        fun close() {}
    }
    private fun move(view:RayPointerView,x:Float,age:Long=0,action:Int=MotionEvent.ACTION_HOVER_MOVE){
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(8))
        val t=SystemClock.uptimeMillis()-age
        val e=MotionEvent.obtain(t,t,action,x,100f,0)
        view.onHoverEvent(e);e.recycle()
    }
    @Test fun actualViewCoalescesButAckCannotDriveMovement(){
        val hid=Transport()
        try {
            val v=RayPointerView(RuntimeEnvironment.getApplication()){hid};v.layout(0,0,800,600)
            move(v,100f);move(v,104f);move(v,108f);move(v,112f)
            assertEquals(listOf(4),hid.moves)
            hid.ready=true;hid.ack?.complete(true)
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(10))
            assertEquals(listOf(4),hid.moves)
            move(v,116f);assertEquals(listOf(4,12),hid.moves)
        } finally {hid.close()}
    }
    @Test fun focusExitDisableStaleAndFailedAckCannotReplayPending(){
        for(reason in 0..4){
            val hid=Transport()
            try {
                val v=RayPointerView(RuntimeEnvironment.getApplication()){hid};v.layout(0,0,800,600)
                move(v,100f);move(v,104f);move(v,120f)
                when(reason){
                    0 -> v.onWindowFocusChanged(false)
                    1 -> move(v,801f,action=MotionEvent.ACTION_HOVER_EXIT)
                    2 -> {v.isEnabled=false;move(v,122f);v.isEnabled=true}
                    3 -> move(v,122f,age=100)
                    4 -> hid.ack?.complete(false)
                }
                hid.ready=true
                move(v,140f)
                assertEquals("reason=$reason",listOf(4),hid.moves)
                move(v,142f)
                assertEquals("reason=$reason",listOf(4,2),hid.moves)
            } finally {hid.close()}
        }
    }
}
