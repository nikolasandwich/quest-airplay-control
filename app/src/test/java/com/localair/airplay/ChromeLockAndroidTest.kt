package com.localair.airplay

import android.view.View
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class ChromeLockAndroidTest {
    @Test fun lockHidesBarsWithoutReplacingSurfaceAndRestoresThem(){
        org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("onboarding",0).edit().putBoolean("seen",true).commit()
        val controller=Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity=controller.get()
        fun view(name:String)=MainActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.get(activity) as View
        try {
            val surface=view("surfaceView")
            val lock=view("chromeLock")
            lock.performClick()
            assertEquals(View.GONE,view("controls").visibility)
            assertEquals(View.GONE,view("hidStatus").visibility)
            assertEquals(View.VISIBLE,lock.visibility)
            assertSame(surface,view("surfaceView"))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(3))
            assertEquals(0f,lock.alpha)
            val hover=android.view.MotionEvent.obtain(0,0,android.view.MotionEvent.ACTION_HOVER_ENTER,10f,10f,0)
            lock.dispatchGenericMotionEvent(hover);hover.recycle()
            assertEquals(1f,lock.alpha)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(4))
            assertEquals(1f,lock.alpha)
            lock.performClick()
            assertEquals(View.VISIBLE,view("controls").visibility)
            assertEquals(View.VISIBLE,view("hidStatus").visibility)
            assertSame(surface,view("surfaceView"))
        } finally {controller.pause().stop().destroy();org.robolectric.RuntimeEnvironment.getApplication().getSharedPreferences("onboarding",0).edit().clear().commit();AppText.resetForTests()}
    }
}
