# 0.2.7-recovery：暂停前保留解码输出面

## 0.2.6复测失败与证据

用户摘戴后仍黑屏但有声音。PID24128存活；事件在05:02:06暂停/停止，05:02:13恢复。media.resource_manager显示视频codec资源在05:02:06移除，随后只剩AAC音频decoder；SurfaceFlinger的新视频BLAST层activeBuffer为0x0，queued-frames为0。音视频网络数据仍到达。因此不是仅有UI遮挡，恢复后实际没有视频缓冲。

关键瞬间的主日志被原生逐包DEBUG日志覆盖，不能断言是系统主动回收，还是旧输出面销毁引起codec错误后应用释放。当前恢复策略等待下一IDR；若发送端没有及时发送，就不能靠仅重建SurfaceView恢复参考帧。

## 改动

- Activity.onPause在窗口销毁前撤销当前渲染目标，并让codec切到由服务内decoder持有的parking SurfaceTexture/Surface。继续解码但不呈现输出，保留参考状态。
- 主线程仅等待这个切换最多500ms，超时/中断留日志；后续resume把codec绑定回当前视频层。没有重复定时重启。
- 原生接收日志调至INFO，避免音视频逐包DEBUG日志冲掉生命周期与codec错误。等待关键帧时每5秒输出一次状态。
- 保留0.2.6的新Surface owner帧确认和显式“采样3秒”入口；不改变HID报告、配对或默认控制门禁。

## 已执行验证

APK、测试APK构建及lint通过。用户授权安装测试窗口后，覆盖安装0.2.7-recovery/code14与测试APK，21项真机instrumentation全部通过。新增测试使用真实MediaCodec检查parking后释放旧Surface、再绑定新Surface，codec对象仍保留；另一个用例验证旧帧不能满足新Surface确认。测试没有连接发送端或发送真实鼠标输入，因此不覆盖真实长GOP视频、系统休眠资源回收或整条摘戴画面恢复。

测试结束后正式启动一次，最终PID25152。前台服务与mDNS正常，新PID crash缓冲为空。头显随后处于Asleep，与用户摘下休息一致；没有主动重启头显、清配对或发送输入。

用户随后完成真实投屏→摘下→戴回复测并确认“这次好了”。对应日志：05:08:49.882 decoder output parked（fed=1534），05:08:58.844 surface rebound、decoder preserved（fed=1921），05:08:58.860新Surface缓冲确认；PID25152未变。这是一次真实恢复验收通过，不代表长期或所有休眠情形均已验证。

APK SHA256：e80c9f1e442a1a3949b01aae84340d68df95069bfb545fb2a9ae91314416a25c。
