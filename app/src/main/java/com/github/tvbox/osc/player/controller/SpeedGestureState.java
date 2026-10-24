package com.github.tvbox.osc.player.controller;

/**
 * Touch-speed state, independent of Android Views and player callbacks.
 * Coordinates and thresholds must use the same units (normally pixels).
 */
public final class SpeedGestureState {
    public enum Change { NONE, SPEED, LOCKED, UNLOCKED }

    private boolean active;
    private boolean locked;
    private boolean verticalConsumed;
    private boolean endedThisPointer;
    private float originalSpeed = 1f;
    private float speed = 1f;
    private float downX;
    private float downY;
    private float speedAnchorX;

    public boolean isActive() { return active; }
    public boolean isLocked() { return locked; }
    public float getSpeed() { return speed; }
    public float getOriginalSpeed() { return originalSpeed; }

    /** A fresh touch can unlock an existing boost; it must not erase its speed. */
    public void pointerDown(float x, float y) {
        downX = x;
        downY = y;
        speedAnchorX = x;
        verticalConsumed = false;
        endedThisPointer = false;
    }

    /** Returns true only when a new boost starts; repeated long-presses are inert. */
    public boolean begin(float originalSpeed) {
        if (active || endedThisPointer) return false;
        this.originalSpeed = originalSpeed > 0f && !Float.isInfinite(originalSpeed)
                && !Float.isNaN(originalSpeed) ? originalSpeed : 1f;
        speed = 2f;
        active = true;
        locked = false;
        return true;
    }

    public Change move(float x, float y, float horizontalThreshold, float verticalThreshold) {
        if (!active || endedThisPointer) return Change.NONE;
        float horizontal = threshold(horizontalThreshold);
        float vertical = threshold(verticalThreshold);
        float deltaY = y - downY;
        // One vertical action per physical touch. A diagonal drag cannot both
        // toggle the latch and change speed in the same move event.
        if (!verticalConsumed && deltaY > vertical && deltaY > Math.abs(x - downX)) {
            verticalConsumed = true;
            speedAnchorX = x;
            if (locked) {
                locked = false;
                active = false;
                speed = originalSpeed;
                endedThisPointer = true;
                return Change.UNLOCKED;
            }
            locked = true;
            return Change.LOCKED;
        }
        // Track the furthest point in the current direction. Reversing a long
        // drag should require only the threshold, not returning to its old origin.
        if (speed == 3f && x > speedAnchorX) speedAnchorX = x;
        else if (speed == 2f && x < speedAnchorX) speedAnchorX = x;
        float deltaX = x - speedAnchorX;
        if ((speed == 2f && deltaX > horizontal) || (speed == 3f && deltaX < -horizontal)) {
            speed = speed == 2f ? 3f : 2f;
            speedAnchorX = x;
            return Change.SPEED;
        }
        return Change.NONE;
    }

    /** Returns true when this release ends an unlocked boost. */
    public boolean pointerUp() {
        if (!active || locked) return false;
        active = false;
        speed = originalSpeed;
        endedThisPointer = true;
        return true;
    }

    /** Cancel also clears a latched boost, e.g. on an episode or player change. */
    public boolean cancel() {
        boolean wasActive = active;
        active = false;
        locked = false;
        speed = originalSpeed;
        endedThisPointer = true;
        verticalConsumed = false;
        return wasActive;
    }

    private static float threshold(float value) {
        return value > 0f && !Float.isNaN(value) && !Float.isInfinite(value) ? value : 1f;
    }
}
