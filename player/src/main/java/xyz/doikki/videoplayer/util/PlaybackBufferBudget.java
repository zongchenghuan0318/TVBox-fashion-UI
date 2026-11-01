package xyz.doikki.videoplayer.util;

import android.app.ActivityManager;
import android.content.Context;

/** Shared compressed-data buffer budget for Exo and IJK. */
public final class PlaybackBufferBudget {
    private static final long MB = 1024L * 1024L;

    private PlaybackBufferBudget() {}

    public static int forDevice(Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        return calculate(Runtime.getRuntime().maxMemory(),
                manager != null ? manager.getMemoryClass() : 64,
                manager != null && manager.isLowRamDevice());
    }

    static int calculate(long heapLimitBytes, int memoryClassMb, boolean lowRamDevice) {
        // largeHeap can exaggerate the memory available on a low-end TV.
        long normalHeap = Math.max(16L, memoryClassMb) * MB;
        long availableHeap = Math.min(heapLimitBytes, normalHeap);
        long cap = (lowRamDevice ? 16L : 64L) * MB;
        return (int) Math.max(2L * MB, Math.min(cap, availableHeap / 8));
    }
}
