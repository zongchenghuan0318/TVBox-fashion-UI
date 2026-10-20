package com.github.tvbox.osc.player.controller;

import android.content.Context;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;

import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;

/**
 * 直播控制器
 */

public class LiveController extends BaseController {
//    protected ProgressBar mLoading;
    private int minFlingDistance = 100;             //最小识别距离
    private int minFlingVelocity = 10;              //最小识别速度
    private static final float MIN_HORIZONTAL_DOMINANCE = 1.25f;

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
//        mLoading = findViewById(R.id.loading);
        initClickListener();
    }

    private void initClickListener() {
        View v = findViewWithTag("pause_layout");
        if (v == null) return;
        v.setOnClickListener(view -> {
            togglePlay();
        });
    }

    @Override
    protected void callBackInUi(Message msg) {
        switch (msg.what) {
            case 1000: { // seek 刷新
                break;
            }
            case 1001: { // seek 关闭
                break;
            }
            case 1002: { // 显示底部菜单
                break;
            }
        }
    }

    public interface LiveControlListener {
        boolean singleTap();

        void longPress();

        void playStateChanged(int playState);

        void changeSource(int direction);
    }

    private LiveController.LiveControlListener listener = null;

    public void setListener(LiveController.LiveControlListener listener) {
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
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1 == null || e2 == null || listener == null) {
            return false;
        }
        float distanceX = e2.getX() - e1.getX();
        float distanceY = e2.getY() - e1.getY();
        if (Math.abs(distanceX) <= minFlingDistance
                || Math.abs(distanceX) < Math.abs(distanceY) * MIN_HORIZONTAL_DOMINANCE
                || Math.abs(velocityX) <= minFlingVelocity
                || Math.abs(velocityX) < Math.abs(velocityY)) {
            return false;
        }
        if (distanceX < 0) {
            listener.changeSource(-1);          //左滑
        } else {
            listener.changeSource(1);           //右滑
        }
        return false;
    }
}
