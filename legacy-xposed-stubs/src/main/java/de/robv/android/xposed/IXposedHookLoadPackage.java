package de.robv.android.xposed;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
/** Compile-only API declaration. The framework supplies the implementation. */
public interface IXposedHookLoadPackage {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) throws Throwable;
}
