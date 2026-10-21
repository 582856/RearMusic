package com.zpb.rearmusic;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;
import java.util.Arrays;

public final class ConfigProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public Bundle call(String method, String arg, Bundle extras) {
        int uid = Binder.getCallingUid();
        String[] packages = getContext().getPackageManager().getPackagesForUid(uid);
        boolean own = uid == Process.myUid();
        boolean host = packages != null && Arrays.asList(packages).contains(Settings.HOST);
        boolean sysui = packages != null && Arrays.asList(packages).contains(Settings.SYSUI);
        if (!own && !host && !sysui) throw new SecurityException("Unsupported caller");
        if ("get".equals(method)) return Settings.bundle(getContext());
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
