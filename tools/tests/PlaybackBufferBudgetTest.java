package xyz.doikki.videoplayer.util;

public final class PlaybackBufferBudgetTest {
    private static final long MB = 1024L * 1024L;

    public static void main(String[] args) {
        expect(512, 128, false, 16); // largeHeap must not inflate a 128MB device budget.
        expect(512, 512, true, 16);
        expect(512, 512, false, 64);
        expect(64, 256, false, 8); // The actual VM limit wins over memoryClass.
        expect(256, 192, false, 24);
        expect(32768, 32768, false, 64); // Long arithmetic must not overflow.
        for (int heap : new int[] {32, 64, 128, 256, 512, 1024}) {
            for (int normal : new int[] {32, 64, 128, 256, 512}) {
                int budget = PlaybackBufferBudget.calculate(heap * MB, normal, false);
                if (budget > Math.min(heap, normal) * MB / 8 || budget > 64 * MB || budget <= 0) {
                    throw new AssertionError("Unsafe budget for " + heap + "/" + normal);
                }
            }
        }
        System.out.println("PASS largeHeap, low-RAM, VM limit, cap and overflow budget checks");
    }

    private static void expect(int heapMb, int normalMb, boolean lowRam, int expectedMb) {
        int actual = PlaybackBufferBudget.calculate(heapMb * MB, normalMb, lowRam);
        if (actual != expectedMb * MB) {
            throw new AssertionError("Expected " + expectedMb + "MB, got " + actual);
        }
    }
}
