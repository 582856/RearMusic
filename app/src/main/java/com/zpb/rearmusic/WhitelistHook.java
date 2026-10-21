package com.zpb.rearmusic;

import android.content.Context;
import android.database.ContentObserver;
import android.media.MediaMetadata;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.luckypray.dexkit.DexKitBridge;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.query.matchers.MethodMatcher;
import org.luckypray.dexkit.result.MethodData;
import org.luckypray.dexkit.result.FieldData;
import org.luckypray.dexkit.result.UsingFieldData;

// Adapted from REAREye's MusicControlWhitelistModule / SmartAssistantRegistry, GPL-3.0.
final class WhitelistHook {
    private WeakReference<Object> snapshot = new WeakReference<>(null);
    private Map<?, ?> baseline;
    private Set<String> lastApps;
    private boolean lastEnabled;

    static void install(Context context, Hooks.Installer hook) {
        Settings.Client settings = new Settings.Client(context);
        try {
            System.loadLibrary("dexkit");
            ClassLoader loader = context.getClassLoader();
            Method snapshotMethod;
            Field mapField;
            try (DexKitBridge bridge = DexKitBridge.create(context.getApplicationInfo().sourceDir)) {
                var points = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create().paramCount(0)
                    .usingStrings("registry loaded: %d apps, %d groups, %d app card intents")));
                if (points.size() != 1) throw new IllegalStateException("Registry matches=" + points.size());
                snapshotMethod = points.get(0).getMethodInstance(loader);
                Class<?> snapshotClass = snapshotMethod.getReturnType();
                var readers = bridge.findMethod(FindMethod.create().matcher(MethodMatcher.create()
                    .paramTypes(String.class).returnType(boolean.class).usingStrings("unified.music", "music")));
                FieldData selected = null;
                for (MethodData reader : readers) {
                    for (UsingFieldData usage : reader.getUsingFields()) {
                        FieldData field = usage.getField();
                        if (field.getClassName().equals(snapshotClass.getName()) && field.getTypeName().equals("java.lang.Object")) {
                            if (selected != null && !selected.getDescriptor().equals(field.getDescriptor()))
                                throw new IllegalStateException("Ambiguous registry field");
                            selected = field;
                        }
                    }
                }
                if (selected == null) throw new IllegalStateException("Registry map not found");
                mapField = selected.getFieldInstance(loader);
            }
            snapshotMethod.setAccessible(true);
            mapField.setAccessible(true);
            WhitelistHook patch = new WhitelistHook();
            hook.after(snapshotMethod, (instance, args, result) -> patch.update(result, mapField, settings.get()));
            Runnable refresh = () -> {
                settings.invalidate();
                try { patch.update(snapshotMethod.invoke(null), mapField, settings.get()); }
                catch (Throwable error) { Log.e(Settings.TAG, "Registry refresh failed", error); }
            };
            context.getContentResolver().registerContentObserver(Settings.URI, true,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override public void onChange(boolean selfChange) { refresh.run(); }
                });
            refresh.run();
            Settings.report(context, "白名单已注入：" + snapshotMethod.getDeclaringClass().getName() + "." + snapshotMethod.getName());
        } catch (Throwable error) {
            Log.e(Settings.TAG, "Whitelist installation failed", error);
            Settings.report(context, "白名单适配失败：" + error.getClass().getSimpleName());
        }
        // Metadata refresh is optional and must not prevent the registry hook from installing.
        try {
            Class<?> listener = context.getClassLoader().loadClass("com.miui.maml.elements.MusicControlScreenElement$1");
            Method metadata = listener.getDeclaredMethod("onClientMetadataUpdate", MediaMetadata.class);
            hook.after(metadata, (instance, args, result) -> {
                if (!settings.get().forceUpdate) return;
                Field owner = listener.getDeclaredField("this$0");
                owner.setAccessible(true);
                Object element = owner.get(instance);
                for (Class<?> type = element.getClass(); type != null; type = type.getSuperclass()) {
                    try {
                        Field root = type.getDeclaredField("mRoot");
                        root.setAccessible(true);
                        Object value = root.get(element);
                        if (value != null) value.getClass().getMethod("requestUpdate").invoke(value);
                        break;
                    } catch (NoSuchFieldException ignored) { }
                }
            });
        } catch (Throwable error) { Log.w(Settings.TAG, "Optional metadata refresh unavailable", error); }
    }

    private synchronized void update(Object current, Field field, Settings settings) throws IllegalAccessException {
        if (current == null) return;
        Object value = field.get(current);
        if (!(value instanceof Map) || value == Collections.EMPTY_MAP) return;
        if (snapshot.get() != current) {
            snapshot = new WeakReference<>(current);
            baseline = new HashMap<>((Map<?, ?>) value);
            lastApps = null;
        }
        if (lastApps != null && lastApps.equals(settings.apps) && lastEnabled == settings.whitelist) return;
        field.set(current, Collections.unmodifiableMap(WhitelistPatch.apply(baseline, settings.whitelist, settings.apps)));
        lastApps = Set.copyOf(settings.apps);
        lastEnabled = settings.whitelist;
        Log.i(Settings.TAG, "whitelist applied count=" + (lastEnabled ? lastApps.size() : 0));
    }
}
