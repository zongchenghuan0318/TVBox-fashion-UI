package com.github.tvbox.osc.player.controller;

import android.content.Context;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;

import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * 直播控制器：
 * - 手机端上下滑动（含轻滑与慢拖）统一切换频道；左右滑动切换同一频道的直播源；
 * - 禁用垂直亮度/音量滑动拦截，保证上下滑切台 100% 灵敏不打架。
 */
public class LiveController extends BaseController {
    private static final float MIN_SWIPE_DISTANCE_PX = 72f;
    private static final float MIN_HORIZONTAL_DOMINANCE = 1.25f;
    private static final float MIN_VERTICAL_DOMINANCE = 1.2f;

    private float touchDownX;
    private float touchDownY;
    private boolean touchDownValid;
    private boolean flingHandled;

    public LiveController(@NotNull Context context) {
        super(context);
    }

    @Override
    protected int getLayoutId() {
        return R.layout.player_live_control_view;
    }

    @Override
    protected void initView() {
        super.initView();
        initClickListener();
    }

    private void initClickListener() {
        View v = findViewWithTag("pause_layout");
        if (v == null) return;
        v.setOnClickListener(view -> togglePlay());
    }

    @Override
    protected void callBackInUi(Message msg) {
    }

    public interface LiveControlListener {
        boolean singleTap();

        void longPress();

        void playStateChanged(int playState);

        void changeSource(int direction);

        void changeChannel(int direction);
    }

    private LiveControlListener listener = null;

    public void setListener(LiveControlListener listener) {
        this.listener = listener;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (listener != null && listener.singleTap())
            return true;
        return super.onSingleTapConfirmed(e);
    }

    @Override
    protected void updateSeekUI(int curr, int seekTo, long duration) {
    }

    @Override
    public void onLongPress(MotionEvent e) {
        if (listener != null) {
            listener.longPress();
        }
        super.onLongPress(e);
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        if (listener != null) {
            listener.playStateChanged(playState);
        }
    }

    @Override
    public boolean onDown(MotionEvent e) {
        touchDownX = e != null ? e.getX() : 0f;
        touchDownY = e != null ? e.getY() : 0f;
        touchDownValid = e != null;
        flingHandled = false;
        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        // 直播手势由上下滑切台 / 左右滑切源主导，不进入点播的亮度/音量/进度滑动拦截
        return true;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled = super.onTouchEvent(event);
        if (event != null && event.getAction() == MotionEvent.ACTION_UP) {
            if (!flingHandled && touchDownValid && listener != null) {
                float dx = event.getX() - touchDownX;
                float dy = event.getY() - touchDownY;
                handleLiveSwipe(dx, dy);
            }
            touchDownValid = false;
            flingHandled = false;
        } else if (event != null && event.getAction() == MotionEvent.ACTION_CANCEL) {
            touchDownValid = false;
            flingHandled = false;
        }
        return handled;
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1 == null || e2 == null || listener == null) {
            return false;
        }
        float dx = e2.getX() - e1.getX();
        float dy = e2.getY() - e1.getY();
        if (handleLiveSwipe(dx, dy)) {
            flingHandled = true;
            return true;
        }
        return false;
    }

    private boolean handleLiveSwipe(float distanceX, float distanceY) {
        if (listener == null) return false;
        // 上下滑动优先切换频道（上滑下一台，下滑上一台）
        if (Math.abs(distanceY) > MIN_SWIPE_DISTANCE_PX
                && Math.abs(distanceY) >= Math.abs(distanceX) * MIN_VERTICAL_DOMINANCE) {
            listener.changeChannel(distanceY < 0 ? 1 : -1);
            return true;
        }
        // 左右滑动切换当前频道线路（直播源）
        if (Math.abs(distanceX) > MIN_SWIPE_DISTANCE_PX
                && Math.abs(distanceX) >= Math.abs(distanceY) * MIN_HORIZONTAL_DOMINANCE) {
            listener.changeSource(distanceX < 0 ? -1 : 1);
            return true;
        }
        return false;
    }
}
