package com.localair.airplay

import android.app.AlertDialog
import android.content.Context
import org.junit.After
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33],qualifiers="zh-rCN")
@LooperMode(LooperMode.Mode.PAUSED)
class LanguagePickerAndroidTest {
    @After fun reset(){
        RuntimeEnvironment.getApplication().getSharedPreferences("app_language",Context.MODE_PRIVATE).edit().clear().commit()
        AppText.resetForTests()
    }
    @Test fun overridePersistsAndSystemLanguageIsUnchanged(){
        val app=RuntimeEnvironment.getApplication()
        val system=app.resources.configuration.locales.toLanguageTags()
        assertEquals("",AppText.selection(app))
        AppText.initialize(app);assertEquals("zh-CN",AppText.language())
        for(tag in arrayOf("en","de","fr","zh-CN")){
            AppText.setLanguage(app,tag);assertEquals(tag,AppText.language())
            AppText.resetForTests();AppText.initialize(app)
            assertEquals(tag,AppText.selection(app));assertEquals(tag,AppText.language())
            assertEquals(system,app.resources.configuration.locales.toLanguageTags())
        }
        AppText.setLanguage(app,"");assertEquals("zh-CN",AppText.language())
    }
    @Test fun systemChangesDoNotOverrideAnExplicitChoice(){
        val app=RuntimeEnvironment.getApplication()
        AppText.setLanguage(app,"de")
        RuntimeEnvironment.setQualifiers("fr-rFR");AppText.initialize(app)
        assertEquals("de",AppText.language())
        AppText.setLanguage(app,"");assertEquals("fr",AppText.language())
    }
    @Test fun unsupportedChoiceCannotCorruptSelection(){
        val app=RuntimeEnvironment.getApplication()
        AppText.setLanguage(app,"en")
        try {AppText.setLanguage(app,"invalid");fail("Expected rejection")}catch(expected:IllegalArgumentException){}
        assertEquals("en",AppText.selection(app))
    }
    @Test fun bottomButtonOpensPickerAndAppliesEnglish(){
        val controller=Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity=controller.get()
            fun buttons(v:android.view.View):List<android.widget.Button> =
                if(v is android.widget.Button)listOf(v) else if(v is android.view.ViewGroup)(0 until v.childCount).flatMap {buttons(v.getChildAt(it))} else emptyList()
            val language=buttons(activity.window.decorView).single {it.text.toString()==AppText.get(R.string.language_settings)}
            assertTrue(language.isEnabled)
            assertEquals(android.view.View.VISIBLE,language.visibility)
            language.performClick()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            val picker=ShadowAlertDialog.getLatestAlertDialog()
            assertEquals(5,picker.listView.adapter.count)
            picker.listView.performItemClick(null,1,1)
            picker.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals("en",AppText.selection(activity));assertEquals("en",AppText.language())
            assertEquals("zh-CN",activity.applicationContext.resources.configuration.locales.get(0).toLanguageTag())
        } finally {controller.destroy()}
    }
}
