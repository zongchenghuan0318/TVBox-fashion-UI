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
        if (listener.singleTap())
            return true;
        return super.onSingleTapConfirmed(e);
    }

    @Override
    protected void updateSeekUI(int curr, int seekTo, long duration) {
    }

    @Override
    public void onLongPress(MotionEvent e) {
        listener.longPress();
        super.onLongPress(e);
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        listener.playStateChanged(playState);
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        if (e1.getX() - e2.getX() > minFlingDistance && Math.abs(velocityX) > minFlingVelocity) {
            listener.changeSource(-1);          //左滑
        } else if (e2.getX() - e1.getX() > minFlingDistance && Math.abs(velocityX) > minFlingVelocity) {
            listener.changeSource(1);           //右滑
        } else if (e1.getY() - e2.getY() > minFlingDistance && Math.abs(velocityY) > minFlingVelocity) {
        } else if (e2.getY() - e1.getY() > minFlingDistance && Math.abs(velocityY) > minFlingVelocity) {
        }
        return false;
    }
}
