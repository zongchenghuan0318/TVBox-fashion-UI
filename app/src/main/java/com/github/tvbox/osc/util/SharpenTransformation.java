package com.github.tvbox.osc.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation;

/**
 * 豆瓣图床最大只提供 1080px 宽的封面，而首页全屏背景要铺满约 2358px，
 * 直接交给 ImageView 放大 2 倍多，边缘必然发虚（卡片是缩小的，所以显得比背景清楚）。
 * 这里在裁切放大到目标尺寸之后补一次轻度 USM 锐化，把放大丢掉的边缘找回来。
 * 只在全屏背景上使用，卡片保持原图不动。
 */
public class SharpenTransformation extends BitmapTransformation {

    private static final String ID = "SharpenTransformation";

    /** 锐化强度：过大时强边缘会出现白边光晕 */
    private final float amount;

    public SharpenTransformation(Context context) {
        this(context, 0.28f);
    }

    public SharpenTransformation(Context context, float amount) {
        super(context);
        this.amount = amount;
    }

    @Override
    protected Bitmap transform(BitmapPool pool, Bitmap toTransform, int outWidth, int outHeight) {
        if (toTransform == null || outWidth <= 0 || outHeight <= 0
                || toTransform.getWidth() <= 0 || toTransform.getHeight() <= 0) {
            return toTransform;
        }
        Bitmap scaled = centerCrop(pool, toTransform, outWidth, outHeight);
        if (scaled == null) {
            return toTransform;
        }
        int w = scaled.getWidth();
        int h = scaled.getHeight();
        Bitmap.Config config = Bitmap.Config.RGB_565; // 背景大图无透明通道，RGB_565 内存直降 50%，极大缓解电视 GC 停顿
        Bitmap sharpened = pool != null ? pool.get(w, h, config) : null;
        if (sharpened == null) {
            sharpened = Bitmap.createBitmap(w, h, config);
        }
        applySharpen(scaled, sharpened, w, h);
        if (pool != null) {
            pool.put(scaled); // 归还中间位图到内存池复用，杜绝频繁触发全屏 GC
        } else {
            scaled.recycle();
        }
        return sharpened;
    }

    /** 按 centerCrop 规则裁切并缩放到目标尺寸（放大时保留双线性过滤，复用 BitmapPool） */
    private Bitmap centerCrop(BitmapPool pool, Bitmap src, int outWidth, int outHeight) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        //以填满目标框为准算缩放比，再居中裁掉多余部分
        float scale = Math.max(outWidth / (float) sw, outHeight / (float) sh);
        int cropW = Math.max(1, Math.min(sw, Math.round(outWidth / scale)));
        int cropH = Math.max(1, Math.min(sh, Math.round(outHeight / scale)));
        int left = (sw - cropW) / 2;
        int top = (sh - cropH) / 2;

        Bitmap.Config config = Bitmap.Config.RGB_565;
        Bitmap out = pool != null ? pool.get(outWidth, outHeight, config) : null;
        if (out == null) {
            out = Bitmap.createBitmap(outWidth, outHeight, config);
        }
        Canvas canvas = new Canvas(out);
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(src, new Rect(left, top, left + cropW, top + cropH),
                new Rect(0, 0, outWidth, outHeight), paint);
        return out;
    }

    /**
     * 逐行做 USM 锐化：按亮度算增量，再等量加到 RGB 上，
     * 只提升明暗边缘、不产生彩色描边，也比逐通道卷积省一半计算。
     */
    private void applySharpen(Bitmap src, Bitmap dst, int w, int h) {
        int[] prev = new int[w];
        int[] cur = new int[w];
        int[] next = new int[w];
        int[] line = new int[w];

        src.getPixels(prev, 0, w, 0, 0, w, 1);
        src.getPixels(cur, 0, w, 0, 0, w, 1);
        src.getPixels(next, 0, w, 0, h > 1 ? 1 : 0, w, 1);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = cur[x];
                int up = prev[x];
                int down = next[x];
                int left = x > 0 ? cur[x - 1] : c;
                int right = x < w - 1 ? cur[x + 1] : c;
                int yc = luma(c);
                int delta = Math.round(amount * (4 * yc - luma(up) - luma(down) - luma(left) - luma(right)));
                int r = clamp(((c >> 16) & 0xFF) + delta);
                int g = clamp(((c >> 8) & 0xFF) + delta);
                int b = clamp((c & 0xFF) + delta);
                line[x] = (c & 0xFF000000) | (r << 16) | (g << 8) | b;
            }
            dst.setPixels(line, 0, w, 0, y, w, 1);
            //三行滚动复用，避免整图数组（2358x1080 的整图数组会多占 10MB）
            int[] tmp = prev;
            prev = cur;
            cur = next;
            next = tmp;
            int ny = y + 2;
            if (ny < h) {
                src.getPixels(next, 0, w, 0, ny, w, 1);
            }
        }
    }

    private static int luma(int c) {
        return (((c >> 16) & 0xFF) * 77 + ((c >> 8) & 0xFF) * 150 + (c & 0xFF) * 29) >> 8;
    }

    private static int clamp(int v) {
        if (v < 0) {
            return 0;
        }
        return v > 255 ? 255 : v;
    }

    @Override
    public String getId() {
        return ID + amount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Float.compare(((SharpenTransformation) o).amount, amount) == 0;
    }

    @Override
    public int hashCode() {
        return ID.hashCode() * 31 + Float.floatToIntBits(amount);
    }
}
