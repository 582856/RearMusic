package com.zpb.rearmusic;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.media.audiofx.Visualizer;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import java.util.Collections;
import java.util.List;

final class RearVisualizer {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Settings.Client settings;
    private final DisplayManager displays;
    private final MediaSessionManager sessions;
    private List<MediaController> controllers = Collections.emptyList();
    private WindowManager windows;
    private RhythmView view;
    private Visualizer audio;
    private MediaController active;
    private int lastHeight, lastBottom;
    private long retryAt;
    private String lastStatus = "";
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
        @Override public void onSessionDestroyed() { schedule(); }
    };
    private final Runnable tick = () -> { refresh(); schedule(); };

    RearVisualizer(Context context) {
        this.context = context;
        settings = new Settings.Client(context);
        displays = context.getSystemService(DisplayManager.class);
        sessions = context.getSystemService(MediaSessionManager.class);
    }

    void start() {
        sessions.addOnActiveSessionsChangedListener(this::sessionList, null, main);
        displays.registerDisplayListener(new DisplayManager.DisplayListener() {
            @Override public void onDisplayAdded(int id) { if (id == 1) refresh(); }
            @Override public void onDisplayRemoved(int id) { if (id == 1) stop(); }
            @Override public void onDisplayChanged(int id) { if (id == 1) refresh(); }
        }, main);
        context.getContentResolver().registerContentObserver(Settings.URI, true, new ContentObserver(main) {
            @Override public void onChange(boolean selfChange) { settings.invalidate(); refresh(); }
        });
        sessionList(sessions.getActiveSessions(null));
        status("走马灯已注入，等待白名单应用播放");
        schedule();
    }

    private void sessionList(List<MediaController> list) {
        for (MediaController controller : controllers) {
            try { controller.unregisterCallback(callback); }
            catch (RuntimeException error) { Log.w(Settings.TAG, "Unregister media callback", error); }
        }
        controllers = list == null ? Collections.emptyList() : list;
        for (MediaController controller : controllers) {
            try { controller.registerCallback(callback, main); }
            catch (RuntimeException error) { Log.w(Settings.TAG, "Register media callback", error); }
        }
        refresh();
    }

    private void schedule() { main.removeCallbacks(tick); main.postDelayed(tick, 1000); }
    private void status(String text) {
        if (!lastStatus.equals(text)) { lastStatus = text; Settings.report(context, text); }
    }

    private void refresh() {
        try { refreshState(); }
        catch (Throwable error) {
            stop();
            retryAt = SystemClock.uptimeMillis() + 10000;
            status("走马灯检测失败：" + error.getClass().getSimpleName());
            Log.e(Settings.TAG, "Playback/display refresh failed", error);
        }
    }

    private void refreshState() {
        Settings config = settings.get();
        Display rear = displays.getDisplay(1);
        MediaController playing = null;
        for (MediaController candidate : controllers) {
            PlaybackState state = candidate.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING) { playing = candidate; break; }
        }
        boolean eligible = config.lights && rear != null && rear.isValid() && rear.getState() == Display.STATE_ON
            && playing != null && config.apps.contains(playing.getPackageName());
        if (!eligible) { stop(); return; }
        if (active != null && !active.getSessionToken().equals(playing.getSessionToken())) stop();
        if (view != null && (lastHeight != config.height || lastBottom != config.bottom)) stop();
        if (view != null || SystemClock.uptimeMillis() < retryAt) return;
        try {
            Context displayContext = context.createDisplayContext(rear)
                .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
            windows = displayContext.getSystemService(WindowManager.class);
            RhythmView nextView = new RhythmView(displayContext);
            float density = displayContext.getResources().getDisplayMetrics().density;
            WindowManager.LayoutParams layout = new WindowManager.LayoutParams((int) (130 * density),
                (int) (config.height * density), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, PixelFormat.TRANSLUCENT);
            layout.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            layout.y = (int) (config.bottom * density);
            layout.setTitle("RearMusic-Rhythm");
            layout.setFitInsetsTypes(0);
            windows.addView(nextView, layout);
            view = nextView;
            lastHeight = config.height;
            lastBottom = config.bottom;
            active = playing;
            Visualizer nextAudio = new Visualizer(0);
            audio = nextAudio;
            int[] range = Visualizer.getCaptureSizeRange();
            int captureSize = Math.max(range[0], Math.min(256, range[1]));
            check(nextAudio.setCaptureSize(captureSize), "capture size");
            check(nextAudio.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED), "scaling");
            check(nextAudio.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] data, int rate) { }
                @Override public void onFftDataCapture(Visualizer v, byte[] data, int rate) {
                    nextView.level(RhythmMath.level(data));
                }
            }, Math.min(10000, Visualizer.getMaxCaptureRate()), false, true), "FFT listener");
            check(nextAudio.setEnabled(true), "enable");
            status("走马灯运行中：背屏 display 1，实时 FFT");
        } catch (Throwable error) {
            stop();
            retryAt = SystemClock.uptimeMillis() + 10000;
            status("音频灯效失败：" + error.getClass().getSimpleName());
            Log.e(Settings.TAG, "Audio/overlay start failed", error);
        }
    }

    private static void check(int code, String stage) {
        if (code != Visualizer.SUCCESS) throw new IllegalStateException(stage + ": " + code);
    }

    private void stop() {
        if (audio != null) {
            try { audio.release(); } catch (RuntimeException error) { Log.w(Settings.TAG, "release audio", error); }
            audio = null;
        }
        if (view != null) {
            try { windows.removeViewImmediate(view); } catch (RuntimeException error) { Log.w(Settings.TAG, "remove overlay", error); }
            view = null;
            status("走马灯已停止：等待播放或背屏亮起");
        }
        active = null;
    }
}
