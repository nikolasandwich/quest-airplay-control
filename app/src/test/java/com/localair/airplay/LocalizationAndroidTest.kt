package com.localair.airplay

import android.view.View
import android.view.ViewGroup
import android.widget.Button
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class LocalizationAndroidTest {
    @After fun reset(){AppText.resetForTests()}
    private fun verify(language:String,restore:String,disconnected:String){
        val controller=Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity=controller.get()
            assertEquals(language,AppText.language())
            assertEquals(restore,AppText.get(R.string.restore_control))
            val hid=HidController(activity)
            try {assertEquals(disconnected,hid.statusText())}finally{hid.close()}
            assertEquals(AppText.get(R.string.not_calibrated),PointerCalibration().status)
            val view=activity.window.decorView
            view.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY))
            view.layout(0,0,1280,720)
            fun buttons(v:View):List<Button> = if(v is Button)listOf(v) else if(v is ViewGroup)(0 until v.childCount).flatMap {buttons(v.getChildAt(it))} else emptyList()
            val controls=buttons(view)
            assertTrue(controls.any {it.text.toString()==restore})
            for(button in controls){
                val layout=button.layout ?: continue
                assertTrue("Too many lines: ${button.text}",layout.lineCount<=2)
                for(line in 0 until layout.lineCount)assertEquals("Clipped label: ${button.text}",0,layout.getEllipsisCount(line))
            }
            val message=AppText.get(R.string.capture_finished,12,"OK")
            assertTrue(message.contains("12"));assertTrue(message.contains("OK"));assertFalse(message.contains("%1"))
        } finally {controller.destroy()}
    }
    @Test @Config(qualifiers="en-rUS") fun english(){verify("en","Restore control","Mouse disconnected")}
    @Test @Config(qualifiers="de-rDE") fun german(){verify("de","Zurücksetzen","Maus nicht verbunden")}
    @Test @Config(qualifiers="fr-rFR") fun french(){verify("fr","Rétablir commande","Souris déconnectée")}
    @Test @Config(qualifiers="fr-rCA") fun canadianFrench(){verify("fr","Rétablir commande","Souris déconnectée")}
    @Test @Config(qualifiers="zh-rCN") fun simplifiedChinese(){verify("zh-CN","恢复控制","鼠标未连接")}
    @Test @Config(qualifiers="es-rES") fun unsupportedFallsBackToEnglish(){verify("en","Restore control","Mouse disconnected")}
    @Test @Config(qualifiers="en-rUS") fun localeChangeRefreshesModelGuideAndResources(){
        AppText.initialize(RuntimeEnvironment.getApplication())
        val original=AppText.get(R.string.restore_control)
        RuntimeEnvironment.setQualifiers("de-rDE")
        AppText.initialize(RuntimeEnvironment.getApplication())
        assertNotEquals(original,AppText.get(R.string.restore_control))
        val calibration=PointerCalibration()
        assertEquals("Nicht kalibriert",calibration.status)
        calibration.begin(0)
        assertTrue(calibration.status.startsWith("Strahl"))
    }
}
