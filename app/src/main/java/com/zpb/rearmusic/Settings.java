package com.zpb.rearmusic;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class Settings {
    static final String PKG = "com.zpb.rearmusic";
    static final String HOST = "com.xiaomi.subscreencenter";
    static final String SYSUI = "com.android.systemui";
    static final Uri URI = Uri.parse("content://" + PKG + ".config");
    static final String TAG = "RearMusic";
    final boolean whitelist, lights, forceUpdate;
    final Set<String> apps;
    final int height, bottom;

    Settings(Bundle data) {
        whitelist = data.getBoolean("whitelist", true);
        lights = data.getBoolean("lights", true);
        forceUpdate = data.getBoolean("force_update", false);
        String[] list = data.getStringArray("apps");
        apps = new HashSet<>(Arrays.asList(list == null ? new String[0] : list));
        height = Math.max(24, Math.min(72, data.getInt("height", 40)));
        bottom = Math.max(0, Math.min(100, data.getInt("bottom", 8)));
    }

    static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static Bundle bundle(Context context) {
        SharedPreferences p = prefs(context);
        Bundle data = new Bundle();
        data.putBoolean("whitelist", p.getBoolean("whitelist", true));
        data.putBoolean("lights", p.getBoolean("lights", true));
        data.putBoolean("force_update", p.getBoolean("force_update", false));
        data.putStringArray("apps", p.getStringSet("apps", Set.of()).toArray(new String[0]));
        data.putInt("height", p.getInt("height", 40));
        data.putInt("bottom", p.getInt("bottom", 8));
        return data;
    }

    static void changed(Context context) {
        context.getContentResolver().notifyChange(URI, null);
    }

    static void report(Context context, String state) {
        Log.i(TAG, state);
        try { context.getContentResolver().call(URI, "report", state, null); }
        catch (RuntimeException error) { Log.w(TAG, "Status provider unavailable", error); }
    }

    static final class Client {
        private final Context context;
        private Settings current = new Settings(new Bundle());
        private long nextRead;
        Client(Context context) { this.context = context; }
        synchronized void invalidate() { nextRead = 0; }
        synchronized Settings get() {
            long now = SystemClock.uptimeMillis();
            if (now >= nextRead) {
                nextRead = now + 2000;
                try {
                    Bundle value = context.getContentResolver().call(URI, "get", null, null);
                    if (value != null) current = new Settings(value);
                } catch (RuntimeException error) {
                    Log.w(TAG, "Config unavailable; retaining last configuration");
                }
            }
            return current;
        }
    }
}
