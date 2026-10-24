import com.github.tvbox.osc.player.controller.SpeedGestureState;
import com.github.tvbox.osc.player.controller.SpeedGestureState.Change;

/** Executes the production state machine directly; no Android or test framework. */
public final class SpeedGestureStateTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void expect(Change actual, Change expected, String message) {
        check(actual == expected, message + ": expected " + expected + ", got " + actual);
    }

    private static SpeedGestureState start(float originalSpeed) {
        SpeedGestureState state = new SpeedGestureState();
        state.pointerDown(100, 100);
        check(state.begin(originalSpeed), "long press must begin acceleration");
        check(state.isActive() && !state.isLocked() && state.getSpeed() == 2f, "initial 2x state");
        return state;
    }

    public static void main(String[] args) {
        SpeedGestureState state = start(1.5f);
        expect(state.move(119, 100, 20, 30), Change.NONE, "below horizontal threshold");
        expect(state.move(120, 100, 20, 30), Change.NONE, "exact threshold is still hysteresis boundary");
        expect(state.move(121, 100, 20, 30), Change.SPEED, "right drag changes to 3x");
        check(state.getSpeed() == 3f, "3x selected");
        expect(state.move(120, 100, 20, 30), Change.NONE, "one pixel reverse must not switch");
        expect(state.move(101, 100, 20, 30), Change.NONE, "reverse exact threshold must not switch");
        expect(state.move(100, 100, 20, 30), Change.SPEED, "backtrack beyond threshold returns to 2x");
        check(state.getSpeed() == 2f, "2x restored without releasing");
        check(state.pointerUp(), "unlocked release must end boost");
        check(!state.isActive() && state.getSpeed() == 1.5f, "release restores original 1.5x");
        System.out.println("PASS 2x -> 3x -> 2x, directional hysteresis, original 1.5x restore");

        state = start(1f);
        expect(state.move(130, 100, 20, 30), Change.SPEED, "initial right drag selects 3x");
        expect(state.move(250, 100, 20, 30), Change.NONE, "continuing right tracks furthest point");
        expect(state.move(230, 100, 20, 30), Change.NONE, "reverse exact threshold preserves 3x");
        expect(state.move(220, 100, 20, 30), Change.SPEED, "reverse beyond latest right extreme selects 2x");
        check(state.getSpeed() == 2f, "long-drag reversal must return to 2x");
        expect(state.move(50, 100, 20, 30), Change.NONE, "continuing left tracks furthest point");
        expect(state.move(70, 100, 20, 30), Change.NONE, "rightward exact threshold preserves 2x");
        expect(state.move(71, 100, 20, 30), Change.SPEED, "right beyond latest left extreme selects 3x");
        check(state.getSpeed() == 3f, "long left drag can reverse back to 3x");
        System.out.println("PASS continued-drag extremes: right-then-left and left-then-right reversals");

        state = start(1.5f);
        expect(state.move(102, 131, 20, 30), Change.LOCKED, "downward drag locks");
        check(state.isLocked(), "locked flag must be set");
        check(!state.pointerUp(), "locked release must keep boost");
        check(state.isActive() && state.getSpeed() == 2f, "locked 2x survives release");
        expect(state.move(103, 170, 20, 30), Change.NONE, "repeated move cannot toggle lock again");
        check(state.isLocked(), "lock must not bounce on repeated move");
        state.pointerDown(200, 200);
        check(!state.begin(2f), "a new long press while locked cannot overwrite original speed");
        expect(state.move(201, 231, 20, 30), Change.UNLOCKED, "fresh touch down drag unlocks");
        check(!state.isActive() && !state.isLocked() && state.getSpeed() == 1.5f, "unlock ends boost and restores original");
        expect(state.move(500, 500, 20, 30), Change.NONE, "remaining unlock gesture cannot restart boost");
        check(!state.begin(1f), "same physical touch cannot begin again after unlock");
        System.out.println("PASS downward lock, release persistence, fresh-touch unlock, no repeated toggles");

        state = start(1.5f);
        expect(state.move(130, 100, 20, 30), Change.SPEED, "select 3x before locking");
        expect(state.move(131, 150, 20, 30), Change.LOCKED, "lock current 3x");
        check(state.getSpeed() == 3f, "locking must not reset 3x");
        state.pointerUp();
        state.pointerDown(0, 0);
        check(state.isLocked() && state.getSpeed() == 3f && state.getOriginalSpeed() == 1.5f,
                "fresh down preserves lock, current speed and original speed");
        check(state.cancel(), "cancel reports previous active state");
        check(!state.isActive() && !state.isLocked() && state.getSpeed() == 1.5f, "cancel fully resets boost");
        check(!state.cancel(), "second cancel must be idempotent");
        state.pointerDown(0, 0);
        check(state.begin(1f), "new episode/fresh touch can start a new boost");
        check(state.getOriginalSpeed() == 1f, "new boost records new original speed");
        System.out.println("PASS locked 3x preservation, cancel, idempotence, new episode original speed");

        state = start(1f);
        expect(state.move(150, 140, 20, 30), Change.SPEED, "predominantly horizontal diagonal changes speed only");
        check(!state.isLocked(), "same diagonal event cannot also lock");
        expect(state.move(151, 170, 20, 30), Change.LOCKED, "predominantly downward move locks only");
        check(state.getSpeed() == 3f, "locking event must not also change speed");
        expect(state.move(152, 200, 20, 30), Change.NONE, "continued downward motion cannot unlock");
        System.out.println("PASS diagonal intent and one change per move");
        System.out.println("ALL SPEED GESTURE REGRESSIONS PASSED");
    }
}
