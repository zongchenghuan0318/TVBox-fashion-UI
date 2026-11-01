package xyz.doikki.videoplayer.util;

public final class PlaybackBufferBudgetTest {
    private static final long MB = 1024L * 1024L;
    public static void main(String[] args) {
        expect(512, 128, false, 32L * MB);
        expect(512, 512, true, 32L * MB);
        expect(512, 512, false, 128L * MB);
        expect(64, 256, false, 16L * MB);
        expect(256, 192, false, 48L * MB);
        expect(32768, 32768, false, 128L * MB);
        for (int heap : new int[] {32, 64, 128, 256, 512, 1024}) {
            for (int normal : new int[] {32, 64, 128, 256, 512}) {
                int budget = PlaybackBufferBudget.calculate(heap * MB, normal, false);
                if (budget <= 0 || budget > Math.min(heap, normal) * MB / 4 || budget > 128 * MB)
                    throw new AssertionError("Unsafe budget: " + heap + "/" + normal);
            }
        }
        System.out.println("PASS heap, memory class, low-RAM and overflow budget checks");
    }
    private static void expect(int heap, int normal, boolean lowRam, long expected) {
        int actual = PlaybackBufferBudget.calculate(heap * MB, normal, lowRam);
        if (actual != expected) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
}
