# 可选测试音源

独立 APK，包名 `com.zpb.rearmusic.testplayer`，不会打入 RearMusic 模块。
点击播放后生成低音量合成节拍，提供 MediaSession、标题和播放/暂停回调，2 分钟自动停止。
不包含歌曲，不读取媒体文件，不使用网络。

```sh
sh gradlew :test-player:assembleRelease
```

为输出 APK 签名后安装，将「RearMusic 测试音源」加入模块白名单。
依次验证背屏音乐控件出现、灯条随声音亮起、暂停隐藏、恢复播放重现、移出白名单隐藏、背屏熄灭释放采样。
测试后卸载此测试 APK 即可。
