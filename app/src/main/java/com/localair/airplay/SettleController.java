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
    private double commandX,commandY,gain=1;
    private int commandDx,commandDy;
    public String status="请移动射线";
    public int targetRevision;
    public double targetX(){return tx;}
    public double targetY(){return ty;}
    public void reset(){changedAt=started=lastCommand=lastFrame=-1;steps=stalled=alignedFrames=0;blocked=false;responseEvaluated=false;lastError=Double.NaN;gain=1;status="请移动射线";}
    public void target(double x,double y,long now){
        if(!Double.isFinite(x)||!Double.isFinite(y)){reset();return;}
        if(changedAt<0||Math.hypot(x-tx,y-ty)>3){reset();targetRevision++;tx=x;ty=y;changedAt=now;status="等待射线停稳";}
    }
    public Step observe(double x,double y,long frameTime,long now,boolean trusted,boolean allowed,boolean transportReady){
        if(changedAt<0||blocked)return null;
        if(!allowed||!trusted||!Double.isFinite(x)||!Double.isFinite(y)||frameTime>now||now-frameTime>250){block("识别丢失或输入暂停，请重新移动射线");return null;}
        if(frameTime<=lastFrame)return null;
        lastFrame=frameTime;
        if(now-changedAt<100){status="等待射线停稳";return null;}
        if(started<0)started=now;
        if(now-started>10000||steps>=24){block("达到校正上限，请重新移动射线");return null;}
        // PixelCopy timestamps are local observations, not remote presentation times.
        // Wait for visible displacement before issuing another command; a fresh copy alone
        // does not establish that AirPlay has carried the previous command back.
        if(lastCommand>=0){
            if(now-lastCommand<100){status="等待上一动作反馈";return null;}
            if(Math.hypot(x-commandX,y-commandY)<2 && now-lastCommand<450){status="等待画面中的指针移动";return null;}
        }
        double error=Math.hypot(tx-x,ty-y);
        if(error<=3){status="正在确认接近位置";if(++alignedFrames>=2){blocked=true;status="已到容差范围（3个采样像素内）";}return null;}
        alignedFrames=0;
        if(lastCommand>=0&&!responseEvaluated&&Double.isFinite(lastError)) {
            responseEvaluated=true;
            stalled=error>=lastError-.3?stalled+1:0;
            if(stalled>=3){block("未持续接近目标，已停止校正");return null;}
            double observedGain=((x-commandX)*commandDx+(y-commandY)*commandDy)/
                    (commandDx*commandDx+commandDy*commandDy);
            if(observedGain>=.25&&observedGain<=4)gain=.5*gain+.5*observedGain;
        }
        if(!transportReady){status="等待蓝牙输入就绪";return null;}
        int cap=error>80?24:error>20?12:4;
        int dx=quantize((tx-x)*.35/gain,cap),dy=quantize((ty-y)*.35/gain,cap);
        if(dx==0&&dy==0){
            if(Math.abs(tx-x)>=Math.abs(ty-y))dx=tx>x?1:-1;
            else dy=ty>y?1:-1;
        }
        commandX=x;commandY=y;commandDx=dx;commandDy=dy;lastError=error;lastCommand=now;responseEvaluated=false;steps++;status="正在接近目标 "+steps+"/24";
        return new Step(dx,dy);
    }
    private int quantize(double v,int cap){return (int)Math.max(-cap,Math.min(cap,Math.round(v)));}
    public void rejected(){block("传输未接受，已停止校正");}
    private void block(String text){blocked=true;status=text;}
}
