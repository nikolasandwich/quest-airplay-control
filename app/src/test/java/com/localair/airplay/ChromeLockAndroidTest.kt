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
        val controller=Robolectric.buildActivity(MainActivity::class.java).create()
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
            lock.performClick()
            assertEquals(View.VISIBLE,view("controls").visibility)
            assertEquals(View.VISIBLE,view("hidStatus").visibility)
            assertSame(surface,view("surfaceView"))
        } finally {controller.destroy();AppText.resetForTests()}
    }
}
