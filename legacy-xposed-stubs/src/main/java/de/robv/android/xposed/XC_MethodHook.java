package de.robv.android.xposed;
/** Compile-only API declaration; never packaged in the module. */
public abstract class XC_MethodHook {
    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }
    public static final class MethodHookParam {
        public Object thisObject;
        public Object[] args;
        public Object getResult() { throw new UnsupportedOperationException(); }
        public boolean hasThrowable() { throw new UnsupportedOperationException(); }
    }
    public class Unhook { }
}
