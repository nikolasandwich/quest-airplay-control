package com.localair.airplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration

/** Optional relative pointer surface. Hover never presses a remote button. */
class RayPointerView(context: Context, private val hid: () -> HidController?) : View(context) {
    var observer: ((Float, Float, Int, Int, Long) -> Unit)? = null
    var onReset: (() -> Unit)? = null
    private val motion = RayDeltaEngine()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.CYAN; strokeWidth = 2f }
    private var aimX = -1f
    private var aimY = -1f
    private var touching = false
    private var tap = false
    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var confirmKey = -1
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    init {
        isFocusable = true; isFocusableInTouchMode = true; isClickable = true
        contentDescription = "相对射线鼠标：移动射线移动指针，确认点击 iPad 当前指针"
    }
    fun resetInput() {
        onReset?.invoke()
        motion.reset(); aimX = -1f; aimY = -1f; touching = false; tap = false; confirmKey = -1
        invalidate()
    }
    private fun inside(x: Float, y: Float) = x.isFinite() && y.isFinite() && x >= 0 && y >= 0 && x < width && y < height
    private fun aim(x: Float, y: Float) { aimX = x; aimY = y; invalidate() }
    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> { resetInput(); requestFocus(); aim(event.x, event.y) }
            MotionEvent.ACTION_HOVER_MOVE -> {
                if (!inside(event.x, event.y)) { resetInput(); return true }
                aim(event.x, event.y)
                observer?.invoke(event.x,event.y,width,height,event.eventTime)
                val controller = hid()
                val delta = motion.event(event.x, event.y, width, height, event.eventTime,
                    !touching && controller?.canMovePointer() == true)
                if ((delta.x != 0 || delta.y != 0) && controller?.movePointer(delta.x, delta.y) != true) motion.reset()
            }
            MotionEvent.ACTION_HOVER_EXIT -> resetInput()
        }
        return true
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus(); motion.reset(); confirmKey = -1
                touching = true; tap = inside(event.x, event.y)
                downX = event.x; downY = event.y; aim(event.x, event.y)
                downTime = event.eventTime
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount != 1 || !inside(event.x,event.y) ||
                    kotlin.math.abs(event.x-downX)>slop || kotlin.math.abs(event.y-downY)>slop) tap = false
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> resetInput()
            MotionEvent.ACTION_UP -> {
                val click = touching && tap && inside(event.x,event.y) &&
                    event.eventTime - downTime in 0..500 &&
                    kotlin.math.abs(event.x-downX)<=slop && kotlin.math.abs(event.y-downY)<=slop
                touching = false; tap = false; motion.reset()
                if (click) performClick()
            }
        }
        return true
    }
    private fun isConfirm(code: Int) = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER ||
        code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isConfirm(keyCode)) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0 && isEnabled && hasFocus() && inside(aimX,aimY) && !touching) confirmKey = keyCode
        return true
    }
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isConfirm(keyCode)) return super.onKeyUp(keyCode, event)
        val click = confirmKey == keyCode && !event.isCanceled
        confirmKey = -1
        if (click) performClick()
        return true
    }
    override fun performClick(): Boolean {
        onReset?.invoke()
        super.performClick()
        if (isEnabled && hasWindowFocus() && inside(aimX,aimY)) hid()?.clickPointer()
        return true
    }
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, rect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus,direction,rect)
        if (!gainFocus) resetInput()
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { resetInput() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!inside(aimX,aimY)) return
        // This marks the local ray only; it does not claim to be the iPad cursor.
        canvas.drawLine(aimX-7,aimY,aimX+7,aimY,paint)
        canvas.drawLine(aimX,aimY-7,aimX,aimY+7,paint)
    }
}
