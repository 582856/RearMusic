# 诊断版

## Debug2 诊断版

附件 `RearMusic-0.1.32-debug2.apk` 与正式版同包名、同签名，可覆盖安装。诊断版不混淆，模块运行日志和异常堆栈输出至 LSPosed；高频页面、播放会话、背屏状态和 FFT 信息每两秒记录一次。

覆盖安装后确认「背屏中心」「系统界面」作用域已启用，重启手机。播放网易云并打开背屏音乐页，保持约 20 秒，在 LSPosed 导出日志用于排查。

诊断日志包含设备型号、固件版本及播放器包名。构建、单元测试、签名及入口检查通过，故障用户设备运行情况尚待验证。

构建命令：`./gradlew :app:assembleDiagnostic :app:testDebugUnitTest`。输出 `app/build/outputs/apk/diagnostic/app-diagnostic-unsigned.apk`，签名后安装。

Debug2 新增 FFT / 波形非零数量、峰值、RMS、变化次数及回调统计；采样返回码、采样器状态、输出设备、播放配置、权限、组件版本、音效实现和绘制状态。只输出统计值，不保存原始音频。
