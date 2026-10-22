# RearMusic

适用于 **小米 17 Pro Max 背屏**的 LSPosed / Vector 模块，将音乐空间白名单与摄像头包围式音乐律动整合到一个应用中，设置界面使用 miuix。

## 下载与使用

从 [Releases](https://github.com/582856/RearMusic/releases) 下载 APK。

1. 安装 RearMusic，在 LSPosed / Vector 中启用模块。
2. 勾选 **背屏中心** `com.xiaomi.subscreencenter` 与 **系统界面** `com.android.systemui` 两个作用域，重启手机。
3. 打开 RearMusic，选择需要使用背屏音乐空间的播放器。
4. 白名单默认始终开启；开启「背屏音乐律动」，播放音乐并打开背屏音乐组件。

**0.1.32 包名变更为 `com.zephyr.rearmusic`**，无法覆盖旧包。请在 LSPosed 关闭旧模块、启用新版并重新勾选两个作用域；新版需重新选择播放器及设置。

播放器需要提供 Android MediaSession。白名单配置即时通知宿主；模块首次启用、作用域变动和升级后需要重启。

## 功能

- 音乐空间白名单始终开启，选中的播放器加入原生音乐业务注册表与媒体会话过滤列表。顶部显示白名单注入状态。
- 复用 MIUI 原生背屏音乐卡片显示封面、歌名、歌手、背景和播放控件；模块只在左侧摄像头区域叠加黑色纵向胶囊与两镜之间的横向对称音柱。
- 音柱由 SystemUI 的实时 FFT 驱动；音柱渐变色从 MIUI MAML 音乐组件收到的原生封面提取。绘制层不覆盖卡片主体，也不绘制镜头圆环或旋转边框。
- 律动仅在背屏音乐组件显示；退出页面、暂停或关闭功能时隐藏。提供「息屏继续显示律动」开关；关闭时进入息屏后提前隐藏。
- 切歌强制刷新默认启用。25 根音柱、13 阶幅度，按 Flip 参考调整动画节奏；系统 FFT 音源与原播放器不同，并非完全一致。
- miuix 设置页，支持深色模式、应用搜索和底部手势区域适配；应用列表在进程内缓存，安装或移除应用后刷新。

## 适配边界

- 仅面向 17 Pro Max，最低 Android 15；不适用于 MIX Flip 或普通小米 17。
- 白名单动态定位背屏中心注册表，不绑定混淆类名。已分析宿主版本为 `RELEASE-1.0.2609231642`；其他固件需验证。
- 灯效使用背屏 `display 1`，以选中且正在播放的优先 MediaSession 为触发条件。系统界面利用系统已有音频权限采样，无独立 Root / Shizuku 授权页。
- Visualizer 采样系统混音；多个应用同时出声时，灯效可能包含其他音源。不会保存录音或联网发送音频。系统音量为零时无法获取有效波形，律动会停止。
- 曲目信息和背景完全由 MIUI 原生卡片处理；播放器未提供封面时，系统卡片可显示默认音符图标，律动使用默认渐变色。
- 系统限制悬浮窗或禁止系统混音采样时，设置页显示失败状态并停止灯效；需要根据具体固件适配。
- 背屏原生音乐空间的样式、媒体信息与可用媒体操作均由系统决定。灯效只叠加在摄像头区域。
- 请避免同时启用其他模块的音乐白名单修改，以免互相覆盖。

**当前为实验版本。** 构建与算法单测通过；真机安装、界面与 Hook 验证记录见 [兼容性记录](docs/COMPATIBILITY.md)。未记录为通过的功能不能视为已实测。

## 构建

需要 JDK 17、Android SDK Platform 37.0 和 Build Tools 36.0.0，使用仓库内 Gradle Wrapper，无需全局 Gradle。
SDK Manager 中的平台包名为 `platforms;android-37.0`。当前 AGP 9.0 使用 `android-37` 目录名；若本机只有 `platforms/android-37.0`，需为其建立 `platforms/android-37` 目录链接（Windows 可用 Junction）。CI 已包含此兼容处理。

Windows：

```powershell
$env:JAVA_HOME = '你的 JDK 17 路径'
$env:ANDROID_HOME = '你的 Android SDK 路径'
.\preflight.ps1
.\gradlew.bat :app:assembleRelease :app:testDebugUnitTest
python scripts/verify_apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

Linux / macOS：

```sh
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
sh preflight.sh
sh gradlew :app:assembleRelease :app:testDebugUnitTest
python3 scripts/verify_apk.py app/build/outputs/apk/release/app-release-unsigned.apk
```

Release 构建启用 **R8 混淆、优化及资源压缩**，保留 Xposed 入口与 DexKit JNI 所需类。Kotlin 2.4 对应 R8 9.1.29，构建从 Google 官方 R8 分发地址获取。映射文件位于 `app/build/outputs/mapping/release/`，不要用 Debug 包代替混淆后的 Release 包测试 Hook。

本地构建和 CI 默认输出未签名 APK，需使用自己的 keystore 和 Android SDK `apksigner` 签名后安装。GitHub Release 附件由项目发布证书签名；签名私钥不包含在仓库中。

## 来源与许可

音乐白名单逻辑改编自 [killerprojecte/REAREye](https://github.com/killerprojecte/REAREye)，基于提交 [`cec416e`](https://github.com/killerprojecte/REAREye/tree/cec416e54ab268c626c0f054a2a234cb9787a64a)，遵循 GPL-3.0。保留上游署名与许可，详见 [NOTICE](NOTICE.md) 和 [LICENSE](LICENSE)。

摄像头包围轮廓及音频驱动为独立实现，未包含网易云音乐或系统 APK 的反编译代码、资源或二进制文件。
