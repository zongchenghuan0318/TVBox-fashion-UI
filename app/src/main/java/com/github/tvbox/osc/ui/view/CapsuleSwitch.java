package com.github.tvbox.osc.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 设置页使用的胶囊开关：中性磨砂配色（白 / 深色），无焦点、不拦截遥控器事件，
 * 焦点由所在的设置行承担，开关仅负责显示状态。
 */
public class CapsuleSwitch extends View {
    private static final int TRACK_ON = 0xE6FFFFFF;
    private static final int TRACK_OFF = 0x33FFFFFF;
    private static final int KNOB_ON = 0xFF1C1F2B;
    private static final int KNOB_OFF = 0xCCFFFFFF;

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
        setMeasuredDimension(Math.round(56 * density), Math.round(32 * density));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float pad = h * 0.12f;
        rect.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(checked ? TRACK_ON : TRACK_OFF);
        canvas.drawRoundRect(rect, h / 2f, h / 2f, paint);

        float knobR = h / 2f - pad;
        float cy = h / 2f;
        float cx = checked ? w - pad - knobR : pad + knobR;
        paint.setColor(checked ? KNOB_ON : KNOB_OFF);
        canvas.drawCircle(cx, cy, knobR, paint);
    }
}
