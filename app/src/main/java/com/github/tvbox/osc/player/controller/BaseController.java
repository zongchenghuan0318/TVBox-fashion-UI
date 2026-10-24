package com.github.tvbox.osc.player.controller;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.ViewConfiguration;

import com.github.tvbox.osc.player.view.SpeedBoostView;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import xyz.doikki.videoplayer.controller.BaseVideoController;
import xyz.doikki.videoplayer.controller.IControlComponent;
import xyz.doikki.videoplayer.controller.IGestureComponent;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

public abstract class BaseController extends BaseVideoController implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener, View.OnTouchListener {
    protected Handler mBaseHandler;
    private GestureDetector mGestureDetector; //捕获手势事件
    private AudioManager mAudioManager;
    private boolean mIsGestureEnabled = true;
    private int mStreamVolume;
    private float mBrightness;
    private int mSeekPosition = -1;
    private long mGestureStartPosition;
    private boolean bFirstTouch;
    private boolean bChangePosition;
    private boolean bChangeBrightness;
    private boolean bChangeVolume;
    private final SpeedGestureState mSpeedGesture = new SpeedGestureState();
    private SpeedBoostView mSpeedBoostView;
    private boolean mConsumeSpeedGesture;
    private boolean mSpeedTouchMoved;
    private float mSpeedDownX;
    private float mSpeedDownY;
    private boolean bCanChangePosition = true;
    private boolean bEnableInNormal;
    private boolean bCanSlide;
    private int mCurPlayState;
    public boolean bLocked = false;
    private TextView mSlideInfoTV;
    private ProgressBar mLoadingPB;
    private ViewGroup mPauseRootView;
    private TextView mPauseTimeTV;
    private long baseDuration =100;

    public BaseController(@NonNull Context context) {
        super(context);
    }

    public BaseController(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public BaseController(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void initView() {
        super.initView();
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mGestureDetector = new GestureDetector(getContext(), this);
        setOnTouchListener(this);
        mSlideInfoTV = findViewWithTag("vod_control_slide_info");
        mSpeedBoostView = findViewWithTag("vod_control_speed_boost");
        if (mSpeedBoostView != null) {
            mSpeedBoostView.setOnClickListener(view -> cancelSpeedBoost(true));
            mSpeedBoostView.setOnTouchListener((view, event) -> handleSpeedOverlayTouch(event));
        }
        mLoadingPB = findViewWithTag("vod_control_loading");
        mPauseRootView = findViewWithTag("vod_control_pause");
        mPauseTimeTV = findViewWithTag("vod_control_pause_t");

        mBaseHandler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(@NonNull Message msg) {
                switch (msg.what) {
                    case 100: { // 亮度+音量调整
                        mSlideInfoTV.setVisibility(VISIBLE);
                        mSlideInfoTV.setText(msg.obj.toString());
                        break;
                    }
                    case 101: { // 亮度+音量调整 关闭
                        mSlideInfoTV.setVisibility(GONE);
                        break;
                    }
                    default: {
                        callBackInUi(msg);
                        break;
                    }
                }
            }
        };
    }

    @Override
    protected void setProgress(int duration, int position) {
        super.setProgress(duration, position);
        mPauseTimeTV.setText(PlayerUtils.stringForTime(position) + " / " + PlayerUtils.stringForTime(duration));
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        baseDuration = mControlWrapper.getDuration();
        if (playState == VideoView.STATE_IDLE || playState == VideoView.STATE_PREPARING
                || playState == VideoView.STATE_ERROR || playState == VideoView.STATE_PAUSED
                || playState == VideoView.STATE_START_ABORT || playState == VideoView.STATE_PLAYBACK_COMPLETED) {
            cancelSpeedBoost(false);
        }
        switch (playState) {
            case VideoView.STATE_IDLE:
                mLoadingPB.setVisibility(GONE);
                break;
            case VideoView.STATE_PLAYING:
                mPauseRootView.setVisibility(GONE);
                mLoadingPB.setVisibility(GONE);
                break;
            case VideoView.STATE_PAUSED:
                mPauseRootView.setVisibility(VISIBLE);
                mLoadingPB.setVisibility(GONE);
                break;
            case VideoView.STATE_PREPARED:
            case VideoView.STATE_ERROR:
            case VideoView.STATE_BUFFERED:
                mLoadingPB.setVisibility(GONE);
                break;
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_BUFFERING:
                mLoadingPB.setVisibility(VISIBLE);
                break;
            case VideoView.STATE_PLAYBACK_COMPLETED:
                mLoadingPB.setVisibility(GONE);
                mPauseRootView.setVisibility(GONE);
                break;
        }
    }

    /**
     * 设置是否可以滑动调节进度，默认可以
     */
    public void setCanChangePosition(boolean canChangePosition) {
        bCanChangePosition = canChangePosition;
    }

    /**
     * 是否在竖屏模式下开始手势控制，默认关闭
     */
    public void setEnableInNormal(boolean enableInNormal) {
        bEnableInNormal = enableInNormal;
    }

    /**
     * 是否开启手势控制，默认开启，关闭之后，手势调节进度，音量，亮度功能将关闭
     */
    public void setGestureEnabled(boolean gestureEnabled) {
        mIsGestureEnabled = gestureEnabled;
        if (!gestureEnabled) cancelSpeedBoost(false);
    }

    @Override
    public void setPlayerState(int playerState) {
        super.setPlayerState(playerState);
        if (playerState == VideoView.PLAYER_NORMAL) {
            bCanSlide = bEnableInNormal;
        } else if (playerState == VideoView.PLAYER_FULL_SCREEN) {
            bCanSlide = true;
        }
    }

    @Override
    public void setPlayState(int playState) {
        super.setPlayState(playState);
        mCurPlayState = playState;
    }

    protected boolean isInPlaybackState() {
        return mControlWrapper != null
                && mCurPlayState != VideoView.STATE_ERROR
                && mCurPlayState != VideoView.STATE_IDLE
                && mCurPlayState != VideoView.STATE_PREPARING
                && mCurPlayState != VideoView.STATE_PREPARED
                && mCurPlayState != VideoView.STATE_START_ABORT
                && mCurPlayState != VideoView.STATE_PLAYBACK_COMPLETED;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (handleSpeedTouch(event, false)) return true;
        boolean handled = mGestureDetector.onTouchEvent(event);
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
            finishPositionGesture(event.getActionMasked() == MotionEvent.ACTION_UP);
        }
        return handled;
    }

    private float speedHorizontalThreshold() {
        return Math.max(ViewConfiguration.get(getContext()).getScaledTouchSlop() * 3f, getWidth() * 0.035f);
    }

    private float speedVerticalThreshold() {
        return Math.max(ViewConfiguration.get(getContext()).getScaledTouchSlop() * 4f, getHeight() * 0.07f);
    }

    /** Speed locking is separate from the screen lock and never persists to the next media. */
    private boolean handleSpeedTouch(MotionEvent event, boolean overlay) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mSpeedDownX = event.getRawX();
            mSpeedDownY = event.getRawY();
            mSpeedTouchMoved = false;
            mConsumeSpeedGesture = mSpeedGesture.isActive();
            mSpeedGesture.pointerDown(mSpeedDownX, mSpeedDownY);
        }
        if (!mSpeedGesture.isActive() && !mConsumeSpeedGesture) return false;
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_CANCEL) {
            cancelSpeedBoost(false);
            cancelGestureDetector(event);
            mConsumeSpeedGesture = action != MotionEvent.ACTION_CANCEL;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE && mSpeedGesture.isActive()) {
            if (Math.abs(event.getRawX() - mSpeedDownX) > speedHorizontalThreshold() / 2
                    || Math.abs(event.getRawY() - mSpeedDownY) > speedVerticalThreshold() / 2) mSpeedTouchMoved = true;
            SpeedGestureState.Change change = mSpeedGesture.move(event.getRawX(), event.getRawY(),
                    speedHorizontalThreshold(), speedVerticalThreshold());
            if (change == SpeedGestureState.Change.UNLOCKED) {
                restoreSpeedFeedback(true);
            } else if (change != SpeedGestureState.Change.NONE) {
                mControlWrapper.setSpeed(mSpeedGesture.getSpeed());
                showSpeedFeedback(true);
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            }
        } else if (action == MotionEvent.ACTION_UP) {
            boolean wasLocked = mSpeedGesture.isLocked();
            if (mSpeedGesture.pointerUp()) restoreSpeedFeedback(true);
            else if (mSpeedGesture.isActive()) showSpeedFeedback(false);
            cancelGestureDetector(event);
            mConsumeSpeedGesture = false;
            // A tap on the picture still exposes controls while speed is locked.
            if (!overlay && wasLocked && !mSpeedTouchMoved) onSingleTapConfirmed(event);
        }
        return true;
    }

    private boolean handleSpeedOverlayTouch(MotionEvent event) {
        boolean active = mSpeedGesture.isActive();
        boolean tap = event.getActionMasked() == MotionEvent.ACTION_UP && active && !mSpeedTouchMoved;
        boolean handled = handleSpeedTouch(event, true);
        if (tap && mSpeedBoostView != null) mSpeedBoostView.performClick();
        return handled || active;
    }

    private void cancelGestureDetector(MotionEvent source) {
        MotionEvent cancel = MotionEvent.obtain(source);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        mGestureDetector.onTouchEvent(cancel);
        cancel.recycle();
    }

    /**
     * 手指按下的瞬间
     */
    @Override
    public boolean onDown(MotionEvent e) {
        if (!isInPlaybackState() //不处于播放状态
                || !mIsGestureEnabled //关闭了手势
                || PlayerUtils.isEdge(getContext(), e)) //处于屏幕边沿
        {
            return true;
        }
        mStreamVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        Activity activity = PlayerUtils.scanForActivity(getContext());
        if (activity == null) {
            mBrightness = 0;
        } else {
            mBrightness = activity.getWindow().getAttributes().screenBrightness;
        }
        bFirstTouch = true;
        bChangePosition = false;
        bChangeBrightness = false;
        bChangeVolume = false;
        mSeekPosition = -1;
        mGestureStartPosition = mControlWrapper.getCurrentPosition();
        mSpeedDownX = e.getRawX();
        mSpeedDownY = e.getRawY();
        mSpeedGesture.pointerDown(mSpeedDownX, mSpeedDownY);
        return true;
    }

    //单击， VodController类onSingleTapConfirmed()方法拦截
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (isInPlaybackState()) {
            mControlWrapper.toggleShowState();
        }
        return true;
    }

    //双击
    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (bLocked) return true;
        if (!isLocked() && isInPlaybackState()) {
            togglePlay();
        }
        return true;
    }

    // 在屏幕上滑动
    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        if (bLocked) return true;
        if (!isInPlaybackState() //不处于播放状态
                || !mIsGestureEnabled //关闭了手势
                || !bCanSlide //关闭了滑动手势
                || isLocked() //锁住了屏幕
                || PlayerUtils.isEdge(getContext(), e1)) //处于屏幕边沿
            return true;
        float deltaX = e1.getX() - e2.getX();
        float deltaY = e1.getY() - e2.getY();
        if (mSpeedGesture.isActive() || mConsumeSpeedGesture) return true;
        if (bFirstTouch) {
            bChangePosition = Math.abs(distanceX) >= Math.abs(distanceY);
            if (!bChangePosition) {
                //半屏宽度
                int halfScreen = PlayerUtils.getScreenWidth(getContext(), true) / 2;
                if (e2.getX() > halfScreen) {
                    bChangeVolume = true;
                } else {
                    bChangeBrightness = true;
                }
            }

            if (bChangePosition) {
                //根据用户设置是否可以滑动调节进度来决定最终是否可以滑动调节进度
                bChangePosition = bCanChangePosition;
            }

            if (bChangePosition || bChangeBrightness || bChangeVolume) {
                for (Map.Entry<IControlComponent, Boolean> next : mControlComponents.entrySet()) {
                    IControlComponent component = next.getKey();
                    if (component instanceof IGestureComponent) {
                        ((IGestureComponent) component).onStartSlide();
                    }
                }
            }
            bFirstTouch = false;
        }
        if (bChangePosition) {
            slideToChangePosition(deltaX);
        } else if (bChangeBrightness) {
            slideToChangeBrightness(deltaY);
        } else if (bChangeVolume) {
            slideToChangeVolume(deltaY);
        }
        return true;
    }

    protected void slideToChangePosition(float deltaX) {
        deltaX = -deltaX;
        baseDuration = mControlWrapper.getDuration();
        if (baseDuration <= 0) return;
        int currentPosition = (int) mGestureStartPosition;
        int position = (int) (deltaX * baseDuration / 5400f + currentPosition);
        if (position >= baseDuration) position = (int) baseDuration;
        if (position <= 0) position = 0;
        for (Map.Entry<IControlComponent, Boolean> next : mControlComponents.entrySet()) {
            IControlComponent component = next.getKey();
            if (component instanceof IGestureComponent) {
                ((IGestureComponent) component).onPositionChange(position, currentPosition, baseDuration);
            }
        }
        updateSeekUI(currentPosition, position, baseDuration);
        mSeekPosition = position;
    }

    protected void slideToChangeBrightness(float deltaY) {
        Activity activity = PlayerUtils.scanForActivity(getContext());
        if (activity == null) return;
        Window window = activity.getWindow();
        WindowManager.LayoutParams attributes = window.getAttributes();
        int height = getMeasuredHeight();
        if (mBrightness == -1.0f) mBrightness = 0.5f;
        float brightness = deltaY * 2 / height * 1.0f + mBrightness;
        if (brightness < 0) {
            brightness = 0f;
        }
        if (brightness > 1.0f) brightness = 1.0f;
        int percent = (int) (brightness * 100);
        attributes.screenBrightness = brightness;
        window.setAttributes(attributes);
        for (Map.Entry<IControlComponent, Boolean> next : mControlComponents.entrySet()) {
            IControlComponent component = next.getKey();
            if (component instanceof IGestureComponent) {
                ((IGestureComponent) component).onBrightnessChange(percent);
            }
        }
        Message msg = Message.obtain();
        msg.what = 100;
        msg.obj = "亮度" + percent + "%";
        mBaseHandler.sendMessage(msg);
        mBaseHandler.removeMessages(101);
        mBaseHandler.sendEmptyMessageDelayed(101, 1000);
    }

    protected void slideToChangeVolume(float deltaY) {
        int streamMaxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int height = getMeasuredHeight();
        float deltaV = deltaY * 2 / height * streamMaxVolume;
        float index = mStreamVolume + deltaV;
        if (index > streamMaxVolume) index = streamMaxVolume;
        if (index < 0) index = 0;
        int percent = (int) (index / streamMaxVolume * 100);
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) index, 0);
        for (Map.Entry<IControlComponent, Boolean> next : mControlComponents.entrySet()) {
            IControlComponent component = next.getKey();
            if (component instanceof IGestureComponent) {
                ((IGestureComponent) component).onVolumeChange(percent);
            }
        }
        Message msg = Message.obtain();
        msg.what = 100;
        msg.obj = "音量" + percent + "%";
        mBaseHandler.sendMessage(msg);
        mBaseHandler.removeMessages(101);
        mBaseHandler.sendEmptyMessageDelayed(101, 1000);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        //滑动结束时事件处理
        if (!mGestureDetector.onTouchEvent(event)) {
            int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_UP:
                    finishPositionGesture(true);
                    break;
                case MotionEvent.ACTION_CANCEL:
                    finishPositionGesture(false);
                    break;
            }
        }
        return super.onTouchEvent(event);
    }

    protected boolean isGestureSeeking() {
        return bChangePosition && mSeekPosition >= 0;
    }

    private void finishPositionGesture(boolean commit) {
        boolean wasSeeking = isGestureSeeking();
        if (wasSeeking && commit && !bLocked) {
            onSeekRequested(mSeekPosition);
            mControlWrapper.seekTo(mSeekPosition);
        }
        mSeekPosition = -1;
        bChangePosition = false;
        stopSlide();
        if (wasSeeking) onSeekPreviewFinished();
    }

    protected void onSeekPreviewFinished() {
    }

    protected void onSeekRequested(int position) {
    }

    private void stopSlide() {
        for (Map.Entry<IControlComponent, Boolean> next : mControlComponents.entrySet()) {
            IControlComponent component = next.getKey();
            if (component instanceof IGestureComponent) {
                ((IGestureComponent) component).onStopSlide();
            }
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        if (mSpeedBoostView == null || bLocked || !isInPlaybackState() || !mIsGestureEnabled
                || isLocked() || PlayerUtils.isEdge(getContext(), e)
                || !mControlWrapper.isPlaying() || mConsumeSpeedGesture) return;
        if (!mSpeedGesture.begin(getConfiguredSpeed())) return;
        mConsumeSpeedGesture = true;
        mSeekPosition = -1;
        bChangePosition = false;
        mControlWrapper.setSpeed(mSpeedGesture.getSpeed());
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        showSpeedFeedback(true);
    }

    protected float getConfiguredSpeed() {
        return 1f;
    }

    protected boolean isSpeedBoostActive() {
        return mSpeedGesture.isActive();
    }

    private void showSpeedFeedback(boolean emphasize) {
        if (mSpeedBoostView != null) {
            mSpeedBoostView.showSpeed(mSpeedGesture.getSpeed(), mSpeedGesture.isLocked(), emphasize);
        }
    }

    private void restoreSpeedFeedback(boolean feedback) {
        if (mControlWrapper != null) mControlWrapper.setSpeed(mSpeedGesture.getOriginalSpeed());
        if (mSpeedBoostView != null) {
            if (feedback) mSpeedBoostView.showRestored(mSpeedGesture.getOriginalSpeed());
            else mSpeedBoostView.dismissImmediately();
        }
    }

    public boolean cancelSpeedBoost(boolean feedback) {
        boolean active = mSpeedGesture.cancel();
        if (active) restoreSpeedFeedback(feedback);
        else if (!feedback && mSpeedBoostView != null) mSpeedBoostView.dismissImmediately();
        return active;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelSpeedBoost(false);
        super.onDetachedFromWindow();
    }

    @Override
    public void onShowPress(MotionEvent e) {
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }


    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    protected abstract void callBackInUi(Message msg);

    protected abstract void updateSeekUI(int curr, int seekTo, long duration);

}
