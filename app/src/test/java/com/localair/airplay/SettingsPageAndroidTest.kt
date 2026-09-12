package com.localair.airplay

import android.os.Bundle
import android.view.View
import org.junit.Test
import org.junit.After
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
@LooperMode(LooperMode.Mode.PAUSED)
class SettingsPageAndroidTest {
    private fun field(a:MainActivity,name:String)=MainActivity::class.java.getDeclaredField(name).apply{isAccessible=true}.get(a)
    private fun invoke(a:MainActivity,name:String){MainActivity::class.java.getDeclaredMethod(name).apply{isAccessible=true}.invoke(a)}
    @After fun reset(){AppText.resetForTests()}
    @Test fun secondPagePausesAssistAndBackKeepsSameSurface(){
        val c=Robolectric.buildActivity(MainActivity::class.java).create().start()
        try {
            val a=c.get();val alignment=field(a,"rayAlignment") as RayAlignment
            val surface=field(a,"surfaceView")
            assertTrue(alignment.enabled)
            invoke(a,"openSettingsPage")
            assertFalse(alignment.enabled)
            assertEquals(View.VISIBLE,(field(a,"settingsPage") as View).visibility)
            assertEquals(View.GONE,(field(a,"rayView") as View).visibility)
            assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,(field(a,"mainPage") as View).importantForAccessibility)
            a.onBackPressedDispatcher.onBackPressed()
            assertEquals(View.GONE,(field(a,"settingsPage") as View).visibility)
            assertTrue(alignment.enabled)
            assertSame(surface,field(a,"surfaceView"))
        } finally {c.stop().destroy()}
    }
    @Test fun settingPersistsAndSavedPageReopensWithoutActiveAssist(){
        val c=Robolectric.buildActivity(MainActivity::class.java).create()
        val saved=Bundle()
        try {
            val a=c.get();invoke(a,"openSettingsPage")
            (field(a,"alignButton") as android.widget.Button).performClick()
            assertFalse(a.getSharedPreferences("pointer_ui",0).getBoolean("alignment_enabled",true))
            c.saveInstanceState(saved)
        } finally {c.destroy()}
        val next=Robolectric.buildActivity(MainActivity::class.java).create(saved)
        try {
            val a=next.get()
            assertEquals(View.VISIBLE,(field(a,"settingsPage") as View).visibility)
            assertFalse((field(a,"rayAlignment") as RayAlignment).enabled)
            invoke(a,"closeSettingsPage")
            assertFalse((field(a,"rayAlignment") as RayAlignment).enabled)
        } finally {
            next.destroy()
            RuntimeEnvironment.getApplication().getSharedPreferences("pointer_ui",0).edit().remove("alignment_enabled").commit()
        }
    }
}
