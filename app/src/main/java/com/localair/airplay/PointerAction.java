package com.localair.airplay;

/** A bounded mouse gesture, not a touch digitizer. Transport acknowledgements
 * advance it; cancellation permits only the neutral button-release report. */
public final class PointerAction {
    public static final class Packet {
        public final int buttons, x;
        private final long action;
        private Packet(int buttons,int x,long action){this.buttons=buttons;this.x=x;this.action=action;}
    }
    private enum Phase { IDLE, PRESS, MOVE, RELEASE }
    private Phase phase=Phase.IDLE;
    private int direction, remaining;
    private int distance=24;
    private long action;
    private boolean pressAttempted;
    public boolean active(){return phase!=Phase.IDLE;}
    public boolean releasing(){return phase==Phase.RELEASE;}
    public boolean start(int direction){
        return start(direction,1);
    }
    public boolean start(int direction,double gain){
        if(!Double.isFinite(gain)||gain<.25||gain>2)throw new IllegalArgumentException();
        if(active()||direction < -1||direction > 1)return false;
        distance=(int)Math.round(24*gain);
        this.direction=direction;remaining=direction==0?0:10;
        action++;pressAttempted=false;phase=Phase.PRESS;return true;
    }
    public Packet next(){
        if(phase==Phase.IDLE)return null;
        return new Packet(phase==Phase.RELEASE?0:1,phase==Phase.MOVE?direction*distance:0,action);
    }
    public void attempted(Packet packet){if(packet.action==action&&packet.buttons!=0)pressAttempted=true;}
    public void cancel(){if(active())phase=pressAttempted?Phase.RELEASE:Phase.IDLE;}
    public void acknowledged(Packet packet,boolean success){
        if(packet.action!=action||!active())return;
        if(!success){cancel();return;}
        if(phase==Phase.RELEASE){if(packet.buttons==0)phase=Phase.IDLE;return;}
        if(phase==Phase.PRESS)phase=remaining==0?Phase.RELEASE:Phase.MOVE;
        else if(phase==Phase.MOVE && --remaining==0)phase=Phase.RELEASE;
    }
    /** Physical link closed: discard all action data, never replay on a new link. */
    public void disconnected(){phase=Phase.IDLE;pressAttempted=false;action++;}
}
