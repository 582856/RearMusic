package com.zephyr.rearmusic;

import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

final class Hooks {
    interface After { void run(Object instance, Object[] args, Object result) throws Throwable; }
    interface Installer { void after(Executable method, After callback) throws Throwable; }
    private static final AtomicBoolean installed = new AtomicBoolean();

    static void attach(String packageName, Installer hook) throws Throwable {
        if (!Settings.HOST.equals(packageName) && !Settings.SYSUI.equals(packageName)) return;
        Method attach = Application.class.getDeclaredMethod("attach", Context.class);
        hook.after(attach, (app, args, result) -> {
            Context context = (Context) args[0];
            if (!packageName.equals(context.getPackageName()) || !installed.compareAndSet(false, true)) return;
            // Defer service lookups until Application.attach has completed in the host loader.
            new Handler(Looper.getMainLooper()).post(() -> {
                try { Toast.makeText(context, "RearMusic 注入成功：" + packageName, Toast.LENGTH_SHORT).show(); }
                catch (RuntimeException error) { Log.w(Settings.TAG, "Startup toast unavailable", error); }
                Log.i(Settings.TAG, "injected=" + packageName);
                if (Settings.HOST.equals(packageName)) {
                    new Thread(() -> WhitelistHook.install(context, hook), "RearMusic-DexKit").start();
                } else {
                    try { new RearVisualizer(context).start(); }
                    catch (Throwable error) { Settings.report(context, "摄像头律动初始化失败：" + error.getClass().getSimpleName()); Log.e(Settings.TAG, "Visualizer init", error); }
                }
            });
        });
    }
}
