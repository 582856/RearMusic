# RearMusic

适用于 **小米 17 Pro / 17 Pro Max 背屏**的 LSPosed / Vector 模块，将音乐空间白名单与音频走马灯整合到一个应用中，设置界面使用 miuix。

## 下载与使用

从 [Releases](https://github.com/582856/RearMusic/releases) 下载 APK。

1. 安装 RearMusic，在 LSPosed / Vector 中启用模块。
2. 勾选 **背屏中心** `com.xiaomi.subscreencenter` 与 **系统界面** `com.android.systemui` 两个作用域，重启手机。
3. 打开 RearMusic，选择需要使用背屏音乐空间的播放器。
4. 开启「音乐控件白名单」和「音频走马灯」，播放音乐并点亮背屏。

播放器需要提供 Android MediaSession。白名单配置即时通知宿主；模块首次启用、作用域变动和升级后需要重启。

## 功能

- 仅将选中的应用加入音乐业务白名单，关闭或移除选择时恢复原注册表内容。
- 25 根对称灯条，实时 FFT 控制点亮范围，紫色流光与平滑回落。
- 暂停、背屏熄灭或关闭功能时移除灯条并释放音频采样。
- 可调灯条高度与底部距离；提供可选的切歌强制刷新。
- miuix 设置页，支持深色模式、应用搜索、系统应用显示开关和底部手势区域适配。

## 适配边界

- 面向有背屏的 17 Pro / Pro Max，最低 Android 15；不适用于 MIX Flip 或普通小米 17。
- 白名单动态定位背屏中心注册表，不绑定混淆类名。已分析宿主版本为 `RELEASE-1.0.2609231642`；其他固件需验证。
- 灯效使用背屏 `display 1`，以选中且正在播放的优先 MediaSession 为触发条件。系统界面利用系统已有音频权限采样，无独立 Root / Shizuku 授权页。
- Visualizer 采样系统混音；多个应用同时出声时，灯效可能包含其他音源。不会保存录音或联网发送音频。
- 系统限制悬浮窗或禁止系统混音采样时，设置页显示失败状态并停止灯效；需要根据具体固件适配。
- 背屏原生音乐空间的样式、可用媒体操作仍由系统决定。灯效是独立叠加层。
- 请避免同时启用其他模块的音乐白名单修改，以免互相覆盖。

**当前为实验版本。** 构建与算法单测通过；真机安装、界面与 Hook 验证记录见 [兼容性记录](docs/COMPATIBILITY.md)。未记录为通过的功能不能视为已实测。

## 构建

需要 JDK 17、Android SDK Platform 37 和 Build Tools 36.0.0，使用仓库内 Gradle Wrapper，无需全局 Gradle。

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

走马灯绘制及音频驱动为独立实现，未包含网易云音乐或系统 APK 的反编译代码、资源或二进制文件。
