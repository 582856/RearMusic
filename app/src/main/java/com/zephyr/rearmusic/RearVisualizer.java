package com.zephyr.rearmusic;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Point;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.media.MediaMetadata;
import android.media.audiofx.Visualizer;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Display;
import android.view.Gravity;
import android.view.WindowManager;
import java.util.Collections;
import java.util.List;

final class RearVisualizer {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Settings.Client settings;
    private final java.util.concurrent.ExecutorService configWorker = java.util.concurrent.Executors.newSingleThreadExecutor();
    private Settings cachedConfig = new Settings(new android.os.Bundle());
    private boolean readingConfig;
    private boolean nativeVisible;
    private long configReceivedAt;
    private final DisplayManager displays;
    private final MediaSessionManager sessions;
    private List<MediaController> controllers = Collections.emptyList();
    private WindowManager windows;
    private RhythmView view;
    private Visualizer audio;
    private android.os.PowerManager.WakeLock rearWakeLock;
    private MediaController active;
    private Rect lastCameraArea;
    private long retryAt;
    private RhythmView fadingView;
    private WindowManager fadingWindows;
    private Runnable removeFading;
    private String lastStatus = "";
    private final AudioDiagnostics diagnostics = new AudioDiagnostics();
    private long nextAudioDiagnostic;
    private volatile long fftAt;
    private volatile long fftCount;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { refresh(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) {
            refresh();
        }
        @Override public void onSessionDestroyed() { schedule(); }
    };
    private final Runnable tick = () -> { readConfig(); refresh(); schedule(); };

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
            @Override public void onChange(boolean selfChange) {
                configWorker.execute(settings::invalidate);
                readConfig();
            }
        });
        sessionList(sessions.getActiveSessions(null));
        status("摄像头律动已注入，等待白名单应用播放");
        schedule();
    }

    private void sessionList(List<MediaController> list) {
        for (MediaController controller : controllers) {
            try { controller.unregisterCallback(callback); }
            catch (RuntimeException error) { DebugLog.w(Settings.TAG, "Unregister media callback", error); }
        }
        controllers = list == null ? Collections.emptyList() : list;
        for (MediaController controller : controllers) {
            try { controller.registerCallback(callback, main); }
            catch (RuntimeException error) { DebugLog.w(Settings.TAG, "Register media callback", error); }
        }
        refresh();
    }

    private void schedule() { main.removeCallbacks(tick); main.postDelayed(tick, 250); }
    private void status(String text) {
        if (!lastStatus.equals(text)) {
            lastStatus = text;
            configWorker.execute(() -> Settings.report(context, text));
        }
    }

    private void readConfig() {
        if (readingConfig) return;
        readingConfig = true;
        configWorker.execute(() -> {
            diagnostics.poll(context);
            Settings config = settings.get();
            boolean visible = false;
            try {
                android.os.Bundle state = context.getContentResolver().call(Settings.URI, "music_visible", null, null);
                visible = state != null && state.getBoolean("visible");
            } catch (RuntimeException error) { DebugLog.w(Settings.TAG, "Music visibility unavailable", error); }
            boolean result = visible;
            main.post(() -> {
                cachedConfig = config;
                nativeVisible = result;
                configReceivedAt = SystemClock.elapsedRealtime();
                readingConfig = false;
                refresh();
            });
        });
    }

    private void refresh() {
        try { refreshState(); }
        catch (Throwable error) {
            stop();
            retryAt = SystemClock.uptimeMillis() + 10000;
            status("摄像头律动检测失败：" + error.getClass().getSimpleName());
            DebugLog.e(Settings.TAG, "Playback/display refresh failed", error);
        }
    }

    private void refreshState() {
        Settings config = cachedConfig;
        Display rear = displays.getDisplay(1);
        MediaController playing = null;
        for (MediaController candidate : controllers) {
            PlaybackState state = candidate.getPlaybackState();
            if (state != null && state.getState() == PlaybackState.STATE_PLAYING
                && config.apps.contains(candidate.getPackageName())) { playing = candidate; break; }
        }
        boolean visibleDisplay = rear != null && rear.isValid()
            && (rear.getState() == Display.STATE_ON || rear.getState() == Display.STATE_DOZE
                || rear.getState() == Display.STATE_DOZE_SUSPEND);
        boolean eligible = config.lights && visibleDisplay && (rear.getState() == Display.STATE_ON || config.screenOffLights) && playing != null
            && nativeVisible && SystemClock.elapsedRealtime() - configReceivedAt < 1000;
        if (BuildConfig.DIAGNOSTIC) {
            StringBuilder media = new StringBuilder();
            for (MediaController c : controllers) {
                PlaybackState st = c.getPlaybackState();
                media.append(c.getPackageName()).append(":").append(st == null ? "null" : st.getState()).append(" ");
            }
            android.media.AudioManager am = context.getSystemService(android.media.AudioManager.class);
            DebugLog.sample("gate", "eligible=" + eligible + " lights=" + config.lights
                + " selected=" + config.apps + " sessions=" + media + " nativeVisible=" + nativeVisible
                + " configAge=" + (SystemClock.elapsedRealtime() - configReceivedAt)
                + " display=" + (rear == null ? "missing" : rear.getState()) + " screenOff=" + config.screenOffLights
                + " volume=" + am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                + " overlay=" + (view != null) + " audio=" + (audio != null)
                + " fftCount=" + fftCount + " fftAge=" + (fftAt == 0 ? -1 : SystemClock.elapsedRealtime() - fftAt));
        }
        if (!eligible) { stop(); return; }
        if (config.screenOffLights && rearWakeLock == null) {
            android.os.PowerManager power = context.getSystemService(android.os.PowerManager.class);
            try {
                rearWakeLock = (android.os.PowerManager.WakeLock) android.os.PowerManager.class
                    .getMethod("newWakeLock", int.class, String.class, int.class)
                    .invoke(power, android.os.PowerManager.SCREEN_DIM_WAKE_LOCK, "RearMusic:rear-music", rear.getDisplayId());
                rearWakeLock.acquire();
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Display-specific wake lock unavailable", error);
            }
        } else if (!config.screenOffLights) releaseRearWakeLock();
        if (view != null) {
            view.palette(config.palette);
            view.setKeepScreenOn(config.screenOffLights);
        }
        Rect cameraArea = cameraArea(rear);
        DebugLog.sample("camera", String.valueOf(cameraArea));
        if (cameraArea == null) { stop(); status("未读取到背屏摄像头区域"); return; }
        if (active != null && !active.getSessionToken().equals(playing.getSessionToken())) stop();
        if (view != null && !cameraArea.equals(lastCameraArea)) stop();
        if (view == null) {
            if (SystemClock.uptimeMillis() < retryAt) return;
            try {
                Context displayContext = context.createDisplayContext(rear)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null);
                windows = displayContext.getSystemService(WindowManager.class);
                RhythmView nextView = new RhythmView(displayContext);
                nextView.cameraWidth(cameraArea.width());
                nextView.palette(config.palette);
                nextView.setKeepScreenOn(config.screenOffLights);
                clearFading();
                nextView.setAlpha(0f);
                int backgroundPadding = Math.round(cameraArea.width() * 0.08f);
                WindowManager.LayoutParams layout = new WindowManager.LayoutParams(cameraArea.width() + 2 * backgroundPadding,
                    cameraArea.height(), WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
                layout.gravity = Gravity.TOP | Gravity.LEFT;
                layout.x = cameraArea.left - backgroundPadding;
                layout.y = cameraArea.top;
                layout.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
                layout.setTitle("RearMusic-Rhythm");
                layout.windowAnimations = 0;
                layout.setFitInsetsTypes(0);
                DebugLog.i(Settings.TAG, "overlay add display=" + rear.getDisplayId() + " x=" + layout.x + " y=" + layout.y + " w=" + layout.width + " h=" + layout.height);
                windows.addView(nextView, layout);
                nextView.animate().alpha(1f).setDuration(280).start();
                view = nextView;
                lastCameraArea = new Rect(cameraArea);
                active = playing;
            } catch (Throwable error) {
                stop();
                retryAt = SystemClock.uptimeMillis() + 10000;
                status("律动窗口失败：" + error.getClass().getSimpleName());
                DebugLog.e(Settings.TAG, "Overlay start failed", error);
                return;
            }
        }
        if (BuildConfig.DIAGNOSTIC && audio != null && SystemClock.elapsedRealtime() >= nextAudioDiagnostic) {
            nextAudioDiagnostic = SystemClock.elapsedRealtime() + 2000;
            diagnostics.visualizer(audio);
        }
        if (audio != null || SystemClock.uptimeMillis() < retryAt) return;
        try {
            startAudio(view);
            status("摄像头律动运行中：背屏 display 1，实时 FFT");
        } catch (Throwable error) {
            stopAudio();
            retryAt = SystemClock.uptimeMillis() + 10000;
            status("音频灯效失败：" + error.getClass().getSimpleName());
            DebugLog.e(Settings.TAG, "Audio capture start failed", error);
        }
    }

    private void startAudio(RhythmView targetView) throws Throwable {
        DebugLog.i(Settings.TAG, "Visualizer create session=0");
        Visualizer nextAudio = new Visualizer(0);
        try {
            int[] range = Visualizer.getCaptureSizeRange();
            int captureSize = Math.max(range[0], Math.min(256, range[1]));
            check(nextAudio.setCaptureSize(captureSize), "capture size");
            check(nextAudio.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED), "scaling");
            if (BuildConfig.DIAGNOSTIC) {
                DebugLog.i(Settings.TAG, "measurementMode result=" + nextAudio.setMeasurementMode(Visualizer.MEASUREMENT_MODE_PEAK_RMS));
            }
            check(nextAudio.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override public void onWaveFormDataCapture(Visualizer v, byte[] data, int rate) { diagnostics.capture(true, data, rate); }
                @Override public void onFftDataCapture(Visualizer v, byte[] data, int rate) {
                    diagnostics.capture(false, data, rate);
                    float level = RhythmMath.level(data);
                    fftAt = SystemClock.elapsedRealtime();
                    fftCount++;
                    DebugLog.sample("fft", "count=" + fftCount + " bytes=" + data.length + " sampleRate=" + rate
                        + " level=" + level + " attached=" + targetView.isAttachedToWindow());
                    targetView.level(level);
                }
            }, Visualizer.getMaxCaptureRate(), BuildConfig.DIAGNOSTIC, true), "FFT listener");
            check(nextAudio.setEnabled(true), "enable");
            audio = nextAudio;
            DebugLog.i(Settings.TAG, "Rhythm capture: normalized FFT at " + Visualizer.getMaxCaptureRate()
                + " mHz, 320ms target interval, logarithmic 13 stages");
        } catch (Throwable error) {
            nextAudio.release();
            throw error;
        }
    }

    private static Rect cameraArea(Display rear) {
        android.view.DisplayCutout cutout = rear.getCutout();
        if (cutout == null) return null;
        Rect area = null;
        for (Rect bounds : cutout.getBoundingRects()) {
            if (bounds.width() > 0 && bounds.height() > 0
                && (area == null || bounds.width() * (long) bounds.height() > area.width() * (long) area.height())) {
                area = new Rect(bounds);
            }
        }
        if (area == null) {
            int left = cutout.getSafeInsetLeft();
            Point size = new Point();
            rear.getRealSize(size);
            if (left > 0 && size.y > 0) area = new Rect(0, 0, left, size.y);
        }
        return area != null && area.width() >= 90 && area.height() >= 120 ? area : null;
    }

    private static void check(int code, String stage) {
        if (BuildConfig.DIAGNOSTIC) DebugLog.i(Settings.TAG, "Visualizer setup " + stage + " result=" + code);
        if (code != Visualizer.SUCCESS) throw new IllegalStateException(stage + ": " + code);
    }

    private void stop() {
        Display rear = displays.getDisplay(1);
        boolean animate = rear != null && rear.isValid() && rear.getState() == Display.STATE_ON;
        if (!animate) clearFading();
        releaseRearWakeLock();
        stopAudio();
        if (view != null) {
            clearFading();
            fadingView = view;
            fadingView.setKeepScreenOn(false);
            fadingWindows = windows;
            fadingView.freeze();
            fadingView.animate().cancel();
            if (animate) {
                removeFading = this::clearFading;
                fadingView.animate().alpha(0f).setDuration(220).withEndAction(removeFading).start();
            } else {
                fadingView.setAlpha(0f);
                clearFading();
            }
            view = null;
            status("摄像头律动已停止：等待白名单播放器或背屏亮起");
            lastCameraArea = null;
        }
        active = null;
    }

    private void clearFading() {
        if (removeFading != null) main.removeCallbacks(removeFading);
        removeFading = null;
        if (fadingView == null) return;
        fadingView.animate().cancel();
        try { fadingWindows.removeViewImmediate(fadingView); }
        catch (RuntimeException error) { DebugLog.w(Settings.TAG, "remove overlay", error); }
        fadingView = null;
        fadingWindows = null;
    }

    private void stopAudio() {
        if (audio != null) {
            DebugLog.i(Settings.TAG, "Visualizer release fftCount=" + fftCount);
            try { audio.release(); } catch (RuntimeException error) { DebugLog.w(Settings.TAG, "release audio", error); }
            audio = null;
        }
    }

    private void releaseRearWakeLock() {
        if (rearWakeLock != null) {
            if (rearWakeLock.isHeld()) rearWakeLock.release();
            rearWakeLock = null;
        }
    }
}
