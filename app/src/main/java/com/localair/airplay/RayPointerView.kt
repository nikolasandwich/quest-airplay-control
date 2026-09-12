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
class RayPointerView(context: Context, private val hid: () -> RayInputTransport?) : View(context) {
    var observer: ((Float, Float, Int, Int, Long) -> Unit)? = null
    var onReset: (() -> Unit)? = null
    var calibration: PointerCalibration? = null
    var onCalibrationConfirm: (() -> Unit)? = null
    var onReport: ((Int,Int,Long,Boolean)->Unit)? = null
    private val motion = RayDeltaEngine()
    private var sampleStart = 0L
    private var samples = 0
    private var busySamples = 0
    private var staleSamples = 0
    private var sentReports = 0
    private var sentUnits = 0L
    private var ackCount = 0
    private var ackTotal = 0L
    private var ackMax = 0L
    private var eventAgeMax = 0L
    private fun recordSample(now: Long, age: Long, busy: Boolean, stale: Boolean) {
        if(sampleStart==0L)sampleStart=now
        samples++; if(busy)busySamples++; if(stale)staleSamples++
        eventAgeMax=maxOf(eventAgeMax,age)
        if(now-sampleStart>=5000){
            android.util.Log.i("RayInputStats", "windowMs=${now-sampleStart} events=$samples busy=$busySamples stale=$staleSamples reports=$sentReports units=$sentUnits eventAgeMaxMs=$eventAgeMax ackCount=$ackCount ackMeanMs=${if(ackCount>0)ackTotal/ackCount else 0} ackMaxMs=$ackMax width=$width height=$height unitsPerPixel=${800.0/width.coerceAtLeast(1)} discardedUnitsTotal=${motion.discardedUnits()} mergedUnitsTotal=${motion.mergedUnits()}")
            sampleStart=now; samples=0;busySamples=0;staleSamples=0;sentReports=0;sentUnits=0;ackCount=0;ackTotal=0;ackMax=0;eventAgeMax=0
        }
    }
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
    fun isInputIdle()=!touching && confirmKey == -1

    init {
        isFocusable = true; isFocusableInTouchMode = true; isClickable = true
        contentDescription = AppText.get(R.string.relative_ray_mouse_move_the_ray_to)
    }
    fun resetInput() {
        onReset?.invoke()
        motion.reset(); aimX = -1f; aimY = -1f; touching = false; tap = false; confirmKey = -1
        invalidate()
    }
    private fun inside(x: Float, y: Float) = x.isFinite() && y.isFinite() && x >= 0 && y >= 0 && x < width && y < height
    private fun aim(x: Float, y: Float) { aimX = x; aimY = y; invalidate() }
    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (!isEnabled) { resetInput(); return false }
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER -> { resetInput(); requestFocus(); aim(event.x, event.y) }
            MotionEvent.ACTION_HOVER_MOVE -> {
                if (!inside(event.x, event.y)) { resetInput(); return true }
                aim(event.x, event.y)
                observer?.invoke(event.x,event.y,width,height,event.eventTime)
                val controller = hid()
                val now=android.os.SystemClock.uptimeMillis()
                val age=(now-event.eventTime).coerceAtLeast(0)
                val eligible=!touching && calibration?.awaitingReference()!=true && controller?.canTrackPointer()==true
                val ready=controller?.canMovePointer()==true
                recordSample(now,age,eligible&&!ready,age>RayDeltaEngine.MAX_PENDING_MS)
                if(age>RayDeltaEngine.MAX_PENDING_MS){motion.reset();return true}
                val delta = motion.event(event.x, event.y, width, height, event.eventTime,
                    eligible,ready,calibration?.gain())
                if (delta.x!=0 || delta.y!=0) {
                    val sent=controller?.movePointer(delta.x,delta.y) { ok ->
                        val completedAt=android.os.SystemClock.uptimeMillis()
                        if(ok){ackCount++;ackTotal+=completedAt-now;ackMax=maxOf(ackMax,completedAt-now)}
                        else motion.reset()
                        onReport?.invoke(delta.x,delta.y,completedAt,ok)
                    }==true
                    if(sent){sentReports++;sentUnits+=kotlin.math.abs(delta.x).toLong()+kotlin.math.abs(delta.y)}
                    if(!sent){motion.reset();onReport?.invoke(delta.x,delta.y,android.os.SystemClock.uptimeMillis(),false)}
                }
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
                if(calibration?.isActive==true)observer?.invoke(event.x,event.y,width,height,event.eventTime)
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
        motion.reset()
        if(calibration?.isActive==true){onCalibrationConfirm?.invoke();motion.reset();return true}
        calibration?.invalidateSegment()
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
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if(!hasWindowFocus)resetInput()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!inside(aimX,aimY)) return
        // This marks the local ray only; it does not claim to be the iPad cursor.
        canvas.drawLine(aimX-7,aimY,aimX+7,aimY,paint)
        canvas.drawLine(aimX,aimY-7,aimX,aimY+7,paint)
    }
}
