package com.zephyr.rearmusic.testplayer;

import android.app.Activity;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.concurrent.atomic.AtomicInteger;

/** Separate test fixture: generated audio only, never included in the module APK. */
public final class PlayerActivity extends Activity {
    private final AtomicInteger generation = new AtomicInteger();
    private final Handler main = new Handler(Looper.getMainLooper());
    private MediaSession session;
    private TextView status;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 160, 48, 64);
        status = new TextView(this);
        status.setText("RearMusic 测试音源\n生成带节拍的合成音，2 分钟后自动停止。\n测试前请调低媒体音量。\n将此应用加入 RearMusic 白名单后播放。");
        status.setTextSize(19);
        layout.addView(status);
        Button play = new Button(this);
        play.setText("播放测试音");
        play.setOnClickListener(v -> play());
        layout.addView(play);
        Button pause = new Button(this);
        pause.setText("暂停");
        pause.setOnClickListener(v -> stop());
        layout.addView(pause);
        setContentView(layout);
        session = new MediaSession(this, "RearMusic Test");
        session.setCallback(new MediaSession.Callback() {
            @Override public void onPlay() { play(); }
            @Override public void onPause() { stop(); }
            @Override public void onStop() { stop(); }
        });
        session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "RearMusic 合成节拍")
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "测试音源").putLong(MediaMetadata.METADATA_KEY_DURATION, 120000).build());
        state(PlaybackState.STATE_PAUSED);
        session.setActive(true);
    }

    private void state(int value) {
        session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY
            | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_STOP)
            .setState(value, 0, value == PlaybackState.STATE_PLAYING ? 1 : 0).build());
    }

    private void play() {
        int token = generation.incrementAndGet();
        state(PlaybackState.STATE_PLAYING);
        status.setText("正在播放合成节拍，可切换背屏。\n2 分钟后自动停止。");
        main.removeCallbacksAndMessages(null);
        main.postDelayed(this::stop, 120000);
        new Thread(() -> {
            AudioTrack track = null;
            try {
                track = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(44100)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setBufferSizeInBytes(8820).setTransferMode(AudioTrack.MODE_STREAM).build();
                track.play();
                short[] samples = new short[882];
                long frame = 0, end = SystemClock.uptimeMillis() + 120000;
                while (generation.get() == token && SystemClock.uptimeMillis() < end) {
                    for (int i = 0; i < samples.length; i++, frame++) {
                        double time = frame / 44100.0;
                        double beat = Math.exp(-7 * (time % 0.5));
                        samples[i] = (short) (3500 * beat * (Math.sin(time * 2 * Math.PI * 220)
                            + 0.35 * Math.sin(time * 2 * Math.PI * 440)));
                    }
                    if (track.write(samples, 0, samples.length) < 0) break;
                }
            } catch (RuntimeException error) {
                main.post(() -> status.setText("测试音源失败：" + error.getClass().getSimpleName()));
            } finally {
                if (track != null) track.release();
            }
        }, "RearMusic-TestTone").start();
    }

    private void stop() {
        generation.incrementAndGet();
        main.removeCallbacksAndMessages(null);
        state(PlaybackState.STATE_PAUSED);
        status.setText("已暂停：背屏灯效应隐藏。\n点击播放可继续测试。");
    }

    @Override protected void onDestroy() {
        stop();
        session.release();
        super.onDestroy();
    }
}
