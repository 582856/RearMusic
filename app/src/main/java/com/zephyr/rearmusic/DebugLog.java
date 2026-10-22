package com.zephyr.rearmusic;

import android.os.SystemClock;
import java.util.HashMap;
import java.util.Map;

final class DebugLog {
    interface Sink { void write(String text); }
    private static volatile Sink sink;
    private static final Map<String, Long> times = new HashMap<>();
    static void bind(Sink value) {
        if (!BuildConfig.DIAGNOSTIC) return;
        sink = value;
        i(Settings.TAG, "diagnostic=" + BuildConfig.VERSION_NAME + " pid=" + android.os.Process.myPid()
            + " model=" + android.os.Build.MODEL + " sdk=" + android.os.Build.VERSION.SDK_INT
            + " firmware=" + android.os.Build.DISPLAY);
    }
    static synchronized void sample(String key, String text) {
        if (!BuildConfig.DIAGNOSTIC) return;
        long now = SystemClock.elapsedRealtime();
        Long last = times.get(key);
        if (last != null && now - last < 2000) return;
        times.put(key, now);
        i(Settings.TAG, key + " " + text);
    }
    static void i(String tag, String text) { write(android.util.Log.INFO, tag, text, null); }
    static void w(String tag, String text) { write(android.util.Log.WARN, tag, text, null); }
    static void w(String tag, String text, Throwable error) { write(android.util.Log.WARN, tag, text, error); }
    static void e(String tag, String text, Throwable error) { write(android.util.Log.ERROR, tag, text, error); }
    private static void write(int priority, String tag, String text, Throwable error) {
        String message = "[RearMusic " + SystemClock.elapsedRealtime() + "] " + text
            + (error == null ? "" : "\n" + android.util.Log.getStackTraceString(error));
        android.util.Log.println(priority, tag, message);
        Sink target = sink;
        if (BuildConfig.DIAGNOSTIC && target != null) {
            try { target.write(message); }
            catch (Throwable failure) { android.util.Log.e(tag, "LSPosed log failed", failure); }
        }
    }
}
