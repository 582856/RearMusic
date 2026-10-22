package com.zephyr.rearmusic;

import android.content.Context;
import android.database.ContentObserver;
import android.media.MediaMetadata;
import android.os.Handler;
import android.os.Looper;
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
    private volatile WeakReference<Object> metadataListener = new WeakReference<>(null);

    static void install(Context context, Hooks.Installer hook) {
        Settings.Client settings = new Settings.Client(context);
        WhitelistHook patch = new WhitelistHook();
        installMusicIntegration(context, hook, settings);
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
            hook.after(snapshotMethod, (instance, args, result) -> patch.update(result, mapField, settings.get()));
            Runnable refresh = () -> {
                settings.invalidate();
                try {
                    patch.update(snapshotMethod.invoke(null), mapField, settings.get());
                    patch.requestMetadataRefresh(patch.metadataListener.get());
                }
                catch (Throwable error) { DebugLog.e(Settings.TAG, "Registry refresh failed", error); }
            };
            context.getContentResolver().registerContentObserver(Settings.URI, true,
                new ContentObserver(new Handler(Looper.getMainLooper())) {
                    @Override public void onChange(boolean selfChange) { refresh.run(); }
                });
            refresh.run();
            Settings.report(context, "白名单已注入：" + snapshotMethod.getDeclaringClass().getName() + "." + snapshotMethod.getName());
        } catch (Throwable error) {
            DebugLog.e(Settings.TAG, "Whitelist installation failed", error);
            Settings.report(context, "白名单适配失败：" + error.getClass().getSimpleName());
        }
        // Metadata refresh is optional and must not prevent the registry hook from installing.
        try {
            Class<?> listener = context.getClassLoader().loadClass("com.miui.maml.elements.MusicControlScreenElement$1");
            Method metadata = listener.getDeclaredMethod("onClientMetadataUpdate", MediaMetadata.class);
            hook.after(metadata, (instance, args, result) -> {
                patch.metadataListener = new WeakReference<>(instance);
                if (args.length > 0 && args[0] instanceof MediaMetadata media)
                    Settings.publishNativePalette(context, media);
                if (settings.get().forceUpdate) patch.requestMetadataRefresh(instance);
            });
            DebugLog.i(Settings.TAG, "MAML music metadata refresh hook installed");
        } catch (Throwable error) { DebugLog.w(Settings.TAG, "Optional metadata refresh unavailable", error); }
    }

    private static void installMusicIntegration(Context context, Hooks.Installer hook, Settings.Client settings) {
        try {
            ClassLoader loader = context.getClassLoader();
            Class<?> controller = loader.loadClass("com.miui.maml.elements.MusicController");
            Field packages = controller.getDeclaredField("sMusicPackages");
            packages.setAccessible(true);
            Runnable merge = () -> {
                try {
                    java.util.List<String> allowed = new java.util.ArrayList<>((java.util.List<String>) packages.get(null));
                    if (settings.get().whitelist) for (String app : settings.get().apps)
                        if (!allowed.contains(app)) allowed.add(app);
                    packages.set(null, Collections.unmodifiableList(allowed));
                    DebugLog.sample("native-filter", "selected=" + settings.get().apps + " containsNetease=" + allowed.contains("com.netease.cloudmusic"));
                } catch (Exception error) { DebugLog.e(Settings.TAG, "Native music package list", error); }
            };
            hook.after(controller.getDeclaredMethod("addMusicPackageList", java.util.List.class), (o, a, r) -> merge.run());
            merge.run();
            Class<?> music = loader.loadClass("com.miui.maml.elements.MusicControlScreenElement");
            Field musicController = music.getDeclaredField("mMusicController");
            musicController.setAccessible(true);
            Method reset = controller.getMethod("reset");
            java.util.Map<Object, String> applied = new java.util.WeakHashMap<>();
            Handler main = new Handler(Looper.getMainLooper());
            Hooks.After reconnect = (o, a, r) -> {
                Settings config = settings.get();
                String selection = config.whitelist + ":" + new java.util.TreeSet<>(config.apps);
                if (selection.equals(applied.get(o))) return;
                Object target = musicController.get(o);
                if (target == null) return;
                merge.run();
                applied.put(o, selection);
                main.post(() -> {
                    try { reset.invoke(target); }
                    catch (Exception error) { DebugLog.w(Settings.TAG, "Native music reconnect failed", error); }
                });
            };
            hook.after(music.getMethod("init"), reconnect);
            hook.after(music.getMethod("resume"), (o, a, r) -> {
                applied.remove(o);
                reconnect.run(o, a, r);
            });
            Class<?> element = loader.loadClass("com.miui.maml.elements.ScreenElement");
            Method visible = element.getMethod("isVisible");
            RearDoze rearDoze = new RearDoze(hook, loader);
            java.util.concurrent.atomic.AtomicReference<WeakReference<Object>> currentMusic =
                new java.util.concurrent.atomic.AtomicReference<>(new WeakReference<>(null));
            java.util.concurrent.ScheduledExecutorService visibilityWorker =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
            visibilityWorker.scheduleWithFixedDelay(() -> {
                Object current = currentMusic.get().get();
                try {
                    boolean showing = current != null && Boolean.TRUE.equals(visible.invoke(current));
                    context.getContentResolver().call(Settings.URI, "rear_doze", Boolean.toString(rearDoze.isDreaming()), null);
                    DebugLog.sample("native-page", "element=" + (current != null) + " visible=" + showing + " dreaming=" + rearDoze.isDreaming());
                    rearDoze.musicVisible(showing);
                    if (showing)
                        context.getContentResolver().call(Settings.URI, "music_frame", null, null);
                    else
                        context.getContentResolver().call(Settings.URI, "music_hidden", null, null);
                    Settings config = settings.get();
                    boolean playing = false;
                    if (showing && config.screenOffLights && config.lights) {
                        for (android.media.session.MediaController session : context.getSystemService(android.media.session.MediaSessionManager.class).getActiveSessions(null)) {
                            android.media.session.PlaybackState state = session.getPlaybackState();
                            if (config.apps.contains(session.getPackageName()) && state != null
                                && state.getState() == android.media.session.PlaybackState.STATE_PLAYING) { playing = true; break; }
                        }
                    }
                    rearDoze.enabled(playing);
                } catch (Exception error) { DebugLog.w(Settings.TAG, "Music visibility poll failed", error); }
            }, 0, 250, java.util.concurrent.TimeUnit.MILLISECONDS);
            hook.after(music.getMethod("finish"), (o, a, r) -> {
                if (currentMusic.get().get() == o) currentMusic.set(new WeakReference<>(null));
            });
            hook.after(music.getMethod("pause"), (o, a, r) -> {
                if (currentMusic.get().get() == o) {
                    currentMusic.set(new WeakReference<>(null));
                    rearDoze.enabled(false);
                    DebugLog.i(Settings.TAG, "Native music page paused; hide rhythm");
                }
            });
            long[] last = {0};
            hook.after(music.getMethod("render", android.graphics.Canvas.class), (o, a, r) -> {
                if (!music.isInstance(o) || !Boolean.TRUE.equals(visible.invoke(o))) return;
                currentMusic.set(new WeakReference<>(o));
                long now = android.os.SystemClock.elapsedRealtime();
                if (now - last[0] < 200) return;
                last[0] = now;
                reconnect.run(o, a, r);
            });
            DebugLog.i(Settings.TAG, "Native music filter and visibility hooks installed");
        } catch (Throwable error) { DebugLog.e(Settings.TAG, "Native music integration failed", error); }
    }

    private void requestMetadataRefresh(Object listener) {
        if (listener == null) return;
        try {
            Field owner = listener.getClass().getDeclaredField("this$0");
            owner.setAccessible(true);
            Object element = owner.get(listener);
            for (Class<?> type = element.getClass(); type != null; type = type.getSuperclass()) {
                try {
                    Field root = type.getDeclaredField("mRoot");
                    root.setAccessible(true);
                    Object value = root.get(element);
                    if (value != null) value.getClass().getMethod("requestUpdate").invoke(value);
                    return;
                } catch (NoSuchFieldException ignored) { }
            }
        } catch (Throwable error) {
            DebugLog.w(Settings.TAG, "MAML music metadata refresh failed", error);
        }
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
        Map writable = writableMap((Map) value);
        Map updated = WhitelistPatch.apply(baseline, settings.whitelist, settings.apps);
        if (lastApps != null) for (String app : lastApps) {
            if (baseline.containsKey(app)) writable.put(app, baseline.get(app));
            else writable.remove(app);
        }
        writable.putAll(updated);
        lastApps = Set.copyOf(settings.apps);
        lastEnabled = settings.whitelist;
        DebugLog.i(Settings.TAG, "whitelist applied count=" + (lastEnabled ? lastApps.size() : 0));
    }

    private static Map writableMap(Map original) throws IllegalAccessException {
        Map current = original;
        for (int depth = 0; depth < 32; depth++) {
            try { current.putAll(Collections.emptyMap()); return current; }
            catch (UnsupportedOperationException ignored) { }
            Map next = null;
            for (Class<?> type = current.getClass(); type != null && next == null; type = type.getSuperclass()) {
                for (Field field : type.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) continue;
                    field.setAccessible(true);
                    next = (Map) field.get(current);
                    if (next != null) break;
                }
            }
            if (next == null || next == current) break;
            current = next;
        }
        throw new IllegalStateException("Cannot resolve registry backing map");
    }
}
