package com.zephyr.rearmusic;

import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.Display;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;

final class RearDoze {
    private final Handler main = new Handler(Looper.getMainLooper());
    private WeakReference<DreamService> service = new WeakReference<>(null);
    private final Method setter;
    private int requested;
    private boolean applying, enabled, forced;
    private long lastMusicAt;
    private long flushUntil;
    private volatile boolean dreaming;

    boolean isDreaming() { return dreaming; }

    RearDoze(Hooks.Installer hook, ClassLoader loader) throws Throwable {
        setter = DreamService.class.getMethod("setDozeScreenState", int.class, int.class, boolean.class);
        Class<?> rearService = loader.loadClass("com.xiaomi.subscreencenter.doze.DozeService");
        hook.after(rearService.getMethod("onDreamingStarted"), (o, a, r) -> dreaming = true);
        hook.after(rearService.getMethod("onDreamingStopped"), (o, a, r) -> dreaming = false);
        hook.after(rearService.getMethod("onDestroy"), (o, a, r) -> dreaming = false);
        hook.after(loader.loadClass("com.xiaomi.subscreencenter.doze.DozeService")
            .getDeclaredMethod("setDozeScreenState", int.class), (o, args, result) -> {
            if (applying || !o.getClass().getName().equals("com.xiaomi.subscreencenter.doze.DozeService")) return;
            service = new WeakReference<>((DreamService) o);
            requested = (Integer) args[0];
            forced = false;
            if (!enabled && requested == Display.STATE_DOZE_SUSPEND
                && android.os.SystemClock.uptimeMillis() - lastMusicAt < 1500) {
                flushUntil = android.os.SystemClock.uptimeMillis() + 400;
                main.postDelayed(this::update, 410);
            } else if (requested != Display.STATE_DOZE_SUSPEND) flushUntil = 0;
            update();
        });
    }

    void enabled(boolean value) {
        main.post(() -> { enabled = value; update(); });
    }

    void musicVisible(boolean showing) {
        if (showing) main.post(() -> lastMusicAt = android.os.SystemClock.uptimeMillis());
    }

    private void update() {
        DreamService current = service.get();
        if (current == null) return;
        boolean keepRefreshing = (enabled || android.os.SystemClock.uptimeMillis() < flushUntil)
            && requested == Display.STATE_DOZE_SUSPEND;
        if (keepRefreshing == forced) return;
        applying = true;
        try {
            setter.invoke(current, keepRefreshing ? Display.STATE_DOZE : requested, 0, false);
            forced = keepRefreshing;
            android.util.Log.i(Settings.TAG, "Rear doze continuous refresh=" + forced);
        } catch (Exception error) { android.util.Log.e(Settings.TAG, "Rear doze state failed", error); }
        finally { applying = false; }
    }
}
