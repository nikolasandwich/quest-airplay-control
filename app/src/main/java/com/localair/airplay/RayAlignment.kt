package com.localair.airplay

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.SurfaceView

/** Experimental visual correction. Activity owns the user's preference; this never clicks. */
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
    private val identity = MultiPointerTracker()
    private val policy = SettleController().apply { setFast(true) }
    fun isFast()=policy.isFast
    fun setFast(value:Boolean){policy.setFast(value);invalidateTarget()}
    fun toggleSpeed(){setFast(!policy.isFast);changed()}
    val calibration = PointerCalibration()
    var interactionIdle: () -> Boolean = { true }
    private fun samplingNeeded()=enabled || calibration.isActive || calibration.hasGain()
    private val calibrationDeadline=Runnable {calibration.tick(SystemClock.uptimeMillis());publish(calibration.status);changed()}
    fun beginCalibration(){enabled=false;calibration.begin(SystemClock.uptimeMillis());main.removeCallbacks(calibrationDeadline);main.postDelayed(calibrationDeadline,180000);invalidateTarget();main.removeCallbacks(pump);main.post(pump);changed()}
    fun confirmCalibration(){
        calibration.confirm(targetX,targetY,SystemClock.uptimeMillis())
        publish(calibration.status)
    }
    fun cancelCalibration(){main.removeCallbacks(calibrationDeadline);calibration.cancel();publish(calibration.status);changed()}
    fun rayReport(x:Int,y:Int,time:Long,ok:Boolean){if(!closed)calibration.report(x,y,time,ok)}
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
        // A user's explicit switch to alignment exits the mutually exclusive wizard.
        if(value && calibration.isActive)calibration.cancel()
        enabled = value
        invalidateTarget()
        main.removeCallbacks(pump)
        if (samplingNeeded()) main.post(pump)
        AirPlayService.instance?.calibrationBoard?.update(calibration,status)
        changed()
    }
    fun invalidateTarget() { epoch++; targetX=Double.NaN; targetY=Double.NaN; identity.reset(); policy.reset(); calibration.invalidateSegment(); status=AppText.get(R.string.move_the_ray_to_identify_the_pointer) }
    fun onRay(x: Float,y: Float,w: Int,h: Int,time: Long) {
        if (!samplingNeeded() || w<=0 || h<=0) return
        val scale=minOf(1.0,720.0/maxOf(w,h))
        targetX=(x*scale).coerceIn(0.0,(w*scale).toInt()-1.0)
        targetY=(y*scale).coerceIn(0.0,(h*scale).toInt()-1.0);rayTime=time
        policy.target(targetX,targetY,time)
    }
    private fun publish(value: String) {
        status=value
        AirPlayService.instance?.calibrationBoard?.update(calibration,value)
        if(value!=lastState){
            lastState=value
            android.util.Log.i("RayAlignment","state target=${policy.targetRevision} reason=$value")
            changed()
        }
    }
    private fun schedule() {
        main.removeCallbacks(pump)
        if (!closed && samplingNeeded()) main.postDelayed(pump,if(enabled&&policy.isFast)30 else if(enabled||calibration.isActive)60 else 150)
    }
    private val pump = object : Runnable {
        override fun run() {
            calibration.tick(SystemClock.uptimeMillis())
            if (closed || !samplingNeeded()) return
            val gate=gateReason()
            if (gate!=null || !targetX.isFinite()) {
                identity.reset();policy.reset()
                calibration.invalidateSegment()
                if(targetX.isFinite())policy.target(targetX,targetY,SystemClock.uptimeMillis())
                publish(gate ?: AppText.get(R.string.point_at_the_mirrored_screen))
                schedule();return
            }
            if (busy) { schedule(); return }
            val surface=view(); val scale=minOf(1f,720f/maxOf(surface.width,surface.height).coerceAtLeast(1))
            val w=(surface.width*scale).toInt(); val h=(surface.height*scale).toInt()
            calibration.geometry(w,h)
            if (w<20 || h<20 || !surface.holder.surface.isValid) { invalidateTarget(); schedule();return }
            val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
            val requestTime=SystemClock.uptimeMillis(); val generation=epoch
            val calibrationRegion=calibration.isActive
            busy=true
            try {
                PixelCopy.request(surface.holder.surface,bitmap,{ code ->
                    val copiedAt=SystemClock.uptimeMillis()
                    val matches=try { if(code==PixelCopy.SUCCESS) {
                        if (pixels.size!=w*h) pixels=IntArray(w*h)
                        bitmap.getPixels(pixels,0,w,0,0,w,h)
                        detector.detect(pixels,w,h).filter { !calibrationRegion || it.y>=h*.28 && it.y+it.height<=h*.90 }
                    } else emptyList() } catch (e: RuntimeException) {
                        android.util.Log.w("RayAlignment","Pointer detection unavailable",e)
                        emptyList()
                    } finally { bitmap.recycle() }
                    val detectedAt=SystemClock.uptimeMillis()
                    main.post {
                        busy=false
                        if (!closed && samplingNeeded() && generation==epoch && gateReason()==null) {
                            val now=SystemClock.uptimeMillis()
                            val fresh=now-requestTime in 0..250
                            val trusted=if(fresh)identity.update(matches,targetX,targetY,requestTime) else {identity.reset();false}
                            val point=identity.selected()
                            val distance=point?.let{kotlin.math.hypot(policy.targetX()-it.x,policy.targetY()-it.y)}
                            val ready=hid()?.canMovePointer()==true
                            val calibrationBefore=calibration.status
                            calibration.observe(point?.x?.toDouble() ?: Double.NaN,point?.y?.toDouble() ?: Double.NaN,now,trusted,ready&&interactionIdle())
                            if(calibration.status!=calibrationBefore){
                                android.util.Log.i("PointerCalibration","state=${calibration.status} active=${calibration.isActive} hasGain=${calibration.hasGain()} hasPosition=${calibration.hasPosition()}")
                                changed()
                            }
                            if(calibration.isActive || !enabled){
                                publish(calibration.status + if(!trusted) " · ${identity.reason}" else "")
                            } else if (trusted && point!=null) {
                                val confirmed=point
                                val step=policy.observe(confirmed.x.toDouble(),confirmed.y.toDouble(),requestTime,now,true,true,ready)
                                if(step!=null)calibration.externalAction()
                                if (step!=null && hid()?.movePointer(step.x,step.y)!=true) policy.rejected()
                                if (step!=null) android.util.Log.i("RayAlignment",
                                    "correction target=${policy.targetRevision} dx=${step.x} dy=${step.y} error=$distance shape=${confirmed.score} copyMs=${copiedAt-requestTime} detectMs=${detectedAt-copiedAt} totalMs=${now-requestTime} state=${policy.status}")
                                publish(policy.status + if(!ready) "：${hid()?.statusText()}" else "")
                            } else {
                                // Pause commands, preserving this target's command/time limits through a gap.
                                publish(if(!fresh) AppText.get(R.string.observation_is_stale_alignment_paused) else identity.reason)
                            }
                            if(now-lastTimingLog>=1000){
                                lastTimingLog=now
                                android.util.Log.i("RayAlignment","tracks="+matches.take(8).joinToString { "${if(it.circular) "circle" else "arrow"}@${it.x},${it.y}:${it.width}x${it.height}" }+" selected=${point?.x},${point?.y}")
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
    fun close(){closed=true;enabled=false;epoch++;main.removeCallbacksAndMessages(null);thread.quitSafely()}
}
