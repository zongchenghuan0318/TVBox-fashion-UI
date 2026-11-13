package com.github.tvbox.osc.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 设置页使用的胶囊开关：只负责显示状态，焦点和点击由所在设置行承担。
 */
public class CapsuleSwitch extends View {
    private static final int TRACK_ON = 0xFFFFFFFF;
    private static final int TRACK_OFF = 0x38FFFFFF;
    private static final int KNOB_ON = 0xFF202329;
    private static final int KNOB_OFF = 0xD9FFFFFF;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private boolean checked;

    public CapsuleSwitch(Context context) {
        this(context, null);
    }

    public CapsuleSwitch(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean value) {
        if (checked == value) return;
        checked = value;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        setMeasuredDimension(Math.round(60 * density), Math.round(34 * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float pad = h * 0.12f;
        float stroke = Math.max(1f, density() * 1f);
        rect.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? TRACK_ON : TRACK_OFF);
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint);

        rect.inset(stroke / 2f, stroke / 2f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(stroke);
        paint.setColor(checked ? 0xFFFFFFFF : 0x52FFFFFF);
        canvas.drawRoundRect(rect, (h - stroke) / 2f, (h - stroke) / 2f, paint);

        paint.setStyle(Paint.Style.FILL);
        float knobR = h / 2f - pad;
        float cy = h / 2f;
        float cx = checked ? w - pad - knobR : pad + knobR;
        paint.setColor(checked ? KNOB_ON : KNOB_OFF);
        canvas.drawCircle(cx, cy, knobR, paint);
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }
}
