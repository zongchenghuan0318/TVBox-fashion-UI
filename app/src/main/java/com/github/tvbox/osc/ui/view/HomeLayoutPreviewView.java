package com.github.tvbox.osc.ui.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.github.tvbox.osc.util.HawkConfig;

/**
 * 首页布局示意图：用极简几何色块直观展示 4 种首页导航与画报空间关系。
 */
public class HomeLayoutPreviewView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int layoutStyle = HawkConfig.HOME_LAYOUT_DEFAULT;

    public HomeLayoutPreviewView(Context context) {
        super(context);
    }

    public HomeLayoutPreviewView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setLayoutStyle(int style) {
        this.layoutStyle = style;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float r = Math.min(w, h) * 0.08f;
        float pad = Math.min(w, h) * 0.07f;

        // 外框底板
        rect.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF101218);
        canvas.drawRoundRect(rect, r, r, paint);

        rect.inset(1f, 1f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
        paint.setColor(0x2BFFFFFF);
        canvas.drawRoundRect(rect, r, r, paint);

        paint.setStyle(Paint.Style.FILL);
        float innerLeft = pad;
        float innerTop = pad;
        float innerRight = w - pad;
        float innerBottom = h - pad;
        float innerW = innerRight - innerLeft;
        float innerH = innerBottom - innerTop;
        float cardR = r * 0.65f;

        switch (layoutStyle) {
            case HawkConfig.HOME_LAYOUT_SIDEBAR: {
                // 左侧固定导航条
                float railW = innerW * 0.22f;
                rect.set(innerLeft, innerTop, innerLeft + railW, innerBottom);
                paint.setColor(0x36FFFFFF);
                canvas.drawRoundRect(rect, cardR, cardR, paint);

                // 导航项小条
                for (int i = 0; i < 4; i++) {
                    float y = innerTop + innerH * (0.14f + i * 0.19f);
                    rect.set(innerLeft + railW * 0.16f, y, innerLeft + railW * 0.84f, y + innerH * 0.10f);
                    paint.setColor(i == 0 ? 0xFFFFFFFF : 0x66FFFFFF);
                    canvas.drawRoundRect(rect, cardR * 0.5f, cardR * 0.5f, paint);
                }

                // 右侧内容主画报 + 次卡片
                float contentLeft = innerLeft + railW + pad * 0.65f;
                float contentW = innerRight - contentLeft;
                drawPosterRow(canvas, contentLeft, innerTop, contentW, innerH, cardR);
                break;
            }
            case HawkConfig.HOME_LAYOUT_APPLE_TV: {
                // 顶部悬浮胶囊栏
                float barH = innerH * 0.20f;
                rect.set(innerLeft + innerW * 0.12f, innerTop, innerRight - innerW * 0.12f, innerTop + barH);
                paint.setColor(0x33FFFFFF);
                canvas.drawRoundRect(rect, barH / 2f, barH / 2f, paint);
                for (int i = 0; i < 4; i++) {
                    float pillW = innerW * 0.12f;
                    float px = innerLeft + innerW * 0.20f + i * (pillW + innerW * 0.04f);
                    rect.set(px, innerTop + barH * 0.28f, px + pillW, innerTop + barH * 0.72f);
                    paint.setColor(i == 0 ? 0xFFFFFFFF : 0x66FFFFFF);
                    canvas.drawRoundRect(rect, barH * 0.22f, barH * 0.22f, paint);
                }

                // 下方宽幅海报舞台
                float stageTop = innerTop + barH + pad * 0.65f;
                drawPosterRow(canvas, innerLeft, stageTop, innerW, innerBottom - stageTop, cardR);
                break;
            }
            case HawkConfig.HOME_LAYOUT_DRAWER: {
                // 半展开悬浮抽屉 + 把手线
                float drawerW = innerW * 0.16f;
                rect.set(innerLeft, innerTop + innerH * 0.06f, innerLeft + drawerW, innerBottom - innerH * 0.06f);
                paint.setColor(0x40FFFFFF);
                canvas.drawRoundRect(rect, cardR, cardR, paint);
                rect.set(innerLeft + drawerW * 0.25f, innerTop + innerH * 0.38f,
                        innerLeft + drawerW * 0.75f, innerTop + innerH * 0.62f);
                paint.setColor(0xFFFFFFFF);
                canvas.drawRoundRect(rect, cardR * 0.4f, cardR * 0.4f, paint);

                // 右侧宽大内容舞台
                float contentLeft = innerLeft + drawerW + pad * 0.6f;
                drawPosterRow(canvas, contentLeft, innerTop, innerRight - contentLeft, innerH, cardR);
                break;
            }
            case HawkConfig.HOME_LAYOUT_IMMERSIVE:
            default: {
                // 全屏沉浸画报 + 顶部极简微点提示
                rect.set(innerLeft, innerTop, innerRight, innerBottom);
                paint.setColor(0x16FFFFFF);
                canvas.drawRoundRect(rect, cardR, cardR, paint);

                float dotW = innerW * 0.18f;
                rect.set(w / 2f - dotW / 2f, innerTop + innerH * 0.06f,
                        w / 2f + dotW / 2f, innerTop + innerH * 0.11f);
                paint.setColor(0x66FFFFFF);
                canvas.drawRoundRect(rect, cardR, cardR, paint);

                drawPosterRow(canvas, innerLeft + innerW * 0.04f, innerTop + innerH * 0.14f,
                        innerW * 0.92f, innerH * 0.80f, cardR);
                break;
            }
        }
    }

    private void drawPosterRow(Canvas canvas, float left, float top, float width, float height, float r) {
        // 居中主海报 + 左右微露海报，突出电视画报感
        float mainW = width * 0.42f;
        float mainH = height * 0.86f;
        float cx = left + width / 2f;
        float cy = top + height / 2f;

        float sideW = width * 0.23f;
        float sideH = height * 0.66f;

        // 左侧卡片
        rect.set(cx - mainW / 2f - sideW * 0.78f, cy - sideH / 2f,
                cx - mainW / 2f - width * 0.03f, cy + sideH / 2f);
        paint.setColor(0x28FFFFFF);
        canvas.drawRoundRect(rect, r * 0.8f, r * 0.8f, paint);

        // 右侧卡片
        rect.set(cx + mainW / 2f + width * 0.03f, cy - sideH / 2f,
                cx + mainW / 2f + sideW * 0.78f, cy + sideH / 2f);
        paint.setColor(0x28FFFFFF);
        canvas.drawRoundRect(rect, r * 0.8f, r * 0.8f, paint);

        // 中心主焦点海报
        rect.set(cx - mainW / 2f, cy - mainH / 2f, cx + mainW / 2f, cy + mainH / 2f);
        paint.setColor(0x55FFFFFF);
        canvas.drawRoundRect(rect, r, r, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
        paint.setColor(0xD9FFFFFF);
        canvas.drawRoundRect(rect, r, r, paint);
        paint.setStyle(Paint.Style.FILL);
    }
}
