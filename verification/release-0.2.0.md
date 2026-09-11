# Quest 投屏与鼠标 0.2.0 验证记录

2026-09-12，Quest 3，包 `com.questlab.airplayreceiver`，versionCode 7。

## 交付结果

已安装单应用“Quest 投屏与鼠标”。同一个前台服务持有 AirPlay 接收器与 BLE HID，投屏页面接收系统 ACTION_SCROLL 并使用已验证的 ScrollDeltaEngine 转换滚轮报告。页面提供连接鼠标、启用/暂停控制、上一条、下一条。

原 `com.questlab.hidlab` 0.1.12/code13 安装和数据保留；迁移时停止了该应用进程，避免两个 GATT 服务并行。只迁移两个当前 schema2 的订阅布尔值，没有修改系统配对、设备名称或 Windows 蓝牙状态。没有自动发送鼠标报告。

## 崩溃证据与修复

- 拉取已安装 0.1.4 APK，与当时本地 app-debug.apk SHA256 完全一致：`982740ad68d483ad44925fa8b54ef4f4c99f8b21f8b359ca0e700526d62c4329`。DEX 中确实已存在 codec 身份检查和 handler 串行 reset；不是旧包装错。
- 03:35:28，PID12051 在 VideoDecoder 输出回调抛出 `releaseOutputBuffer ... currently at Released state`。应用对象身份正确并不能证明平台 codec 仍处于可执行状态；此前 onError 只记录日志，输出失败没有撤销实例。此次统一在 handler 上撤销失败实例、清除其在途缓冲、分别尝试 stop/release、保留当前 SPS/PPS 并等待 IDR 重建。平台进入 Released 的最初触发因素没有完整现场日志，不能进一步断言。
- Surface 采用每次创建的 owner token 和原子请求引用，过期 attach/旧 Activity detach 不会影响新窗口；释放后拒绝新工作。帧显示确认绑定当前 Surface 与实际提交的帧 PTS，旧窗口回调不应隐藏当前等待提示。
- 同次崩溃另有原生 SIGABRT：`raop_ntp_convert_remote_time` 访问已销毁 mutex。源码 conn_destroy 确实先销毁 NTP，再停止仍使用 NTP 的 RTP/mirror 线程。现在先停止并 join 两个媒体线程，再发视频会话结束事件并销毁时钟；自然退出但尚未 join 的线程也必须回收。
- 视频会话结束绑定 NTP 所有者并在生产线程退出后通知。普通控制连接关闭不再重置全局视频解码器。

Android 官方 MediaCodec 文档说明其状态、错误与缓冲所有权约束：[MediaCodec](https://developer.android.com/reference/android/media/MediaCodec)。

## 二合一生命周期

- HidController 由 AirPlayService 持有，不保存 Activity；界面 owner token 负责监听和焦点。
- 蓝牙回调在主线程按 GATT generation 校验后执行；旧服务回调不能修改新连接。
- 失焦、暂停、换界面、断连会清空滚动状态并停用控制。连接成功不会自动启用控制。
- 使用原 0.1.12 的 Report Map、服务顺序与四字节 REPORT 输入格式。后台保持连接，但不产生输入。
- 迁移时系统已有蓝牙链路早于新服务注册，因此界面提示在 iPad 中断开并重连原 Quest 项目一次；不需要忽略配对。

## 已完成的检查

1. Android debug APK、测试 APK 构建与 lint 成功；lint 零错误，仍有原有兼容性/界面样式建议，不等于零警告。
2. 最终 0.2.0 上运行 11 项 Android 真机测试，全部通过：已释放平台 codec 的在途输出回调、旧 codec 回调隔离、Surface 乱序、会话重置、终止后拒绝数据、HID 焦点和界面归属、过期 GATT 回调、未启用时不发送、滚动限幅和背压。
3. 使用生产 C 清理函数体的确定性生命周期测试在 Quest 上通过：运行中/自然退出线程均先 join；时钟销毁最后进行；重复 stop 无副作用。该测试使用模拟线程资源，不能代替真实媒体线程压力测试。
4. 最终进程 PID15757：AirPlay 监听端口 35065，前台服务类型同时包含 mediaPlayback 和 connectedDevice；三个 HID/Battery/Device Information 服务注册成功。
5. 蓝牙数据库复核：Report 151、CCCD 153、Boot 155/156、Battery 159、PnP 162、Model 164、Software 166，与已验证旧版一致。原配对 host 的当前 schema2 订阅恢复为 true。
6. 连续 20 次 RTSP OPTIONS 连接/关闭均获得 200 OK；未触发视频 session reset，最终进程没有新 FATAL EXCEPTION。
7. 拉取最终已安装 APK，哈希与交付构建一致。UI 层级检查已见投屏区和四个按钮；头显显示关闭后截图为空，未据此宣称像素级视觉验收。

## 尚需用户实际验证

最终版本尚未观察到实际 AirPlay 帧或真实 HID 输入报告。因此画面/声音、横竖屏、切出返回，以及指向投屏画面后的实际滚动效果均未在这个合并版本端到端验收。先前分体版本的用户验证不能替代本次验收。电脑侧 mDNS 探测未收到响应，不能据此判断 iPad 是否可发现。

下次使用：打开“Quest 投屏与鼠标”，在 iPad 蓝牙中断开并重连原 Quest 项目一次；屏幕镜像仍选择“Quest AirPlay Lab”，再点“启用控制”，将手柄射线指向投屏画面拨动摇杆。旧 HID 应用作为回退保留，使用合并版时不要同时启动旧版。

原始测试与诊断存于 `work/airplay-crash-evidence`；其中包含设备内部诊断与原偏好备份，不随公开安装包打包。
