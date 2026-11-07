package android.util;

/** Replaces android.jar's Log, which throws "Stub!", while the tests run on the computer. */
public final class Log {
    public static int i(String t, String m) { return 0; }
    public static int w(String t, String m) { return 0; }
    public static int w(String t, String m, Throwable e) { return 0; }
    public static int e(String t, String m) { return 0; }
    public static int e(String t, String m, Throwable e) { return 0; }
    public static int d(String t, String m) { return 0; }
}
