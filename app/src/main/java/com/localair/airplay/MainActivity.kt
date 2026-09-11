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
        }
        root.addView(hidStatus, LinearLayout.LayoutParams(-1, -2))
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
        previousButton = action(primary, "上一条") { svc?.hid?.scrollPage(1) }
        nextButton = action(primary, "下一条") { svc?.hid?.scrollPage(-1) }
        leftButton = action(pointer, "左滑（拖拽）") { svc?.hid?.dragPointer(-1) }
        clickButton = action(pointer, "点击当前指针") { svc?.hid?.clickPointer() }
        rightButton = action(pointer, "右滑（拖拽）") { svc?.hid?.dragPointer(1) }
        rayButton = action(pointer, "相对射线：关") {
            rayMode = !rayMode
            getSharedPreferences("pointer_ui", MODE_PRIVATE).edit()
                .putBoolean("relative_ray_enabled", rayMode).apply()
            rayView.resetInput()
            updateHidUi()
            refreshVideoState()
        }
        root.addView(controls, LinearLayout.LayoutParams(-1, -2))
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
        hidStatus.text = hid.statusText()
        if (rayMode && hid.isArmed) hidStatus.text = "相对射线：移动射线带动指针 · 扳机/确认键点击当前指针（非绝对定位）"
        rayButton.text = if (rayMode) "相对射线：开" else "相对射线：关"
        controlButton.text = if (hid.isArmed) "暂停控制" else "启用控制"
        previousButton.isEnabled = hid.isArmed
        nextButton.isEnabled = hid.isArmed
        leftButton.isEnabled = hid.isArmed
        rightButton.isEnabled = hid.isArmed
        clickButton.isEnabled = hid.isArmed
        refreshVideoState()
    }

    private fun refreshVideoState() {
        if (!::waiting.isInitialized) return
        // Broadcasts are notifications; always read the current service snapshot.
        waiting.visibility = if (surfaceReady && svc?.video?.hasFrames == true) View.GONE else View.VISIBLE
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
        if (attachedHid?.onMotion(event) == true) return true
        return super.dispatchGenericMotionEvent(event)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        attachedHid?.setFocused(inputOwner, hasFocus)
        if (!hasFocus && ::rayView.isInitialized) rayView.resetInput()
    }

    override fun onPause() {
        if (::rayView.isInitialized) rayView.resetInput()
        attachedHid?.setFocused(inputOwner, false)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        attachedHid?.setFocused(inputOwner, hasWindowFocus())
        refreshVideoState()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
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
        surfaceReady = false
        surfaceOwner?.let { svc?.detachSurface(it) }
        surfaceOwner = null
        waiting.visibility = View.VISIBLE
        rayView.resetInput()
        rayView.visibility = View.GONE
    }

    override fun onDestroy() {
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
