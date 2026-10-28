package com.github.tvbox.osc.util;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOverlay;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

public final class SeniorMode {
    private static final float TEXT_SCALE = 1.2f;  // 降低文字缩放比例从1.3到1.2
    private static final float FOCUS_SCALE = 1.04f;

    private SeniorMode() {
    }

    public static boolean isEnabled() {
        return Hawk.get(HawkConfig.SENIOR_MODE, false);
    }

    public static int getGridColumnCount(int regularCount) {
        return isEnabled() ? Math.max(1, Math.round(regularCount / TEXT_SCALE)) : regularCount;
    }

    public static void apply(View root) {
        if (root == null) {
            return;
        }

        if (isEnabled()) {
            installObservers(root);
            applyToTree(root, true);
            updateFocus(root.findFocus(), true);
        } else if (root.getTag(R.id.senior_mode_layout_listener) != null
                || root.getTag(R.id.senior_mode_focus_listener) != null) {
            removeObservers(root);
            applyToTree(root, false);
        }
    }

    private static void installObservers(final View root) {
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (root.getTag(R.id.senior_mode_layout_listener) == null) {
            ViewTreeObserver.OnGlobalLayoutListener layoutListener =
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        private boolean isApplying = false;

                        @Override
                        public void onGlobalLayout() {
                            if (isApplying) return;
                            if (!isEnabled()) {
                                apply(root);
                                return;
                            }
                            try {
                                isApplying = true;
                                applyToTree(root, true);
                                updateFocus(root.findFocus(), true);
                            } finally {
                                isApplying = false;
                            }
                        }
                    };
            root.setTag(R.id.senior_mode_layout_listener, layoutListener);
            observer.addOnGlobalLayoutListener(layoutListener);
        }

        if (root.getTag(R.id.senior_mode_focus_listener) == null) {
            ViewTreeObserver.OnGlobalFocusChangeListener focusListener =
                    new ViewTreeObserver.OnGlobalFocusChangeListener() {
                        @Override
                        public void onGlobalFocusChanged(View oldFocus, View newFocus) {
                            updateFocus(oldFocus, false);
                            updateFocus(newFocus, isEnabled());
                        }
                    };
            root.setTag(R.id.senior_mode_focus_listener, focusListener);
            observer.addOnGlobalFocusChangeListener(focusListener);
        }
    }

    private static void removeObservers(View root) {
        ViewTreeObserver observer = root.getViewTreeObserver();
        Object layoutListener = root.getTag(R.id.senior_mode_layout_listener);
        if (observer.isAlive() && layoutListener instanceof ViewTreeObserver.OnGlobalLayoutListener) {
            observer.removeOnGlobalLayoutListener(
                    (ViewTreeObserver.OnGlobalLayoutListener) layoutListener);
        }
        Object focusListener = root.getTag(R.id.senior_mode_focus_listener);
        if (observer.isAlive()
                && focusListener instanceof ViewTreeObserver.OnGlobalFocusChangeListener) {
            observer.removeOnGlobalFocusChangeListener(
                    (ViewTreeObserver.OnGlobalFocusChangeListener) focusListener);
        }
        root.setTag(R.id.senior_mode_layout_listener, null);
        root.setTag(R.id.senior_mode_focus_listener, null);
    }

    private static void applyToTree(View view, boolean enabled) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            Object originalSize = textView.getTag(R.id.senior_mode_original_text_size);
            if (originalSize == null) {
                originalSize = textView.getTextSize();
                textView.setTag(R.id.senior_mode_original_text_size, originalSize);
            }
            float targetSize = (Float) originalSize * (enabled ? TEXT_SCALE : 1f);
            if (Math.abs(textView.getTextSize() - targetSize) > 0.5f) {
                textView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, targetSize);
            }
        }

        if (!enabled) {
            updateFocus(view, false);
        }

        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyToTree(group.getChildAt(i), enabled);
            }
        }
    }

    private static void updateFocus(View view, boolean focused) {
        if (view == null) {
            return;
        }

        Object existing = view.getTag(R.id.senior_mode_focus_overlay);
        if (focused && !view.isFocusable() && !view.isClickable()) {
            return;
        }
        if (focused && existing == null) {
            FocusOutline outline = new FocusOutline(view.getResources().getDisplayMetrics().density);
            outline.setBounds(2, 2, Math.max(2, view.getWidth() - 2),
                    Math.max(2, view.getHeight() - 2));
            view.setTag(R.id.senior_mode_focus_overlay, outline);
            view.setTag(R.id.senior_mode_original_scale_x, view.getScaleX());
            view.setTag(R.id.senior_mode_original_scale_y, view.getScaleY());
            ViewOverlay overlay = view.getOverlay();
            overlay.add(outline);
            view.setScaleX(view.getScaleX() * FOCUS_SCALE);
            view.setScaleY(view.getScaleY() * FOCUS_SCALE);
        } else if (focused && existing instanceof FocusOutline) {
            FocusOutline outline = (FocusOutline) existing;
            outline.setBounds(2, 2, Math.max(2, view.getWidth() - 2),
                    Math.max(2, view.getHeight() - 2));
        } else if (!focused && existing instanceof Drawable) {
            view.getOverlay().remove((Drawable) existing);
            view.setTag(R.id.senior_mode_focus_overlay, null);
            Object originalScaleX = view.getTag(R.id.senior_mode_original_scale_x);
            Object originalScaleY = view.getTag(R.id.senior_mode_original_scale_y);
            if (originalScaleX instanceof Float && originalScaleY instanceof Float) {
                view.setScaleX((Float) originalScaleX);
                view.setScaleY((Float) originalScaleY);
            }
            view.setTag(R.id.senior_mode_original_scale_x, null);
            view.setTag(R.id.senior_mode_original_scale_y, null);
        }
    }

    private static final class FocusOutline extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float density;

        FocusOutline(float density) {
            this.density = density;
            paint.setStyle(Paint.Style.STROKE);
        }

        @Override
        public void draw(Canvas canvas) {
            RectF bounds = new RectF(getBounds());
            float radius = 8f * density;
            paint.setColor(Color.BLACK);
            paint.setStrokeWidth(7f * density);
            canvas.drawRoundRect(bounds, radius, radius, paint);
            paint.setColor(0xFFFFD54F);
            paint.setStrokeWidth(3f * density);
            canvas.drawRoundRect(bounds, radius, radius, paint);
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(android.graphics.ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
