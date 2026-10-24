import android.content.Context;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import sun.misc.Unsafe;
import xyz.doikki.videoplayer.controller.BaseVideoController;
import xyz.doikki.videoplayer.controller.ControlWrapper;
import xyz.doikki.videoplayer.controller.MediaPlayerControl;
import xyz.doikki.videoplayer.controller.OrientationHelper;
import xyz.doikki.videoplayer.player.VideoView;

/**
 * Runs the actual production progress Runnable and start/stop methods on a JVM.
 * Only Android construction and the View message queue are replaced: Android's
 * SDK jar cannot create a View on a JVM. No copy of the ticker implementation is used.
 */
public final class ControllerTickerRegression {
    private static final class Pending {
        final Runnable runnable;
        final long delay;
        Pending(Runnable runnable, long delay) {
            this.runnable = runnable;
            this.delay = delay;
        }
    }

    private static final class FakeOrientation extends OrientationHelper {
        FakeOrientation() { super(null); }
        @Override public void disable() { }
    }

    private static final class FakeController extends BaseVideoController {
        List<Pending> queue;
        int refreshes;
        int lastPosition;
        Runnable duringRefresh;

        // This constructor is never executed. Unsafe skips Android SDK stubs.
        FakeController() { super((Context) null); }

        @Override protected int getLayoutId() { return 0; }
        @Override public boolean post(Runnable action) {
            queue.add(new Pending(action, 0));
            return true;
        }
        @Override public boolean postDelayed(Runnable action, long delay) {
            queue.add(new Pending(action, delay));
            return true;
        }
        @Override public boolean removeCallbacks(Runnable action) {
            return queue.removeIf(pending -> pending.runnable == action);
        }
        @Override protected void setProgress(int duration, int position) {
            refreshes++;
            lastPosition = position;
            Runnable callback = duringRefresh;
            duringRefresh = null;
            if (callback != null) callback.run();
        }

        void initialize(MediaPlayerControl player, Runnable productionTicker, OrientationHelper orientation) {
            queue = new ArrayList<>();
            mControlComponents = new LinkedHashMap<>();
            mControlWrapper = new ControlWrapper(player, this);
            mOrientationHelper = orientation;
            mShowProgress = productionTicker;
        }

        void tick() {
            check(queue.size() == 1, "expected exactly one pending ticker before dispatch, got " + queue.size());
            queue.remove(0).runnable.run();
        }

        void expectOne(long delay) {
            check(queue.size() == 1, "expected exactly one pending ticker, got " + queue.size());
            check(queue.get(0).delay == delay, "expected delay " + delay + ", got " + queue.get(0).delay);
        }
    }

    private static final class FakePlayer {
        boolean playing = true;
        long position = 1_000;

        MediaPlayerControl proxy() {
            return (MediaPlayerControl) Proxy.newProxyInstance(
                    MediaPlayerControl.class.getClassLoader(), new Class<?>[]{MediaPlayerControl.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "isPlaying": return playing;
                            case "getDuration": return 100_000L;
                            case "getCurrentPosition": return position;
                            case "getSpeed": return 1.0f;
                            default:
                                Class<?> type = method.getReturnType();
                                if (type == boolean.class) return false;
                                if (type == int.class) return 0;
                                if (type == long.class) return 0L;
                                if (type == float.class) return 0.0f;
                                return null;
                        }
                    });
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        FakeController controller = (FakeController) unsafe.allocateInstance(FakeController.class);
        FakeOrientation orientation = (FakeOrientation) unsafe.allocateInstance(FakeOrientation.class);
        FakePlayer player = new FakePlayer();

        // In the current production source $1 is fade-out, and $2 is mShowProgress.
        // If that source structure changes, the first refresh assertion fails loudly.
        Class<?> tickerClass = Class.forName(BaseVideoController.class.getName() + "$2");
        Constructor<?> constructor = tickerClass.getDeclaredConstructor(BaseVideoController.class);
        constructor.setAccessible(true);
        Runnable actualTicker = (Runnable) constructor.newInstance(controller);
        controller.initialize(player.proxy(), actualTicker, orientation);

        controller.startProgress();
        controller.startProgress();
        controller.expectOne(0);
        controller.tick();
        check(controller.refreshes == 1, "production progress callback did not run");
        controller.expectOne(250);
        System.out.println("PASS playing: real callback refreshes, repeated start keeps one ticker");

        player.playing = false;
        player.position = 25_000;
        controller.setPlayState(VideoView.STATE_PAUSED);
        controller.tick();
        check(controller.lastPosition == 25_000, "paused position was not refreshed");
        controller.expectOne(500);
        player.position = 45_000; // Seeking while paused must also update displayed time.
        controller.tick();
        check(controller.lastPosition == 45_000, "paused seek was not refreshed");
        controller.expectOne(500);
        System.out.println("PASS paused seek: updated position and one 500 ms ticker");

        controller.setPlayState(VideoView.STATE_BUFFERING);
        controller.tick();
        controller.expectOne(500);
        player.playing = true;
        controller.setPlayState(VideoView.STATE_BUFFERED);
        controller.tick();
        controller.expectOne(250);
        System.out.println("PASS buffering/resume: ticker survives and resumes playing cadence");

        controller.duringRefresh = controller::stopProgress;
        controller.tick();
        check(controller.queue.isEmpty(), "stop inside progress callback re-enqueued a ticker");
        System.out.println("PASS reentrant stop: callback cannot re-enqueue after stop");

        controller.startProgress();
        controller.duringRefresh = () -> {
            controller.stopProgress();
            controller.startProgress();
        };
        controller.tick();
        controller.expectOne(250);
        for (int i = 0; i < 20; i++) {
            controller.tick();
            controller.expectOne(250);
        }
        System.out.println("PASS reentrant stop/start: one ticker for 20 following dispatches");

        for (int terminal : new int[]{VideoView.STATE_ERROR, VideoView.STATE_PLAYBACK_COMPLETED, VideoView.STATE_IDLE}) {
            controller.startProgress();
            controller.setPlayState(terminal);
            check(controller.queue.isEmpty(), "terminal state left a pending ticker: " + terminal);
        }
        System.out.println("PASS terminal states: error, completion and idle remove the ticker");
        System.out.println("ALL 6 REGRESSION GROUPS PASSED (real production controller; fake View queue only)");
    }
}
