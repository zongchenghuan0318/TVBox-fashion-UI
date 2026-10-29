package com.github.tvbox.osc.ui.view;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.studio.osc.R;

/** Small, focusable undo prompt for reversible list deletions. */
public final class UndoBar {
    private static final long DURATION_MS = 8000L;

    private final Activity activity;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private View bar;
    private View previousFocus;
    private View fallbackFocus;
    private Runnable undoAction;
    private final Runnable dismissRunnable = () -> dismiss(true);

    public UndoBar(Activity activity) {
        this.activity = activity;
    }

    public void show(String message, Runnable undoAction, View fallbackFocus) {
        dismiss(false);
        if (activity.isFinishing() || activity.isDestroyed()) return;
        View decor = activity.getWindow().getDecorView();
        if (!(decor instanceof ViewGroup)) return;
        this.previousFocus = activity.getCurrentFocus();
        this.fallbackFocus = fallbackFocus;
        this.undoAction = undoAction;

        LinearLayout container = new LinearLayout(activity);
        container.setGravity(Gravity.CENTER_VERTICAL);
        container.setOrientation(LinearLayout.HORIZONTAL);
        container.setPadding(dp(18), dp(8), dp(10), dp(8));
        container.setBackground(background());
        container.setElevation(dp(8));

        TextView label = new TextView(activity);
        label.setText(message);
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setMaxLines(2);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        labelParams.rightMargin = dp(12);
        container.addView(label, labelParams);

        TextView undo = new TextView(activity);
        undo.setText("撤销");
        undo.setTextColor(Color.WHITE);
        undo.setTextSize(16);
        undo.setGravity(Gravity.CENTER);
        undo.setMinWidth(dp(72));
        undo.setMinHeight(dp(48));
        undo.setPadding(dp(12), 0, dp(12), 0);
        undo.setBackgroundResource(R.drawable.selector_player_control_button);
        undo.setFocusable(true);
        undo.setFocusableInTouchMode(true);
        undo.setClickable(true);
        undo.setContentDescription("撤销删除");
        undo.setOnClickListener(v -> {
            Runnable action = this.undoAction;
            if (action != null) action.run();
            dismiss(true);
        });
        container.addView(undo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                Math.min(dp(520), activity.getResources().getDisplayMetrics().widthPixels - dp(32)),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.bottomMargin = dp(36);
        ((ViewGroup) decor).addView(container, params);
        bar = container;
        container.setAlpha(0f);
        container.setTranslationY(dp(12));
        container.animate().alpha(1f).translationY(0f).setDuration(180L).start();
        undo.requestFocus();
        handler.removeCallbacks(dismissRunnable);
        handler.postDelayed(dismissRunnable, DURATION_MS);
    }

    public void dismiss(boolean restoreFocus) {
        handler.removeCallbacks(dismissRunnable);
        View oldBar = bar;
        bar = null;
        undoAction = null;
        if (oldBar != null && oldBar.getParent() instanceof ViewGroup) {
            ViewGroup parent = (ViewGroup) oldBar.getParent();
            parent.removeView(oldBar);
        }
        if (restoreFocus && !activity.isFinishing() && !activity.isDestroyed()) {
            if (isFocusableAttached(previousFocus)) {
                previousFocus.requestFocus();
            } else if (isFocusableAttached(fallbackFocus)) {
                fallbackFocus.requestFocus();
            }
        }
        previousFocus = null;
        fallbackFocus = null;
    }

    private boolean isFocusableAttached(View view) {
        return view != null && view.isAttachedToWindow() && view.getVisibility() == View.VISIBLE && view.isFocusable();
    }

    private GradientDrawable background() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.argb(242, 20, 22, 30));
        drawable.setCornerRadius(dp(12));
        drawable.setStroke(dp(1), Color.argb(40, 255, 255, 255));
        return drawable;
    }

    private int dp(float value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
