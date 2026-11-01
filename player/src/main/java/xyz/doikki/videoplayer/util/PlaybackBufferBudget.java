package xyz.doikki.videoplayer.util;

import android.app.ActivityManager;
import android.content.Context;

/** Limit compressed-media buffering using the app's heap limits. */
public final class PlaybackBufferBudget {
    private static final long MB = 1024L * 1024L;
    private PlaybackBufferBudget() {}

    public static int forDevice(Context context) {
        ActivityManager manager = context == null ? null
                : (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return calculate(Runtime.getRuntime().maxMemory(),
                manager != null ? manager.getMemoryClass() : 64,
                manager != null && manager.isLowRamDevice());
    }

    public static int calculate(long heapLimitBytes, int memoryClassMb, boolean lowRamDevice) {
        long normalHeap = Math.max(1L, (long) memoryClassMb) * MB;
        long available = Math.max(1L, Math.min(heapLimitBytes, normalHeap));
        long cap = (lowRamDevice ? 32L : 128L) * MB;
        return (int) Math.max(1L, Math.min(cap, available / 4));
    }
}
