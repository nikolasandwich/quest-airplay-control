package com.localair.airplay

import android.media.MediaCodec
import android.os.Handler
import android.widget.Button
import android.view.View
import android.view.ViewGroup
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
@LooperMode(LooperMode.Mode.PAUSED)
class RecoveryAndroidTest {
    @Test @Config(sdk=[33]) fun pipLifecycleAndSurfaceNotificationsCannotRestoreWaitingOverlay(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).create().start().resume()
        val activity=controller.get()
        assertTrue(activity.enterPictureInPictureMode(android.app.PictureInPictureParams.Builder().build()))
        assertTrue(activity.isInPictureInPictureMode)
        activity.onPictureInPictureModeChanged(true)
        controller.pause()
        val surface=field(activity,"surfaceView").get(activity) as android.view.SurfaceView
        activity.surfaceDestroyed(surface.holder)
        assertEquals(View.GONE,(field(activity,"waiting").get(activity) as View).visibility)
        controller.stop().destroy()
    }
    @Test fun droppedOrdinaryAckRetiresGenerationAndRejectsLateCallback(){
        val hid=HidController(RuntimeEnvironment.getApplication())
        val packetType=Class.forName("com.localair.airplay.HidController\$PendingReport")
        val packet=packetType.declaredConstructors.single().apply {isAccessible=true}.newInstance(null)
        @Suppress("UNCHECKED_CAST") val pending=field(hid,"pendingReports").get(hid) as java.util.ArrayDeque<Any>
        pending.add(packet);field(hid,"notificationPending").setBoolean(hid,true)
        val oldGeneration=field(hid,"generation").getInt(hid)
        val callback=hid.javaClass.getDeclaredMethod("createCallback",Int::class.javaPrimitiveType).apply {isAccessible=true}.invoke(hid,oldGeneration) as android.bluetooth.BluetoothGattServerCallback
        hid.javaClass.getDeclaredMethod("armReportDeadline",packetType,Int::class.javaPrimitiveType).apply {isAccessible=true}.invoke(hid,packet,oldGeneration)
        shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(1001))
        assertTrue(pending.isEmpty());assertFalse(field(hid,"notificationPending").getBoolean(hid))
        assertTrue(field(hid,"generation").getInt(hid)>oldGeneration);assertTrue(field(hid,"needsReconnect").getBoolean(hid))
        val replacement=packetType.declaredConstructors.single().apply {isAccessible=true}.newInstance(null)
        pending.add(replacement)
        callback.onNotificationSent(null,0)
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertSame(replacement,pending.peek());hid.close()
    }
    private fun field(o:Any,name:String)=o.javaClass.getDeclaredField(name).apply {isAccessible=true}
    private fun barrier(audio:AudioDecoder){
        val done=CountDownLatch(1)
        assertTrue((field(audio,"codecHandler").get(audio) as Handler).post {done.countDown()})
        assertTrue(done.await(5,TimeUnit.SECONDS))
    }
    @Test fun audioSessionResetKeepsWorkerButFinalCloseRejectsFrames(){
        val audio=AudioDecoder()
        audio.resetSession();barrier(audio)
        val worker=field(audio,"codecThread").get(audio) as Thread
        assertTrue(worker.isAlive)
        assertEquals(1,field(audio,"appliedGeneration").getInt(audio))
        audio.release();worker.join(5000);assertFalse(worker.isAlive)
        repeat(100){audio.onAacFrame(byteArrayOf(1,2,3),it.toLong())}
        assertTrue((field(audio,"pending").get(audio) as java.util.ArrayDeque<*>).isEmpty())
    }
    @Test fun staleAudioOutputCannotAccessRetiredCodec(){
        val audio=AudioDecoder();val old=MediaCodec.createDecoderByType("audio/mp4a-latm")
        old.release();audio.release();(field(audio,"codecThread").get(audio) as Thread).join(5000)
        val callback=field(audio,"callback").get(audio) as MediaCodec.Callback
        callback.onOutputBufferAvailable(old,999,MediaCodec.BufferInfo())
        callback.onInputBufferAvailable(old,999)
        assertNull(field(audio,"codec").get(audio))
    }
    @Test @Config(sdk=[33]) fun normalScreenHasRecoveryAndNoWizardEntry(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).create()
        val activity=controller.get()
        fun buttons(v:View):List<Button> = if(v is Button)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap {buttons(v.getChildAt(it))} else emptyList()
        val controls=buttons(activity.window.decorView)
        assertFalse(controls.any {it.text.toString()=="Calibration"})
        val alignment=field(activity,"rayAlignment").get(activity) as RayAlignment
        alignment.calibration.begin(0)
        controls.single {it.text.toString()==AppText.get(R.string.restore_control)}.performClick()
        assertFalse(alignment.calibration.isActive);assertFalse(alignment.enabled);assertFalse(alignment.calibration.hasGain())
        controller.destroy()
    }
}
