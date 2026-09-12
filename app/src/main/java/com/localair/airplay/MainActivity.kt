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
    private var pendingCalibrationStart=false

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
        pointerObservation = PointerObservation(this)
        rayAlignment = RayAlignment({ surfaceView }, { attachedHid }, {
            when {
                !hasWindowFocus() -> "投屏页未在前台"
                !rayMode -> "射线模式已关闭"
                attachedHid?.isArmed != true -> "鼠标控制尚未启用"
                !surfaceReady -> "等待投屏画面"
                svc?.video?.hasRecentOutputFor(surfaceOwner) != true -> "等待新的投屏帧"
                else -> null
            }
        }) { updateHidUi() }
        val pointerPrefs=getSharedPreferences("pointer_ui",MODE_PRIVATE)
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
            text = "Quest 投屏与鼠标 · 正在启动"
            maxLines=1;ellipsize=android.text.TextUtils.TruncateAt.END
        }
        // Status updates cannot change the weighted video area's size.
        root.addView(hidStatus, LinearLayout.LayoutParams(-1, hidStatus.lineHeight+hidStatus.paddingTop+hidStatus.paddingBottom))
        hidStatus.setOnClickListener { showCalibration() }
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
            text = "${DeviceIdentity.deviceName(this@MainActivity)}\nwaiting for AirPlay…"
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
            setOnClickListener { run() }
            row.addView(this, LinearLayout.LayoutParams(0, (56 * resources.displayMetrics.density).toInt(), 1f))
        }
        action(primary, "连接鼠标") { connectMouse() }
        controlButton = action(primary, "启用控制") { svc?.hid?.toggleArmed(); updateHidUi() }
        previousButton = action(primary, "上一条") { rayAlignment.calibration.invalidateSegment(); svc?.hid?.scrollPage(1) }
        nextButton = action(primary, "下一条") { rayAlignment.calibration.invalidateSegment(); svc?.hid?.scrollPage(-1) }
        action(primary, "校准") { showCalibration() }
        lateinit var speedButton: Button
        speedButton=action(primary,if(rayAlignment.isFast())"跟手优先" else "稳定优先") {
            rayAlignment.toggleSpeed()
            getSharedPreferences("pointer_ui",MODE_PRIVATE).edit().putBoolean("fast_alignment",rayAlignment.isFast()).apply()
            speedButton.text=if(rayAlignment.isFast())"跟手优先" else "稳定优先"
        }
        leftButton = action(pointer, "左滑（拖拽）") { rayAlignment.calibration.externalAction(); svc?.hid?.dragPointer(-1) }
        clickButton = action(pointer, "点击当前指针") { rayAlignment.calibration.invalidateSegment(); svc?.hid?.clickPointer() }
        rightButton = action(pointer, "右滑（拖拽）") { rayAlignment.calibration.externalAction(); svc?.hid?.dragPointer(1) }
        rayButton = action(pointer, "相对射线：关") {
            rayMode = !rayMode
            getSharedPreferences("pointer_ui", MODE_PRIVATE).edit()
                .putBoolean("relative_ray_enabled", rayMode).apply()
            rayView.resetInput()
            updateHidUi()
            refreshVideoState()
        }
        alignButton = action(pointer, "停稳对齐：关") {
            rayAlignment.setEnabled(!rayAlignment.enabled)
        }
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))
        rayButton.setOnLongClickListener {
            startPointerObservation()
            true
        }
        setContentView(root)

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
            android.widget.Toast.makeText(this,"未开始：请先恢复投屏并保持应用在前台",android.widget.Toast.LENGTH_LONG).show()
            return
        }
        val started = pointerObservation.start(surfaceView.holder.surface,surfaceView.width,surfaceView.height) { message ->
            android.util.Log.i("PointerObservation",message)
            if (!isDestroyed) android.widget.Toast.makeText(this,message,android.widget.Toast.LENGTH_LONG).show()
        }
        android.util.Log.i("PointerObservation", "Capture accepted=$started")
        android.widget.Toast.makeText(this,if (started) "只读采样约3秒，请手动移动指针" else "未开始：采样忙或视频不可用",android.widget.Toast.LENGTH_LONG).show()
    }

    private fun attachControlsWhenReady() {
        if (isDestroyed || isFinishing) return
        val hid = svc?.hid
        if (hid == null) { hidStatus.postDelayed({ attachControlsWhenReady() }, 100); return }
        attachedHid = hid
        hid.attachUi(inputOwner) { updateHidUi() }
        hid.setFocused(inputOwner, hasWindowFocus())
        updateHidUi()
        refreshVideoState()
    }

    private fun updateHidUi() {
        if (!::hidStatus.isInitialized) return
        val hid = attachedHid ?: return
        var primary=hid.statusText()
        var detail=""
        if(rayMode&&hid.isArmed){primary="相对射线控制";detail="移动射线带动指针；确认键点击当前指针"}
        if(rayAlignment.enabled){primary=rayAlignment.status;detail="停稳对齐（实验） · 不自动点击"}
        if(rayAlignment.calibration.isActive){
            primary=if(rayAlignment.calibration.awaitingReference())"校准 · 最后确认（见页面）" else "校准 ${rayAlignment.calibration.stage()+1}/10 · 按页面提示操作"
            detail=rayAlignment.calibration.status+" · "+rayAlignment.status
        }else if(hid.isArmed&&rayAlignment.calibration.status!="尚未校准"){
            if(rayAlignment.enabled)detail=rayAlignment.calibration.status
            else primary=rayAlignment.calibration.status
        }
        if(hidStatus.text.toString()!=primary)hidStatus.text=primary
        hidStatus.contentDescription=primary+"。"+detail
        alignButton.text = if(rayAlignment.calibration.isActive)"退出校准并对齐" else if (rayAlignment.enabled) "停稳对齐：开" else "停稳对齐：关"
        rayButton.text = if (rayMode) "相对射线：开" else "相对射线：关"
        controlButton.text = if (hid.isArmed) "暂停控制" else "启用控制"
        val actionsAllowed=hid.isArmed&&!rayAlignment.calibration.isActive
        previousButton.isEnabled = actionsAllowed
        nextButton.isEnabled = actionsAllowed
        leftButton.isEnabled = actionsAllowed
        rightButton.isEnabled = actionsAllowed
        clickButton.isEnabled = actionsAllowed
        refreshVideoState()
    }

    private fun showCalibration(){
        val calibration=rayAlignment.calibration
        val panel=LinearLayout(this).apply {orientation=LinearLayout.VERTICAL;setPadding(28,12,28,12)}
        fun note(value:String,selectable:Boolean=false){panel.addView(TextView(this).apply {
            text=value;textSize=16f;setPadding(0,8,0,8);setTextIsSelectable(selectable)
        })}
        note("首次：在 iPad 打开下面的底板，保持屏幕镜像。已经打开就直接开始。")
        note(svc?.calibrationBoard?.address() ?: "底板服务尚未就绪",true)
        note("在 Safari 分享菜单添加收藏；下次点收藏即可。网络地址改变时，以此处新地址为准。")
        note("开始后，看同一投屏页面内的目标和文字完成步骤。无需点击目标，最后在 Quest 确认。")
        val periods=android.widget.RadioGroup(this)
        val choices=listOf(3 to "每3分钟复核",5 to "每5分钟复核（默认）")
        for((minutes,label) in choices)periods.addView(android.widget.RadioButton(this).apply {id=minutes;text=label})
        periods.check(calibration.intervalMinutes())
        periods.setOnCheckedChangeListener {_,minutes ->
            calibration.intervalMinutes(minutes)
            getSharedPreferences("pointer_ui",MODE_PRIVATE).edit().putInt("calibration_minutes",minutes).apply()
        }
        panel.addView(periods)
        note("当前：${calibration.status}\n${rayAlignment.status}")
        val dialog=android.app.AlertDialog.Builder(this)
            .setTitle("校准")
            .setView(android.widget.ScrollView(this).apply {addView(panel)})
            .setPositiveButton("底板已打开，开始"){_,_ ->
                pendingCalibrationStart=true
            }
            .setNeutralButton("取消当前校准"){_,_ -> rayAlignment.cancelCalibration()}
            .setNegativeButton("关闭",null).create()
        dialog.setOnDismissListener {window.decorView.post { startPendingCalibration() }}
        dialog.show()
    }
    private fun startPendingCalibration(){
        if(!pendingCalibrationStart || !hasWindowFocus())return
        pendingCalibrationStart=false
        if(attachedHid?.isArmed!=true)attachedHid?.toggleArmed()
        rayAlignment.beginCalibration()
        android.widget.Toast.makeText(this,"按投屏页面内的提示操作",android.widget.Toast.LENGTH_SHORT).show()
    }

    private fun refreshVideoState() {
        if (!::waiting.isInitialized) return
        // Broadcasts are notifications; always read the current service snapshot.
        waiting.visibility = if (surfaceReady && svc?.video?.hasFramesFor(surfaceOwner) == true) View.GONE else View.VISIBLE
        if (::rayView.isInitialized) {
            val usable = rayMode && waiting.visibility == View.GONE && attachedHid?.isArmed == true && !isInPictureInPictureMode
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
            else hidStatus.text = "需要蓝牙权限才能使用鼠标；投屏可继续使用"
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if(::rayAlignment.isInitialized && rayAlignment.calibration.isActive && event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK))return true
        if(::rayAlignment.isInitialized && rayAlignment.calibration.isActive && event.actionMasked==MotionEvent.ACTION_SCROLL)return true
        if(::rayAlignment.isInitialized && event.actionMasked==MotionEvent.ACTION_SCROLL)rayAlignment.calibration.externalAction()
        if (attachedHid?.onMotion(event) == true) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        attachedHid?.setFocused(inputOwner, hasFocus)
        if (!hasFocus && ::rayView.isInitialized) rayView.resetInput()
        if (!hasFocus && ::pointerObservation.isInitialized) pointerObservation.cancel()
        if(hasFocus&&::rayAlignment.isInitialized)startPendingCalibration()
    }

    override fun onPause() {
        pendingCalibrationStart=false
        if(rayAlignment.calibration.isActive)rayAlignment.cancelCalibration()
        rayAlignment.calibration.externalAction()
        rayAlignment.setEnabled(false)
        android.util.Log.i("AirPlayLifecycle", "pause surfaceReady=$surfaceReady")
        surfaceOwner?.let { svc?.video?.parkBeforeWindowStops(it) }
        pointerObservation.cancel()
        if (::rayView.isInitialized) rayView.resetInput()
        attachedHid?.setFocused(inputOwner, false)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.i("AirPlayLifecycle", "resume rebuild=$rebuildSurfaceOnResume surfaceReady=$surfaceReady")
        if (rebuildSurfaceOnResume && ::surfaceView.isInitialized) {
            rebuildSurfaceOnResume = false
            rebuildVideoSurface()
        } else if (surfaceReady) {
            // Pause/resume without stop also needs to leave the parking surface.
            surfaceOwner?.let { svc?.attachSurface(it,surfaceView.holder.surface) }
        }
        attachedHid?.setFocused(inputOwner, hasWindowFocus())
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
        controls.visibility = if (inPip) View.GONE else View.VISIBLE
        hidStatus.visibility = if (inPip) View.GONE else View.VISIBLE
        if (inPip) attachedHid?.setFocused(inputOwner, false)
        rayView.resetInput()
        rayView.visibility = View.GONE
        waiting.visibility = if (inPip) View.GONE else
            if (svc?.video?.hasFrames == true) View.GONE else View.VISIBLE
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        }
    }
}
