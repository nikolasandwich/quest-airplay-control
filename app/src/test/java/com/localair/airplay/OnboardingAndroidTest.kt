package com.localair.airplay

import android.app.AlertDialog
import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class OnboardingAndroidTest {
    @Test fun cardsNavigateAndDismissalPersists(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).create()
        val activity=controller.get()
        val prefs=activity.getSharedPreferences("onboarding",Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        try {
            MainActivity::class.java.getDeclaredMethod("showOnboarding").apply {isAccessible=true}.invoke(activity)
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val dialog=ShadowAlertDialog.getLatestAlertDialog()
            val next=dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val back=dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
            assertFalse(back.isEnabled)
            next.performClick();assertTrue(back.isEnabled)
            back.performClick();assertFalse(back.isEnabled)
            repeat(4){next.performClick()}
            assertEquals(AppText.get(R.string.guide_done),next.text.toString())
            assertFalse(prefs.getBoolean("seen",false))
            next.performClick()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertFalse(dialog.isShowing)
            assertTrue(prefs.getBoolean("seen",false))
        } finally {controller.destroy();prefs.edit().clear().commit();AppText.resetForTests()}
    }
}
