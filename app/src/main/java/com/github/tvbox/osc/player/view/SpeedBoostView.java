package com.github.tvbox.osc.player.view;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.provider.Settings;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.Locale;

/** Visual feedback only. The playback controller owns gestures, locking and speed changes. */
public final class SpeedBoostView extends View {
    private static final int HIDDEN = 0;
    private static final int BOOSTING = 1;
    private static final int RESTORED = 2;
    private static final int ACCENT = Color.rgb(2, 248, 225);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF bounds = new RectF();
    private ValueAnimator flowAnimator;
    private int mode = HIDDEN;
    private float speed = 2f;
    private boolean locked;
    private float phase;
    private long presentation;

    private final Runnable hideRestored = new Runnable() {
        @Override public void run() {
            if (mode != RESTORED || getVisibility() != VISIBLE) return;
            final long token = presentation;
            if (!animationsEnabled()) {
                dismissImmediately();
                return;
            }
            animate().alpha(0f).scaleX(0.98f).scaleY(0.98f).setDuration(160L)
                    .setInterpolator(new DecelerateInterpolator()).withEndAction(() -> {
                        if (token == presentation && mode == RESTORED) dismissImmediately();
                    }).start();
        }
    };

    public SpeedBoostView(Context context) {
        this(context, null);
    }

    public SpeedBoostView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SpeedBoostView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        setVisibility(GONE);
    }

    /** Repeated MOVE calls with an unchanged state do not restart animations. */
    public void showSpeed(float speed, boolean locked, boolean emphasize) {
        float requested = Float.isNaN(speed) || Float.isInfinite(speed) ? 2f : Math.max(0.1f, speed);
        boolean changed = mode != BOOSTING || Math.abs(this.speed - requested) > 0.01f
                || this.locked != locked;
        if (!changed && getVisibility() == VISIBLE) {
            startFlowIfNeeded();
            return;
        }
        boolean entering = getVisibility() != VISIBLE || mode != BOOSTING;
        boolean speedChanged = Math.abs(this.speed - requested) > 0.01f;
        this.speed = requested;
        this.locked = locked;
        mode = BOOSTING;
        presentation++;
        removeCallbacks(hideRestored);
        animate().cancel();
        setContentDescription(formatSpeed(requested) + "倍速播放，"
                + (locked ? "已锁定，下滑或点此退出" : "右滑三倍，左滑两倍，下滑锁定"));
        setVisibility(VISIBLE);
        if (speedChanged) stopFlow();
        if (animationsEnabled()) {
            if (entering) {
                setAlpha(0f);
                setScaleX(0.93f);
                setScaleY(0.93f);
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else if (emphasize || locked) {
                setAlpha(1f);
                setScaleX(0.97f);
                setScaleY(0.97f);
                animate().scaleX(1f).scaleY(1f).setDuration(250L)
                        .setInterpolator(new OvershootInterpolator(1.2f)).start();
            } else {
                setAlpha(1f);
                setScaleX(1f);
                setScaleY(1f);
            }
        } else {
            setAlpha(1f);
            setScaleX(1f);
            setScaleY(1f);
        }
        startFlowIfNeeded();
        invalidate();
    }

    /** A brief acknowledgement; it never changes the player's configured speed. */
    public void showRestored(float originalSpeed) {
        mode = RESTORED;
        speed = Float.isNaN(originalSpeed) || Float.isInfinite(originalSpeed)
                ? 1f : Math.max(0.1f, originalSpeed);
        locked = false;
        presentation++;
        removeCallbacks(hideRestored);
        stopFlow();
        animate().cancel();
        setContentDescription("已恢复 " + formatSpeed(speed) + " 倍速");
        setVisibility(VISIBLE);
        setScaleX(1f);
        setScaleY(1f);
        setAlpha(1f);
        if (animationsEnabled()) {
            setAlpha(0.6f);
            animate().alpha(1f).setDuration(120L)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        postDelayed(hideRestored, 650L);
        invalidate();
    }

    public void dismissImmediately() {
        presentation++;
        mode = HIDDEN;
        removeCallbacks(hideRestored);
        stopFlow();
        animate().cancel();
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
        setVisibility(GONE);
    }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int desiredWidth = Math.round(176f * density) + getPaddingLeft() + getPaddingRight();
        int desiredHeight = Math.round(42f * density) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0 || mode == HIDDEN) return;
        int save = canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());
        float unit = Math.min(width / 176f, height / 42f);
        float centerY = height / 2f;
        drawArrows(canvas, 10f * unit, centerY, unit);
        if (mode == RESTORED) {
            drawFittedText(canvas, formatSpeed(speed) + "x  已释放",
                    62f * unit, centerY + 6f * unit, width - 68f * unit,
                    16f * unit, Color.WHITE, true, Paint.Align.LEFT);
        } else {
            drawFittedText(canvas, formatSpeed(speed) + "x",
                    62f * unit, centerY + 6f * unit, 42f * unit,
                    18f * unit, ACCENT, true, Paint.Align.LEFT);
            drawFittedText(canvas, locked ? "下滑释放" : "下滑锁定",
                    108f * unit, centerY + 5f * unit, width - 112f * unit,
                    14f * unit, Color.WHITE, false, Paint.Align.LEFT);
        }
        canvas.restoreToCount(save);
    }

    private void drawArrows(Canvas canvas, float left, float centerY, float unit) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.7f * unit);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (int i = 0; i < 3; i++) {
            float wave = (phase - i / 3f + 1f) % 1f;
            int alpha = 65 + Math.round(190f * (1f - wave));
            paint.setColor(withAlpha(ACCENT, alpha));
            float x = left + i * 12f * unit;
            path.reset();
            path.moveTo(x, centerY - 7f * unit);
            path.lineTo(x + 6f * unit, centerY);
            path.lineTo(x, centerY + 7f * unit);
            canvas.drawPath(path, paint);
        }
    }

    private void drawLock(Canvas canvas, float x, float y, float unit, float pulse) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(ACCENT, Math.round(12 + 16 * pulse)));
        canvas.drawCircle(x, y, (16f + pulse) * unit, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.8f * unit);
        paint.setColor(ACCENT);
        bounds.set(x - 4f * unit, y - 8f * unit, x + 4f * unit, y + unit);
        canvas.drawRoundRect(bounds, 4f * unit, 4f * unit, paint);
        paint.setStyle(Paint.Style.FILL);
        bounds.set(x - 7f * unit, y - 2f * unit, x + 7f * unit, y + 8f * unit);
        canvas.drawRoundRect(bounds, 2f * unit, 2f * unit, paint);
        paint.setColor(Color.rgb(14, 23, 28));
        canvas.drawCircle(x, y + 2f * unit, 1.3f * unit, paint);
        canvas.drawRect(x - 0.6f * unit, y + 2f * unit, x + 0.6f * unit, y + 5f * unit, paint);
    }

    private void drawCheck(Canvas canvas, float x, float y, float size) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(size * 0.25f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(ACCENT);
        path.reset();
        path.moveTo(x - size * 0.7f, y);
        path.lineTo(x - size * 0.15f, y + size * 0.5f);
        path.lineTo(x + size * 0.9f, y - size * 0.65f);
        canvas.drawPath(path, paint);
    }

    private void drawFittedText(Canvas canvas, String text, float x, float baseline, float maxWidth,
                                float size, int color, boolean bold, Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextAlign(align);
        paint.setTypeface(bold ? Typeface.create(Typeface.DEFAULT, Typeface.BOLD) : Typeface.DEFAULT);
        paint.setTextSize(size);
        float measured = paint.measureText(text);
        if (measured > maxWidth && measured > 0) paint.setTextSize(size * maxWidth / measured);
        canvas.drawText(text, x, baseline, paint);
    }

    private void startFlowIfNeeded() {
        if (mode != BOOSTING || !isShown() || getWindowVisibility() != VISIBLE) return;
        if (!animationsEnabled()) {
            stopFlow();
            phase = 0.25f;
            invalidate();
            return;
        }
        if (flowAnimator != null && flowAnimator.isRunning()) return;
        flowAnimator = ValueAnimator.ofFloat(0f, 1f);
        flowAnimator.setDuration(speed >= 2.9f ? 600L : 900L);
        flowAnimator.setInterpolator(new LinearInterpolator());
        flowAnimator.setRepeatCount(ValueAnimator.INFINITE);
        flowAnimator.addUpdateListener(animation -> {
            phase = (float) animation.getAnimatedValue();
            invalidate();
        });
        flowAnimator.start();
    }

    private void stopFlow() {
        if (flowAnimator != null) {
            flowAnimator.removeAllUpdateListeners();
            flowAnimator.cancel();
            flowAnimator = null;
        }
    }

    private boolean animationsEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return ValueAnimator.areAnimatorsEnabled();
        try {
            return Settings.Global.getFloat(getContext().getContentResolver(),
                    Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f;
        } catch (RuntimeException ignored) {
            return true;
        }
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        resumeVisuals();
    }

    @Override protected void onDetachedFromWindow() {
        stopFlow();
        removeCallbacks(hideRestored);
        animate().cancel();
        resetTransform();
        super.onDetachedFromWindow();
    }

    @Override protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE || !isShown()) {
            stopFlow();
            removeCallbacks(hideRestored);
            animate().cancel();
            resetTransform();
        } else {
            resumeVisuals();
        }
    }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility != VISIBLE) {
            stopFlow();
            removeCallbacks(hideRestored);
            animate().cancel();
            resetTransform();
        } else {
            resumeVisuals();
        }
    }

    private void resumeVisuals() {
        startFlowIfNeeded();
        if (mode == RESTORED && isShown() && getWindowVisibility() == VISIBLE) {
            setAlpha(1f);
            removeCallbacks(hideRestored);
            postDelayed(hideRestored, 650L);
        }
    }

    private void resetTransform() {
        setAlpha(1f);
        setScaleX(1f);
        setScaleY(1f);
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    private static String formatSpeed(float value) {
        return Math.abs(value - Math.round(value)) < 0.01f
                ? Integer.toString(Math.round(value)) : String.format(Locale.US, "%.1f", value);
    }
}
