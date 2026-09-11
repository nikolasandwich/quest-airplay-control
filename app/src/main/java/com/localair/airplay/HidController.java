package com.localair.airplay;

import android.Manifest;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.os.*;
import android.util.Log;
import android.view.MotionEvent;
import java.util.*;

/** Service-owned BLE HID. All state and GATT callbacks run on the main looper.
 * GATT layout and report map retain the validated HID 0.1.12 schema. */
@android.annotation.SuppressLint("MissingPermission")
public final class HidController extends ContextWrapper {
    private int generation;
    private boolean focused;
    private boolean needsReconnect;
    private Object uiOwner;
    private Runnable listener;
    private String lastStatus="鼠标未连接";
    public HidController(Context context) {
        super(context);
        BluetoothManager manager=getSystemService(BluetoothManager.class);
        adapter=manager==null?null:manager.getAdapter();
        mappedScroll.configure(false,1,6);
    }
    public void attachUi(Object owner, Runnable changed) {
        if(uiOwner!=owner){disarm();uiOwner=owner;}
        listener=changed;
        changed.run();
    }
    public void detachUi(Object owner) {
        if(uiOwner!=owner)return;
        disarm();focused=false;listener=null;uiOwner=null;
    }
    public void setFocused(Object owner, boolean value) {
        if(uiOwner!=owner)return;
        focused=value;
        if(!value)disarm();
    }
    public void disarm() {
        armed=false;cancelGesture();mappedScroll.stop();horizontalGate.reset();cancelPointerAction();changed();
    }
    public void toggleArmed() {
        if(armed){disarm();return;}
        if(releaseUnconfirmed||needsReconnect||!focused||!serviceReady||host==null||!subscribed||suspended||protocolMode!=1||host.getBondState()!=BluetoothDevice.BOND_BONDED) {
            note("请先连接已配对的 iPad，并保持投屏页在前台");return;
        }
        armed=true;note("鼠标控制已启用：指向投屏画面，拨动摇杆滚动");
    }
    public boolean isArmed(){return armed;}
    public boolean canMovePointer(){
        return armed&&focused&&!needsReconnect&&!releaseUnconfirmed&&serviceReady&&server!=null&&host!=null&&subscribed&&!suspended&&protocolMode==1&&!notificationPending&&gestureRemaining==0&&!pointerAction.active()&&host.getBondState()==BluetoothDevice.BOND_BONDED;
    }
    /** Only a foreground video ray event may request bounded relative movement. */
    public boolean movePointer(int x,int y){
        if(!canMovePointer()||Math.abs((long)x)>32||Math.abs((long)y)>32)return false;
        if(x==0&&y==0)return true;
        return transmit(0,x,y,0,null);
    }
    public boolean isStarted(){return server!=null;}
    public String statusText(){
        if(releaseUnconfirmed)return "鼠标松开未确认 · 请重连原 Quest 蓝牙设备";
        if(needsReconnect)return "请在 iPad 蓝牙中断开并重连原 Quest 设备一次";
        if(armed)return "上下滚动 · 左右拖拽 · 点击作用于 iPad 当前指针";
        if(host!=null&&subscribed)return "鼠标已连接 · 点击启用控制";
        if(host!=null)return "蓝牙已连接 · 等待鼠标订阅";
        if(advertising)return "请在 iPad 蓝牙中连接原 Quest 设备";
        return lastStatus;
    }
    private void changed(){if(listener!=null)listener.run();}
    private synchronized void note(String text) {
        Log.i("QuestHidLab",text);
        lastStatus=text;
        try {
            java.io.File log=new java.io.File(getFilesDir(),"hid-diagnostic.log");
            if(log.length()>262144){java.io.File old=new java.io.File(getFilesDir(),"hid-diagnostic.previous.log");java.nio.file.Files.move(log.toPath(),old.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}
            try(java.io.FileWriter writer=new java.io.FileWriter(log,true)){writer.write(System.currentTimeMillis()+" "+text+"\n");}
        }catch(java.io.IOException e){Log.w("QuestHidLab","Diagnostic file unavailable");}
        handler.post(this::changed);
    }
    public boolean permitted() {
        return Build.VERSION.SDK_INT<31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);
    }
    public boolean onMotion(MotionEvent event) {
        if(event.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT){mappedScroll.stop();horizontalGate.reset();cancelPointerAction();}
        if(event.getActionMasked()!=MotionEvent.ACTION_SCROLL)return false;
        float h=event.getAxisValue(MotionEvent.AXIS_HSCROLL),v=event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if(event.getEventTime()-lastAxisLog>250 || (Math.abs(h)>.008 && !sawHorizontal)){
            note(String.format(Locale.ROOT,"SCROLL source=%x H=%.4f V=%.4f armed=%s",event.getSource(),h,v,armed));lastAxisLog=event.getEventTime();
        }
        if(Math.abs(h)>.008)sawHorizontal=true;
        boolean ready=armed&&focused&&serviceReady&&host!=null&&subscribed&&!suspended&&protocolMode==1&&gestureRemaining==0&&!releaseUnconfirmed;
        int direction=horizontalGate.event(h,v,event.getEventTime(),ready);
        if(direction!=0&&!pointerAction.active())startPointerAction(direction);
        if(pointerAction.active() || Math.abs(h)>Math.abs(v)*1.3 && Math.abs(h)>.02){mappedScroll.stop();return armed;}
        int wheel=mappedScroll.event(v,event.getEventTime(),ready,!notificationPending);
        if(wheel!=0)send(0,0,0,wheel);
        return armed;
    }
    private static UUID uuid(int id) { return UUID.fromString(String.format("0000%04x-0000-1000-8000-00805f9b34fb", id)); }
    private static final byte[] MAP = new byte[] {
        0x05,0x01,0x09,0x02,(byte)0xa1,0x01,(byte)0x85,0x01,0x09,0x01,(byte)0xa1,0x00,
        0x05,0x09,0x19,0x01,0x29,0x03,0x15,0x00,0x25,0x01,(byte)0x95,0x03,0x75,0x01,(byte)0x81,0x02,
        (byte)0x95,0x01,0x75,0x05,(byte)0x81,0x03,0x05,0x01,0x09,0x30,0x09,0x31,0x09,0x38,
        0x15,(byte)0x81,0x25,0x7f,0x75,0x08,(byte)0x95,0x03,(byte)0x81,0x06,(byte)0xc0,(byte)0xc0
    };
    private final Handler handler = new Handler(Looper.getMainLooper());
    private BluetoothAdapter adapter;
    private BluetoothGattServer server;
    private BluetoothGattCharacteristic report, bootReport;
    private final ArrayDeque<BluetoothGattService> pendingServices = new ArrayDeque<>();
    private BluetoothDevice host;
    private BluetoothDevice lastPeer;
    private BluetoothDevice pendingReconnect;
    private final Runnable reconnectTimeout=() -> {
        if(pendingReconnect!=null&&server!=null){
            note("Known-peer reconnect timed out; cancelling this one attempt");
            server.cancelConnection(pendingReconnect);pendingReconnect=null;
        }
    };
    private volatile boolean subscribed, bootSubscribed, advertising, armed, suspended, serviceReady;
    private volatile int protocolMode = 1;
    private final ScrollDeltaEngine mappedScroll=new ScrollDeltaEngine();
    private final HorizontalGestureGate horizontalGate=new HorizontalGestureGate();
    private long lastAxisLog;
    private boolean sawHorizontal;
    private final PointerAction pointerAction=new PointerAction();
    private BluetoothDevice pointerHost;
    private int pointerEpoch, releaseAttempts;
    private boolean releaseUnconfirmed;
    private static final class PendingReport {
        final PointerAction.Packet pointer;
        PendingReport(PointerAction.Packet pointer){this.pointer=pointer;}
    }
    private final ArrayDeque<PendingReport> pendingReports=new ArrayDeque<>();
    /** Called only by a deliberate UI click or recognized horizontal input. */
    public void clickPointer(){startPointerAction(0);}
    public void dragPointer(int direction){if(direction==1||direction==-1)startPointerAction(direction);}
    private void startPointerAction(int direction){
        if(!armed||!focused||needsReconnect||releaseUnconfirmed||!serviceReady||server==null||host==null||!subscribed||suspended||protocolMode!=1||notificationPending||gestureRemaining!=0||host.getBondState()!=BluetoothDevice.BOND_BONDED)return;
        if(!pointerAction.start(direction))return;
        mappedScroll.stop();pointerHost=host;pointerEpoch=generation;releaseAttempts=0;
        note(direction==0?"User action: left click at current iPad pointer":"User action: mouse drag direction="+direction+"; not a touch digitizer");
        handler.postDelayed(pointerTimeout,650);
        pointerPump.run();
    }
    private boolean pointerLinkValid(){return pointerEpoch==generation&&pointerHost!=null&&pointerHost.equals(host)&&server!=null&&serviceReady&&subscribed&&permitted();}
    private void cancelPointerAction(){
        if(releaseUnconfirmed)return;
        pointerAction.cancel();handler.removeCallbacks(pointerPump);
        if(pointerAction.active()){handler.removeCallbacks(pointerTimeout);handler.postDelayed(pointerTimeout,200);pointerPump.run();}
    }
    private final Runnable pointerPump=new Runnable(){public void run(){
        if(!pointerAction.active()||releaseUnconfirmed)return;
        if(!pointerLinkValid()){pointerAction.disconnected();pointerHost=null;return;}
        if(!armed||!focused||suspended)pointerAction.cancel();
        if(notificationPending)return;
        PointerAction.Packet packet=pointerAction.next();if(packet==null)return;
        if(packet.buttons==0&&releaseAttempts>=3){failPointerRelease();return;}
        pointerAction.attempted(packet);
        if(!transmit(packet.buttons,packet.x,0,0,packet)){pointerAction.cancel();handler.removeCallbacks(pointerTimeout);handler.postDelayed(pointerTimeout,100);}
    }};
    private final Runnable pointerTimeout=new Runnable(){public void run(){
        if(!pointerAction.active()||releaseUnconfirmed)return;
        pointerAction.cancel();
        if(!pointerLinkValid()){pointerAction.disconnected();pointerHost=null;return;}
        if(releaseAttempts>=3){failPointerRelease();return;}
        // A neutral report may bypass backpressure only to release a held button.
        // Keep FIFO metadata so a late press ACK cannot be mistaken for release.
        PointerAction.Packet neutral=pointerAction.next();
        if(neutral!=null)transmit(0,0,0,0,neutral);
        handler.postDelayed(this,200);
    }};
    private void resetPointerLink(){
        handler.removeCallbacks(pointerPump);handler.removeCallbacks(pointerTimeout);
        pointerAction.disconnected();pointerHost=null;pendingReports.clear();notificationPending=false;releaseUnconfirmed=false;
        values.put(uuid(0x2a4d),new byte[4]);values.put(uuid(0x2a33),new byte[3]);horizontalGate.reset();
    }
    private void failPointerRelease(){
        releaseUnconfirmed=true;armed=false;
        handler.removeCallbacks(pointerPump);handler.removeCallbacks(pointerTimeout);
        note("Pointer release unconfirmed: controls stopped; reconnect required");
        try{server.cancelConnection(pointerHost);}catch(RuntimeException e){Log.w("QuestHidLab","Cannot close failed pointer link",e);}
    }
    private volatile boolean notificationPending;
    private BluetoothDevice gestureHost;
    private int gestureRemaining;
    private int gestureSteps;
    private int gestureDirection;
    private final Runnable gestureStep=new Runnable(){
        @Override public void run(){
            if(gestureRemaining<=0)return;
            if(!focused||!serviceReady||!armed||!subscribed||suspended||protocolMode!=1||host==null||!host.equals(gestureHost)||host.getBondState()!=BluetoothDevice.BOND_BONDED){cancelGesture();return;}
            gestureRemaining--;
            send(0,0,0,gestureDirection*10);
            if(gestureHost==null)return;
            if(gestureRemaining>0)handler.postDelayed(this,60);
            else {gestureHost=null;note("Short scroll gesture complete: "+gestureSteps+" reports requested; app effect requires observation");}
        }
    };
    private void cancelGesture(){
        handler.removeCallbacks(gestureStep);gestureRemaining=0;gestureHost=null;
    }
    public void scrollPage(int direction){
        if(gestureRemaining>0||pointerAction.active()||releaseUnconfirmed)return;
        int steps=6;
        if(!focused || (direction!=1 && direction!=-1))return;
        gestureDirection=direction;
        if(!focused||!serviceReady||!armed||!subscribed||suspended||protocolMode!=1||host==null||host.getBondState()!=BluetoothDevice.BOND_BONDED){note("Gesture unavailable: enable controls on connected REPORT host");return;}
        gestureHost=host;gestureRemaining=steps;gestureSteps=steps;
        note("Short scroll gesture started: "+steps+" x wheel "+(gestureDirection*10)+" at 60ms; no automatic repeat");
        handler.post(gestureStep);
    }

    private int sent, completed;
    private final Map<UUID,byte[]> values = new HashMap<>();
    private BluetoothGattCharacteristic characteristic(int id,int properties,int permissions,byte[] value) {
        BluetoothGattCharacteristic c=new BluetoothGattCharacteristic(uuid(id),properties,permissions);
        c.setValue(value); values.put(uuid(id),value); return c;
    }
    private byte[] batteryLevel() {
        android.content.Intent battery=registerReceiver(null,new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED));
        int level=battery==null?-1:battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
        int scale=battery==null?0:battery.getIntExtra(BatteryManager.EXTRA_SCALE,0);
        if(level<0||scale<=0)return null;
        return new byte[]{(byte)Math.max(0,Math.min(100,Math.round(100f*level/scale)))};
    }
    private void addNextService() {
        if(server==null)return;
        BluetoothGattService next=pendingServices.poll();
        if(next!=null){
            boolean accepted=server.addService(next);
            note("add service="+next.getUuid().toString().substring(4,8)+" accepted="+accepted);
            if(!accepted)stop();
            return;
        }
        serviceReady=true;
        note("HID, Battery and Device Information services ready");
        if(host!=null){
            needsReconnect=true;
            disarm();
            note("Link predates service registration: reconnecting HID once; pairing retained");
            server.cancelConnection(host);
            return;
        }
        restartAdvertising();

    }
    public void start() {
        if(!permitted()){note("Bluetooth permission required");return;}
        if(server!=null){note("Already started");return;}
        if(adapter==null||!adapter.isEnabled()||adapter.getBluetoothLeAdvertiser()==null){note("BLE peripheral unavailable");return;}
        try {
            server=getSystemService(BluetoothManager.class).openGattServer(this,createCallback(++generation));
            if(server==null){note("openGattServer returned null");return;}
            BluetoothGattService service=new BluetoothGattService(uuid(0x1812),BluetoothGattService.SERVICE_TYPE_PRIMARY);
            service.addCharacteristic(characteristic(0x2a4a,2,1,new byte[]{0x11,0x01,0x00,0x02}));
            service.addCharacteristic(characteristic(0x2a4b,2,1,MAP));
            service.addCharacteristic(characteristic(0x2a4c,4,BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,new byte[]{0}));
            service.addCharacteristic(characteristic(0x2a4e,2|4,BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED|BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,new byte[]{1}));
            report=characteristic(0x2a4d,2|16,BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,new byte[4]);
            BluetoothGattDescriptor ref=new BluetoothGattDescriptor(uuid(0x2908),BluetoothGattDescriptor.PERMISSION_READ); ref.setValue(new byte[]{1,1}); report.addDescriptor(ref);
            BluetoothGattDescriptor cccd=new BluetoothGattDescriptor(uuid(0x2902),BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED|BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED); cccd.setValue(new byte[]{0,0}); report.addDescriptor(cccd);
            service.addCharacteristic(report);
            // Append to preserve the existing Report characteristic ordering.
            bootReport=characteristic(0x2a33,2|16,BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,new byte[3]);
            BluetoothGattDescriptor bootCccd=new BluetoothGattDescriptor(uuid(0x2902),BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED|BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED);
            bootCccd.setValue(new byte[]{0,0});bootReport.addDescriptor(bootCccd);
            service.addCharacteristic(bootReport);
            // HOGP requires BAS and DIS/PnP ID even when the Report Map only
            // describes mouse input. Register serially; advertise only when complete.
            BluetoothGattService battery=new BluetoothGattService(uuid(0x180f),BluetoothGattService.SERVICE_TYPE_PRIMARY);
            byte[] initialBattery=batteryLevel();
            if(initialBattery==null)throw new IllegalStateException("Battery level unavailable");
            battery.addCharacteristic(characteristic(0x2a19,2,BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,initialBattery));
            BluetoothGattService device=new BluetoothGattService(uuid(0x180a),BluetoothGattService.SERVICE_TYPE_PRIMARY);
            // Bluetooth SIG source, reserved internal/interoperability test vendor
            // 0xffff, local product 1, version 0x0018. Not a shipping vendor ID.
            device.addCharacteristic(characteristic(0x2a50,2,1,new byte[]{1,(byte)0xff,(byte)0xff,1,0,0x18,0}));
            device.addCharacteristic(characteristic(0x2a24,2,1,"Quest HID Lab prototype".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            device.addCharacteristic(characteristic(0x2a28,2,1,"0.2.8-align-preview".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
            pendingServices.clear();
            pendingServices.add(service);pendingServices.add(battery);pendingServices.add(device);
            addNextService();
        }catch(Exception e){note("start failed: "+e.getClass().getSimpleName());stop();}
    }
    private void restartAdvertising() {
        if(!permitted()||!serviceReady||server==null||adapter==null||!adapter.isEnabled())return;
        if(host!=null){note("Connected: discovery restart skipped");return;}
        BluetoothLeAdvertiser advertiser=adapter.getBluetoothLeAdvertiser();
        if(advertiser==null){note("Advertiser unavailable");return;}
        advertiser.stopAdvertising(advertiseCallback);
        advertising=false;
        handler.removeCallbacks(resumeAdvertising);
        handler.postDelayed(resumeAdvertising,300);
        note("Restoring BLE discovery");
    }
    private final Runnable resumeAdvertising=() -> {
        if(server==null||!serviceReady||host!=null||!permitted()||adapter==null||!adapter.isEnabled())return;
        BluetoothLeAdvertiser advertiser=adapter.getBluetoothLeAdvertiser();
        if(advertiser==null)return;
        AdvertiseData data=new AdvertiseData.Builder().addServiceUuid(new ParcelUuid(uuid(0x1812))).build();
        AdvertiseData scan=new AdvertiseData.Builder().setIncludeDeviceName(true).build();
        advertiser.startAdvertising(new AdvertiseSettings.Builder().setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY).setConnectable(true).setTimeout(0).build(),data,scan,this.advertiseCallback);
    };
    private final AdvertiseCallback advertiseCallback=new AdvertiseCallback(){
        @Override public void onStartSuccess(AdvertiseSettings settings){handler.post(()->{if(server==null)return;advertising=true;note("BLE advertisement ACTIVE; iPad Bluetooth should discover the Quest system name. No input is sent until armed.");});}
        @Override public void onStartFailure(int code){handler.post(()->{if(server==null)return;note("BLE advertising failed code="+code);});}
    };
    private BluetoothGattServerCallback createCallback(final int epoch) { return new BluetoothGattServerCallback(){
        @Override public void onServiceAdded(int code,BluetoothGattService service){ handler.post(() -> { if(epoch!=generation || server==null)return;
            note("Service="+service.getUuid().toString().substring(4,8)+" status="+code);
            if(code!=0)stop();else addNextService();

        }); }
        @Override public void onConnectionStateChange(BluetoothDevice d,int code,int state){ handler.post(() -> { if(epoch!=generation || server==null)return;
            note("GATT connection status="+code+" state="+state+" bond="+d.getBondState()+" transportType="+d.getType());
            if(state==BluetoothProfile.STATE_CONNECTED){
                if(host!=null&&host.equals(d)){note("Duplicate connected callback ignored");return;}
                if(host!=null&&!host.equals(d)){server.cancelConnection(d);note("Additional host rejected: one diagnostic host only");return;}
                resetPointerLink();host=d;lastPeer=d;armed=false;suspended=false;protocolMode=1;notificationPending=false;needsReconnect=false;
                cancelGesture();mappedScroll.stop();
                if(d.equals(pendingReconnect)){handler.removeCallbacks(reconnectTimeout);pendingReconnect=null;}
                values.put(uuid(0x2a4e),new byte[]{1});
                // The database gained Boot Mouse. Old-schema subscriptions cannot prove
                // the client has discovered the current report handles.
                subscribed=d.getBondState()==BluetoothDevice.BOND_BONDED&&getSharedPreferences("hid",MODE_PRIVATE).getBoolean("schema2_cccd_"+d.getAddress(),false);
                bootSubscribed=d.getBondState()==BluetoothDevice.BOND_BONDED&&getSharedPreferences("hid",MODE_PRIVATE).getBoolean("schema2_boot_cccd_"+d.getAddress(),false);
                note("Bonded CCCD restore="+subscribed+" boot="+bootSubscribed+" protocol=REPORT");
            }
            else if(state==BluetoothProfile.STATE_DISCONNECTED){
                if(host!=null&&!host.equals(d))return;
                cancelGesture();mappedScroll.stop();resetPointerLink();
                host=null;subscribed=false;bootSubscribed=false;armed=false;suspended=false;
                // Re-advertising an unbonded link can create a second pairing
                // attempt while the first system dialog is still being dismissed.
                if(d.getBondState()==BluetoothDevice.BOND_BONDED)
                    handler.postDelayed(() -> { if(epoch==generation&&host==null&&server!=null)restartAdvertising(); }, 1000);
                else {
                    handler.removeCallbacks(resumeAdvertising);
                    if(adapter.getBluetoothLeAdvertiser()!=null)adapter.getBluetoothLeAdvertiser().stopAdvertising(advertiseCallback);
                    advertising=false;
                    note("Unbonded disconnect: discovery paused, press 2 only for a deliberate new attempt");
                }
            }

        }); }
        private void respond(BluetoothDevice d,int id,int offset,byte[] data){
            if(server==null)return;
            if(offset<0||offset>data.length){server.sendResponse(d,id,BluetoothGatt.GATT_INVALID_OFFSET,offset,null);return;}
            server.sendResponse(d,id,BluetoothGatt.GATT_SUCCESS,offset,Arrays.copyOfRange(data,offset,data.length));
        }
        @Override public void onCharacteristicReadRequest(BluetoothDevice d,int id,int offset,BluetoothGattCharacteristic c){ handler.post(() -> { if(epoch!=generation || server==null)return;
            note("GATT read characteristic="+c.getUuid().toString().substring(4,8)+" offset="+offset);
            byte[] value=c.getUuid().equals(uuid(0x2a19))?batteryLevel():values.getOrDefault(c.getUuid(),new byte[0]);
            if(value==null){if(server!=null)server.sendResponse(d,id,BluetoothGatt.GATT_FAILURE,offset,null);return;}
            respond(d,id,offset,value);

        }); }
        @Override public void onDescriptorReadRequest(BluetoothDevice d,int id,int offset,BluetoothGattDescriptor desc){ handler.post(() -> { if(epoch!=generation || server==null)return;
            boolean boot=desc.getCharacteristic().getUuid().equals(uuid(0x2a33));
            note("GATT descriptor read="+desc.getUuid().toString().substring(4,8)+" characteristic="+desc.getCharacteristic().getUuid().toString().substring(4,8)+" offset="+offset);
            respond(d,id,offset,desc.getUuid().equals(uuid(0x2902))?new byte[]{(byte)((boot?bootSubscribed:subscribed)?1:0),0}:new byte[]{1,1});

        }); }
        @Override public void onDescriptorWriteRequest(BluetoothDevice d,int id,BluetoothGattDescriptor desc,boolean prepared,boolean response,int offset,byte[] data){ handler.post(() -> { if(epoch!=generation || server==null)return;
            int code=BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
            if(d.equals(host)&&desc.getUuid().equals(uuid(0x2902))&&!prepared&&offset==0&&data.length==2&&(data[0]==0||data[0]==1)&&data[1]==0){
                boolean boot=desc.getCharacteristic().getUuid().equals(uuid(0x2a33));
                boolean enabled=data[0]==1;if(!enabled)disarm();if(boot)bootSubscribed=enabled;else subscribed=enabled;
                getSharedPreferences("hid",MODE_PRIVATE).edit().putBoolean((boot?"schema2_boot_cccd_":"schema2_cccd_")+d.getAddress(),enabled).apply();code=0;
                note("Mouse input subscribed="+enabled+" mode="+(boot?"BOOT":"REPORT")+" bonded="+(d.getBondState()==BluetoothDevice.BOND_BONDED));
            }
            if(response&&server!=null)server.sendResponse(d,id,code,offset,null);

        }); }
        @Override public void onCharacteristicWriteRequest(BluetoothDevice d,int id,BluetoothGattCharacteristic c,boolean prepared,boolean response,int offset,byte[] data){ handler.post(() -> { if(epoch!=generation || server==null)return;
            note("GATT control write characteristic="+c.getUuid().toString().substring(4,8)+" length="+data.length);
            int code=BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED;
            if(d.equals(host)&&!prepared&&offset==0&&data.length==1&&(data[0]==0||data[0]==1)&&(c.getUuid().equals(uuid(0x2a4c))||c.getUuid().equals(uuid(0x2a4e)))){
                disarm();
                if(c.getUuid().equals(uuid(0x2a4e))){protocolMode=data[0];note("Protocol mode="+(protocolMode==0?"BOOT":"REPORT"));}
                else {suspended=data[0]==0;note("Host suspend="+suspended);}
                values.put(c.getUuid(),data.clone());code=0;
            }
            if(response&&server!=null)server.sendResponse(d,id,code,offset,null);

        }); }
        @Override public void onNotificationSent(BluetoothDevice d,int code){ handler.post(() -> { if(epoch!=generation || server==null || !d.equals(host))return;
            PendingReport completedReport=pendingReports.poll();notificationPending=!pendingReports.isEmpty();
            if(completedReport==null)return;
            completed++;note("notification complete="+completed+" status="+code);
            if(completedReport.pointer!=null){
                pointerAction.acknowledged(completedReport.pointer,code==0);
                if(!pointerAction.active()){handler.removeCallbacks(pointerTimeout);pointerHost=null;releaseUnconfirmed=false;changed();}
                else {handler.removeCallbacks(pointerPump);handler.postDelayed(pointerPump,30);}
            }
            if(code!=0){cancelGesture();mappedScroll.stop();cancelPointerAction();}
        }); }
    }; }
    private void send(int buttons,int x,int y,int wheel){
        BluetoothDevice peer=host;BluetoothGattServer gatt=server;boolean boot=protocolMode==0;
        if(pointerAction.active()||releaseUnconfirmed||!focused||!serviceReady||!armed||notificationPending||!(boot?bootSubscribed:subscribed)||peer==null||gatt==null||suspended||peer.getBondState()!=BluetoothDevice.BOND_BONDED){note("No report: require ready service, bonded host, current-schema subscription, not suspended, and armed controls");return;}
        if(boot&&wheel!=0){note("No wheel: host selected BOOT mouse mode (buttons and X/Y only)");return;}
        transmit(buttons,x,y,wheel,null);
    }
    private boolean transmit(int buttons,int x,int y,int wheel,PointerAction.Packet pointer){
        BluetoothDevice peer=host;BluetoothGattServer gatt=server;boolean boot=protocolMode==0;
        if(peer==null||gatt==null||!permitted())return false;
        if(pointer!=null && buttons==0)releaseAttempts++;
        byte[] data=boot?new byte[]{(byte)buttons,(byte)x,(byte)y}:new byte[]{(byte)buttons,(byte)x,(byte)y,(byte)wheel};
        BluetoothGattCharacteristic input=boot?bootReport:report;
        // Characteristic reads must return current button state, without replaying relative motion.
        values.put(uuid(0x2a4d),new byte[]{(byte)buttons,0,0,0});
        values.put(uuid(0x2a33),new byte[]{(byte)buttons,0,0});
        PendingReport queued=new PendingReport(pointer);pendingReports.add(queued);notificationPending=true;
        boolean accepted=false;
        try {
            if(Build.VERSION.SDK_INT>=33){int code=gatt.notifyCharacteristicChanged(peer,input,false,data);accepted=code==0;note("mode="+(boot?"BOOT":"REPORT")+" bytes="+data.length+" buttons="+buttons+" x="+x+" y="+y+" wheel="+wheel+" send="+(++sent)+" status="+code);}
            else {input.setValue(data);accepted=gatt.notifyCharacteristicChanged(peer,input,false);note("send="+(++sent)+" accepted="+accepted);}
        }catch(RuntimeException e){Log.w("QuestHidLab","Report submission failed",e);}
        if(!accepted){pendingReports.remove(queued);notificationPending=!pendingReports.isEmpty();cancelGesture();mappedScroll.stop();}
        return accepted;
    }
    public void stop(){
        cancelPointerAction();
        if(pointerAction.active()&&pointerLinkValid()){PointerAction.Packet neutral=pointerAction.next();if(neutral!=null)transmit(0,0,0,0,neutral);}
        resetPointerLink();
        generation++;
        cancelGesture();
        mappedScroll.stop();notificationPending=false;
        handler.removeCallbacks(reconnectTimeout);
        if(pendingReconnect!=null&&server!=null)server.cancelConnection(pendingReconnect);
        pendingReconnect=null;
        pendingServices.clear();
        handler.removeCallbacks(resumeAdvertising);
        armed=false;subscribed=false;bootSubscribed=false;suspended=false;serviceReady=false;needsReconnect=false;
        if(adapter!=null&&permitted()&&adapter.getBluetoothLeAdvertiser()!=null)adapter.getBluetoothLeAdvertiser().stopAdvertising(advertiseCallback);
        if(server!=null){server.close();server=null;}host=null;advertising=false;note("BLE experiment stopped");
    }
}
