package de.robv.android.xposed;
import java.lang.reflect.Member;
/** Compile-only API declaration. */
public final class XposedBridge {
    public static XC_MethodHook.Unhook hookMethod(Member method, XC_MethodHook callback) {
        throw new UnsupportedOperationException();
    }
}
