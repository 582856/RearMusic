package com.zpb.rearmusic;

import android.util.Log;
import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ModernEntry extends XposedModule {
    private final AtomicBoolean attached = new AtomicBoolean();
    @Override public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam load) {
        if (!load.isFirstPackage() || !attached.compareAndSet(false, true)) return;
        try {
            Hooks.attach(load.getPackageName(), (method, callback) -> hook(method)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE).intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        int count = chain.getExecutable().getParameterCount();
                        Object[] args = new Object[count];
                        for (int i = 0; i < count; i++) args[i] = chain.getArg(i);
                        callback.run(chain.getThisObject(), args, result);
                    } catch (Throwable error) { Log.e(Settings.TAG, "Hook callback failed", error); }
                    return result;
                }));
        } catch (Throwable error) { Log.e(Settings.TAG, "Modern injection failed", error); }
    }
}
