package com.localair.airplay

import android.app.PictureInPictureParams
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.view.MotionEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var waiting: TextView
    private var surfaceReady = false
    private var rebuildSurfaceOnResume = false
    private var surfaceOwner: Any? = null
    private val inputOwner = Any()
    private var attachedHid: HidController? = null
    private lateinit var hidStatus: TextView
    private lateinit var controlButton: Button
    private lateinit var controls: LinearLayout
    private lateinit var previousButton: Button
    private lateinit var nextButton: Button
    private lateinit var leftButton: Button
    private lateinit var rightButton: Button
    private lateinit var clickButton: Button
    private lateinit var rayView: RayPointerView
    private lateinit var rayButton: Button
    private var rayMode = true
    private lateinit var pointerObservation: PointerObservation
    private lateinit var rayAlignment: RayAlignment
    private lateinit var alignButton: Button
    private var pendingControlRestore=false
    private var lastWaitingDecision=""
    private var settingsOpen=false
    private var alignmentRequested=true
    private lateinit var settingsPage: View
    private lateinit var mainPage: View
    private lateinit var chromeLock: Button
    private var chromeHidden=false
    private val settingsBack = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed(){closeSettingsPage()}
    }

    private val svc get() = AirPlayService.instance

    private val framesReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.hasExtra("hasFrames")) {
                refreshVideoState()
            }
            fitVideo()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppText.initialize(this)
        pointerObservation = PointerObservation(this)
        rayAlignment = RayAlignment({ surfaceView }, { attachedHid }, {
            when {
                settingsOpen -> AppText.get(R.string.app_settings)
                !hasWindowFocus() -> AppText.get(R.string.mirroring_window_is_not_in_the_foreground)
                !rayMode -> AppText.get(R.string.ray_mode_is_off)
                attachedHid?.isArmed != true -> AppText.get(R.string.mouse_control_is_not_enabled)
                !surfaceReady -> AppText.get(R.string.waiting_for_mirroring)
                svc?.video?.hasRecentOutputFor(surfaceOwner) != true -> AppText.get(R.string.waiting_for_a_new_video_frame)
                else -> null
            }
        }) { updateHidUi() }
        val pointerPrefs=getSharedPreferences("pointer_ui",MODE_PRIVATE)
        alignmentRequested=pointerPrefs.getBoolean("alignment_enabled",true)
        rayAlignment.setFast(pointerPrefs.getBoolean("fast_alignment",true))
        rayAlignment.calibration.intervalMinutes(if(pointerPrefs.getInt("calibration_minutes",5)==3)3 else 5)
        rayMode = getSharedPreferences("pointer_ui", MODE_PRIVATE).getBoolean("relative_ray_enabled", true)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.setBackgroundColor(Color.BLACK)
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fitVideo() }
        hidStatus = TextView(this).apply {
            setTextColor(Color.LTGRAY); textSize = 15f; gravity = Gravity.CENTER
            setPadding(12, 8, 12, 8)
            text = AppText.get(R.string.quest_mirror_mouse_starting)
            maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
        }
        // Status updates cannot change the weighted video area's size.
        root.addView(hidStatus, LinearLayout.LayoutParams(-1, hidStatus.lineHeight+hidStatus.paddingTop+hidStatus.paddingBottom))
        hidStatus.setOnClickListener { showControlHelp() }
        val videoArea = FrameLayout(this)
        root.addView(videoArea, LinearLayout.LayoutParams(-1, 0, 1f))
        surfaceView = SurfaceView(this).apply {
            holder.addCallback(this@MainActivity)
            holder.setFormat(PixelFormat.OPAQUE)
        }
        videoArea.addView(surfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        rayView = RayPointerView(this) { attachedHid }.apply { visibility = View.GONE }
        rayView.calibration=rayAlignment.calibration
        rayAlignment.interactionIdle={rayView.isInputIdle()}
        rayView.onCalibrationConfirm=rayAlignment::confirmCalibration
        rayView.onReport=rayAlignment::rayReport
        rayView.observer = { x,y,w,h,time ->
            pointerObservation.recordRay(x,y,w,h,time)
            rayAlignment.onRay(x,y,w,h,time)
        }
        rayView.onReset = rayAlignment::invalidateTarget
        videoArea.addView(rayView, FrameLayout.LayoutParams(-1, -1).apply { gravity = Gravity.CENTER })
        waiting = TextView(this).apply {
            text = AppText.get(R.string.waiting_airplay,DeviceIdentity.deviceName(this@MainActivity))
            setTextColor(Color.WHITE)
            textSize = 28f
            gravity = Gravity.CENTER
        }
        videoArea.addView(waiting, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ).apply { gravity = Gravity.CENTER })
        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val primary = LinearLayout(this)
        val pointer = LinearLayout(this)
        controls.addView(primary); controls.addView(pointer)
        fun action(row: LinearLayout, label: String, run: () -> Unit): Button = Button(this).apply {
            text = label; textSize = 16f; isAllCaps = false
            maxLines=2
            ellipsize=android.text.TextUtils.TruncateAt.END
            androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this,10,16,1,android.util.TypedValue.COMPLEX_UNIT_SP)
            setOnClickListener { run() }
            row.addView(this, LinearLayout.LayoutParams(0, (56 * resources.displayMetrics.density).toInt(), 1f))
        }
        action(primary, AppText.get(R.string.connect_mouse)) { connectMouse() }
        controlButton = action(primary, AppText.get(R.string.enable_control)) { svc?.hid?.toggleArmed(); updateHidUi() }
        previousButton = action(primary, AppText.get(R.string.scroll_up)) { rayAlignment.calibration.invalidateSegment(); svc?.hid?.scrollPage(1) }
        nextButton = action(primary, AppText.get(R.string.scroll_down)) { rayAlignment.calibration.invalidateSegment(); svc?.hid?.scrollPage(-1) }
        action(primary, AppText.get(R.string.restore_control)) { restoreDirectControl() }
        leftButton = action(pointer, AppText.get(R.string.drag_left)) { rayAlignment.calibration.externalAction(); svc?.hid?.dragPointer(-1) }
        clickButton = action(pointer, AppText.get(R.string.click_pointer)) { rayAlignment.calibration.invalidateSegment(); svc?.hid?.clickPointer() }
        rightButton = action(pointer, AppText.get(R.string.drag_right)) { rayAlignment.calibration.externalAction(); svc?.hid?.dragPointer(1) }
        action(pointer, AppText.get(R.string.app_settings)) { openSettingsPage() }
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))
        mainPage=root
        val pages=FrameLayout(this)
        pages.addView(root,FrameLayout.LayoutParams(-1,-1))
        chromeHidden=savedInstanceState?.getBoolean("chrome_hidden",false)?:false
        chromeLock=Button(this).apply {
            textSize=22f;minWidth=0;minimumWidth=0;setPadding(0,0,0,0)
            setTextColor(Color.WHITE)
            background=android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.argb(190,35,43,56));cornerRadius=24*resources.displayMetrics.density
            }
            setOnClickListener {chromeHidden=!chromeHidden;rayView.resetInput();updateChrome()}
        }
        val lockSize=(48*resources.displayMetrics.density).toInt()
        pages.addView(chromeLock,FrameLayout.LayoutParams(lockSize,lockSize,Gravity.END or Gravity.CENTER_VERTICAL).apply {
            marginEnd=(8*resources.displayMetrics.density).toInt()
        })
        updateChrome()
        settingsPage=createSettingsPage().apply {visibility=View.GONE}
        pages.addView(settingsPage,FrameLayout.LayoutParams(-1,-1))
        setContentView(pages)
        onBackPressedDispatcher.addCallback(this,settingsBack)
        if(savedInstanceState?.getBoolean("settings_open")==true)openSettingsPage()
        syncAlignmentPreference()
        if(savedInstanceState==null&&!getSharedPreferences("onboarding",MODE_PRIVATE).getBoolean("seen",false)){
            pages.post {if(!isFinishing&&!isDestroyed){openSettingsPage();showOnboarding()}}
        }

        val svcIntent = Intent(this, AirPlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(svcIntent)
        } else {
            startService(svcIntent)
        }

        androidx.core.content.ContextCompat.registerReceiver(this, framesReceiver, IntentFilter(AirPlayService.ACTION_FRAMES_CHANGED), androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        attachControlsWhenReady()
    }

    private fun startPointerObservation() {
        val ready = surfaceReady && svc?.video?.hasFrames == true && hasWindowFocus()
        android.util.Log.i("PointerObservation", "User requested capture: surface=$surfaceReady frames=${svc?.video?.hasFrames} focus=${hasWindowFocus()}")
        if (!ready) {
            android.widget.Toast.makeText(this,AppText.get(R.string.not_started_restore_mirroring_and_keep_this),android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val started = pointerObservation.start(surfaceView.holder.surface,surfaceView.width,surfaceView.height) { message ->
            android.util.Log.i("PointerObservation",message)
            if (!isDestroyed) android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show()
        }
        android.util.Log.i("PointerObservation", "Capture accepted=$started")
        android.widget.Toast.makeText(this,if (started) AppText.get(R.string.read_only_capture_takes_about_3_seconds) else AppText.get(R.string.not_started_capture_is_busy_or_video),android.widget.Toast.LENGTH_LONG).show()
    }

    private fun attachControlsWhenReady() {
        if (isDestroyed || isFinishing) return
        val hid = svc?.hid
        if (hid == null) { hidStatus.postDelayed({ attachControlsWhenReady() }, 100); return }
        attachedHid = hid
        hid.attachUi(inputOwner) { updateHidUi() }
        hid.setFocused(inputOwner, hasWindowFocus()&&!settingsOpen)
        updateHidUi()
        refreshVideoState()
    }

    private fun updateHidUi() {
        if (!::hidStatus.isInitialized) return
        if(::alignButton.isInitialized)alignButton.text=AppText.get(if(alignmentRequested)R.string.alignment_on else R.string.alignment_off)
        if(::rayButton.isInitialized)rayButton.text=AppText.get(if(rayMode)R.string.relative_ray_on else R.string.relative_ray_off)
        val hid = attachedHid ?: return
        var primary=hid.statusText()
        var detail=""
        if(rayMode&&hid.isArmed){primary=AppText.get(R.string.relative_ray_control);detail=AppText.get(R.string.move_the_ray_to_move_the_pointer)}
        if(rayAlignment.enabled&&hid.isArmed){primary=AppText.get(R.string.alignment_assist)+rayAlignment.status;detail=AppText.get(R.string.direct_movement_still_works_if_experimental_detection)}
        if(rayAlignment.calibration.isActive){
            primary=if(rayAlignment.calibration.awaitingReference())AppText.get(R.string.calibration_final_confirmation_see_page) else AppText.get(R.string.calibration_progress,rayAlignment.calibration.stage()+1)
            detail=rayAlignment.calibration.status+" · "+rayAlignment.status
        }else if(hid.isArmed&&rayAlignment.calibration.status!=AppText.get(R.string.not_calibrated)){
            if(rayAlignment.enabled)detail=rayAlignment.calibration.status
            else primary=rayAlignment.calibration.status
        }
        if(hidStatus.text.toString()!=primary)hidStatus.text=primary
        hidStatus.contentDescription=primary+". "+detail
        alignButton.text = if (alignmentRequested) AppText.get(R.string.alignment_on) else AppText.get(R.string.alignment_off)
        rayButton.text = if (rayMode) AppText.get(R.string.relative_ray_on) else AppText.get(R.string.relative_ray_off)
        controlButton.text = if (hid.isArmed) AppText.get(R.string.pause_control) else AppText.get(R.string.enable_control)
        val actionsAllowed=hid.isArmed&&!rayAlignment.calibration.isActive
        previousButton.isEnabled = actionsAllowed
        nextButton.isEnabled = actionsAllowed
        leftButton.isEnabled = actionsAllowed
        rightButton.isEnabled = actionsAllowed
        clickButton.isEnabled = actionsAllowed
        refreshVideoState()
    }

    private fun showControlHelp(){
        val panel=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(28,12,28,12)}
        fun note(value:String,selectable:Boolean=false){panel.addView(TextView(this).apply {
            text=value;textSize=16f;setPadding(0,8,0,8);setTextIsSelectable(selectable)
        })}
        note(AppText.get(R.string.connect_the_mouse_and_enable_control_move))
        note(AppText.get(R.string.alignment_assist_is_optional_and_experimental_direct))
        note(AppText.get(R.string.restore_control_does_not_center_the_ipad))
        note(AppText.get(R.string.current_status,attachedHid?.statusText() ?: "",if(rayAlignment.enabled)rayAlignment.status else AppText.get(R.string.direct_no_detection)))
        val dialog=android.app.AlertDialog.Builder(this)
            .setTitle(AppText.get(R.string.mouse_control))
            .setView(android.widget.ScrollView(this).apply {addView(panel)})
            .setPositiveButton(AppText.get(R.string.restore_direct_control)){_,_ ->
                pendingControlRestore=true
            }
            .setNeutralButton(AppText.get(R.string.app_settings)){_,_ -> openSettingsPage()}
            .setNegativeButton(AppText.get(R.string.close),null).create()
        dialog.setOnDismissListener {window.decorView.post { restoreWhenFocused() }}
        dialog.show()
    }
    private fun createSettingsPage():View {
        fun dp(value:Int)=(value*resources.displayMetrics.density).toInt()
        fun surface()=android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.rgb(35,43,56));cornerRadius=dp(16).toFloat()
            setStroke(dp(1),Color.rgb(57,69,87))
        }
        val page=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL;setBackgroundColor(Color.rgb(17,23,33))
            setPadding(dp(28),dp(16),dp(28),dp(16));isClickable=true;isFocusableInTouchMode=true
        }
        page.addView(Button(this).apply {
            text=AppText.get(R.string.back_to_mirroring);isAllCaps=false
            setOnClickListener {closeSettingsPage()}
        },LinearLayout.LayoutParams(-2,-2))
        page.addView(TextView(this).apply {
            text=AppText.get(R.string.app_settings);textSize=26f;setTextColor(Color.WHITE);setPadding(0,16,0,16)
        })
        val content=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL}
        val selected=when(AppText.selection(this)){
            "en" -> AppText.get(R.string.language_english)
            "de" -> AppText.get(R.string.language_german)
            "fr" -> AppText.get(R.string.language_french)
            "zh-CN" -> AppText.get(R.string.language_chinese)
            else -> AppText.get(R.string.follow_system)
        }
        content.addView(TextView(this).apply {
            text=AppText.get(R.string.selected_language,selected);textSize=15f;setTextColor(Color.rgb(173,187,208));setPadding(dp(4),0,0,dp(8))
        })
        content.addView(Button(this).apply {
            text=AppText.get(R.string.language_settings);isAllCaps=false
            background=surface();setTextColor(Color.WHITE);minHeight=dp(56)
            setOnClickListener {showLanguageSettings()}
        },LinearLayout.LayoutParams(-1,-2))
        fun setting(label:String,run:()->Unit)=Button(this).apply {
            text=label;isAllCaps=false;maxLines=2
            textSize=17f;setTextColor(Color.WHITE);background=surface()
            gravity=android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
            setPadding(dp(20),dp(12),dp(20),dp(12));minHeight=dp(56)
            setOnClickListener {run()}
            content.addView(this,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(10)})
        }
        rayButton=setting(AppText.get(if(rayMode)R.string.relative_ray_on else R.string.relative_ray_off)){
            rayMode=!rayMode
            getSharedPreferences("pointer_ui",MODE_PRIVATE).edit().putBoolean("relative_ray_enabled",rayMode).apply()
            rayView.resetInput()
            rayButton.text=AppText.get(if(rayMode)R.string.relative_ray_on else R.string.relative_ray_off)
            updateHidUi()
        }
        rayButton.setOnLongClickListener {closeSettingsPage();startPointerObservation();true}
        alignButton=setting(AppText.get(if(alignmentRequested)R.string.alignment_on else R.string.alignment_off)){
            alignmentRequested=!alignmentRequested
            getSharedPreferences("pointer_ui",MODE_PRIVATE).edit().putBoolean("alignment_enabled",alignmentRequested).apply()
            alignButton.text=AppText.get(if(alignmentRequested)R.string.alignment_on else R.string.alignment_off)
            syncAlignmentPreference()
        }
        lateinit var speed:Button
        speed=setting(AppText.get(if(rayAlignment.isFast())R.string.assist_responsive else R.string.assist_steady)){
            rayAlignment.toggleSpeed()
            getSharedPreferences("pointer_ui",MODE_PRIVATE).edit().putBoolean("fast_alignment",rayAlignment.isFast()).apply()
            speed.text=AppText.get(if(rayAlignment.isFast())R.string.assist_responsive else R.string.assist_steady)
        }
        setting(AppText.get(R.string.button_guide)){showButtonGuide()}
        setting(AppText.get(R.string.quick_start)){showOnboarding()}
        page.addView(android.widget.ScrollView(this).apply {addView(content)},LinearLayout.LayoutParams(-1,0,1f))
        return page
    }
    private fun showButtonGuide(){
        val content=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            val inset=(24*resources.displayMetrics.density).toInt()
            setPadding(inset,inset,inset,inset)
        }
        val rows=listOf(
            listOf(R.string.connect_mouse,R.string.enable_control,R.string.pause_control,R.string.scroll_up,R.string.scroll_down) to R.string.main_buttons_description,
            listOf(R.string.drag_left,R.string.click_pointer,R.string.drag_right) to R.string.pointer_buttons_description,
            listOf(R.string.relative_ray_control) to R.string.ray_setting_description,
            listOf(R.string.alignment_on) to R.string.alignment_setting_description,
            listOf(R.string.assist_responsive,R.string.assist_steady) to R.string.speed_setting_description,
            listOf(R.string.language_settings) to R.string.app_language_description,
            listOf(R.string.restore_control) to R.string.restore_control_does_not_center_the_ipad,
            listOf(R.string.hide_controls,R.string.show_controls) to R.string.lock_description)
        rows.forEachIndexed {index,(labels,description) ->
            content.addView(LinearLayout(this).apply {
                orientation=LinearLayout.HORIZONTAL
                setBackgroundColor(if(index%2==0)Color.rgb(35,43,56) else Color.rgb(24,31,42))
                fun cell(value:String,weight:Float){
                    addView(TextView(this@MainActivity).apply {
                        text=value;textSize=16f;setTextColor(Color.WHITE)
                        val pad=(12*resources.displayMetrics.density).toInt()
                        setPadding(pad,pad,pad,pad)
                    },LinearLayout.LayoutParams(0,-2,weight))
                }
                cell(labels.joinToString("\n"){AppText.get(it)},1f)
                cell(AppText.get(description),2f)
            },LinearLayout.LayoutParams(-1,-2))
        }
        android.app.AlertDialog.Builder(this).setTitle(AppText.get(R.string.button_guide))
            .setView(android.widget.ScrollView(this).apply {addView(content)})
            .setPositiveButton(AppText.get(R.string.close),null).show()
    }
    private fun showOnboarding(){
        val cards=intArrayOf(R.string.start_card_1,R.string.start_card_2,R.string.start_card_3,
            R.string.start_card_4,R.string.start_card_5)
        var step=0
        val card=TextView(this).apply {
            textSize=21f;setTextColor(Color.WHITE)
            val pad=(28*resources.displayMetrics.density).toInt()
            setPadding(pad,pad,pad,pad);setLineSpacing(8f,1.1f)
            setBackgroundColor(Color.rgb(35,43,56))
        }
        val dialog=android.app.AlertDialog.Builder(this)
            .setTitle(AppText.get(R.string.quick_start))
            .setView(android.widget.ScrollView(this).apply {addView(card)})
            .setPositiveButton(AppText.get(R.string.guide_next),null)
            .setNegativeButton(AppText.get(R.string.guide_skip),null)
            .setNeutralButton(AppText.get(R.string.guide_previous),null).create()
        fun render(){
            dialog.setTitle("${AppText.get(R.string.quick_start)} · ${step+1}/5")
            card.text=AppText.get(cards[step]);card.scrollTo(0,0)
            (card.parent as View).scrollTo(0,0)
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).isEnabled=step>0
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).text=AppText.get(
                if(step==cards.lastIndex)R.string.guide_done else R.string.guide_next)
        }
        dialog.setOnDismissListener {
            getSharedPreferences("onboarding",MODE_PRIVATE).edit().putBoolean("seen",true).apply()
        }
        dialog.setOnShowListener {
            render()
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if(step==cards.lastIndex)dialog.dismiss() else {step++;render()}
            }
            dialog.getButton(android.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                if(step>0){step--;render()}
            }
        }
        dialog.show()
    }
    private fun openSettingsPage(){
        settingsOpen=true;settingsBack.isEnabled=true;pendingControlRestore=false
        updateChrome()
        attachedHid?.setFocused(inputOwner,false)
        rayAlignment.setEnabled(false);rayView.resetInput();pointerObservation.cancel()
        mainPage.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        settingsPage.visibility=View.VISIBLE;settingsPage.requestFocus()
        refreshVideoState()
    }
    private fun closeSettingsPage(){
        settingsOpen=false;settingsBack.isEnabled=false;settingsPage.visibility=View.GONE
        updateChrome()
        mainPage.importantForAccessibility=View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
        attachedHid?.setFocused(inputOwner,hasWindowFocus())
        syncAlignmentPreference()
        updateHidUi();refreshVideoState()
    }
    override fun onSaveInstanceState(outState:Bundle){
        outState.putBoolean("chrome_hidden",chromeHidden)
        outState.putBoolean("settings_open",settingsOpen)
        super.onSaveInstanceState(outState)
    }
    private fun syncAlignmentPreference(){
        val enabled=alignmentRequested&&!settingsOpen&&!isInPictureInPictureMode
        if(rayAlignment.enabled!=enabled)rayAlignment.setEnabled(enabled)
    }
    private fun showLanguageSettings(){
        val tags=arrayOf("","en","de","fr","zh-CN")
        val names=arrayOf(AppText.get(R.string.follow_system),AppText.get(R.string.language_english),AppText.get(R.string.language_german),AppText.get(R.string.language_french),AppText.get(R.string.language_chinese))
        var selected=tags.indexOf(AppText.selection(this)).coerceAtLeast(0)
        android.app.AlertDialog.Builder(this)
            .setTitle(AppText.get(R.string.language_settings))
            .setSingleChoiceItems(names,selected){_,which -> selected=which}
            .setPositiveButton(AppText.get(R.string.apply_language)){_,_ ->
                if(tags[selected]!=AppText.selection(this)){
                    pendingControlRestore=false
                    attachedHid?.disarm()
                    rayView.resetInput()
                    AppText.setLanguage(this,tags[selected])
                    svc?.refreshLanguage()
                    recreate()
                }
            }
            .setNegativeButton(AppText.get(R.string.close),null).show()
    }
    private fun restoreWhenFocused(){
        if(!pendingControlRestore || !hasWindowFocus())return
        pendingControlRestore=false
        restoreDirectControl()
    }
    private fun restoreDirectControl(){
        alignmentRequested=false
        getSharedPreferences("pointer_ui",MODE_PRIVATE).edit().putBoolean("alignment_enabled",false).apply()
        rayAlignment.cancelCalibration()
        rayAlignment.setEnabled(false)
        rayAlignment.calibration.resetForDirectControl()
        rayMode=true
        getSharedPreferences("pointer_ui",MODE_PRIVATE).edit().putBoolean("relative_ray_enabled",true).apply()
        rayView.resetInput()
        if(attachedHid?.isArmed!=true)attachedHid?.toggleArmed()
        updateHidUi()
    }

    private fun refreshVideoState() {
        if (!::waiting.isInitialized) return
        // Broadcasts are notifications; always read the current service snapshot.
        waiting.visibility = if (VideoUiState.showWaiting(isInPictureInPictureMode,surfaceReady,svc?.video?.hasFramesFor(surfaceOwner)==true)) View.VISIBLE else View.GONE
        val decision="waiting=${waiting.visibility==View.VISIBLE} pip=$isInPictureInPictureMode ready=$surfaceReady owner=${System.identityHashCode(surfaceOwner)}"
        if(decision!=lastWaitingDecision){lastWaitingDecision=decision;android.util.Log.i("AirPlayDisplay",decision+" "+svc?.video?.displayDiagnostic(surfaceOwner))}
        if (::rayView.isInitialized) {
            val usable = !settingsOpen && rayMode && waiting.visibility == View.GONE && attachedHid?.isArmed == true && !isInPictureInPictureMode
            rayView.visibility = if (usable) View.VISIBLE else View.GONE
            rayView.isEnabled = usable
            if (!usable) rayView.resetInput()
        }
    }

    private fun connectMouse() {
        val receiver = svc ?: return
        if (!receiver.hid.permitted() && Build.VERSION.SDK_INT >= 31) {
            requestPermissions(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_ADVERTISE), 41)
        } else receiver.enableHid()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 41) {
            if (svc?.hid?.permitted() == true) svc?.enableHid()
            else hidStatus.text = AppText.get(R.string.bluetooth_permission_is_needed_for_the_mouse)
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if(settingsOpen)return super.dispatchGenericMotionEvent(event)
        if(::chromeLock.isInitialized&&chromeLock.isShown){
            val bounds=android.graphics.Rect()
            if(chromeLock.getGlobalVisibleRect(bounds)&&bounds.contains(event.rawX.toInt(),event.rawY.toInt())){
                rayView.resetInput()
                return super.dispatchGenericMotionEvent(event)
            }
        }
        if(::rayAlignment.isInitialized && rayAlignment.calibration.isActive && event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK))return true
        if(::rayAlignment.isInitialized && rayAlignment.calibration.isActive && event.actionMasked==MotionEvent.ACTION_SCROLL)return true
        if(::rayAlignment.isInitialized && event.actionMasked==MotionEvent.ACTION_SCROLL)rayAlignment.calibration.externalAction()
        if (attachedHid?.onMotion(event) == true) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        attachedHid?.setFocused(inputOwner, hasFocus&&!settingsOpen)
        if (!hasFocus && ::rayView.isInitialized) rayView.resetInput()
        if (!hasFocus && ::pointerObservation.isInitialized) pointerObservation.cancel()
        if(hasFocus&&::rayAlignment.isInitialized)restoreWhenFocused()
    }

    override fun onPause() {
        pendingControlRestore=false
        if(rayAlignment.calibration.isActive)rayAlignment.cancelCalibration()
        rayAlignment.calibration.externalAction()
        rayAlignment.setEnabled(false)
        android.util.Log.i("AirPlayLifecycle", "pause surfaceReady=$surfaceReady")
        if(VideoUiState.parkOnPause(isInPictureInPictureMode))surfaceOwner?.let { svc?.video?.parkBeforeWindowStops(it) }
        pointerObservation.cancel()
        if (::rayView.isInitialized) rayView.resetInput()
        attachedHid?.setFocused(inputOwner, false)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        syncAlignmentPreference()
        android.util.Log.i("AirPlayLifecycle", "resume rebuild=$rebuildSurfaceOnResume surfaceReady=$surfaceReady")
        if (rebuildSurfaceOnResume && ::surfaceView.isInitialized) {
            rebuildSurfaceOnResume = false
            rebuildVideoSurface()
        } else if (surfaceReady) {
            // Pause/resume without stop also needs to leave the parking surface.
            surfaceOwner?.let { svc?.attachSurface(it,surfaceView.holder.surface) }
        }
        attachedHid?.setFocused(inputOwner, hasWindowFocus()&&!settingsOpen)
        refreshVideoState()
    }

    override fun onStop() {
        // Quest may preserve the Java Surface while replacing its window/layer
        // during doff/wake. Recreate that layer exactly once on the next resume.
        rebuildSurfaceOnResume = true
        android.util.Log.i("AirPlayLifecycle", "stop: display layer will be recreated on return")
        super.onStop()
    }

    private fun rebuildVideoSurface() {
        val old = surfaceView
        val parent = old.parent as? FrameLayout ?: return
        val index = parent.indexOfChild(old)
        val layout = old.layoutParams
        pointerObservation.cancel()
        surfaceOwner?.let { svc?.detachSurface(it) }
        surfaceOwner = null
        surfaceReady = false
        rayView.resetInput()
        rayView.visibility = View.GONE
        waiting.visibility = View.VISIBLE
        old.holder.removeCallback(this)
        parent.removeView(old)
        surfaceView = SurfaceView(this).apply {
            holder.setFormat(PixelFormat.OPAQUE)
            holder.addCallback(this@MainActivity)
        }
        parent.addView(surfaceView,index,layout)
        android.util.Log.i("AirPlayLifecycle", "new video layer created; awaiting fresh holder and buffer")
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (holder !== surfaceView.holder) return
        android.util.Log.i("AirPlayLifecycle", "current holder created valid=${holder.surface.isValid}")
        surfaceReady = true
        surfaceOwner = Any()
        attachWhenReady()
        fitVideo()
    }

    private fun fitVideo() {
        val ratio = svc?.videoAspectRatio ?: return
        val parent = surfaceView.parent as? View ?: return
        if (!ratio.isFinite() || ratio <= 0f || parent.width <= 0 || parent.height <= 0) return
        val width: Int
        val height: Int
        if (parent.width.toFloat() / parent.height > ratio) {
            height = parent.height
            width = (height * ratio).toInt().coerceAtLeast(1)
        } else {
            width = parent.width
            height = (width / ratio).toInt().coerceAtLeast(1)
        }
        val lp = surfaceView.layoutParams as FrameLayout.LayoutParams
        if (lp.width != width || lp.height != height || lp.gravity != Gravity.CENTER) {
            pointerObservation.cancel()
            lp.width = width; lp.height = height; lp.gravity = Gravity.CENTER
            surfaceView.layoutParams = lp
            if (::rayView.isInitialized) {
                rayView.resetInput()
                rayView.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.CENTER)
            }
            android.util.Log.i("AirPlayLayout", "fit=${width}x${height} container=${parent.width}x${parent.height} aspect=$ratio")
        }
    }

    private fun attachWhenReady() {
        if (!surfaceReady || isDestroyed) return
        val receiver = svc
        if (receiver != null) surfaceOwner?.let { receiver.attachSurface(it, surfaceView.holder.surface) }
        else surfaceView.postDelayed({ attachWhenReady() }, 100)
        refreshVideoState()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        if (holder !== surfaceView.holder) return
        android.util.Log.i("AirPlayLifecycle", "current holder destroyed")
        pointerObservation.cancel()
        surfaceReady = false
        surfaceOwner?.let { svc?.detachSurface(it) }
        surfaceOwner = null
        waiting.visibility = View.VISIBLE
        rayView.resetInput()
        rayView.visibility = View.GONE
        refreshVideoState()
    }

    override fun onDestroy() {
        rayAlignment.close()
        pointerObservation.close()
        attachedHid?.detachUi(inputOwner)
        attachedHid = null
        unregisterReceiver(framesReceiver)
        if (isFinishing) stopService(Intent(this, AirPlayService::class.java))
        super.onDestroy()
    }

    override fun onUserLeaveHint() {
        if (svc?.video?.hasFrames == true) enterPip()
    }

    override fun onPictureInPictureModeChanged(inPip: Boolean) {
        super.onPictureInPictureModeChanged(inPip)
        updateChrome()
        if (inPip) attachedHid?.setFocused(inputOwner, false)
        rayView.resetInput()
        rayView.visibility = View.GONE
        refreshVideoState()
    }

    private fun updateChrome(){
        val hide=chromeHidden||isInPictureInPictureMode
        controls.visibility=if(hide)View.GONE else View.VISIBLE
        hidStatus.visibility=if(hide)View.GONE else View.VISIBLE
        chromeLock.visibility=if(isInPictureInPictureMode||settingsOpen)View.GONE else View.VISIBLE
        chromeLock.text=if(chromeHidden)"\uD83D\uDD12" else "\uD83D\uDD13"
        chromeLock.contentDescription=AppText.get(if(chromeHidden)R.string.show_controls else R.string.hide_controls)
        chromeLock.tooltipText=chromeLock.contentDescription
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }
}
