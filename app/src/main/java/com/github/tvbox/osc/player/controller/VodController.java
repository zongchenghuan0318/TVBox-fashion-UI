package com.github.tvbox.osc.player.controller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Message;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.player.thirdparty.MXPlayer;
import com.github.tvbox.osc.player.thirdparty.ReexPlayer;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.TimerMin;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PlayerUtils;

import static xyz.doikki.videoplayer.util.PlayerUtils.stringForTime;

public class VodController extends BaseController {
    private final static int MSG_WHAT_SHOW_SEEK = 1000;
    private final static int MSG_WHAT_HIDE_SEEK = 1001;
    private final static int MSG_WHAT_SEEK_PLAY = 10011;

    private SeekBar mSeekBar;
    private TextView mCurrentTimeTV;
    private TextView mTotalTimeTV;
    private boolean mIsDragging;
    private LinearLayout mProgressLayout;
    private TextView mProgressTv;
    private ImageView mProgressImg;
    private LinearLayout mBottomLayout;
    private TextView mPlayTitleTV;
    private Button mNextBtn;
    private Button mPreBtn;
    private Button mPlayerScaleBtn;
    private Button mPlayerSpeedBtn;
    private TextView mPlayerTV;
    private TextView mPlayerIJKTV;
    private TextView mPlayerRetryTv;
    private TextView mPlayerTimeStartTV;
    private TextView mPlayerTimeSkipTV;
    private TextView mPlayerTimeStepTV;
    private ImageView lockImg;
    private ImageView portHorImg;
    private Button mPlayingBtn;
    private JSONObject mJsonObject = null;
    private boolean bMxPlayerExist = false;
    private boolean bReexPlayerExist = false;
    private boolean bSimSlideStart = false;
    private int simSeekPosition = 0;
    private long simSlideOffset = 0;
    private VodControlListener controlListener;
    private boolean bSkipEnd = true;
    private TimerMin mTimerMin;
    private int mTimeSpeed = 1;
    private String mDualtion;

    public VodController(Context context) {
        super(context);
    }
    @Override
    protected int getLayoutId() {
        return R.layout.player_vod_control_view;
    }

    @Override
    protected void callBackInUi(Message msg) {
        switch (msg.what) {
            case MSG_WHAT_SHOW_SEEK: { // seek 显示
                mProgressLayout.setVisibility(VISIBLE);
                break;
            }
            case MSG_WHAT_HIDE_SEEK: { // seek 隐藏
                L.d("case 1001");
                mProgressLayout.setVisibility(GONE);
                break;
            }
            case MSG_WHAT_SEEK_PLAY: { // 遥控按键后播放
                L.d("case MSG_WHAT_SEEK_PLAY");
                slideStop();
                break;
            }
            case 1004: { // 设置速度
                if (isInPlaybackState()) {
                    try {
                        float speed = (float) mJsonObject.getDouble("sp");
                        mControlWrapper.setSpeed(speed);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    mBaseHandler.sendEmptyMessageDelayed(1004, 100);
                }
                break;
            }
        }
    }

    @Override
    protected void initView() {
        super.initView();
        L.d("initView()");
        mCurrentTimeTV = findViewById(R.id.curr_time);
        mTotalTimeTV = findViewById(R.id.total_time);
        mPlayTitleTV = findViewById(R.id.tv_info_name);
        mSeekBar = findViewById(R.id.seekBar);
        mPlayingBtn = findViewById(R.id.playingTV);
        mProgressLayout = findViewById(R.id.ll_progress_container);
        mProgressImg = findViewById(R.id.tv_progress_icon);
        mProgressTv = findViewById(R.id.tv_progress_text);
        mBottomLayout = findViewById(R.id.bottom_container);
        mPlayerRetryTv = findViewById(R.id.play_retry);
        mNextBtn = findViewById(R.id.play_next);
        mPreBtn = findViewById(R.id.play_pre);
        mPlayerScaleBtn = findViewById(R.id.play_scale);
        mPlayerSpeedBtn = findViewById(R.id.play_speed);
        mPlayerTV = findViewById(R.id.play_player);
        mPlayerIJKTV = findViewById(R.id.play_ijk);
        mPlayerTimeStartTV = findViewById(R.id.play_time_start);
        mPlayerTimeSkipTV = findViewById(R.id.play_time_end);
        mPlayerTimeStepTV = findViewById(R.id.play_time_step);
        lockImg = findViewById(R.id.lock_img);
        portHorImg = findViewById(R.id.port_img);

        initClickListener();

        mTimerMin = new TimerMin();
        mTimerMin.startTimer(() -> {
            L.d("callback() mTimerMin=" + mTimerMin);
            mActivity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    hideBottom();
                    hideLockImg();
                }
            });
        }, 8 * 1000);
    }

    private void initClickListener() {
        findViewWithTag("pause_layout").setOnClickListener(view -> {
            if (bLocked) return;
            togglePlay();
        });

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (bLocked) return;
                if (!fromUser) return;
                long duration = mControlWrapper.getDuration();
                long newPosition = (duration * progress) / seekBar.getMax();
                if (mCurrentTimeTV != null) {
                    mCurrentTimeTV.setText(stringForTime((int) newPosition));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if(bLocked) return;
                mIsDragging = true;
                mControlWrapper.stopProgress();
                mControlWrapper.stopFadeOut();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if(bLocked) return;
                long duration = mControlWrapper.getDuration();
                long newPosition = (duration * seekBar.getProgress()) / seekBar.getMax();
                mControlWrapper.seekTo((int) newPosition);
                mIsDragging = false;
                mControlWrapper.startProgress();
                mControlWrapper.startFadeOut();
            }
        });
        mPlayerRetryTv.setOnClickListener(v -> {
            if(bLocked) return;
            controlListener.replay();
            hideBottom();
        });
        mNextBtn.setOnClickListener(view -> {
            if(bLocked) return;
            controlListener.playNext(false);
            hideBottom();
        });
        mPreBtn.setOnClickListener(view -> {
            if(bLocked) return;
            controlListener.playPre();
            hideBottom();
        });
        mPlayerScaleBtn.setOnClickListener(view -> {
            if(bLocked) return;
            try {
                int scaleType = mJsonObject.getInt("sc");
                scaleType++;
                if (scaleType > 5) {
                    scaleType = 0;
                }
                mJsonObject.put("sc", scaleType);
                updatePlayerCfgView();
                controlListener.updatePlayerCfg();
                mControlWrapper.setScreenScaleType(scaleType);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mPlayerSpeedBtn.setOnClickListener(view -> {
            if(bLocked) return;
            try {
                float speed = (float) mJsonObject.getDouble("sp");
                speed += 0.25f;
                if (speed > 3) {
                    speed = 0.5f;
                }
                mJsonObject.put("sp", speed);
                updatePlayerCfgView();
                controlListener.updatePlayerCfg();
                mControlWrapper.setSpeed(speed);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mPlayerTV.setOnClickListener(view -> {
            if(bLocked) return;
            try {
                int playerType = mJsonObject.getInt("pl");
                boolean playerVail = false;
                do {
                    playerType++;
                    if (playerType <= 2) {
                        playerVail = true;
                    } else if (playerType == 10) {
                        playerVail = bMxPlayerExist;
                    } else if (playerType == 11) {
                        playerVail = bReexPlayerExist;
                    } else if (playerType > 11) {
                        playerType = 0;
                        playerVail = true;
                    }
                } while (!playerVail);
                mJsonObject.put("pl", playerType);
                updatePlayerCfgView();
                controlListener.updatePlayerCfg();
                controlListener.replay();
                // hideBottom();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mPlayerIJKTV.setOnClickListener(view -> {
            if(bLocked) return;
            try {
                String ijk = mJsonObject.getString("ijk");
                List<IJKCode> codecs = ApiConfig.getInstance().getmIjkCodes();
                for (int i = 0; i < codecs.size(); i++) {
                    if (ijk.equals(codecs.get(i).getName())) {
                        if (i >= codecs.size() - 1)
                            ijk = codecs.get(0).getName();
                        else {
                            ijk = codecs.get(i + 1).getName();
                        }
                        break;
                    }
                }
                mJsonObject.put("ijk", ijk);
                updatePlayerCfgView();
                controlListener.updatePlayerCfg();
                controlListener.replay();
                hideBottom();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mPlayerTimeStartTV.setOnClickListener(view -> {
            if(bLocked) return;
            try {
                int step = Hawk.get(HawkConfig.PLAY_TIME_STEP, 5);
                int st = mJsonObject.getInt("st");
                st += step;
                if (st > 60 * 10) {
                    st = 0;
                }
                mJsonObject.put("st", st);
                updatePlayerCfgView();
                controlListener.updatePlayerCfg();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mPlayerTimeSkipTV.setOnClickListener(view -> {
            if(bLocked) return;
            try {
                int step = Hawk.get(HawkConfig.PLAY_TIME_STEP, 5);
                int et = mJsonObject.getInt("et");
                et += step;
                if (et > 60 * 10) {
                    et = 0;
                }
                mJsonObject.put("et", et);
                updatePlayerCfgView();
                controlListener.updatePlayerCfg();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        });
        mPlayerTimeStepTV.setOnClickListener(view -> {
            if(bLocked) return;
            int step = Hawk.get(HawkConfig.PLAY_TIME_STEP, 5);
            step += 5;
            if (step > 30) {
                step = 5;
            }
            Hawk.put(HawkConfig.PLAY_TIME_STEP, step);
            updatePlayerCfgView();
        });
        mPlayingBtn.setText("暂停");
        mPlayingBtn.setOnClickListener(view -> {
            if(bLocked) return;
            if (!mControlWrapper.isPlaying()) {
                mPlayingBtn.setText("暂停");
            }else{
                mPlayingBtn.setText("播放");
            }
            togglePlay();
        });
        lockImg.setOnClickListener(view -> {
            bLocked = !bLocked;
            //setLocked(bLocked);
            if (bLocked) {
                lockImg.setImageResource(R.drawable.icon_locked);
            } else {
                lockImg.setImageResource(R.drawable.icon_unlock);
            }
        });

        portHorImg.setOnClickListener(v -> {
            if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            } else if (mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                mActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            }
        });
    }

    public void setPlayerConfig(JSONObject playerCfg) {
        this.mJsonObject = playerCfg;
        updatePlayerCfgView();
        bMxPlayerExist = MXPlayer.getPackageInfo() != null;
        bReexPlayerExist = ReexPlayer.getPackageInfo() != null;
    }

    @SuppressLint("SetTextI18n")
    void updatePlayerCfgView() {
        try {
            int playerType = mJsonObject.getInt("pl");
            mPlayerTV.setText(PlayerHelper.getPlayerName(playerType));
            mPlayerScaleBtn.setText(PlayerHelper.getScaleName(mJsonObject.getInt("sc")));
            mPlayerIJKTV.setText(mJsonObject.getString("ijk"));
            mPlayerIJKTV.setVisibility(playerType == 1 ? GONE : GONE);
            mPlayerScaleBtn.setText(PlayerHelper.getScaleName(mJsonObject.getInt("sc")));
            mPlayerSpeedBtn.setText("x" + mJsonObject.getDouble("sp"));
            mPlayerTimeStartTV.setText(PlayerUtils.stringForTime(mJsonObject.getInt("st") * 1000));
            mPlayerTimeSkipTV.setText(PlayerUtils.stringForTime(mJsonObject.getInt("et") * 1000));
            mPlayerTimeStepTV.setText(Hawk.get(HawkConfig.PLAY_TIME_STEP, 5) + "s");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setTitle(String playTitleInfo) {
        mPlayTitleTV.setText(playTitleInfo);
    }

    public void resetSpeed() {
        bSkipEnd = true;
        mBaseHandler.removeMessages(1004);
        mBaseHandler.sendEmptyMessageDelayed(1004, 100);
    }

    public void setControlListener(VodControlListener controlListener) {
        this.controlListener = controlListener;
    }

    @Override
    protected void setProgress(int duration, int position) {
        if (mIsDragging) {
            return;
        }
        super.setProgress(duration, position);
        if (bSkipEnd && position != 0 && duration != 0) {
            int et = 0;
            try {
                et = mJsonObject.getInt("et");
            } catch (JSONException e) {
                e.printStackTrace();
            }
            if (et > 0 && position + (et * 1000) >= duration) {
                bSkipEnd = false;
                controlListener.playNext(true);
            }
        }
        mCurrentTimeTV.setText(PlayerUtils.stringForTime(position));
        mTotalTimeTV.setText(PlayerUtils.stringForTime(duration));
        if (duration > 0) {
            mSeekBar.setEnabled(true);
            int pos = (int) (position * 1.0 / duration * mSeekBar.getMax());
            mSeekBar.setProgress(pos);
        } else {
            mSeekBar.setEnabled(false);
        }
        int percent = mControlWrapper.getBufferedPercentage();
        if (percent >= 95) {
            mSeekBar.setSecondaryProgress(mSeekBar.getMax());
        } else {
            mSeekBar.setSecondaryProgress(percent * 10);
        }
    }

    public void slideStop() {
        if (!bSimSlideStart) return;
        mControlWrapper.seekTo(simSeekPosition);
        if (!mControlWrapper.isPlaying()) {
            mControlWrapper.start();
        }
        bSimSlideStart = false;
        simSeekPosition = 0;
        simSlideOffset = 0;
    }

    public void slideStart(int iFoward) {
        long duration = mControlWrapper.getDuration();
        if (duration <= 0) return;
        if (!bSimSlideStart) {
            bSimSlideStart = true;
        }
        // 快进算法
        simSlideOffset += (10000.0f * (mTimeSpeed++ % 10) * iFoward);
        int currentPosition = (int) mControlWrapper.getCurrentPosition();
        int position = (int) (simSlideOffset + currentPosition);
        if (position > duration) position = (int) duration;
        if (position < 0) position = 0;
        updateSeekUI(currentPosition, position, duration);
        simSeekPosition = position;
    }

    @Override
    protected void updateSeekUI(int curr, int seekTo, long duration) {
        if (seekTo > curr) {
            mProgressImg.setImageResource(R.drawable.icon_pre);
        } else {
            mProgressImg.setImageResource(R.drawable.icon_back);
        }
        mProgressTv.setText(PlayerUtils.stringForTime(seekTo) + " / " + mDualtion);
        mBaseHandler.sendEmptyMessage(MSG_WHAT_SHOW_SEEK);
        mBaseHandler.removeMessages(MSG_WHAT_HIDE_SEEK);
        mBaseHandler.sendEmptyMessageDelayed(MSG_WHAT_HIDE_SEEK, 1000);
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        int duration = (int) mControlWrapper.getDuration();
        mDualtion = PlayerUtils.stringForTime(duration);
        switch (playState) {
            case VideoView.STATE_IDLE:
                break;
            case VideoView.STATE_PLAYING:
                startProgress();
                break;
            case VideoView.STATE_PAUSED:
                break;
            case VideoView.STATE_ERROR:
                controlListener.errReplay();
                break;
            case VideoView.STATE_PREPARED:
            case VideoView.STATE_BUFFERED:
                break;
            case VideoView.STATE_PREPARING:
            case VideoView.STATE_BUFFERING:
                break;
            case VideoView.STATE_PLAYBACK_COMPLETED:
                controlListener.playNext(true);
                break;
        }
    }

    boolean isBottomVisible() {
        return mBottomLayout.getVisibility() == VISIBLE;
    }

    boolean isLockVisible() {
        return findViewById(R.id.img_layout).getVisibility() == VISIBLE;
    }

    void showBottom() {
        if (!bLocked) {
            //不锁定时，才显示底部
            mBottomLayout.setVisibility(VISIBLE);
        }
    }

    void hideBottom() {
        mBottomLayout.setVisibility(GONE);
    }

    public void hideLockImg() {
        findViewById(R.id.img_layout).setVisibility(GONE);
    }

    public void showLockImg() {
        findViewById(R.id.img_layout).setVisibility(VISIBLE);
    }

    public boolean dealKeyEvent(KeyEvent event) {
        boolean isInPlayback = isInPlaybackState();
        int keyCode = event.getKeyCode();
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isInPlayback) {
                    slideStart(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (isInPlayback) {
                    togglePlay();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (!bLocked) {
                    controlListener.playPre();
                    hideBottom();
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!bLocked) {
                    controlListener.playNext(false);
                    hideBottom();
                }
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                mTimeSpeed = 1;
                if (isInPlayback) {
                    mBaseHandler.removeMessages(MSG_WHAT_SEEK_PLAY);
                    mBaseHandler.sendEmptyMessageDelayed(MSG_WHAT_SEEK_PLAY, 1000);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    /**
     * 手指点击
     * onSingleTapConfirmed()覆盖父类方法
     */
    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (!isBottomVisible()) {
            showBottom();
        } else {
            hideBottom();
        }
        if (bLocked) {
            //锁定，则出现提示
            showLockImg();
        } else {
            if (isLockVisible()) {
                hideLockImg();
            } else {
                showLockImg();
            }
        }
        return super.onSingleTapConfirmed(e);
    }

    public boolean lockWarning() {
        if (bLocked) {
            showLockImg();
            return true;
        }
        return false;
    }

    public void releaseController() {
        L.d("releaseController() mTimerMin=" + mTimerMin);
        if (mTimerMin != null) {
            mTimerMin.cancelTimer();
            mTimerMin = null;
        }
    }

    public interface VodControlListener {
        void playNext(boolean rmProgress);
        void playPre();
        void changeParse(ParseBean pb);
        void updatePlayerCfg();
        void replay();
        void errReplay();
    }

}
