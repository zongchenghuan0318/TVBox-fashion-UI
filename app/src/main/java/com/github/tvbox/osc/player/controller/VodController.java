package com.github.tvbox.osc.player.controller;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Message;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.VodInfo;
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
import java.util.ArrayList;

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
    private ImageButton mEpisodeBtn;
    private boolean mIsDragging;
    private LinearLayout mProgressLayout;
    private TextView mProgressTv;
    private ImageView mProgressImg;
    private LinearLayout mBottomLayout;
    private TextView mPlayTitleTV;
    private ImageButton mNextBtn;
    private ImageButton mPreBtn;
    private ImageButton mPlayerScaleBtn;
    private Button mPlayerSpeedBtn;
    private TextView mPlayerTV;
    private TextView mPlayerIJKTV;
    private TextView mPlayerRetryTv;
    private TextView mPlayerTimeStartTV;
    private TextView mPlayerTimeSkipTV;
    private TextView mPlayerTimeStepTV;
    private ImageView lockImg;
    private ImageView portHorImg;
    private ImageButton mPlayingBtn;
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
    private int mSeekSpeedMultiplier = 1;
    private boolean mDownLongHandled;
    private boolean mDownNextPending;
    private final Runnable mDownNextRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mDownLongHandled && !bLocked && mDownNextPending) {
                mDownNextPending = false;
                controlListener.playNext(false);
                hideBottom();
            }
        }
    };
    private String mDualtion = "00:00";
    private List<VodInfo.VodSeries> mEpisodes = new ArrayList<>();
    private int mCurrentEpisode;

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
        mEpisodeBtn = findViewById(R.id.play_episode);
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
        mEpisodeBtn.setOnClickListener(view -> showEpisodeSelector());
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
                if (!mIsDragging) return;
                //先恢复刷新，再处理跳转：中途被锁定也不能让进度条停在原地
                mIsDragging = false;
                mControlWrapper.startProgress();
                mControlWrapper.startFadeOut();
                if (bLocked) return;
                long duration = mControlWrapper.getDuration();
                if (duration <= 0) return;
                long newPosition = (duration * seekBar.getProgress()) / seekBar.getMax();
                mControlWrapper.seekTo((int) newPosition);
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
                float[] speeds = {0.5f, 1.0f, 1.5f, 2.0f, 3.0f};
                int next = 0;
                for (int i = 0; i < speeds.length; i++) {
                    if (Math.abs(speeds[i] - speed) < 0.01f) {
                        next = (i + 1) % speeds.length;
                        break;
                    }
                }
                speed = speeds[next];
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
        mPlayingBtn.setImageResource(R.drawable.ic_player_pause);
        mPlayingBtn.setOnClickListener(view -> {
            if(bLocked) return;
            if (!mControlWrapper.isPlaying()) {
                mPlayingBtn.setImageResource(R.drawable.vod_pause);
            }else{
                mPlayingBtn.setImageResource(R.drawable.vod_play);
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
            mPlayerScaleBtn.setContentDescription("画面比例 " + PlayerHelper.getScaleName(mJsonObject.getInt("sc")));
            mPlayerIJKTV.setText(mJsonObject.getString("ijk"));
            mPlayerIJKTV.setVisibility(playerType == 1 ? GONE : GONE);
            mPlayerScaleBtn.setContentDescription("画面比例 " + PlayerHelper.getScaleName(mJsonObject.getInt("sc")));
            String speedText = String.format(java.util.Locale.US, "x%.1f", mJsonObject.getDouble("sp"));
            mPlayerSpeedBtn.setText(speedText);
            mPlayerSpeedBtn.setContentDescription("播放速度 " + speedText);
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

    @Override
    protected float getConfiguredSpeed() {
        if (mJsonObject == null) {
            return 1f;
        }
        try {
            return (float) mJsonObject.getDouble("sp");
        } catch (JSONException e) {
            return 1f;
        }
    }

    public void setControlListener(VodControlListener controlListener) {
        this.controlListener = controlListener;
    }

    public void setEpisodes(List<VodInfo.VodSeries> episodes, int currentEpisode) {
        mEpisodes = episodes == null ? new ArrayList<>() : episodes;
        mCurrentEpisode = Math.max(0, Math.min(currentEpisode, Math.max(0, mEpisodes.size() - 1)));
        if (mEpisodes.isEmpty()) {
            mEpisodeBtn.setVisibility(GONE);
        } else {
            mEpisodeBtn.setVisibility(VISIBLE);
            updateEpisodeButton();
        }
    }

    public void setCurrentEpisode(int currentEpisode) {
        mCurrentEpisode = Math.max(0, Math.min(currentEpisode, Math.max(0, mEpisodes.size() - 1)));
        updateEpisodeButton();
    }

    private void updateEpisodeButton() {
        if (mEpisodeBtn != null) {
            mEpisodeBtn.setContentDescription(mEpisodes.isEmpty()
                    ? "选集"
                    : "选集 " + (mCurrentEpisode + 1) + "/" + mEpisodes.size());
        }
    }

    public void showEpisodeSelector() {
        if (bLocked || mEpisodes.isEmpty() || mActivity == null) {
            return;
        }
        Dialog dialog = new Dialog(mActivity);
        LinearLayout panel = new LinearLayout(mActivity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(24, 28, 24, 20);
        panel.setBackgroundResource(R.drawable.shape_player_episode_panel);

        LinearLayout header = new LinearLayout(mActivity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(mActivity);
        title.setText("选集");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView count = new TextView(mActivity);
        count.setText((mCurrentEpisode + 1) + " / " + mEpisodes.size());
        count.setTextColor(Color.rgb(2, 248, 225));
        count.setTextSize(16);
        header.addView(count, new LinearLayout.LayoutParams(-2, -2));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        TextView hint = new TextView(mActivity);
        hint.setText("方向键选择 · 确认播放 · 返回关闭");
        hint.setTextColor(Color.argb(170, 255, 255, 255));
        hint.setTextSize(13);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(-1, -2);
        hintParams.topMargin = 8;
        hintParams.bottomMargin = 16;
        panel.addView(hint, hintParams);

        ScrollView scrollView = new ScrollView(mActivity);
        GridLayout grid = new GridLayout(mActivity);
        grid.setColumnCount(4);
        grid.setUseDefaultMargins(false);
        for (int i = 0; i < mEpisodes.size(); i++) {
            final int episodeIndex = i;
            Button episodeButton = new Button(mActivity);
            String name = mEpisodes.get(i).name;
            episodeButton.setText(name == null || name.length() == 0 ? "第" + (i + 1) + "集" : name);
            episodeButton.setTextColor(Color.WHITE);
            episodeButton.setTextSize(16);
            episodeButton.setSingleLine(true);
            episodeButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
            episodeButton.setAllCaps(false);
            episodeButton.setGravity(Gravity.CENTER);
            episodeButton.setPadding(4, 0, 4, 0);
            episodeButton.setFocusable(true);
            episodeButton.setBackgroundResource(R.drawable.selector_player_episode_item);
            if (i == mCurrentEpisode) {
                episodeButton.setTextColor(Color.rgb(2, 248, 225));
            }
            episodeButton.setOnClickListener(view -> {
                if (episodeIndex != mCurrentEpisode) {
                    mCurrentEpisode = episodeIndex;
                    updateEpisodeButton();
                    controlListener.playEpisode(episodeIndex);
                }
                dialog.dismiss();
            });
            GridLayout.LayoutParams itemParams = new GridLayout.LayoutParams();
            itemParams.width = 0;
            itemParams.height = 52;
            itemParams.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
            itemParams.setMargins(5, 5, 5, 5);
            grid.addView(episodeButton, itemParams);
            if (i == mCurrentEpisode) {
                episodeButton.post(episodeButton::requestFocus);
            }
        }
        scrollView.addView(grid, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scrollView, new LinearLayout.LayoutParams(-1, 0, 1));
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        WindowManager.LayoutParams windowParams = window.getAttributes();
        windowParams.width = (int) (mActivity.getResources().getDisplayMetrics().widthPixels * 0.43f);
        windowParams.height = (int) (mActivity.getResources().getDisplayMetrics().heightPixels * 0.72f);
        windowParams.gravity = Gravity.RIGHT | Gravity.CENTER_VERTICAL;
        windowParams.dimAmount = 0.58f;
        window.setAttributes(windowParams);
        dialog.show();
        window.setLayout(windowParams.width, windowParams.height);
    }

    @Override
    protected void setProgress(int duration, int position) {
        //拖动进度条或遥控快进时，播放位置不要回头覆盖进度条
        if (mIsDragging || bSimSlideStart) {
            return;
        }
        super.setProgress(duration, position);
        if (bSkipEnd && position != 0 && duration != 0) {
            int et = 0;
            if (mJsonObject != null) {
                try {
                    et = mJsonObject.getInt("et");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
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
        simSlideOffset += (10000.0f * mSeekSpeedMultiplier * iFoward);
        int currentPosition = (int) mControlWrapper.getCurrentPosition();
        int position = (int) (simSlideOffset + currentPosition);
        if (position > duration) position = (int) duration;
        if (position < 0) position = 0;
        updateSeekUI(currentPosition, position, duration);
        simSeekPosition = position;
        //把遥控快进的落点同步到进度条，操作结果可见
        mSeekBar.setProgress((int) (position * 1.0 / duration * mSeekBar.getMax()));
        mCurrentTimeTV.setText(PlayerUtils.stringForTime(position));
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
                mBottomLayout.setVisibility(VISIBLE);
                if (!bLocked) {
                    mPlayingBtn.requestFocus();
                }
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
        boolean wasVisible = mBottomLayout.getVisibility() == VISIBLE;
        mBottomLayout.setVisibility(VISIBLE);
        if (!wasVisible && !bLocked && mPlayingBtn != null) {
            mPlayingBtn.requestFocus();
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
        boolean seekBarFocused = mSeekBar != null && mSeekBar.hasFocus();
        if (action == KeyEvent.ACTION_DOWN) {
            showBottom();
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (isInPlayback && seekBarFocused) {
                    int repeatCount = event.getRepeatCount();
                    mSeekSpeedMultiplier = repeatCount >= 6 ? 3 : repeatCount >= 2 ? 2 : 1;
                    slideStart(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (isInPlayback && !hasFocusedControl()) {
                    togglePlay();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!bLocked) {
                    mDownLongHandled = false;
                    mDownNextPending = false;
                    mBaseHandler.removeCallbacks(mDownNextRunnable);
                    showBottom();
                    if (mSeekBar != null && mSeekBar.isEnabled()) {
                        mSeekBar.requestFocus();
                    }
                }
                return true;
            }
        } else if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (!seekBarFocused) {
                    return false;
                }
                mTimeSpeed = 1;
                mSeekSpeedMultiplier = 1;
                if (isInPlayback) {
                    mBaseHandler.removeMessages(MSG_WHAT_SEEK_PLAY);
                    mBaseHandler.sendEmptyMessageDelayed(MSG_WHAT_SEEK_PLAY, 1000);
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private boolean hasFocusedControl() {
        View focusedView = findFocus();
        return focusedView != null && focusedView != this && focusedView.isFocusable();
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
        // 触摸端由自定义底部控制栏管理显隐，避免父控制器再次切换可见状态，
        // 导致暂停时进度条刚显示又被隐藏。
        return true;
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
        void playEpisode(int index);
        void changeParse(ParseBean pb);
        void updatePlayerCfg();
        void replay();
        void errReplay();
    }

}
