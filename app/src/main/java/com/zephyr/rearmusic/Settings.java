package com.zephyr.rearmusic;

import android.app.WallpaperColors;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

final class Settings {
    static final String PKG = "com.zephyr.rearmusic";
    static final String HOST = "com.xiaomi.subscreencenter";
    static final String SYSUI = "com.android.systemui";
    static final Uri URI = Uri.parse("content://" + PKG + ".config");
    static final String TAG = "RearMusic";
    final boolean whitelist, lights, forceUpdate, screenOffLights;
    final Set<String> apps;
    final int[] palette;

    Settings(Bundle data) {
        whitelist = true;
        lights = data.getBoolean("lights", true);
        forceUpdate = true;
        screenOffLights = data.getBoolean("screen_off_lights", false);
        String[] list = data.getStringArray("apps");
        apps = new HashSet<>(Arrays.asList(list == null ? new String[0] : list));
        int[] savedPalette = data.getIntArray("palette");
        palette = savedPalette == null || savedPalette.length < 3
            ? new int[] {0xFF52E7FF, 0xFF9B6CFF, 0xFFFF5FC8} : savedPalette;
    }

    static SharedPreferences prefs(Context context) {
        return context.createDeviceProtectedStorageContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static Bundle bundle(Context context) {
        SharedPreferences p = prefs(context);
        Bundle data = new Bundle();
        data.putBoolean("whitelist", true);
        data.putBoolean("lights", p.getBoolean("lights", true));
        data.putBoolean("force_update", true);
        data.putBoolean("screen_off_lights", p.getBoolean("screen_off_lights", false));
        data.putStringArray("apps", p.getStringSet("apps", Set.of()).toArray(new String[0]));
        data.putIntArray("palette", new int[] {
            p.getInt("native_palette", 0xFF52E7FF),
            p.getInt("native_palette_1", 0xFF9B6CFF),
            p.getInt("native_palette_2", 0xFFFF5FC8)
        });
        return data;
    }

    static void publishNativePalette(Context context, android.media.MediaMetadata metadata) {
        if (metadata == null) return;
        Bitmap cover = null;
        for (String key : new String[] {
            android.media.MediaMetadata.METADATA_KEY_ALBUM_ART,
            android.media.MediaMetadata.METADATA_KEY_ART,
            android.media.MediaMetadata.METADATA_KEY_DISPLAY_ICON
        }) {
            try { cover = metadata.getBitmap(key); }
            catch (RuntimeException ignored) { }
            if (cover != null && !cover.isRecycled()) break;
            cover = null;
        }
        if (cover == null) return;
        try {
            WallpaperColors colors = WallpaperColors.fromBitmap(cover);
            int first = color(colors.getPrimaryColor(), 0xFF52E7FF);
            int second = color(colors.getSecondaryColor(), shiftHue(first, 38));
            int third = color(colors.getTertiaryColor(), shiftHue(first, 300));
            context.getContentResolver().call(URI, "publish_palette", null,
                bundle(new int[] {first, second, third}));
        } catch (RuntimeException error) { Log.w(TAG, "Native artwork palette unavailable", error); }
    }

    private static Bundle bundle(int[] palette) {
        Bundle result = new Bundle();
        result.putIntArray("palette", palette);
        return result;
    }

    private static int color(Color color, int fallback) {
        return color == null ? fallback : color.toArgb() | 0xFF000000;
    }

    private static int shiftHue(int color, float amount) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + amount) % 360;
        hsv[1] = Math.max(0.48f, hsv[1]);
        hsv[2] = Math.max(0.84f, hsv[2]);
        return Color.HSVToColor(hsv);
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
