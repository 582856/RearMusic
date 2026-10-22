package com.zephyr.rearmusic;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import java.util.Arrays;

public final class ConfigProvider extends ContentProvider {
    private volatile long musicFrameAt;
    private volatile long dozeStartedAt;
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        boolean own = uid == Process.myUid();
        boolean host = packages != null && Arrays.asList(packages).contains(Settings.HOST);
        boolean sysui = packages != null && Arrays.asList(packages).contains(Settings.SYSUI);
        if (!own && !host && !sysui) throw new SecurityException("Unsupported caller");
        if ("get".equals(method)) return Settings.bundle(getContext());
        if ("music_frame".equals(method) && host) {
            musicFrameAt = android.os.SystemClock.elapsedRealtime();
            return Bundle.EMPTY;
        }
        if ("music_visible".equals(method) && sysui) {
            Bundle value = new Bundle();
            long now = android.os.SystemClock.elapsedRealtime();
            boolean hideForDoze = dozeStartedAt > 0 && now - dozeStartedAt >= 300
                && !Settings.prefs(getContext()).getBoolean("screen_off_lights", false);
            value.putBoolean("visible", !hideForDoze && musicFrameAt > 0 && now - musicFrameAt < 800);
            return value;
        }
        if ("rear_doze".equals(method) && host) {
            if (!Boolean.parseBoolean(arg)) dozeStartedAt = 0;
            else if (dozeStartedAt == 0) dozeStartedAt = android.os.SystemClock.elapsedRealtime();
            return Bundle.EMPTY;
        }
        if ("music_hidden".equals(method) && host) {
            musicFrameAt = 0;
            return Bundle.EMPTY;
        }
        if ("publish_palette".equals(method) && host && extras != null) {
            int[] palette = extras.getIntArray("palette");
            if (palette != null && palette.length == 3)
                Settings.prefs(getContext()).edit().putInt("native_palette", palette[0])
                    .putInt("native_palette_1", palette[1]).putInt("native_palette_2", palette[2]).apply();
            return Bundle.EMPTY;
        }
        if ("report".equals(method) && !own && arg != null) {
            String key = sysui ? "status_systemui" : "status_whitelist";
            Settings.prefs(getContext()).edit().putString(key, arg.substring(0, Math.min(240, arg.length()))).apply();
            return Bundle.EMPTY;
        }
        return null;
    }
    @Override public String getType(Uri uri) { return null; }
    @Override public Cursor query(Uri uri, String[] p, String s, String[] a, String order) { throw new UnsupportedOperationException(); }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String where, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String where, String[] args) { throw new UnsupportedOperationException(); }
}
