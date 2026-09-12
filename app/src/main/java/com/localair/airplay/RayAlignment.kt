package com.localair.airplay

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView

/** Opt-in experiment. Samples video only; never clicks and never starts enabled. */
class RayAlignment(
    private val view: () -> SurfaceView,
    private val hid: () -> HidController?,
    private val gateReason: () -> String?,
    private val changed: () -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val thread = HandlerThread("RayAlignment").apply { start() }
    private val worker = Handler(thread.looper)
    private val detector = ArrowPointerDetector()
    private val identity = PointerIdentityTracker()
    private val policy = SettleController()
    private var epoch = 0
    private var busy = false
    private var closed = false
    private var targetX = Double.NaN
    private var targetY = Double.NaN
    private var rayTime = 0L
    private var lastTimingLog = 0L
    private var lastState = ""
    private var pixels = IntArray(0) // Sampling worker only.
    var enabled = false; private set
    var status = ""; private set
    fun setEnabled(value: Boolean) {
        enabled = value
        invalidateTarget()
        main.removeCallbacks(pump)
        if (value) main.post(pump)
        changed()
    }
    fun invalidateTarget() { epoch++; targetX=Double.NaN; targetY=Double.NaN; identity.reset(); policy.reset(); status="请移动射线识别真实指针" }
    fun onRay(x: Float,y: Float,w: Int,h: Int,time: Long) {
        if (!enabled || w<=0 || h<=0) return
        val scale=minOf(1.0,720.0/maxOf(w,h))
        targetX=(x*scale).coerceIn(0.0,(w*scale).toInt()-1.0)
        targetY=(y*scale).coerceIn(0.0,(h*scale).toInt()-1.0);rayTime=time
        policy.target(targetX,targetY,time)
    }
    private fun publish(value: String) {
        status=value
        if(value!=lastState){
            lastState=value
            android.util.Log.i("RayAlignment","state target=${policy.targetRevision} reason=$value")
            changed()
        }
    }
    private fun schedule() {
        main.removeCallbacks(pump)
        if (!closed && enabled) main.postDelayed(pump,60)
    }
    private val pump = object : Runnable {
        override fun run() {
            if (closed || !enabled) return
            val gate=gateReason()
            if (gate!=null || !targetX.isFinite()) {
                identity.reset();policy.reset()
                if(targetX.isFinite())policy.target(targetX,targetY,SystemClock.uptimeMillis())
                publish(gate ?: "请指向投屏画面")
                schedule();return
            }
            if (busy) { schedule(); return }
            val surface=view(); val scale=minOf(1f,720f/maxOf(surface.width,surface.height).coerceAtLeast(1))
            val w=(surface.width*scale).toInt(); val h=(surface.height*scale).toInt()
            if (w<20 || h<20 || !surface.holder.surface.isValid) { invalidateTarget(); schedule();return }
            val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            val requestTime=SystemClock.uptimeMillis(); val generation=epoch
            busy=true
            try {
                PixelCopy.request(surface.holder.surface,bitmap,{ code ->
                    val copiedAt=SystemClock.uptimeMillis()
                    val matches=try { if(code==PixelCopy.SUCCESS) {
                        if (pixels.size!=w*h) pixels=IntArray(w*h)
                        bitmap.getPixels(pixels,0,w,0,0,w,h)
                        detector.detect(pixels,w,h)
                    } else emptyList() } catch (e: RuntimeException) {
                        android.util.Log.w("RayAlignment","Pointer detection unavailable",e)
                        emptyList()
                    } finally { bitmap.recycle() }
                    val detectedAt=SystemClock.uptimeMillis()
                    main.post {
                        busy=false
                        if (!closed && enabled && generation==epoch && gateReason()==null) {
                            val now=SystemClock.uptimeMillis()
                            val fresh=now-requestTime in 0..250
                            val trusted=if(fresh)identity.update(matches,targetX,targetY,requestTime) else {identity.reset();false}
                            val point=matches.singleOrNull()
                            val distance=point?.let{kotlin.math.hypot(policy.targetX()-it.x,policy.targetY()-it.y)}
                            val ready=hid()?.canMovePointer()==true
                            if (trusted && matches.size==1) {
                                val confirmed=matches[0]
                                val step=policy.observe(confirmed.x.toDouble(),confirmed.y.toDouble(),requestTime,now,true,true,ready)
                                if (step!=null && hid()?.movePointer(step.x,step.y)!=true) policy.rejected()
                                if (step!=null) android.util.Log.i("RayAlignment",
                                    "correction target=${policy.targetRevision} dx=${step.x} dy=${step.y} error=$distance shape=${confirmed.score} copyMs=${copiedAt-requestTime} detectMs=${detectedAt-copiedAt} totalMs=${now-requestTime} state=${policy.status}")
                                publish(policy.status + if(!ready) "：${hid()?.statusText()}" else "")
                            } else {
                                // Pause commands, preserving this target's command/time limits through a gap.
                                publish(if(!fresh) "观察过旧，暂停对齐" else identity.reason)
                            }
                            if(now-lastTimingLog>=1000){
                                lastTimingLog=now
                                android.util.Log.i("RayAlignment","observation target=${policy.targetRevision} copyResult=$code candidates=${matches.size} trusted=$trusted identity=${identity.reason} shape=${point?.score} candidateDistance=$distance targetX=${policy.targetX()} targetY=${policy.targetY()} rayX=$targetX rayY=$targetY transportReady=$ready copyMs=${copiedAt-requestTime} detectMs=${detectedAt-copiedAt} totalMs=${now-requestTime} state=$status")
                            }
                        }
                        schedule()
                    }
                },worker)
            } catch (_: IllegalArgumentException) {
                bitmap.recycle();busy=false;invalidateTarget();schedule()
            }
        }
    }
    fun close(){closed=true;enabled=false;epoch++;main.removeCallbacks(pump);thread.quitSafely()}
}
