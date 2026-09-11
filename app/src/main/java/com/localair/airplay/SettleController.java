package com.localair.airplay;

/** Pure bounded convergence policy. Transport is deliberately outside this class. */
public final class SettleController {
    public static final class Step {
        public final int x,y;
        Step(int x,int y){this.x=x;this.y=y;}
    }
    private double tx,ty,lastError=Double.NaN;
    private long changedAt=-1,started=-1,lastCommand=-1,lastFrame=-1;
    private int steps,stalled,alignedFrames;
    private boolean blocked;
    private boolean responseEvaluated;
    public String status="请移动射线";
    public void reset(){changedAt=started=lastCommand=lastFrame=-1;steps=stalled=alignedFrames=0;blocked=false;responseEvaluated=false;lastError=Double.NaN;status="请移动射线";}
    public void target(double x,double y,long now){
        if(!Double.isFinite(x)||!Double.isFinite(y)){reset();return;}
        if(changedAt<0||Math.hypot(x-tx,y-ty)>3){reset();tx=x;ty=y;changedAt=now;status="等待射线停稳";}
    }
    public Step observe(double x,double y,long frameTime,long now,boolean trusted,boolean allowed,boolean transportReady){
        if(changedAt<0||blocked)return null;
        if(!allowed||!trusted||!Double.isFinite(x)||!Double.isFinite(y)||frameTime>now||now-frameTime>250){block("识别丢失或输入暂停，请重新移动射线");return null;}
        if(frameTime<=lastFrame)return null;
        lastFrame=frameTime;
        if(now-changedAt<400){status="等待射线停稳";return null;}
        if(started<0)started=now;
        if(now-started>10000||steps>=24){block("达到校正上限，请重新移动射线");return null;}
        if(lastCommand>=0&&now-lastCommand<350)return null;
        double error=Math.hypot(tx-x,ty-y);
        if(error<=3){if(++alignedFrames>=2){blocked=true;status="位置已接近（约3像素内）";}return null;}
        alignedFrames=0;
        if(lastCommand>=0&&!responseEvaluated&&Double.isFinite(lastError)) {
            responseEvaluated=true;
            stalled=error>=lastError-.3?stalled+1:0;
            if(stalled>=3){block("未持续接近目标，已停止校正");return null;}
        }
        if(!transportReady)return null;
        int dx=quantize((tx-x)*.35),dy=quantize((ty-y)*.35);
        if(dx==0&&dy==0)return null;
        lastError=error;lastCommand=now;responseEvaluated=false;steps++;status="正在接近目标 "+steps+"/24";
        return new Step(dx,dy);
    }
    private int quantize(double v){return (int)Math.max(-12,Math.min(12,Math.round(v)));}
    public void rejected(){block("传输未接受，已停止校正");}
    private void block(String text){blocked=true;status=text;}
}
