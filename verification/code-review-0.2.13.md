# 0.2.13 深入代码审查

审查日期：2026-09-12。基准为已安装的`75180f4`，版本0.2.13/code20。随后`32ec085`只增加复盘文档，应用代码与基准无差异。未部署的自然学习改动保留在独立stash，不属于本次审查。

结论：存在需要先修的生命周期、输入恢复和校准状态机问题。以下P1表示应优先修复，P2表示正常迭代中应修复；不是声称每项都已在用户设备触发。本次没有安装、重启、重连或发送设备输入。

## 优先问题

### F1 · P1 · 持续识别不到时，校准不会按时退出【已离线复现】

位置：[PointerCalibration.java:55](../app/src/main/java/com/localair/airplay/PointerCalibration.java:55)，另见[RayAlignment.kt:82](../app/src/main/java/com/localair/airplay/RayAlignment.kt:82)。

不可信观察先return，180秒超时检查在后面；上层焦点/视频/射线门禁失败时甚至不调用observe。结果是向导可以无限占用校准模式，滚动和点击等继续受限。直接编译当前生产类，连续传入200秒不可信观察后`isActive=true`；下一次可信观察才触发超时。最小修复：独立的状态机时钟检查，位于所有观察门禁之前，取消路径统一恢复直接控制。回归必须覆盖无帧、无候选、无目标、失焦和无observe调用，不能只测试理想完成流程。

### F2 · P1 · 进入画中画会停掉可见视频输出【代码与平台生命周期确认】

位置：[MainActivity.kt:323](../app/src/main/java/com/localair/airplay/MainActivity.kt:323)，[VideoDecoder.kt:113](../app/src/main/java/com/localair/airplay/VideoDecoder.kt:113)。

Activity在有视频时主动进入PiP，但onPause无条件park：清空target、hasFrames=false并把解码输出转到隐藏Surface。Android进入PiP也会调用onPause，可见小窗因此冻结/失去输出；直到onResume重绑才有恢复路径。[Android生命周期说明](https://developer.android.com/develop/ui/views/picture-in-picture#continuing_video_playback_while_in_pip)

同时PiP回调直接隐藏waiting，但随后帧广播的refreshVideoState不区分PiP，会重新按false显示它；PiP返回又用全局hasFrames而非当前owner。最小修复：保留PiP可见Surface的输出，只撤销输入；所有等待层决策集中到含当前owner/可见模式的函数。需验证PiP持续输出与普通摘戴park两条不同路径。**这不是当前白字投诉的已确认根因**：当前现场快照为stopped且非PiP。

### F3 · P1 · 接收器自动恢复后音频线程永久失效【确定调用链】

位置：[AirPlayService.kt:107](../app/src/main/java/com/localair/airplay/AirPlayService.kt:107)，[AudioDecoder.kt:77](../app/src/main/java/com/localair/airplay/AudioDecoder.kt:77)。

健康检查在RAOP退出后调用audio.release，再startReceiver把同一个`val audio`重新设为音频sink。release已经quitSafely，后续onAacFrame继续入队，但handler不能再处理；会无声并持续积累未消费音频。初始化失败也只在第一帧尝试，没有有界恢复。最小修复：区分可重用的session reset与最终close，或在恢复时创建新音频对象；关闭后拒收、队列有界。验收注入一次接收器退出后重新进音频，确认实际消费且队列不增长。[HandlerThread终止后的行为](https://developer.android.com/reference/android/os/HandlerThread#quitSafely())

### F4 · P1 · 普通鼠标报告缺少确认超时，可能永久堵住全部输入【条件性代码缺陷】

位置：[HidController.java:429](../app/src/main/java/com/localair/airplay/HidController.java:429)，[HidController.java:399](../app/src/main/java/com/localair/airplay/HidController.java:399)。

普通移动/滚动发送成功入队后只能等onNotificationSent清除pending；只有点拖状态机有超时。若回调未返回而连接也未报告断开，canMovePointer、点拖入口及滚动均被pending挡住，关闭再启用控制也不清除此状态。不是声称用户这轮已发生丢ACK；目前日志显示过正常ACK。最小修复：所有报告都有期限，超期转明确传输故障、使采样失败、停止新输入并执行有界链路恢复；不可简单清空后把迟到回调配给新报告。测试要覆盖接受发送但无回调、迟到回调、同设备重连与取消。

### F5 · P1 · 音频释放与输出回调缺少同一线程所有权【可导致崩溃的竞态路径】

位置：[AudioDecoder.kt:71](../app/src/main/java/com/localair/airplay/AudioDecoder.kt:71)，[AudioDecoder.kt:121](../app/src/main/java/com/localair/airplay/AudioDecoder.kt:121)。

release在调用线程释放AudioTrack，codec停止在另一个handler排队；输出回调没有`c===codec`/closed检查，对getOutputBuffer、write和releaseOutputBuffer也无状态异常隔离。关闭与旧回调交错时可能访问已释放资源并终止进程。视频已经有代际所有权防护，音频没有。最小修复：在音频线程先撤销所有权，再各自可靠stop/release，所有回调校验当前代际，关闭后不再收帧。需故障注入旧回调/释放交错；本次未在头显触发此竞态，不能算新增实测crash。

### F6 · P1 · 引导目标会在用户动作中改变，且与过关条件脱节【代码确认，用户体验失败】

位置：[calibration-board.html:51](../app/src/main/assets/calibration-board.html:51)，[calibration-board.html:58](../app/src/main/assets/calibration-board.html:58)，[PointerCalibration.java:75](../app/src/main/java/com/localair/airplay/PointerCalibration.java:75)。

网页首次pointermove会重新放置已显示目标；原生满足方向/位移条件就自动step++，无需命中圆环，页面立刻换方向。原生失锁清基线，网页保留旧目标时，两边起点也可不同。另一个会话细节是server session仅在服务启动生成；若两次校准开始时stage同为0且浏览器没看到中间inactive，key相同可保留上一轮目标。最小处理先撤下默认追圈入口。若保留实验测量，必须有每轮/每段标识、固定目标及用户确认推进，不能继续只修文字。首次pointermove、重开始、识别波动和真实输入时序必须纳入验证。详见体验复盘，不能将所有投诉跳动归结为某一单独触发点。

## 其他需要修复的问题

### F7 · P2 · 底板请求悬挂时，过期提示不会显示【离线浏览器复现】

位置：[calibration-board.html:55](../app/src/main/assets/calibration-board.html:55)。

fetch无Abort期限，ageMs检查只在收到响应之后执行，下次poll也只在await完成后安排。服务端或网络保持连接但不回包时，旧的“向左移动/与头显同步”会一直保留。最小修复：以客户端最后成功响应时间独立驱动失联提示，请求有超时，失联冻结目标并明确停止引导。测试使用本机原始页面：13:18:20首次返回第2步，下一次响应故意延后到13:20:20，随后请求再次挂起；13:20:55结束测试前浏览器仍显示“缓慢向左”“进度2/10·与头显同步”，没有等待提示。这是离线故障注入，不是iPad实测。

### F8 · P2 · 慢请求可长时间占满底板工作线程【代码确认】

位置：[CalibrationBoardServer.kt:51](../app/src/main/java/com/localair/airplay/CalibrationBoardServer.kt:51)，[CalibrationBoardServer.kt:70](../app/src/main/java/com/localair/airplay/CalibrationBoardServer.kt:70)。

2秒soTimeout是每次阻塞读取的期限，并非整个header期限；缓慢逐字节请求可以长期占住两个worker，让正常/state排队。shutdownNow丢弃的排队Runnable捕获socket，却不显式关闭它们。最小修复：请求总截止时间、拒绝不完整/超限header，跟踪并关闭排队/运行连接。测试只在本地用两个慢连接，不对用户设备压测。服务绑定所有接口且无认证，但当前GET只暴露页面/少量状态，无HID写接口；应如实描述为可达网络可读，不能说只允许指定同子网。

### F9 · P2 · 每条鼠标报告同步写文件，给输入主线程增加不必要负担【确定热路径，影响未量化】

位置：[HidController.java:83](../app/src/main/java/com/localair/airplay/HidController.java:83)，调用处433及402。

发送与确认每次调用note，主线程打开/追加/关闭文件，并再post UI刷新。连续射线报告导致多次磁盘操作进入输入和界面热路径。最小修复：把必要数值事件写有界内存队列，由独立写线程批量落盘，状态变化才刷新UI，保留故障上下文。需测主线程耗时再量化性能收益；不是通过删日志掩盖卡顿。

## 需要进一步验证，暂不列为确定故障

- 松开超时会绕过notificationPending提交中立报告（HidController.java:177），而Android文档要求等onNotificationSent再发下一条。现有FIFO只证明应用层配对，不证明Android栈在该异常路径的行为。需无真实鼠标输入的传输替身和受控平台验证，再决定失败时断连策略。[官方回调契约](https://developer.android.com/reference/android/bluetooth/BluetoothGattServerCallback#onNotificationSent(android.bluetooth.BluetoothDevice,%20int))
- 同设备重连时GATT server generation不变，只有PointerAction代际递增；回调携带device而无报告ID。需注入旧连接迟到notification来验证是否会消耗新pending项，不能仅靠PointerAction纯状态测试宣称解决。
- JNI视频owner在不同sender包到来时切换，RAOP允许多个连接；并发视频sender交错可能反复reset。已检查销毁顺序为先join RTP、后session-end、最后销毁NTP，但没有并发sender实测。
- VideoDecoder只在handler内限制pending数量，入口handler消息队列并未有界；积压时丢压缩帧也没有同步切回IDR等待。需慢解码/背压回放才能评估实际延迟、参考帧破坏与内存风险。
- PointerIdentityTracker是保守箭头启发式，不覆盖iPad指针所有形态；短重捕获测试不代表圆形、文本、吸附控件均可靠。PixelCopy本地请求时间也不等于远端画面时间。不能把“可信”当作已验证的全场景身份。

## 检查过但不应重复报旧问题

VideoDecoder当前回调检查codec所有权、旧Surface detach不会撤销新owner、reset先撤销codec后释放、窗口park保留解码参考状态；这些是已经存在的防护。当前只读日志中的03:35 PID12051异常属于旧进程，不能算0.2.13新崩溃。

PointerAction取消只允许松开，按下与松开包有动作代际，断连丢弃旧动作。HID普通移动不直接依赖视觉识别。底板现无文件读取/输入注入端点，状态输出为不可变字符串；这些事实与上述剩余缺口并不矛盾。

## 测试覆盖为何不足

38项JVM测试覆盖理想方向样本、状态门禁、模拟增益/延迟和简单箭头；检测器唯一测试主要比较缓冲复用与新实例的一致性。它们没有覆盖真实iPad指针变形、页面与原生基线联合时序、用户追圈直到完成的路径。

Android测试有codec旧回调、Surface owner、停车重绑、HID未启用门禁和PointerAction纯状态检查；没有真实Activity PiP持续帧、HTTP挂起、HID通知缺失、音频reset后复用的测试。近几版只构建了测试APK，没有运行完整真机测试；本次也未运行会打断当前用户的仪器测试。

本次新增离线取证是直接运行生产PointerCalibration的超时反例，以及本机浏览器的请求悬挂反例。它们证明具体缺陷，不代表完整设备验收。优先修F1–F6并撤回复杂默认流程，分别补能失败的回归测试；F2/F4/F5仍需要受控平台故障注入。当前白字投诉缺活动视频/owner/waiting同步证据，继续标为未定位，不靠重启宣称修好。
