# Quest射线控制iPad：官方与实现证据比较

查阅日期2026-09-12，在0.2.9提速候选构建及模拟完成之后开展。本轮未安装软件、改配对或向iPad发送输入。

结论：当前“相对HID即时移动＋视觉纠偏”是自研实验。查到的官方资料和主流实现没有证明Quest可以用通用蓝牙鼠标报告直接把任意iPad应用的指针设置到屏幕坐标。这里的“未找到”不是证明技术上绝对不可能。

| 路线 | 可核验做法 | 对当前项目的意义 |
|---|---|---|
| Quest本地原生射线 | Meta的Interactor与Interactable求交，生成Pointer Event，目标平面属于本地应用。[Meta文档](https://developers.meta.com/horizon/documentation/unreal/unreal-isdk-ray-interactions/) | 能立即得到本地命中点；不能仅凭这一点改变远端iPad指针。 |
| 远程桌面绝对坐标 | Moonlight的LiSendMousePositionEvent发送相对于参考平面的坐标；Sunshine接收ABS_MOUSE_MOVE并调用平台abs_mouse，Windows绝对定位使用系统输入注入。[Moonlight接口](https://github.com/moonlight-stream/moonlight-common-c/blob/master/src/Limelight.h)、[Sunshine接收实现](https://github.com/LizardByte/Sunshine/blob/master/src/input.cpp)、[平台说明](https://github.com/LizardByte/Sunshine/blob/master/docs/getting_started.md) | 有明确端到端代码证据，值得借鉴坐标映射。依赖被控电脑端权限与服务，不能将iOS客户端支持误读为iPad能当同等被控主机。 |
| iPad鼠标/触控板HID | Apple支持蓝牙鼠标与触控板；配件指南第15章的X/Y物理和逻辑范围对应触控板大小、分辨率及各手指位置。[鼠标支持](https://support.apple.com/en-ie/111775)、[配件指南](https://developer.apple.com/accessories/Accessory-Design-Guidelines.pdf) | 已有真机相对鼠标链路可继续优化。触控板的Abs字段不是屏幕定位承诺。配件指南本轮在线解析失败，细节复核了此前从官方URL下载的2026-06-08版第15章本地文本。 |
| Apple系统远程控制 | FaceTime支持在符合条件的iPhone/iPad之间获准控制；通用控制允许Mac键鼠跨到附近iPad。[FaceTime](https://support.apple.com/en-gb/guide/ipad/ipada92df253/ipados)、[通用控制](https://support.apple.com/en-au/102459) | 证明Apple系统存在跨应用控制，不能说iPad完全不支持远控。所查说明未给Quest/Android可调用的同等公开接口；Mac中继还需额外设备且未验证。 |
| 商业远程支持 | TeamViewer的iPad/iPhone方案提供屏幕共享，不能据其PC远控能力推断iPad拥有同等操作能力。[厂商说明](https://www.teamviewer.com/en/global/support/knowledge-base/teamviewer-classic/mobile/ios/share-the-screen-on-your-ipad-iphone/) | 商业成熟度并不消除本项目的iPad输入限制。 |
| ESP32绝对触摸尝试 | 存在宣称面向iOS的Touchscreen代码，API描述move会保持手指按下。[项目与API](https://github.com/iw20/ESP32-BLE-Touchscreen) | 可列为后续实验线索；本轮没有找到当前用户设备/系统版本的独立验证，不能认定为主流稳定方案，也不能把触摸按住当作悬停鼠标。 |
| 视觉纠偏 | 本项目从回传画面识别真实指针，再发送有限相对位移；0.2.9已降低等待并加入反馈门槛。 | 不依赖iPad端安装服务，但受视频回传、识别、吸附形变和加速度影响；本轮未找到Apple/Meta将其推荐为Quest控制iPad的官方路线。 |

## 可执行路线

1. 先在用户同意换版的窗口安装0.2.9，由用户在空白目标上测试。区分首步等待、每轮本地复制/识别耗时、反馈间隔和总收敛时间。观察间隔60ms不是端到端60ms；不承诺突破AirPlay反馈下限。
2. 用新增日志决定下一轮：若识别耗时高，优先优化图像处理；若本地耗时低而等待反馈长，继续加发送频率可能放大超调，应测视频链路延迟、调节预测及步长。不要仅凭蓝牙ACK快就认定视频反馈快。
3. 当前目标仍是控制iPad任意应用，保留已经验证的相对HID路径。绝对HID探索应作为独立兼容性实验，先证实当前iPad能接受无接触/无点击的绝对悬停报告，再谈整合；本轮没有通过的证据。
4. 如果以后允许把目标应用迁到PC或自有应用，采用本地射线坐标＋被控端明确处理绝对坐标的路线，才有清晰实现依据。它改变使用范围，不能作为本次iPad需求已经解决的交付。
