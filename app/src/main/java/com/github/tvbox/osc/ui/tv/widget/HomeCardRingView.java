package com.github.tvbox.osc.ui.tv.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * 首页卡片前半环的低调轨迹，帮助电视端看出卡片沿环形轨道运行。
 */
public class HomeCardRingView extends View {
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF ringBounds = new RectF();

    public HomeCardRingView(Context context) {
        this(context, null);
    }

    public HomeCardRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeCap(Paint.Cap.ROUND);
        ringPaint.setStrokeWidth(2f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float height = getHeight();
        if (width <= 0 || height <= 0) {
            return;
        }
        // 使用更高的椭圆纵深，避免看起来只是横向装饰线。
        float horizontalPadding = width * 0.16f;
        ringBounds.set(horizontalPadding, -height * 0.12f,
                width - horizontalPadding, height * 1.72f);

        // 完整椭圆环：后半环只作为空间轮廓，前半环更亮，卡片沿前半环运动。
        ringPaint.setStrokeWidth(4f);
        ringPaint.setShader(null);
        ringPaint.setColor(0x5036E0D0);
        canvas.drawOval(ringBounds, ringPaint);
        ringPaint.setShader(new LinearGradient(
                0, 0, width, 0,
                new int[]{0x2036E0D0, 0xF036E0D0, 0x2036E0D0},
                null, Shader.TileMode.CLAMP));
        canvas.drawArc(ringBounds, 12f, 156f, false, ringPaint);
        ringPaint.setShader(null);
        ringPaint.setStrokeWidth(3f);
        ringPaint.setColor(0x7036E0D0);
        canvas.drawArc(ringBounds, 192f, 156f, false, ringPaint);
    }
}
