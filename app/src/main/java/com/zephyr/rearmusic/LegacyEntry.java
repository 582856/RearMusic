package com.zephyr.rearmusic;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class LegacyEntry implements IXposedHookLoadPackage {
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam load) {
        if (!load.packageName.equals(load.processName)) return;
        if (!Settings.HOST.equals(load.packageName) && !Settings.SYSUI.equals(load.packageName)) return;
        DebugLog.bind(text -> XposedBridge.log(text));
        DebugLog.i(Settings.TAG, "entry legacy package=" + load.packageName);
        try {
            Hooks.attach(load.packageName, (method, callback) -> XposedBridge.hookMethod(method, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    try { callback.run(param.thisObject, param.args, param.getResult()); }
                    catch (Throwable error) { DebugLog.e(Settings.TAG, "Hook callback failed", error); }
                }
            }));
        } catch (Throwable error) { DebugLog.e(Settings.TAG, "Legacy injection failed", error); }
    }
}
