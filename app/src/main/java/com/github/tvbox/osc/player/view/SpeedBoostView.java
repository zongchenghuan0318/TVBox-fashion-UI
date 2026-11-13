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
import android.util.TypedValue;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.OvershootInterpolator;

import java.util.Locale;

/** 长按倍速轻盈磨砂胶囊指示器：高透明中性灰底 + 柔和白金图标，不遮挡视频主体。 */
public final class SpeedBoostView extends View {
    private static final int HIDDEN = 0;
    private static final int BOOSTING = 1;
    private static final int RESTORED = 2;

    private static final int PILL_BG = 0x6E12141A;      // ~43% 不透明深灰玻璃底，通透不挡画面
    private static final int PILL_BORDER = 0x2EFFFFFF;  // 极细 18% 柔白玻璃边
    private static final int ACCENT = 0xE6FFFFFF;       // 柔和纯白（90% 不透明）
    private static final int SUB_TEXT = 0xB8FFFFFF;     // 次级提示文字（72% 柔白）

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
        @Override
        public void run() {
            if (mode != RESTORED || getVisibility() != VISIBLE) return;
            final long token = presentation;
            if (!animationsEnabled()) {
                dismissImmediately();
                return;
            }
            animate().alpha(0f).scaleX(0.96f).scaleY(0.96f).setDuration(140L)
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
                setScaleX(0.94f);
                setScaleY(0.94f);
                animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(160L)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else if (emphasize || locked) {
                setAlpha(1f);
                setScaleX(0.97f);
                setScaleY(0.97f);
                animate().scaleX(1f).scaleY(1f).setDuration(220L)
                        .setInterpolator(new OvershootInterpolator(1.1f)).start();
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
            animate().alpha(1f).setDuration(110L)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        postDelayed(hideRestored, 450L);
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

    private float mm(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_MM, value, getResources().getDisplayMetrics());
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredWidth = Math.round(mm(152f)) + getPaddingLeft() + getPaddingRight();
        int desiredHeight = Math.round(mm(34f)) + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(resolveSize(desiredWidth, widthMeasureSpec),
                resolveSize(desiredHeight, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth() - getPaddingLeft() - getPaddingRight();
        float height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0 || mode == HIDDEN) return;
        int save = canvas.save();
        canvas.translate(getPaddingLeft(), getPaddingTop());

        float unit = Math.min(width / 152f, height / 34f);
        float radius = height / 2f;

        // 高通透中性磨砂底板
        bounds.set(0f, 0f, width, height);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PILL_BG);
        canvas.drawRoundRect(bounds, radius, radius, paint);

        float stroke = Math.max(1f, unit * 0.9f);
        bounds.inset(stroke / 2f, stroke / 2f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(PILL_BORDER);
        canvas.drawRoundRect(bounds, radius - stroke / 2f, radius - stroke / 2f, paint);

        float centerY = height / 2f;
        drawArrows(canvas, 14f * unit, centerY, unit);
        if (mode == RESTORED) {
            drawFittedText(canvas, formatSpeed(speed) + "x 已恢复",
                    52f * unit, centerY + 5f * unit, width - 64f * unit,
                    14f * unit, ACCENT, true, Paint.Align.LEFT);
        } else {
            drawFittedText(canvas, formatSpeed(speed) + "x",
                    52f * unit, centerY + 5.5f * unit, 38f * unit,
                    15.5f * unit, Color.WHITE, true, Paint.Align.LEFT);
            drawFittedText(canvas, locked ? "下滑释放" : "下滑锁定",
                    92f * unit, centerY + 4.5f * unit, width - 104f * unit,
                    12.5f * unit, SUB_TEXT, false, Paint.Align.LEFT);
        }
        canvas.restoreToCount(save);
    }

    private void drawArrows(Canvas canvas, float left, float centerY, float unit) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2.1f * unit);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (int i = 0; i < 3; i++) {
            float wave = (phase - i / 3f + 1f) % 1f;
            int alpha = 75 + Math.round(165f * (1f - wave));
            paint.setColor(withAlpha(Color.WHITE, alpha));
            float x = left + i * 9.5f * unit;
            path.reset();
            path.moveTo(x, centerY - 5f * unit);
            path.lineTo(x + 4.5f * unit, centerY);
            path.lineTo(x, centerY + 5f * unit);
            canvas.drawPath(path, paint);
        }
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

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)), Color.red(color), Color.green(color), Color.blue(color));
    }

    private static String formatSpeed(float value) {
        if (Math.abs(value - Math.round(value)) < 0.05f) {
            return String.format(Locale.US, "%d", Math.round(value));
        }
        return String.format(Locale.US, "%.1f", value);
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility != VISIBLE) stopFlow();
        else if (mode == BOOSTING) startFlowIfNeeded();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        removeCallbacks(hideRestored);
        stopFlow();
    }
}
