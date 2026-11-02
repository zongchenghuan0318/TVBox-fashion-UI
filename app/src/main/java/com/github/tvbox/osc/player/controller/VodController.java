package com.github.tvbox.osc.player.controller;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Message;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.EpisodeRangeBean;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.player.thirdparty.MXPlayer;
import com.github.tvbox.osc.player.thirdparty.ReexPlayer;
import com.github.tvbox.osc.ui.adapter.PlayerEpisodeAdapter;
import com.github.tvbox.osc.ui.adapter.PlayerEpisodeRangeAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
import com.lcstudio.commonsurport.L;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
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
    private View mEpisodeBtn;
    private boolean mIsDragging;
    private LinearLayout mProgressLayout;
    private TextView mProgressTv;
    private TextView mProgressTotalTv;
    private TextView mProgressDeltaTv;
    private View mProgressLine;
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
    private long simSeekAnchor;
    private boolean mOptionsOpen;
    private View mOptionsReturnFocus;
    private int mPendingSeekPosition = -1;
    private long mPendingSeekTime;
    /** 尚未收到播放器 seek 完成事件的 seek 次数，归零时才真正结束"待定位置"显示 */
    private int mPendingSeekCount;
    private int mLastValidDuration = 0;
    private VodControlListener controlListener;
    private boolean bSkipEnd = true;
    private int mTimeSpeed = 1;
    private int mSeekSpeedMultiplier = 1;
    private boolean mDownLongHandled;
    private boolean mDownNextPending;
    private boolean mCenterPressed;
    private boolean mCenterLongHandled;
    private boolean mPendingDirectionKeyUp;
    private final Runnable mCenterLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCenterPressed && isInPlaybackState() && mControlWrapper != null && mControlWrapper.isPlaying()) {
                mCenterLongHandled = true;
                startKeySpeedBoost();
            }
        }
    };
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
    private Dialog mEpisodeDialog;
    private View mAutoNextPanel;
    private TextView mAutoNextEpisode;
    private TextView mAutoNextCountdown;
    private TextView mAutoNextCancel;
    private TextView mAutoNextNow;
    private static final int AUTO_NEXT_COUNTDOWN_SECONDS = 8;
    private int mAutoNextSeconds;
    private boolean mAutoNextPending;
    private final Runnable mAutoNextTick = new Runnable() {
        @Override
        public void run() {
            if (!mAutoNextPending || mAutoNextPanel == null) return;
            mAutoNextSeconds--;
            if (mAutoNextSeconds <= 0) {
                finishAutoNextCountdown();
                return;
            }
            updateAutoNextCountdownLabel();
            mBaseHandler.postDelayed(this, 1000L);
        }
    };

    public VodController(Context context) {
        super(context);
        setDismissTimeout(com.github.tvbox.osc.util.SeniorMode.isEnabled() ? 10000 : 4000);
    }
    @Override
    protected int getLayoutId() {
        return R.layout.player_vod_control_view;
    }

    @Override
    protected void callBackInUi(Message msg) {
        switch (msg.what) {
            case MSG_WHAT_SHOW_SEEK: { // seek 显示
                showProgressOverlay(true);
                break;
            }
            case MSG_WHAT_HIDE_SEEK: { // seek 隐藏
                L.d("case 1001");
                showProgressOverlay(false);
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
        mProgressTotalTv = findViewById(R.id.tv_progress_total);
        mProgressDeltaTv = findViewById(R.id.tv_progress_delta);
        mProgressLine = findViewById(R.id.tv_progress_line);
        mBottomLayout = findViewById(R.id.bottom_container);
        mBottomLayout.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> updateSubtitlePlacement());
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
        mAutoNextPanel = findViewById(R.id.auto_next_panel);
        mAutoNextEpisode = findViewById(R.id.auto_next_episode);
        mAutoNextCountdown = findViewById(R.id.auto_next_countdown);
        mAutoNextCancel = findViewById(R.id.auto_next_cancel);
        mAutoNextNow = findViewById(R.id.auto_next_now);
        lockImg = findViewById(R.id.lock_img);
        portHorImg = findViewById(R.id.port_img);

        initClickListener();

        mBaseHandler.postDelayed(() -> {
            hideBottom();
            hideLockImg();
        }, 8000L);
    }

    private void initClickListener() {
        mEpisodeBtn.setOnClickListener(view -> showEpisodeSelector());
        mAutoNextCancel.setOnClickListener(view -> cancelAutoNextCountdown());
        mAutoNextNow.setOnClickListener(view -> finishAutoNextCountdown());
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
                if (duration <= 0) return;
                long newPosition = (duration * progress) / seekBar.getMax();
                if (mCurrentTimeTV != null) {
                    mCurrentTimeTV.setText(stringForTime((int) newPosition));
                }
                updateSeekUI((int) mControlWrapper.getCurrentPosition(), (int) newPosition, duration);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                android.util.Log.w("SeekDiag", "startTrack locked=" + bLocked);
                if(bLocked) return;
                mIsDragging = true;
                mControlWrapper.stopProgress();
                mControlWrapper.stopFadeOut();
                mBaseHandler.removeCallbacks(mSeniorModeHideBottomRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                android.util.Log.w("SeekDiag", "stopTrack dragging=" + mIsDragging + " locked=" + bLocked + " progress=" + seekBar.getProgress());
                if (!mIsDragging) return;
                //先恢复刷新，再处理跳转：中途被锁定也不能让进度条停在原地
                mIsDragging = false;
                mControlWrapper.startProgress();
                mControlWrapper.startFadeOut();
                if (bLocked) return;
                long duration = mControlWrapper.getDuration();
                if (duration <= 0) return;
                long newPosition = (duration * seekBar.getProgress()) / seekBar.getMax();
                onSeekRequested((int) newPosition);
                mControlWrapper.seekTo((int) newPosition);
                onSeekPreviewFinished();
            }
        });
        // 进度条获得焦点时，时间高亮纯白并加粗，契合 Apple TV 中性发光交互规范
        mSeekBar.setOnFocusChangeListener((view, hasFocus) -> {
            int color = mActivity == null ? 0 : mActivity.getResources().getColor(
                    hasFocus ? R.color.color_FFFFFF : R.color.color_CCFFFFFF);
            if (mCurrentTimeTV != null) {
                mCurrentTimeTV.setTextColor(color);
                mCurrentTimeTV.getPaint().setFakeBoldText(hasFocus);
            }
            if (mTotalTimeTV != null) {
                mTotalTimeTV.setTextColor(color);
                mTotalTimeTV.getPaint().setFakeBoldText(hasFocus);
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
            cancelSpeedBoost(false);
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
                // 记住这是用户主动选的内核，之后打开本片不再被全局默认覆盖
                mJsonObject.put("pl_pinned", true);
                mJsonObject.put("pl_pinned_manual", true);
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
        mPlayerTimeStartTV.setOnLongClickListener(view -> resetSkip("st"));
        mPlayerTimeSkipTV.setOnLongClickListener(view -> resetSkip("et"));
        findViewById(R.id.play_subtitle).setOnClickListener(view -> {
            if (!bLocked && controlListener != null) controlListener.showSubtitles();
        });
        findViewById(R.id.play_audio).setOnClickListener(view -> {
            if (!bLocked && controlListener != null) controlListener.showAudioTracks();
        });
        mPlayingBtn.setImageResource(R.drawable.ic_player_pause);
        mPlayingBtn.setOnClickListener(view -> {
            if(bLocked) return;
            if (!mControlWrapper.isPlaying()) {
                mPlayingBtn.setImageResource(R.drawable.ic_player_pause);
            }else{
                mPlayingBtn.setImageResource(R.drawable.ic_player_play);
            }
            togglePlay();
        });
        lockImg.setOnClickListener(view -> {
            cancelSpeedBoost(false);
            bLocked = !bLocked;
            //setLocked(bLocked);
            if (bLocked) {
                lockImg.setImageResource(R.drawable.ic_player_locked);
            } else {
                lockImg.setImageResource(R.drawable.ic_player_unlock);
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

    private boolean resetSkip(String key) {
        if (bLocked || mJsonObject == null || controlListener == null) return true;
        try {
            mJsonObject.put(key, 0);
            updatePlayerCfgView();
            controlListener.updatePlayerCfg();
        } catch (JSONException e) {
            L.e(e);
        }
        return true;
    }

    public void setOptionsOpen(boolean open) {
        if (open) cancelSpeedBoost(false);
        if (open) mOptionsReturnFocus = findFocus();
        mOptionsOpen = open;
        if (mControlWrapper == null) return;
        mControlWrapper.stopFadeOut();
        mBaseHandler.removeCallbacks(mSeniorModeHideBottomRunnable);
        if (!open) {
            showBottom();
            mControlWrapper.startFadeOut();
            if (mOptionsReturnFocus != null) mOptionsReturnFocus.requestFocus();
        }
    }

    private void updateSubtitlePlacement() {
        View subtitles = findViewById(R.id.player_subtitle_text);
        if (subtitles == null) return;
        android.widget.FrameLayout.LayoutParams params = (android.widget.FrameLayout.LayoutParams) subtitles.getLayoutParams();
        int margin = mBottomLayout.getVisibility() == VISIBLE
                ? mBottomLayout.getHeight() + getResources().getDimensionPixelSize(R.dimen.vs_10)
                : getResources().getDimensionPixelSize(R.dimen.vs_50);
        if (params.bottomMargin != margin) {
            params.bottomMargin = margin;
            subtitles.setLayoutParams(params);
        }
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
            mPlayerTimeStartTV.setText("片头 " + PlayerUtils.stringForTime(mJsonObject.getInt("st") * 1000));
            mPlayerTimeSkipTV.setText("片尾 " + PlayerUtils.stringForTime(mJsonObject.getInt("et") * 1000));
            mPlayerTimeStepTV.setText("步长 " + Hawk.get(HawkConfig.PLAY_TIME_STEP, 5) + "秒");
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void setTitle(String playTitleInfo) {
        mPlayTitleTV.setText(playTitleInfo);
    }

    public void resetSpeed() {
        cancelSpeedBoost(false);
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

    @Override
    public void onLongPress(MotionEvent event) {
        super.onLongPress(event);
        if (isSpeedBoostActive()) hideBottom();
    }

    public void setControlListener(VodControlListener controlListener) {
        this.controlListener = controlListener;
    }

    public void setEpisodes(List<VodInfo.VodSeries> episodes, int currentEpisode) {
        cancelAutoNextCountdown();
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
        cancelAutoNextCountdown();
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

    private void showAutoNextCountdown() {
        if (mAutoNextPanel == null || mCurrentEpisode + 1 >= mEpisodes.size()) {
            if (controlListener != null) controlListener.playNext(true);
            return;
        }
        mBaseHandler.removeCallbacks(mAutoNextTick);
        mAutoNextPending = true;
        mAutoNextSeconds = AUTO_NEXT_COUNTDOWN_SECONDS;
        mAutoNextEpisode.setText(episodeName(mCurrentEpisode + 1));
        updateAutoNextCountdownLabel();
        mAutoNextPanel.setVisibility(VISIBLE);
        mAutoNextNow.requestFocus();
        mBaseHandler.postDelayed(mAutoNextTick, 1000L);
    }

    private void updateAutoNextCountdownLabel() {
        if (mAutoNextCountdown != null) {
            mAutoNextCountdown.setText("下一集将在 " + mAutoNextSeconds + " 秒后播放");
        }
    }

    private void finishAutoNextCountdown() {
        if (!mAutoNextPending) return;
        mAutoNextPending = false;
        mBaseHandler.removeCallbacks(mAutoNextTick);
        if (mAutoNextPanel != null) mAutoNextPanel.setVisibility(GONE);
        if (controlListener != null) controlListener.playNext(true);
    }

    public boolean cancelAutoNextCountdown() {
        if (!mAutoNextPending) return false;
        mAutoNextPending = false;
        mBaseHandler.removeCallbacks(mAutoNextTick);
        if (mAutoNextPanel != null) mAutoNextPanel.setVisibility(GONE);
        showBottom();
        return true;
    }

    public void showEpisodeSelector() {
        if (bLocked || mEpisodes.isEmpty() || mActivity == null) {
            return;
        }
        if (mEpisodeDialog != null && mEpisodeDialog.isShowing()) {
            return;
        }
        Dialog dialog = new Dialog(mActivity);
        View panel = LayoutInflater.from(mActivity).inflate(R.layout.dialog_player_episode, null);
        TextView count = panel.findViewById(R.id.episodeCount);
        count.setText("共 " + mEpisodes.size() + " 集");
        TextView current = panel.findViewById(R.id.episodeCurrent);
        current.setText(episodeName(mCurrentEpisode));

        TvRecyclerView episodeRangeRV = panel.findViewById(R.id.episodeRangeRV);
        TvRecyclerView episodeGrid = panel.findViewById(R.id.episodeGrid);
        episodeGrid.setLayoutManager(new V7GridLayoutManager(mActivity, 3));
        PlayerEpisodeAdapter adapter = new PlayerEpisodeAdapter();
        adapter.setNewData(new ArrayList<>(mEpisodes));
        adapter.setCurrent(mCurrentEpisode);
        episodeGrid.setAdapter(adapter);

        PlayerEpisodeRangeAdapter rangeAdapter = null;
        final int step = 30;
        if (mEpisodes.size() > step && episodeRangeRV != null) {
            episodeRangeRV.setVisibility(View.VISIBLE);
            episodeRangeRV.setLayoutManager(new V7LinearLayoutManager(mActivity, 0, false));
            rangeAdapter = new PlayerEpisodeRangeAdapter();
            List<EpisodeRangeBean> rangeList = new ArrayList<>();
            for (int i = 0; i < mEpisodes.size(); i += step) {
                int start = i + 1;
                int end = Math.min(i + step, mEpisodes.size());
                rangeList.add(new EpisodeRangeBean(i, end - 1, start + "-" + end));
            }
            rangeAdapter.setNewData(rangeList);
            int currentRange = mCurrentEpisode / step;
            rangeAdapter.setSelectedIndex(currentRange);
            episodeRangeRV.setAdapter(rangeAdapter);
            episodeRangeRV.scrollToPosition(currentRange);

            final PlayerEpisodeRangeAdapter finalRangeAdapter = rangeAdapter;
            PlayerEpisodeRangeAdapter.OnItemClickListener rangeClickListener = (baseQuickAdapter, v, position) -> {
                if (position >= 0 && position < rangeList.size()) {
                    finalRangeAdapter.setSelectedIndex(position);
                    EpisodeRangeBean item = rangeList.get(position);
                    focusEpisodeItem(episodeGrid, dialog, item.startIndex, 0);
                }
            };
            rangeAdapter.setOnItemClickListener(rangeClickListener);
            episodeRangeRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
                @Override
                public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

                @Override
                public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}

                @Override
                public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                    if (position >= 0 && position < rangeList.size()) {
                        finalRangeAdapter.setSelectedIndex(position);
                        EpisodeRangeBean item = rangeList.get(position);
                        focusEpisodeItem(episodeGrid, dialog, item.startIndex, 0);
                    }
                }
            });
        } else {
            if (episodeRangeRV != null) episodeRangeRV.setVisibility(View.GONE);
        }

        final PlayerEpisodeRangeAdapter effectiveRangeAdapter = rangeAdapter;
        episodeGrid.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (effectiveRangeAdapter != null && episodeRangeRV != null && position >= 0 && position < mEpisodes.size()) {
                    int rIdx = position / step;
                    if (rIdx != effectiveRangeAdapter.getSelectedIndex()) {
                        effectiveRangeAdapter.setSelectedIndex(rIdx);
                        episodeRangeRV.scrollToPosition(rIdx);
                    }
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (position != mCurrentEpisode) {
                    mCurrentEpisode = position;
                    updateEpisodeButton();
                    controlListener.playEpisode(position);
                }
                dialog.dismiss();
            }
        });

        adapter.setOnItemClickListener((baseQuickAdapter, view, position) -> {
            if (position != mCurrentEpisode) {
                mCurrentEpisode = position;
                updateEpisodeButton();
                controlListener.playEpisode(position);
            }
            dialog.dismiss();
        });

        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        if (mControlWrapper != null) {
            mControlWrapper.stopFadeOut();
        }
        mEpisodeDialog = dialog;
        dialog.setOnDismissListener(d -> {
            mEpisodeDialog = null;
            if (mEpisodeBtn != null && mBottomLayout != null && mBottomLayout.getVisibility() == VISIBLE) {
                mEpisodeBtn.requestFocus();
            }
            if (mControlWrapper != null) {
                mControlWrapper.startFadeOut();
            }
        });
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.setWindowAnimations(R.style.PlayerEpisodeWindowAnim);
        WindowManager.LayoutParams windowParams = window.getAttributes();
        windowParams.width = (int) (mActivity.getResources().getDisplayMetrics().widthPixels * 0.44f);
        windowParams.height = WindowManager.LayoutParams.MATCH_PARENT;
        windowParams.gravity = Gravity.RIGHT;
        windowParams.dimAmount = 0.55f;
        window.setAttributes(windowParams);
        dialog.show();
        window.setLayout(windowParams.width, WindowManager.LayoutParams.MATCH_PARENT);
        if (mCurrentEpisode >= 0 && mCurrentEpisode < mEpisodes.size()) {
            episodeGrid.scrollToPosition(mCurrentEpisode);
        }
        episodeGrid.post(() -> focusEpisodeItem(episodeGrid, dialog, mCurrentEpisode, 0));
    }

    private void focusEpisodeItem(TvRecyclerView grid, Dialog dialog, int position, int retry) {
        if (!dialog.isShowing() || mActivity == null || grid.getLayoutManager() == null) {
            return;
        }
        if (retry == 0) {
            grid.scrollToPosition(position);
        }
        if (grid.getLayoutManager().findViewByPosition(position) != null || retry >= 10) {
            grid.setSelection(position);
            grid.requestFocus();
        } else {
            grid.scrollToPosition(position);
            grid.postDelayed(() -> focusEpisodeItem(grid, dialog, position, retry + 1), 60);
        }
    }

    private String episodeName(int index) {
        if (index < 0 || index >= mEpisodes.size()) {
            return "";
        }
        String name = mEpisodes.get(index).name;
        return TextUtils.isEmpty(name) ? "第" + (index + 1) + "集" : name;
    }

    @Override
    protected void setProgress(int duration, int position) {
        //拖动进度条或遥控快进时，播放位置不要回头覆盖进度条
        if (mIsDragging || bSimSlideStart || isGestureSeeking()) {
            return;
        }
        if (duration > 0) {
            mLastValidDuration = duration;
        } else if (mLastValidDuration > 0) {
            duration = mLastValidDuration;
        }
        duration = Math.max(0, duration);
        position = duration > 0 ? Math.max(0, Math.min(position, duration)) : Math.max(0, position);
        boolean pendingSeek = false;
        if (mPendingSeekPosition >= 0 && duration > 0) {
            long elapsed = android.os.SystemClock.uptimeMillis() - mPendingSeekTime;
            // 待定位置只由播放器的 seek 完成事件结束（见 onPlayerSeekComplete），
            // 不再按距离判断：关键帧 seek 会落在目标之前，按距离会让进度条停在目标处、与画面不一致。
            // 4 秒超时仅作兜底，防止事件丢失时进度条一直卡住。
            if (elapsed >= 4000) {
                mPendingSeekPosition = -1;
                mPendingSeekCount = 0;
            } else {
                position = Math.min(duration, mPendingSeekPosition);
                pendingSeek = true;
            }
        }
        super.setProgress(duration, position);
        mDualtion = duration > 0 ? PlayerUtils.stringForTime(duration) : "--:--";
        if (bSkipEnd && !pendingSeek && position > 0 && duration > 0 && mControlWrapper.isPlaying()) {
            int et = 0;
            if (mJsonObject != null) {
                try {
                    et = mJsonObject.getInt("et");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            if (controlListener != null && et > 0 && et * 1000L < duration
                    && position + (et * 1000L) >= duration) {
                bSkipEnd = false;
                controlListener.playNext(true);
                return;
            }
        }
        if (!isBottomVisible()) return;
        mCurrentTimeTV.setText(PlayerUtils.stringForTime(position));
        mTotalTimeTV.setText(mDualtion);
        if (duration > 0) {
            mSeekBar.setEnabled(true);
            int pos = (int) Math.round((double) position / duration * mSeekBar.getMax());
            mSeekBar.setProgress(pos);
        } else {
            mSeekBar.setProgress(0);
            mSeekBar.setEnabled(false);
        }
        int percent = Math.max(0, Math.min(100, mControlWrapper.getBufferedPercentage()));
        mSeekBar.setSecondaryProgress(duration > 0 ? percent * mSeekBar.getMax() / 100 : 0);
    }

    public void slideStop() {
        if (!bSimSlideStart) return;
        int target = simSeekPosition;
        bSimSlideStart = false;
        simSeekPosition = 0;
        simSlideOffset = 0;
        onSeekRequested(target);
        mControlWrapper.seekTo(target);
        onSeekPreviewFinished();
    }

    public void slideStart(int iFoward) {
        long duration = mControlWrapper.getDuration();
        if (duration <= 0 && mLastValidDuration > 0) {
            duration = mLastValidDuration;
        }
        if (duration <= 0) return;
        if (!bSimSlideStart) {
            bSimSlideStart = true;
            simSeekAnchor = mPendingSeekPosition >= 0 ? mPendingSeekPosition : mControlWrapper.getCurrentPosition();
        }
        mBaseHandler.removeMessages(MSG_WHAT_SEEK_PLAY);
        // 快进算法: 尊重用户设置的步长 (5s, 10s, 15s, 30s)
        int stepSec = Hawk.get(HawkConfig.PLAY_TIME_STEP, 10);
        if (stepSec <= 0) stepSec = 10;
        simSlideOffset += (stepSec * 1000.0f * mSeekSpeedMultiplier * iFoward);
        int currentPosition = (int) simSeekAnchor;
        int position = (int) (simSlideOffset + currentPosition);
        if (position > duration) position = (int) duration;
        if (position < 0) position = 0;
        updateSeekUI(currentPosition, position, duration);
        simSeekPosition = position;
        //把遥控快进的落点同步到进度条，操作结果可见
        mSeekBar.setProgress((int) Math.round((double) position / duration * mSeekBar.getMax()));
        mCurrentTimeTV.setText(PlayerUtils.stringForTime(position));
    }

    @Override
    protected void updateSeekUI(int curr, int seekTo, long duration) {
        boolean forward = seekTo > curr;
        mProgressImg.setImageResource(forward ? R.drawable.icon_pre : R.drawable.icon_back);
        mProgressTv.setText(PlayerUtils.stringForTime(seekTo));
        if (mProgressTotalTv != null) {
            mProgressTotalTv.setText("/ " + (duration > 0 ? PlayerUtils.stringForTime((int) duration) : "--:--"));
        }
        if (mProgressDeltaTv != null) {
            int delta = Math.abs(seekTo - curr) / 1000;
            mProgressDeltaTv.setText((forward ? "+" : "-") + PlayerUtils.stringForTime(delta * 1000));
        }
        if (duration > 0) {
            mSeekBar.setProgress((int) (Math.max(0, Math.min(seekTo, duration)) * (long) mSeekBar.getMax() / duration));
            mCurrentTimeTV.setText(PlayerUtils.stringForTime(seekTo));
            mTotalTimeTV.setText(PlayerUtils.stringForTime((int) duration));
        }
        updateSeekLine(seekTo, duration);
        mBaseHandler.sendEmptyMessage(MSG_WHAT_SHOW_SEEK);
        mBaseHandler.removeMessages(MSG_WHAT_HIDE_SEEK);
        mBaseHandler.sendEmptyMessageDelayed(MSG_WHAT_HIDE_SEEK, 1000);
    }

    @Override
    protected void onSeekRequested(int position) {
        mPendingSeekPosition = Math.max(0, position);
        mPendingSeekTime = android.os.SystemClock.uptimeMillis();
        mPendingSeekCount++;
    }

    /** 播放器 seek 完成：与 onSeekRequested 配对，全部完成后立即以播放器真实位置刷新进度条 */
    @Override
    public void onPlayerSeekComplete() {
        mBaseHandler.post(() -> {
            if (mPendingSeekCount > 0) mPendingSeekCount--;
            if (mPendingSeekCount == 0 && mPendingSeekPosition >= 0) {
                mPendingSeekPosition = -1;
                refreshProgressNow();
            }
        });
    }

    /** 上一次 seek 还没完成时，播放器可能返回 0 或旧位置，此时以待定目标为准，避免连续快进时跳回开头 */
    @Override
    protected long getSeekBasePosition() {
        if (mPendingSeekPosition >= 0) return mPendingSeekPosition;
        return super.getSeekBasePosition();
    }

    @Override
    protected void onSeekPreviewFinished() {
        mBaseHandler.removeMessages(MSG_WHAT_HIDE_SEEK);
        mBaseHandler.sendEmptyMessageDelayed(MSG_WHAT_HIDE_SEEK, 500);
        startProgress();
        // Native seeks finish asynchronously. Refresh both immediately and after settlement.
        refreshProgressNow();
        mBaseHandler.removeCallbacks(mRefreshAfterSeek);
        mBaseHandler.postDelayed(mRefreshAfterSeek, 350);
    }

    private final Runnable mRefreshAfterSeek = () -> refreshProgressNow();

    private void refreshProgressNow() {
        if (mControlWrapper != null) setProgress((int) mControlWrapper.getDuration(), (int) mControlWrapper.getCurrentPosition());
    }

    /**
     * 中央拖动浮层：先轻微放大淡入，隐藏时淡出，避免生硬闪现。
     */
    private void showProgressOverlay(boolean show) {
        if (mProgressLayout == null) {
            return;
        }
        mProgressLayout.animate().cancel();
        if (show) {
            if (mProgressLayout.getVisibility() != VISIBLE) {
                mProgressLayout.setAlpha(0f);
                mProgressLayout.setScaleX(0.96f);
                mProgressLayout.setScaleY(0.96f);
                mProgressLayout.setVisibility(VISIBLE);
                mProgressLayout.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(150).start();
            } else {
                mProgressLayout.animate().alpha(1f).setDuration(90).start();
            }
        } else if (mProgressLayout.getVisibility() == VISIBLE) {
            mProgressLayout.animate().alpha(0f).setDuration(160).withEndAction(() -> {
                if (mProgressLayout != null && mProgressLayout.getAlpha() < 0.5f) {
                    mProgressLayout.setVisibility(GONE);
                }
            }).start();
        }
    }

    /**
     * 浮层里的迷你时间轴：宽度跟随拖动落点。
     */
    private void updateSeekLine(int seekTo, long duration) {
        if (mProgressLine == null) {
            return;
        }
        int max = getResources().getDimensionPixelSize(R.dimen.vs_300);
        float ratio = duration > 0 ? Math.min(1f, Math.max(0f, seekTo / (float) duration)) : 0f;
        int width = (int) (max * ratio);
        ViewGroup.LayoutParams params = mProgressLine.getLayoutParams();
        if (params.width != width) {
            params.width = width;
            mProgressLine.setLayoutParams(params);
        }
    }

    @Override
    protected void onPlayStateChanged(int playState) {
        super.onPlayStateChanged(playState);
        int duration = (int) mControlWrapper.getDuration();
        mDualtion = PlayerUtils.stringForTime(duration);
        switch (playState) {
            case VideoView.STATE_IDLE:
            case VideoView.STATE_PREPARING:
                mLastValidDuration = 0;
                mPendingSeekPosition = -1;
                mPendingSeekCount = 0;
                mIsDragging = false;
                bSimSlideStart = false;
                simSlideOffset = 0;
                mBaseHandler.removeMessages(MSG_WHAT_SEEK_PLAY);
                showProgressOverlay(false);
                setProgress(0, 0);
                break;
            case VideoView.STATE_PLAYING:
                mPlayingBtn.setImageResource(R.drawable.ic_player_pause);
                mPlayingBtn.setContentDescription("暂停");
                startProgress();
                if (isBottomVisible()) showBottom();
                break;
            case VideoView.STATE_PAUSED:
                mPlayingBtn.setImageResource(R.drawable.ic_player_play);
                mPlayingBtn.setContentDescription("播放");
                startProgress();
                refreshProgressNow();
                setControlsVisible(true);
                if (!bLocked) {
                    mPlayingBtn.requestFocus();
                }
                break;
            case VideoView.STATE_ERROR:
                controlListener.errReplay();
                break;
            case VideoView.STATE_PREPARED:
            case VideoView.STATE_BUFFERED:
                startProgress();
                refreshProgressNow();
                break;
            case VideoView.STATE_BUFFERING:
                startProgress();
                break;
            case VideoView.STATE_PLAYBACK_COMPLETED:
                if (mCurrentEpisode + 1 < mEpisodes.size()) {
                    showAutoNextCountdown();
                } else {
                    controlListener.playNext(true);
                }
                break;
        }
    }

    public boolean isBottomVisible() {
        return mBottomLayout.getVisibility() == VISIBLE;
    }

    boolean isLockVisible() {
        return findViewById(R.id.img_layout).getVisibility() == VISIBLE;
    }

    public void showBottom() {
        boolean wasVisible = mBottomLayout.getVisibility() == VISIBLE;
        setControlsVisible(true);
        refreshProgressNow();
        if (isInPlaybackState()) startProgress();
        if (!wasVisible && !bLocked && mPlayingBtn != null) {
            mPlayingBtn.requestFocus();
        }
        mBaseHandler.removeCallbacks(mSeniorModeHideBottomRunnable);
        if (!mOptionsOpen && !mIsDragging) mBaseHandler.postDelayed(mSeniorModeHideBottomRunnable,
                com.github.tvbox.osc.util.SeniorMode.isEnabled() ? 10000L : 6000L);
    }

    public void hideBottom() {
        hideBottom(false);
    }

    public void hideBottom(boolean force) {
        if (!force && (mOptionsOpen || mIsDragging || bSimSlideStart || !mControlWrapper.isPlaying())) return;
        if (force) {
            mIsDragging = false;
            bSimSlideStart = false;
            simSlideOffset = 0;
            mBaseHandler.removeMessages(MSG_WHAT_SEEK_PLAY);
            showProgressOverlay(false);
        }
        mBaseHandler.removeCallbacks(mSeniorModeHideBottomRunnable);
        setControlsVisible(false);
        // 电视端防丢焦点：底栏无论是自然超时隐藏还是强制隐藏，都主动将焦点拉回 Controller 容器，杜绝悬空焦点吞按键
        requestFocus();
    }

    private void setControlsVisible(boolean visible) {
        mBottomLayout.setVisibility(visible ? VISIBLE : GONE);
        if (visible && !bLocked) showLockImg();
        else if (!bLocked) hideLockImg();
        updateSubtitlePlacement();
        if (controlListener != null) controlListener.onControlsVisibilityChanged(visible);
    }

    private final Runnable mSeniorModeHideBottomRunnable = new Runnable() {
        @Override
        public void run() {
            hideBottom();
        }
    };

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
        if (keyCode == KeyEvent.KEYCODE_BACK) return false;
        boolean confirmKey = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
        boolean directionKey = keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        if (!confirmKey && !directionKey && keyCode != KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) return false;
        if (bLocked) {
            if (confirmKey && action == KeyEvent.ACTION_UP) {
                lockImg.performClick();
            } else if (action == KeyEvent.ACTION_DOWN) {
                showLockImg();
                lockImg.requestFocus();
            }
            return true;
        }
        if (mAutoNextPending && directionKey) {
            if (action == KeyEvent.ACTION_DOWN) {
                // 播完自动下一集倒计时面板：方向键不得把焦点带离面板（默认焦点搜索会飘到底栏/顶栏），
                // 确认键保持原生行为让面板按钮自行处理点击
                View focused = findFocus();
                if (focused != mAutoNextCancel && focused != mAutoNextNow
                        && mAutoNextNow != null && mAutoNextNow.isShown()) {
                    mAutoNextNow.requestFocus();
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    mPendingDirectionKeyUp = true;
                }
                showBottom();
                return true;
            }
            return true;
        }
        boolean seekBarFocused = mSeekBar != null && mSeekBar.hasFocus();
        if (action == KeyEvent.ACTION_DOWN) {
            if (isBottomVisible() && movePlaybackControlFocus(keyCode)) {
                showBottom();
                return true;
            }
            if (isBottomVisible() && seekBarFocused && !isInPlayback
                    && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)) {
                // 焦点在进度条上但未起播/出错态，本次左右键不会走 seek 路径，对称消费避免逃逸；
                // 起播状态下不拦截，保留进度条左右键快进能力
                mPendingDirectionKeyUp = true;
                showBottom();
                return true;
            }
            if (isBottomVisible() && !hasFocusedControl() && !seekBarFocused
                    && (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP)) {
                // 电视端焦点兜底：底栏可见但焦点悬空（容器自身持焦）时，左右/上键不允许
                // 穿透到 Activity 层的其他可聚焦控件，统一拉回默认控制按钮。
                focusDefaultControl();
                mPendingDirectionKeyUp = true;
                showBottom();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (isBottomVisible()) {
                    if (seekBarFocused) {
                        hideBottom(true);
                        return true;
                    }
                } else if (!bLocked) {
                    if (mEpisodes != null && mEpisodes.size() > 1) {
                        showEpisodeSelector();
                        return true;
                    } else {
                        showBottom();
                        return true;
                    }
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!isBottomVisible() && !bLocked) {
                    showBottom();
                    return true;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                boolean canDirectSeek = !isBottomVisible() || seekBarFocused;
                if (isInPlayback && canDirectSeek) {
                    showBottom();
                    if (mSeekBar != null && mSeekBar.isEnabled() && !seekBarFocused) {
                        mSeekBar.requestFocus();
                    }
                    int repeatCount = event.getRepeatCount();
                    // 智能长按平滑倍速递增曲线：单按精准微调(10s)，长按1秒稳步加速，长按3秒以上飞速穿越长篇巨作
                    int multiplier;
                    if (repeatCount >= 28) {
                        multiplier = 30;
                    } else if (repeatCount >= 18) {
                        multiplier = 15;
                    } else if (repeatCount >= 10) {
                        multiplier = 8;
                    } else if (repeatCount >= 4) {
                        multiplier = 4;
                    } else if (repeatCount >= 1) {
                        multiplier = 2;
                    } else {
                        multiplier = 1;
                    }
                    mSeekSpeedMultiplier = multiplier;
                    slideStart(keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
                    return true;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (isInPlayback) {
                    togglePlay();
                    return true;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (isInPlayback && !hasFocusedControl()) {
                    if (event.getRepeatCount() == 0) {
                        mCenterPressed = true;
                        mCenterLongHandled = false;
                        mBaseHandler.removeCallbacks(mCenterLongPressRunnable);
                        mBaseHandler.postDelayed(mCenterLongPressRunnable, 400L);
                    } else if (event.getRepeatCount() >= 2) {
                        if (!mCenterLongHandled && mControlWrapper != null && mControlWrapper.isPlaying()) {
                            mCenterLongHandled = true;
                            mBaseHandler.removeCallbacks(mCenterLongPressRunnable);
                            startKeySpeedBoost();
                        }
                    }
                    return true;
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (!bLocked && (!isBottomVisible() || seekBarFocused || !hasFocusedControl())) {
                    mDownLongHandled = false;
                    mDownNextPending = false;
                    mBaseHandler.removeCallbacks(mDownNextRunnable);
                    showBottom();
                    mPlayingBtn.requestFocus();
                    return true;
                }
                // 底栏已在屏幕最底部：焦点在控件上按↓无非地方可去，消费掉避免默认焦点搜索把焦点带出播放器
                if (isBottomVisible() && hasFocusedControl()) {
                    return true;
                }
            }
            showBottom();
        } else if (action == KeyEvent.ACTION_UP) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                mBaseHandler.removeCallbacks(mCenterLongPressRunnable);
                if (mCenterLongHandled) {
                    mCenterLongHandled = false;
                    mCenterPressed = false;
                    stopKeySpeedBoost();
                    return true;
                }
                if (mCenterPressed) {
                    mCenterPressed = false;
                    if (isInPlayback && !hasFocusedControl()) {
                        togglePlay();
                        return true;
                    }
                }
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT || keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (mPendingDirectionKeyUp) {
                    // 对称消费：对应 DOWN 已被本控制器处理（焦点迁移/兜底拦截），UP 不再漏给默认分发
                    mPendingDirectionKeyUp = false;
                    return true;
                }
                if (!seekBarFocused && !bSimSlideStart) {
                    return false;
                }
                mTimeSpeed = 1;
                mSeekSpeedMultiplier = 1;
                if (isInPlayback) {
                    mBaseHandler.removeMessages(MSG_WHAT_SEEK_PLAY);
                    mBaseHandler.sendEmptyMessageDelayed(MSG_WHAT_SEEK_PLAY, 500);
                    return true;
                }
            }
        }
        if (action == KeyEvent.ACTION_UP) {
            mPendingDirectionKeyUp = false;
        }
        return false;
    }

    private boolean movePlaybackControlFocus(int keyCode) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT
                && keyCode != KeyEvent.KEYCODE_DPAD_UP) return false;
        View focused = findFocus();
        View[] controls = {mPreBtn, mPlayingBtn, mNextBtn, mPlayerRetryTv, mPlayerTV,
                mPlayerIJKTV, mPlayerSpeedBtn, mEpisodeBtn, mPlayerScaleBtn, portHorImg};
        for (int i = 0; i < controls.length; i++) {
            if (focused == null || focused != controls[i]) continue;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (mSeekBar != null && mSeekBar.isEnabled()) {
                    return mSeekBar.requestFocus();
                }
                // 进度条禁用（起播瞬间 duration 未就绪）：不穿透，停在本控件上
                return true;
            }
            int direction = keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1;
            for (int next = i + direction; next >= 0 && next < controls.length; next += direction) {
                View target = controls[next];
                if (isFocusableControl(target) && target.requestFocus()) return true;
            }
            // 端点回绕：焦点在最左按←/最右按→时跳到另一端第一个可用控件，避免死键
            int wrapStart = direction > 0 ? 0 : controls.length - 1;
            int wrapEnd = direction > 0 ? controls.length : -1;
            for (int next = wrapStart; next != wrapEnd; next += direction) {
                View target = controls[next];
                if (isFocusableControl(target) && target.requestFocus()) return true;
            }
            return true;
        }
        return false;
    }

    private boolean isFocusableControl(View target) {
        return target != null && target.isShown() && target.isEnabled() && target.isFocusable();
    }

    private void focusDefaultControl() {
        if (mPlayingBtn != null && mPlayingBtn.isShown() && mPlayingBtn.requestFocus()) return;
        for (View target : new View[]{mPreBtn, mNextBtn, mEpisodeBtn, mSeekBar}) {
            if (isFocusableControl(target) && target.requestFocus()) return;
        }
        requestFocus();
    }

    public long getCurrentOrPendingPosition() {
        if (bSimSlideStart) {
            return simSeekPosition;
        }
        if (mPendingSeekPosition >= 0) {
            return mPendingSeekPosition;
        }
        return mControlWrapper != null ? mControlWrapper.getCurrentPosition() : 0L;
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
        if (bLocked) {
            showLockImg();
            return true;
        }
        if (!isBottomVisible()) showBottom();
        else hideBottom(true);
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
        cancelSpeedBoost(false);
        cancelAutoNextCountdown();
        if (mEpisodeDialog != null) {
            mEpisodeDialog.dismiss();
            mEpisodeDialog = null;
        }
        stopProgress();
        mBaseHandler.removeCallbacksAndMessages(null);
    }

    public interface VodControlListener {
        void onControlsVisibilityChanged(boolean visible);
        void playNext(boolean rmProgress);
        void playPre();
        void playEpisode(int index);
        void changeParse(ParseBean pb);
        void updatePlayerCfg();
        void replay();
        void errReplay();
        void showSubtitles();
        void showAudioTracks();
    }

}
