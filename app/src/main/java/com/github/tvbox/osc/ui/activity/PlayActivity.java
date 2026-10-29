package com.github.tvbox.osc.ui.activity;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.http.SslError;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.ConsoleMessage;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.PlayBean;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.CacheManager;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.player.controller.VodController;
import com.github.tvbox.osc.player.PlaybackOptionsManager;
import com.github.tvbox.osc.player.thirdparty.MXPlayer;
import com.github.tvbox.osc.player.thirdparty.ReexPlayer;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.thunder.Thunder;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.StringUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;
import com.uisupport.update.Initer;

import org.greenrobot.eventbus.EventBus;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import me.jessyan.autosize.AutoSize;
import xyz.doikki.videoplayer.player.ProgressManager;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PrintUtil;

/**
 * tvbox交流QQ群：879811030
 */
public class PlayActivity extends BaseActivity  implements View.OnClickListener {
    private VideoView mVideoView;
    private TextView mTimeTV;
    private TextView mPlayLoadTipTV;
    private TextView mMovieNameTV;
    private TextView mAdTipTV;
    /** 播放中自动收起顶部节目介绍/时间与右下角提示的倒计时 */
    private final Runnable mHideOverlaysRunnable = new Runnable() {
        @Override
        public void run() {
            if (mController == null || !mController.isBottomVisible()) setOverlaysVisible(false);
        }
    };
    private ImageView mPlayErrImg;
    private ProgressBar mPlayLoadingPb;
    private VodController mController;
    private PlaybackOptionsManager mPlaybackOptions;
    private SourceViewModel sourceViewModel;
    private String parseFlagStr;
    private String mWebUrl;
    private Map<String, Boolean> loadedUrlsMap = new HashMap<>();
    private boolean m_bLoadFound = false;
    private ExecutorService mExecutorService;
    private VodInfo mVodInfo;
    private String mSourceKeyStr;
    private JSONObject mVodPlayerCfgJsonObject;
    private SiteBean mSiteBean;
    private int autoRetryCount = 0;
    /** 本片是否已经尝试过 Exo 失败后回退 IJK，避免同一部片来回抖动 */
    private boolean kernelFallbackTried = false;
    /** 内核回退重播时直接续播，不再弹“从头开始”提示 */
    private boolean autoResumeWithoutPrompt = false;
    private Handler mHandler;
    private PlayBean mPlayBean;
    private String mLegacyProgressKey;
    /** 本次实际播放地址：旧版本以播放 URL 作为进度 key，用它兜底读取老进度 */
    private String mCurrentPlayUrl;
    private String mResumeKey;
    private long mResumePosition;
    private boolean mHasSavedResumePosition;
    private boolean mResumePromptShown;
    private TextView mResumeRestartHint;
    private boolean mResumeHintPending;
    private long mResumeHintDeadline;
    private int mResumeHintConsumedKey = KeyEvent.KEYCODE_UNKNOWN;
    private final Runnable mResumeHintTick = this::refreshResumeHintCountdown;
    private static final long RESUME_PROMPT_MIN_POSITION_MS = 30_000L;
    private boolean playbackStartPending;
    private final Runnable mProgressSaveRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isActivityActive() || mVideoView == null) return;
            saveCurrentProgress();
            if (mHandler != null) {
                mHandler.postDelayed(this, 5000L);
            }
        }
    };
    private final Runnable playbackStartTimeout = new Runnable() {
        @Override
        public void run() {
            if (playbackStartPending) {
                playbackStartPending = false;
                errorWithRetry("播放超时，请检查线路或网络", false, true);
            }
        }
    };

    private WebView mSysWebView;
    private SysWebClient mSysWebClient;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_play;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        L.d("onConfigurationChanged()");
        if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
        } else if (this.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
        }
    }

    @Override
    protected void init() {
        mPlayBean = new PlayBean();
        mHandler = new Handler(Looper.getMainLooper());
        mExecutorService = Executors.newSingleThreadExecutor();
        getIntentData();
        initView();
        showSystemTime();
        initModelObserver();
        initPlayerConfig();
        mPlaybackOptions = new PlaybackOptionsManager(this, mVideoView,
                mController.findViewById(R.id.player_subtitle_text), mVodPlayerCfgJsonObject,
                this::savePlayerConfig, () -> mController.setOptionsOpen(true),
                () -> { if (mController != null) mController.setOptionsOpen(false); });
        preGetPlayUrl();
        PrintUtil.printPlayErrorMap();
    }

    private void getIntentData() {
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            Bundle bundle = intent.getExtras();
            mVodInfo = (VodInfo) bundle.getSerializable("VodInfo");
            mSourceKeyStr = bundle.getString("sourceKey");
            mSiteBean = ApiConfig.getInstance().getSiteBean(mSourceKeyStr);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        L.d("onResume()");
        if (mVideoView != null) {
            mVideoView.resume();
        }
        if (mPlaybackOptions != null) mPlaybackOptions.onResume();
        if (mHandler != null) {
            mHandler.removeCallbacks(mProgressSaveRunnable);
            mHandler.postDelayed(mProgressSaveRunnable, 5000L);
        }
    }

    private boolean isActivityActive() {
        return !isFinishing() && !isDestroyed();
    }

    private void initView() {
        mVideoView = findViewById(R.id.mVideoView);
        // 点播开启磁盘缓存：边看边存，拖动/回看命中本地缓存（看完即释放由播放器负责）
        xyz.doikki.videoplayer.exo.ExoMediaSourceHelper.getInstance(this).setCacheEnabled(true);
        mResumeRestartHint = findViewById(R.id.resume_restart_hint);
        mResumeRestartHint.setOnClickListener(v -> restartFromResumeHint());
        mTimeTV = findViewById(R.id.system_time_tv);
        mPlayLoadTipTV = findViewById(R.id.play_load_tip);
        mMovieNameTV = findViewById(R.id.movie_name_tv);
        mAdTipTV = findViewById(R.id.play_ad_tip);
        mPlayLoadingPb = findViewById(R.id.play_loading);
        mPlayErrImg = findViewById(R.id.play_load_error);
        mVideoView.addOnStateChangeListener(new VideoView.SimpleOnStateChangeListener() {
            @Override
            public void onPlayStateChanged(int playState) {
                if (playState == VideoView.STATE_ERROR || playState == VideoView.STATE_PLAYBACK_COMPLETED
                        || playState == VideoView.STATE_PAUSED) dismissResumePrompt(false);
                if (playState == VideoView.STATE_PREPARED) {
                    if (mResumePosition > 0) {
                        long resumePosition = mResumePosition;
                        mResumePosition = 0;
                        if (mHasSavedResumePosition && !mResumePromptShown && !autoResumeWithoutPrompt
                                && hasUsefulResumePosition(resumePosition)) {
                            mResumeHintPending = true;
                        }
                        autoResumeWithoutPrompt = false;
                        mVideoView.seekTo(resumePosition);
                    }
                } else if (playState == VideoView.STATE_PREPARING || playState == VideoView.STATE_BUFFERING) {
                    showTipsView("缓冲中...", true, false);
                } else if (playState == VideoView.STATE_PLAYING) {
                    playbackStartPending = false;
                    mHandler.removeCallbacks(playbackStartTimeout);
                    hideTipsView();
                    if (mResumeHintPending) showResumePrompt();
                } else if (playState == VideoView.STATE_BUFFERED) {
                    hideTipsView();
                } else if (playState == VideoView.STATE_PLAYBACK_COMPLETED) {
                    if (mResumeKey != null) {
                        saveProgressValue(mResumeKey, 0);
                    }
                } else if (playState == VideoView.STATE_ERROR) {
                    playbackStartPending = false;
                    mHandler.removeCallbacks(playbackStartTimeout);
                    errorWithRetry("视频播放出错", false, true);
                }
            }
        });
        mMovieNameTV.setOnClickListener(this);
        findViewById(R.id.player_back).setOnClickListener(this);
        //触摸屏幕（手机/平板）时同样重新显示，但不拦截手势
        mVideoView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                showOverlaysThenAutoHide();
            }
            return false;
        });
        addControl();
        addProcessMng();
    }

    private void initModelObserver() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        Initer.init(this);
        sourceViewModel.playResult.observe(this, new Observer<JSONObject>() {
            @Override
            public void onChanged(JSONObject info) {
                L.d("observe onChanged():" + new Gson().toJson(info));
                if (info != null) {
                    if (mPlaybackOptions != null) mPlaybackOptions.setSources(info);
                    mPlayBean = UiHelper.getPlayBean(info);
                    String url = null;
                    if (!NullUtil.isNull(mPlayBean.playUrl) && DefaultConfig.isVideoFormat2(mPlayBean.playUrl)) {
                        url = mPlayBean.playUrl;
                    } else {
                        if (!NullUtil.isNull(mPlayBean.url)) {
                            url = mPlayBean.url;
                        }
                    }
                    if (!NullUtil.isNull(url) && DefaultConfig.isVideoFormat2(url)) {
                        playVideo(url, mPlayBean.headersMap);
                    } else if (!NullUtil.isNull(url) && DefaultConfig.isVideoFormat(url)) {
                        playVideo(url, mPlayBean.headersMap);
                    } else {
                        boolean bUseJxList = (mPlayBean.playUrl.isEmpty() && ApiConfig.getInstance().getmVipFlagList().contains(mPlayBean.flag)) || mPlayBean.bNeedJx;
                        initParse(mPlayBean.flag, bUseJxList, mPlayBean.playUrl, mPlayBean.url);
                    }
                } else {
                    errorWithRetry("获取播放地址错误", true);
                }
            }
        });
    }

    private void initPlayerConfig() {
        try {
            mVodPlayerCfgJsonObject = new JSONObject(mVodInfo.playerCfg);
        } catch (Throwable th) {
            mVodPlayerCfgJsonObject = new JSONObject();
        }
        try {
            // 只有用户主动选过内核（或本片已自动回退过）才用影片自己存的 pl；
            // 旧版本没这个标记，存下来的只是当年的默认值，统一跟随全局默认，
            // 否则升级后老片仍停在 IJK，磁盘缓存等于没开。
            int globalPlayType = Hawk.get(HawkConfig.PLAY_TYPE, HawkConfig.PLAY_TYPE_DEFAULT);
            if (!mVodPlayerCfgJsonObject.optBoolean("pl_pinned", false)) {
                mVodPlayerCfgJsonObject.put("pl", globalPlayType);
            }
            if (!mVodPlayerCfgJsonObject.has("pr")) {
                mVodPlayerCfgJsonObject.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, HawkConfig.PLAY_RENDER_DEFAULT));
            }
            if (!mVodPlayerCfgJsonObject.has("ijk")) {
                mVodPlayerCfgJsonObject.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, ""));
            }
            // 已有影片统一恢复一次默认比例，此后的手动选择不再重置。
            if (mVodPlayerCfgJsonObject.optInt("scv", 0) < 4) {
                int scale = mVodPlayerCfgJsonObject.has("sc") ? VideoView.SCREEN_SCALE_DEFAULT
                        : Hawk.get(HawkConfig.PLAY_SCALE, HawkConfig.PLAY_SCALE_DEFAULT);
                mVodPlayerCfgJsonObject.put("sc", scale);
                mVodPlayerCfgJsonObject.put("scv", 4);
            }
            if (!mVodPlayerCfgJsonObject.has("sc")) {
                mVodPlayerCfgJsonObject.put("sc", Hawk.get(HawkConfig.PLAY_SCALE, HawkConfig.PLAY_SCALE_DEFAULT));
            }
            if (!mVodPlayerCfgJsonObject.has("sp")) {
                mVodPlayerCfgJsonObject.put("sp", 1.0f);
            }
            if (!mVodPlayerCfgJsonObject.has("st")) {
                mVodPlayerCfgJsonObject.put("st", Hawk.get(HawkConfig.PLAY_TIME_START, 0));
            }
            if (!mVodPlayerCfgJsonObject.has("et")) {
                mVodPlayerCfgJsonObject.put("et", Hawk.get(HawkConfig.PLAY_TIME_END, 0));
            }
            if (!mVodPlayerCfgJsonObject.has("audio_language")) {
                mVodPlayerCfgJsonObject.put("audio_language",
                        Hawk.get(HawkConfig.AUDIO_LANGUAGE, ""));
            }
            if (!mVodPlayerCfgJsonObject.has("subtitle_language")) {
                mVodPlayerCfgJsonObject.put("subtitle_language",
                        Hawk.get(HawkConfig.SUBTITLE_LANGUAGE, ""));
            }
            if (!mVodPlayerCfgJsonObject.has("subtitle_off")) {
                mVodPlayerCfgJsonObject.put("subtitle_off",
                        Hawk.get(HawkConfig.SUBTITLE_OFF, false));
            }
        } catch (Throwable th) {
            L.e(th.getMessage());
        }
        mController.setPlayerConfig(mVodPlayerCfgJsonObject);
    }

    private void preGetPlayUrl() {
        if (!isActivityActive() || mVodInfo == null || mVodInfo.seriesMap == null
                || mVodInfo.playFlag == null || mHandler == null || mController == null) {
            return;
        }
        java.util.List<VodInfo.VodSeries> episodes = mVodInfo.seriesMap.get(mVodInfo.playFlag);
        if (episodes == null || mVodInfo.playIndex < 0 || mVodInfo.playIndex >= episodes.size()) {
            L.e("preGetPlayUrl() ignored because current episode is invalid");
            return;
        }
        L.d("preGetPlayUrl() start");
        dismissResumePrompt(false);
        if (mPlaybackOptions != null) {
            mPlaybackOptions.stopSession();
            mPlaybackOptions.setSources(null);
        }
        mHandler.removeCallbacks(playbackStartTimeout);
        playbackStartPending = true;
        mHandler.postDelayed(playbackStartTimeout, 20000L);
        VodInfo.VodSeries vs = episodes.get(mVodInfo.playIndex);
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodInfo.playIndex));
        showTipsView("正在获取播放信息", true, false);
        String title = mVodInfo.name + " " + vs.name;
        mMovieNameTV.setText(title);
        mController.setTitle("");
        playVideo(null, null); //stopPlay

        boolean bSuccess = Thunder.playStartWithTvbox(vs.url, new Thunder.ThunderCallback() {
            @Override
            public void status(int code, String info) {
                L.d("Thunder.play status()  info = " + info);
                if (code < 0) {
                    showTipsView(info, false, true);
                } else {
                    showTipsView(info, true, false);
                }
            }

            @Override
            public void list(String playList) {
                L.d("Thunder.play list() ");
            }

            @Override
            public void play(String url) {
                L.d("Thunder.play play() ");
                playVideo(url, null);
            }
        });

        if (!bSuccess) {
            String progressKey = mVodInfo.sourceKey + mVodInfo.id + mVodInfo.playFlag + mVodInfo.playIndex;
            sourceViewModel.getPlayUrl(mSourceKeyStr, mVodInfo.playFlag, progressKey, vs.url);
        }
    }

    private void addControl() {
        mController = new VodController(this);
        mController.setCanChangePosition(true);
        mController.setEnableInNormal(true);
        mController.setFocusable(true);
        mController.setFocusableInTouchMode(true);
        mController.setGestureEnabled(true);
        mController.setControlListener(new VodController.VodControlListener() {
            @Override
            public void onControlsVisibilityChanged(boolean visible) {
                if (mHandler != null) mHandler.removeCallbacks(mHideOverlaysRunnable);
                setOverlaysVisible(visible);
            }

            @Override
            public void playNext(boolean rmProgress) {
                String preProgressKey = buildStableProgressKey();
                PlayActivity.this.playNext();
                if (rmProgress && preProgressKey != null) {
                    final String k = preProgressKey;
                    AsyncTask.SERIAL_EXECUTOR.execute(() -> {
                        try {
                            CacheManager.delete(MD5.string2MD5(k), 0);
                        } catch (Throwable ignored) {
                        }
                    });
                }
            }

            @Override
            public void playPre() {
                playPrevious();
            }

            @Override
            public void playEpisode(int index) {
                PlayActivity.this.playEpisode(index);
            }

            @Override
            public void changeParse(ParseBean pb) {
                autoRetryCount = 0;
                doParse(pb);
            }

            @Override
            public void updatePlayerCfg() {
                // 用户手动换内核（含换回 Exo）后，允许重新走一次失败回退判断
                kernelFallbackTried = false;
                savePlayerConfig();
            }

            @Override
            public void replay() {
                autoRetryCount = 0;
                preGetPlayUrl();
            }

            @Override
            public void errReplay() {
                errorWithRetry("视频播放出错", false, true);
            }

            @Override
            public void showSubtitles() {
                if (mPlaybackOptions != null) mPlaybackOptions.showSubtitles();
            }

            @Override
            public void showAudioTracks() {
                if (mPlaybackOptions != null) mPlaybackOptions.showAudioTracks();
            }
        });
        mVideoView.addControlView(mController);
        if (mVodInfo != null && mVodInfo.seriesMap != null && mVodInfo.playFlag != null) {
            mController.setEpisodes(mVodInfo.seriesMap.get(mVodInfo.playFlag), mVodInfo.playIndex);
        }
        mController.requestFocus();
    }

    private void savePlayerConfig() {
        if (mVodInfo == null || mVodPlayerCfgJsonObject == null) return;
        mVodInfo.playerCfg = mVodPlayerCfgJsonObject.toString();
        // 异步持久化配置，避免切换设置时阻塞主线程导致画面卡顿
        final VodInfo info = mVodInfo;
        final String sKey = mSourceKeyStr;
        AsyncTask.SERIAL_EXECUTOR.execute(() -> {
            try {
                RoomDataManger.insertVodRecord(sKey, info);
            } catch (Throwable ignored) {
            }
        });
        EventBus.getDefault().post(new RefreshEvent(RefreshEvent.TYPE_REFRESH, mVodPlayerCfgJsonObject));
    }

    private void addProcessMng(){
        mVideoView.setProgressManager(null);
    }

    private long readSavedProgress(String key) {
        if (key == null || key.trim().isEmpty()) return 0L;
        Object cached = CacheManager.getCache(MD5.string2MD5(key));
        return cached instanceof Number ? Math.max(0L, ((Number) cached).longValue()) : 0L;
    }

    private void saveProgressValue(String key, long position) {
        if (key == null || key.trim().isEmpty()) return;
        // 播放进度高频保存改走异步线程，消除主线程每 5 秒同步写库导致的微卡顿
        AsyncTask.SERIAL_EXECUTOR.execute(() -> {
            try {
                CacheManager.save(MD5.string2MD5(key), position);
            } catch (Throwable ignored) {
            }
        });
    }

    private String buildStableProgressKey() {
        if (mVodInfo == null) return mPlayBean == null ? "" : mPlayBean.progressKeyStr;
        //source 以启动参数为准（详情页传入的 sourceKey），避免不同入口取到不同的 mVodInfo.sourceKey
        String source = mSourceKeyStr;
        if (source == null || source.trim().isEmpty()) source = mVodInfo.sourceKey;
        return "resume|v2|" + source + "|" + mVodInfo.id + "|" + mVodInfo.playFlag + "|" + mVodInfo.playIndex;
    }

    private String buildEpisodeProgressKey() {
        if (mVodInfo == null || mVodInfo.seriesMap == null || mVodInfo.playFlag == null) {
            return buildStableProgressKey();
        }
        java.util.List<VodInfo.VodSeries> episodes = mVodInfo.seriesMap.get(mVodInfo.playFlag);
        if (episodes == null || mVodInfo.playIndex < 0 || mVodInfo.playIndex >= episodes.size()) {
            return buildStableProgressKey();
        }
        VodInfo.VodSeries episode = episodes.get(mVodInfo.playIndex);
        String episodeIdentity = episode.url;
        if (episodeIdentity == null || episodeIdentity.trim().isEmpty()) {
            episodeIdentity = episode.name;
        }
        if (episodeIdentity == null || episodeIdentity.trim().isEmpty()) {
            return buildStableProgressKey();
        }
        String source = mSourceKeyStr;
        if (source == null || source.trim().isEmpty()) source = mVodInfo.sourceKey;
        return "resume|v3|" + source + "|" + mVodInfo.id + "|" + mVodInfo.playFlag
                + "|" + episodeIdentity;
    }

    private String buildLegacyProgressKey() {
        if (mPlayBean != null && mPlayBean.progressKeyStr != null && !mPlayBean.progressKeyStr.trim().isEmpty()) {
            return mPlayBean.progressKeyStr;
        }
        if (mCurrentPlayUrl != null && !mCurrentPlayUrl.trim().isEmpty()) {
            return mCurrentPlayUrl;
        }
        return null;
    }

    private void errorWithRetry(String err, boolean finish) {
        errorWithRetry(err, finish, false);
    }

    /**
     * @param fromPlayer true 表示错误来自播放器本身（起播失败、起播超时、播放中报错），
     *                   这类错误先尝试 Exo→IJK 内核回退，再走原来的解析重试
     */
    private void errorWithRetry(String err, boolean finish, boolean fromPlayer) {
        if (!isActivityActive() || mVodInfo == null || mVodInfo.seriesMap == null
                || mVodInfo.playFlag == null || mHandler == null) {
            return;
        }
        java.util.List<VodInfo.VodSeries> episodes = mVodInfo.seriesMap.get(mVodInfo.playFlag);
        if (episodes == null || mVodInfo.playIndex < 0 || mVodInfo.playIndex >= episodes.size()) {
            L.e("errorWithRetry() ignored because current episode is invalid");
            return;
        }
        L.d("errorWithRetry() "+err);
        mHandler.removeCallbacks(playbackStartTimeout);
        playbackStartPending = false;
        VodInfo.VodSeries vs = episodes.get(mVodInfo.playIndex);
        SiteBean siteBean = ApiConfig.getInstance().getSiteBean(mVodInfo.sourceKey);
        String site = siteBean == null ? mVodInfo.sourceKey : siteBean.getName();
        PrintUtil.saveError(site + "|" + mVodInfo.name + vs.name, err);
        runOnUiThread(() -> {
            if (!isActivityActive()) return;
            if (fromPlayer && fallbackToIjkIfNeeded(err)) return;
            if (!doRetry()) {
                if (finish) {
                    Toast.makeText(mContext, err, Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    showTipsView(err, false, true);
                }
            }
        });
    }

    /**
     * Exo 内核播放失败时自动回退 IJK：保留当前进度，用同一播放地址重播。
     * 只对点播自带播放器生效，同一部片只回退一次；回退后本片播放内核记为 IJK，
     * 后续集数直接用 IJK 播放，不会反复经历 Exo 失败。
     */
    private boolean fallbackToIjkIfNeeded(String err) {
        if (kernelFallbackTried || mVodPlayerCfgJsonObject == null || mVideoView == null) return false;
        if (mVodPlayerCfgJsonObject.optInt("pl", -1) != HawkConfig.PLAY_TYPE_EXO) return false;
        kernelFallbackTried = true;
        long position = mVideoView.getCurrentPosition();
        if (position > 0) {
            String key = buildEpisodeProgressKey();
            mResumeKey = key;
            saveProgressValue(key, position);
            mResumePosition = position;
            mHasSavedResumePosition = position >= RESUME_PROMPT_MIN_POSITION_MS;
            autoResumeWithoutPrompt = true;
        }
        try {
            mVodPlayerCfgJsonObject.put("pl", HawkConfig.PLAY_TYPE_IJK);
            // 标记为已固定：下次打开本片继续用 IJK，不再反复撞 Exo 的错误
            mVodPlayerCfgJsonObject.put("pl_pinned", true);
        } catch (JSONException ignored) {
        }
        savePlayerConfig();
        L.d("Exo 播放失败，自动回退 IJK，position=" + position + " err=" + err);
        showTipsView("该片源用 Exo 放不了，已自动切换到 IJK", true, false);
        autoRetryCount = 0;
        String url = mCurrentPlayUrl;
        if (!NullUtil.isNull(url)) {
            playVideo(url, mPlayBean == null ? null : mPlayBean.headersMap);
        } else {
            preGetPlayUrl();
        }
        return true;
    }

    private boolean doRetry() {
        L.d("doRetry （）autoRetryCount= " + autoRetryCount);
        int parseSize = ApiConfig.getInstance().getmParseBeanList().size();
        if (autoRetryCount < parseSize) {
            autoRetryCount++;
            preGetPlayUrl();
            return true;
        } else {
            autoRetryCount = 0;
            if (mPlayBean == null) return false;
            //最后重试播放url
            String url = null;
            if (!NullUtil.isNull(mPlayBean.playUrl)) {
                url = mPlayBean.playUrl;
            }
            if (!NullUtil.isNull(mPlayBean.url)) {
                url = mPlayBean.url;
            }
            if (!NullUtil.isNull(url)) {
                initWebViewAndLoad(url);
            }
            return false;
        }
    }

    private void playVideo(final String url, HashMap<String, String> headers) {
        L.d("playVideo() stop if url==null, url=" + url);
        runOnUiThread(() -> {
            if (!isActivityActive() || mVideoView == null || mVodInfo == null
                    || mVodInfo.seriesMap == null || mVodInfo.playFlag == null
                    || mPlayBean == null || mController == null) {
                return;
            }
            java.util.List<VodInfo.VodSeries> episodes = mVodInfo.seriesMap.get(mVodInfo.playFlag);
            if (episodes == null || mVodInfo.playIndex < 0 || mVodInfo.playIndex >= episodes.size()) {
                L.e("playVideo() ignored because current episode is invalid");
                return;
            }
            stopParse();
            VodInfo.VodSeries vs = episodes.get(mVodInfo.playIndex);
            String playTitle = mVodInfo.name + " " + vs.name;
            SiteBean siteBean = ApiConfig.getInstance().getSiteBean(mVodInfo.sourceKey);
            String site = siteBean == null ? mVodInfo.sourceKey : siteBean.getName();
            if (mVodPlayerCfgJsonObject == null) {
                L.e("playVideo() ignored because player configuration is missing");
                return;
            }
            //停止播放， 再开始
            saveCurrentProgress();
            mVideoView.release();
            if (mPlaybackOptions != null) mPlaybackOptions.stopSession();
            String playUrl = url;
            if (!NullUtil.isNull(playUrl)) {
                    showTipsView("缓冲中...", true, false);
                    //修复鑫鑫影视播放地址错误问题
                    if (playUrl.contains("bf.hhuus.com")) {
                        playUrl = playUrl.replace("bf.hhuus.com", "play.hhuus.com");
                        L.d("after replace playUrl=" + playUrl);
                    }
                    //记录最终播放地址，供旧版 URL 进度兜底读取
                    mCurrentPlayUrl = playUrl;

                    try {
                        int playerType = mVodPlayerCfgJsonObject.getInt("pl");
                        L.d("playerType=" + playerType);
                        if (playerType >= 10) {
                            showTipsView("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + "进行播放", true, false);
                            boolean bSuccess = false;
                            switch (playerType) {
                                case 10: {
                                    bSuccess = MXPlayer.run(PlayActivity.this, playUrl, playTitle, mPlayBean.playSubtitleStr, headers);
                                    break;
                                }
                                case 11: {
                                    bSuccess = ReexPlayer.run(PlayActivity.this, playUrl, playTitle, mPlayBean.playSubtitleStr, headers);
                                    break;
                                }
                            }
                            showTipsView("调用外部播放器" + PlayerHelper.getPlayerName(playerType) + (bSuccess ? "成功" : "失败"), bSuccess, !bSuccess);
                            playbackStartPending = false;
                            mHandler.removeCallbacks(playbackStartTimeout);
                            insertVod(mSourceKeyStr, mVodInfo);
                            return;
                        }
                    } catch (JSONException e) {
                        L.e("playVideo " + e.getMessage());
                    }
                    L.d("调用自带播放器播放");
                    PlayerHelper.updateCfg(mVideoView, mVodPlayerCfgJsonObject);
                    mLegacyProgressKey = mPlayBean.progressKeyStr;
                    prepareResumePosition();
                    mVideoView.setProgressKey(null);
                    if (headers != null) {
                        mVideoView.setUrl(playUrl, headers);
                    } else {
                        mVideoView.setUrl(playUrl);
                    }
                    mVideoView.setInfo("(" + site + ")" + playTitle);
                    mVideoView.start();
                    if (mPlaybackOptions != null) mPlaybackOptions.beginSession(headers);
                    showOverlaysThenAutoHide();
                    mController.resetSpeed();
                    insertVod(mSourceKeyStr, mVodInfo);
            }
        });
    }

    private void insertVod(String sourceKey, VodInfo vodInfo) {
        if (vodInfo == null) return;
        try {
            vodInfo.playNote = vodInfo.seriesMap.get(vodInfo.playFlag).get(vodInfo.playIndex).name;
        } catch (Throwable th) {
            vodInfo.playNote = "";
        }
        AsyncTask.SERIAL_EXECUTOR.execute(() -> {
            try {
                RoomDataManger.insertVodRecord(sourceKey, vodInfo);
            } catch (Throwable ignored) {
            }
        });
    }

    private void playNext() {
        boolean hasNext;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasNext = false;
        } else {
            hasNext = mVodInfo.playIndex + 1 < mVodInfo.seriesMap.get(mVodInfo.playFlag).size();
        }
        if (!hasNext) {
            Toast.makeText(this, "已经是最后一集了!", Toast.LENGTH_SHORT).show();
            return;
        }
        mVodInfo.playIndex++;
        mController.setCurrentEpisode(mVodInfo.playIndex);
        preGetPlayUrl();
    }

    private void playPrevious() {
        boolean hasPre = true;
        if (mVodInfo == null || mVodInfo.seriesMap.get(mVodInfo.playFlag) == null) {
            hasPre = false;
        } else {
            hasPre = mVodInfo.playIndex - 1 >= 0;
        }
        if (!hasPre) {
            Toast.makeText(this, "已经是第一集了!", Toast.LENGTH_SHORT).show();
            return;
        }
        mVodInfo.playIndex--;
        mController.setCurrentEpisode(mVodInfo.playIndex);
        preGetPlayUrl();
    }

    private void playEpisode(int index) {
        if (mVodInfo == null || mVodInfo.seriesMap == null || mVodInfo.playFlag == null) {
            return;
        }
        java.util.List<VodInfo.VodSeries> episodes = mVodInfo.seriesMap.get(mVodInfo.playFlag);
        if (episodes == null || index < 0 || index >= episodes.size() || index == mVodInfo.playIndex) {
            return;
        }
        mVodInfo.playIndex = index;
        mController.setCurrentEpisode(index);
        preGetPlayUrl();
    }


    private void initParse(String flag, boolean useParse, String playUrl, final String url) {
        L.d("initParse() " + useParse + "=useParse \t url =" + url);
        parseFlagStr = flag;
        mWebUrl = url;
        ParseBean parseBean = null;
        if (useParse) {
            if (autoRetryCount == 0) {
                parseBean = ApiConfig.getInstance().getDefaultParse();
            } else {
                parseBean = ApiConfig.getInstance().getmParseBeanList().get(autoRetryCount-1);
            }
        } else {
            if (playUrl.startsWith("json:")) {
                parseBean = new ParseBean();
                parseBean.setType(1);
                parseBean.setUrl(playUrl.substring(5));
            } else if (playUrl.startsWith("parse:")) {
                String parseRedirect = playUrl.substring(6);
                for (ParseBean pb : ApiConfig.getInstance().getmParseBeanList()) {
                    if (pb.getName().equals(parseRedirect)) {
                        parseBean = pb;
                        break;
                    }
                }
            }
        }
        if (parseBean == null) {
            parseBean = new ParseBean();
            parseBean.setType(0);
            parseBean.setUrl(playUrl);
        }
        m_bLoadFound = false;
        doParse(parseBean);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.player_back:
            case R.id.movie_name_tv:
                if (mController != null && mController.bLocked) {
                    mController.showLockImg();
                    return;
                }
                finish();
                break;
            default:
                break;
        }
    }

    @Override
    public void onBackPressed() {
        if (mController != null && mController.cancelAutoNextCountdown()) return;
        if (mController != null && mController.cancelSpeedBoost(true)) return;
        if (mController != null && mController.lockWarning()) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event != null) {
            int keyCode = event.getKeyCode();
            if (mResumeHintConsumedKey != KeyEvent.KEYCODE_UNKNOWN && keyCode == mResumeHintConsumedKey) {
                if (event.getAction() == KeyEvent.ACTION_UP) mResumeHintConsumedKey = KeyEvent.KEYCODE_UNKNOWN;
                return true;
            }
            if (isResumeHintVisible() && event.getAction() == KeyEvent.ACTION_DOWN) {
                boolean volumeKey = keyCode == KeyEvent.KEYCODE_VOLUME_UP
                        || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE;
                if (!volumeKey) {
                    mResumeHintConsumedKey = keyCode;
                    if (event.getRepeatCount() == 0 && (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER)) {
                        restartFromResumeHint();
                    } else {
                        dismissResumePrompt(true);
                    }
                    return true;
                }
                dismissResumePrompt(true);
            }
            //有按键操作时重新显示已自动收起的节目介绍/时间/右下提示，5 秒后再次收起
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                showOverlaysThenAutoHide();
                if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                    if (mController != null) {
                        mController.showEpisodeSelector();
                    }
                    return true;
                }
            }
            if (mController != null && mController.dealKeyEvent(event)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN && isResumeHintVisible()) {
            Rect bounds = new Rect();
            mResumeRestartHint.getGlobalVisibleRect(bounds);
            if (!bounds.contains((int) event.getRawX(), (int) event.getRawY())) dismissResumePrompt(true);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    protected void onPause() {
        dismissResumePrompt(false);
        mResumeHintConsumedKey = KeyEvent.KEYCODE_UNKNOWN;
        if (mController != null) mController.cancelAutoNextCountdown();
        super.onPause();
        if (mPlaybackOptions != null) mPlaybackOptions.onPause();
        if (mHandler != null) mHandler.removeCallbacks(mProgressSaveRunnable);
        if (mVideoView != null) {
            mVideoView.pause();
        }
        saveCurrentProgress();
    }

    @Override
    protected void onStop() {
        saveCurrentProgress();
        super.onStop();
        UiHelper.upPlayResult(getApplicationContext());
    }

    private void saveCurrentProgress() {
        if (mVideoView == null || mVodInfo == null) return;
        long position = mController != null ? mController.getCurrentOrPendingPosition() : mVideoView.getCurrentPosition();
        long duration = mVideoView.getDuration();
        if (position <= 0 || (duration > 0 && position >= duration - 1000)) return;
        String key = buildEpisodeProgressKey();
        saveProgressValue(key, position);
        L.d("resume save key=" + key + " position=" + position);
    }

    private void prepareResumePosition() {
        dismissResumePrompt(false);
        mResumePromptShown = false;
        mResumeKey = buildEpisodeProgressKey();
        mResumePosition = readSavedProgress(mResumeKey);
        mHasSavedResumePosition = mResumePosition >= RESUME_PROMPT_MIN_POSITION_MS;
        if (mResumePosition <= 0) {
            int st = 0;
            try {
                if (mVodPlayerCfgJsonObject != null) st = mVodPlayerCfgJsonObject.getInt("st");
            } catch (JSONException e) {
                L.e(e);
            }
            mResumePosition = st * 1000L;
        }
        L.d("resume read key=" + mResumeKey + " position=" + mResumePosition);
    }

    private boolean hasUsefulResumePosition(long position) {
        if (position < RESUME_PROMPT_MIN_POSITION_MS) return false;
        long duration = mVideoView == null ? 0L : mVideoView.getDuration();
        return duration <= 0 || position < duration - 15_000L;
    }

    private void showResumePrompt() {
        mResumeHintPending = false;
        if (mVideoView == null || !isActivityActive() || mResumePromptShown) return;
        mResumePromptShown = true;
        mResumeHintDeadline = SystemClock.uptimeMillis() + 5000L;
        mResumeRestartHint.setVisibility(View.VISIBLE);
        mResumeRestartHint.setAlpha(0f);
        mResumeRestartHint.animate().alpha(1f).setDuration(150L).start();
        mResumeRestartHint.requestFocus();
        refreshResumeHintCountdown();
    }

    private boolean isResumeHintVisible() {
        return mResumeRestartHint != null && mResumeRestartHint.getVisibility() == View.VISIBLE;
    }

    private void refreshResumeHintCountdown() {
        if (!isResumeHintVisible()) return;
        long remaining = mResumeHintDeadline - SystemClock.uptimeMillis();
        if (remaining <= 0L) {
            dismissResumePrompt(true);
            return;
        }
        mResumeRestartHint.setText("从头开始 · " + ((remaining + 999L) / 1000L) + "秒");
        mHandler.removeCallbacks(mResumeHintTick);
        mHandler.postDelayed(mResumeHintTick, Math.min(1000L, remaining));
    }

    private void restartFromResumeHint() {
        if (!isResumeHintVisible()) return;
        if (SystemClock.uptimeMillis() >= mResumeHintDeadline) {
            dismissResumePrompt(true);
            return;
        }
        dismissResumePrompt(true);
        if (mVideoView == null || !isActivityActive()) return;
        mResumePosition = 0L;
        mHasSavedResumePosition = false;
        saveProgressValue(mResumeKey, 0L);
        mVideoView.seekTo(0L);
        mVideoView.start();
    }

    private void dismissResumePrompt(boolean restoreFocus) {
        mResumeHintPending = false;
        if (mHandler != null) mHandler.removeCallbacks(mResumeHintTick);
        if (mResumeRestartHint == null) return;
        boolean hadFocus = mResumeRestartHint.hasFocus();
        mResumeRestartHint.animate().cancel();
        mResumeRestartHint.setVisibility(View.GONE);
        if (restoreFocus && hadFocus && mController != null && isActivityActive()) mController.requestFocus();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissResumePrompt(false);
        if (mHandler != null) {
            mHandler.removeCallbacks(mProgressSaveRunnable);
        }
        if (mPlaybackOptions != null) {
            mPlaybackOptions.release();
            mPlaybackOptions = null;
        }
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        stopLoadWebView(true);
        stopParse();
        if (mController != null) {
            mController.releaseController();
            mController = null;
        }
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
            mExecutorService = null;
        }
        if (null != mHandler) {
            // removeCallbacks(null) 在 MessageQueue 里会直接 return，清不掉任何定时器，
            // 会导致 showSystemTime() 的每秒自循环在退出后继续持有本 Activity 并刷新已销毁的视图
            mHandler.removeCallbacksAndMessages(null);
        }
        playbackStartPending = false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PlaybackOptionsManager.REQUEST_SUBTITLE && resultCode == Activity.RESULT_OK
                && data != null && data.getData() != null && mPlaybackOptions != null) {
            mPlaybackOptions.chooseLocalSubtitleResult(data.getData());
        }
    }

    private void stopParse() {
        stopLoadWebView(false);
        m_bLoadFound = false;
        OkGo.getInstance().cancelTag("json_jx");
    }

    private void doParse(ParseBean parseBean) {
        if (!isActivityActive() || parseBean == null || mExecutorService == null) return;
        stopParse();
        L.d("doParse parseBean.getType()=" + parseBean.getType() + " \t" +parseBean.getName());
        if (parseBean.getType() == 0) {
            showTipsView("缓冲中...", true, false);
            initWebViewAndLoad(parseBean.getUrl() + mWebUrl);
        } else if (parseBean.getType() == 1) { // json 解析
            showTipsView("正在解析播放地址", true, false);
            requestJx1Url(parseBean);
        } else if (parseBean.getType() == 2) { // json 扩展
            showTipsView("正在解析扩展播放地址", true, false);
            LinkedHashMap<String, String> jxsMap = new LinkedHashMap<>();
            for (ParseBean p : ApiConfig.getInstance().getmParseBeanList()) {
                if (p.getType() == 1) {
                    jxsMap.put(p.getName(), p.mixUrl());
                }
            }
            mExecutorService.execute(() -> {
                JSONObject rs = ApiConfig.getInstance().jarExt(parseBean.getUrl(), jxsMap, mWebUrl);
                if (rs == null || !rs.has("url")) {
                    errorWithRetry("解析错误", false);
                } else {
                    boolean bParseWV = (rs.optInt("parse", 0) == 1);
                    if (bParseWV) {
                        String wvUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                        loadInWebView(wvUrl);
                    } else {
                        playVideo(rs.optString("url", ""), UiHelper.getHttpHeader(rs));
                    }
                }
            });
        } else if (parseBean.getType() == 3) { // json 聚合
            showTipsView("正在解析聚合播放地址", true, false);
            LinkedHashMap<String, HashMap<String, String>> jxsMap = new LinkedHashMap<>();
            String extendName = "";
            for (ParseBean p : ApiConfig.getInstance().getmParseBeanList()) {
                HashMap map = new HashMap<String, String>();
                map.put("url", p.getUrl());
                if (p.getUrl().equals(parseBean.getUrl())) {
                    extendName = p.getName();
                }
                map.put("type", p.getType() + "");
                map.put("ext", p.getExt());
                jxsMap.put(p.getName(), map);
            }
            String finalExtendName = extendName;
            mExecutorService.execute(() -> {
                JSONObject rs = ApiConfig.getInstance().jarExtMix(parseFlagStr + "111", parseBean.getUrl(), finalExtendName, jxsMap, mWebUrl);
                if (rs == null || !rs.has("url")) {
                    errorWithRetry("解析错误", false);
                } else {
                    if (rs.has("parse") && rs.optInt("parse", 0) == 1) {
                        runOnUiThread(() -> {
                            String mixParseUrl = DefaultConfig.checkReplaceProxy(rs.optString("url", ""));
                            stopParse();
                            showTipsView("缓冲中...", true, false);
                            initWebViewAndLoad(mixParseUrl);
                        });
                    } else {
                        playVideo(rs.optString("url", ""), UiHelper.getHttpHeader(rs));
                    }
                }
            });
        }
    }

    private void requestJx1Url(ParseBean parseBean) {
        L.d("OkGo get " + (parseBean.getUrl() + mWebUrl));
        OkGo.<String>get(parseBean.getUrl() + mWebUrl)
                .tag("json_jx")
                .headers(UiHelper.getHttpHeader(parseBean))
                .execute(new AbsCallback<String>() {
                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() != null) {
                            return response.body().string();
                        } else {
                            throw new IllegalStateException("网络请求错误");
                        }
                    }

                    @Override
                    public void onSuccess(Response<String> response) {
                        String json = response.body();
                        L.d("onSuccess json=" + json);
                        try {
                            JSONObject rs = UiHelper.jsonParse(json);
                            playVideo(rs.getString("url"), UiHelper.getHttpHeader(rs));
                        } catch (Throwable e) {
                            errorWithRetry("解析错误", false);
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        L.d("onError response=" + (response == null ? "" : response.toString()));
                        errorWithRetry("解析错误", false);
                    }
                });
    }

    private void initWebViewAndLoad(String url) {
        L.d("initWebViewAndLoad()  " + url);
        if (mSysWebView == null) {
            doInitWebView();
            loadInWebView(url);
        } else {
            loadInWebView(url);
        }
    }

    private void doInitWebView() {
        mSysWebView = new MyWebView(mContext);
        configWebViewSys(mSysWebView);
    }

    private void loadInWebView(String url) {
        L.d("loadInWebView() url=" + url);
        runOnUiThread(() -> {
            if (isActivityActive() && mSysWebView != null) {
                mSysWebView.stopLoading();
                mSysWebView.clearCache(true);
                mSysWebView.loadUrl(url);
            }
        });
    }

    private void stopLoadWebView(boolean bDestroy) {
        runOnUiThread(() -> {
            if (mSysWebView != null) {
                mSysWebView.stopLoading();
                mSysWebView.loadUrl("about:blank");
                if (bDestroy) {
                    if (mSysWebView.getParent() instanceof ViewGroup) {
                        ((ViewGroup) mSysWebView.getParent()).removeView(mSysWebView);
                    }
                    mSysWebView.clearCache(true);
                    mSysWebView.removeAllViews();
                    mSysWebView.destroy();
                    mSysWebView = null;
                }
            }
        });
    }

    private boolean checkVideoFormat(String url) {
        return DefaultConfig.isVideoFormat(url);
    }

    private void showSystemTime() {
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mTimeTV.setText(StringUtil.getFormatTimeStr(System.currentTimeMillis()));
                mHandler.postDelayed(this, 1000);
            }
        }, 1000);
    }

    /**
     * 顶部节目介绍、右上角时间、右下角提示：播放中显示 5 秒后自动收起，按键操作时重新显示
     */
    private void setOverlaysVisible(boolean visible) {
        int vis = visible ? View.VISIBLE : View.GONE;
        View topBar = findViewById(R.id.player_top_bar);
        if (topBar != null) topBar.setVisibility(vis);
        if (mMovieNameTV != null) mMovieNameTV.setVisibility(vis);
        if (mTimeTV != null) mTimeTV.setVisibility(vis);
        if (mAdTipTV != null) mAdTipTV.setVisibility(vis);
    }

    private void showOverlaysThenAutoHide() {
        setOverlaysVisible(true);
        if (mHandler != null) {
            mHandler.removeCallbacks(mHideOverlaysRunnable);
            mHandler.postDelayed(mHideOverlaysRunnable, 5000);
        }
    }

    private void showTipsView(String msg, boolean loading, boolean err) {
        if (!isActivityActive() || mHandler == null) return;
        mHandler.post(() -> {
            if (!isActivityActive() || mPlayLoadTipTV == null
                    || mPlayLoadingPb == null || mPlayErrImg == null) return;
            mPlayLoadTipTV.setText(msg);
            mPlayLoadTipTV.setVisibility(View.VISIBLE);
            mPlayLoadingPb.setVisibility(loading ? View.VISIBLE : View.GONE);
            mPlayErrImg.setVisibility(err ? View.VISIBLE : View.GONE);
        });
    }

    private void hideTipsView() {
        if (!isActivityActive() || mHandler == null) return;
        mHandler.post(() -> {
            if (!isActivityActive() || mPlayLoadTipTV == null
                    || mPlayLoadingPb == null || mPlayErrImg == null) return;
            mPlayLoadTipTV.setVisibility(View.GONE);
            mPlayLoadingPb.setVisibility(View.GONE);
            mPlayErrImg.setVisibility(View.GONE);
        });
    }

    private void configWebViewSys(WebView webView) {
        if (webView == null) return;
        ViewGroup.LayoutParams layoutParams = Hawk.get(HawkConfig.DEBUG_OPEN, false)
                ? new ViewGroup.LayoutParams(800, 400) :
                new ViewGroup.LayoutParams(1, 1);
        webView.setFocusable(false);
        webView.setFocusableInTouchMode(false);
        webView.clearFocus();
        webView.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        addContentView(webView, layoutParams);
        /* 添加webView配置 */
        final WebSettings settings = webView.getSettings();
        settings.setNeedInitialFocus(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccess(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptEnabled(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            settings.setMediaPlaybackRequiresUserGesture(false);
        }
        if (Hawk.get(HawkConfig.DEBUG_OPEN, false)) {
            settings.setBlockNetworkImage(false);
        } else {
            settings.setBlockNetworkImage(true);
        }
        settings.setUseWideViewPort(true);
        settings.setDomStorageEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadWithOverviewMode(true);
        settings.setBuiltInZoomControls(true);
        settings.setSupportZoom(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        /* 添加webView配置 */
        settings.setDefaultTextEncodingName("utf-8");
        settings.setUserAgentString(webView.getSettings().getUserAgentString());
        // settings.setUserAgentString(ANDROID_UA);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                return false;
            }

            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
                return true;
            }

            @Override
            public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
                return true;
            }
        });
        mSysWebClient = new SysWebClient();
        webView.setWebViewClient(mSysWebClient);
        webView.setBackgroundColor(Color.BLACK);
    }

    //========================webView类==============================================
    class MyWebView extends WebView {
        public MyWebView(@NonNull Context context) {
            super(context);
        }

        @Override
        public void setOverScrollMode(int mode) {
            super.setOverScrollMode(mode);
            if (mContext instanceof Activity) {
                AutoSize.autoConvertDensityOfCustomAdapt((Activity) mContext, PlayActivity.this);
            }
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return false;
        }
    }

    class SysWebClient extends WebViewClient {
        @Override
        public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
            sslErrorHandler.proceed();
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            return false;
        }

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
            L.d("shouldInterceptRequest() url");
            WebResourceResponse response = checkIsVideo(url, null);
            if (response == null) {
                return super.shouldInterceptRequest(view, url);
            } else {
                return response;
            }
        }

        @Override
        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            L.d("shouldInterceptRequest() request ");
            String url = request.getUrl().toString();
            HashMap<String, String> webHeaders = new HashMap<>();
            try {
                Map<String, String> hds = request.getRequestHeaders();
                for (String k : hds.keySet()) {
                    if (k.equalsIgnoreCase("user-agent")
                            || k.equalsIgnoreCase("referer")
                            || k.equalsIgnoreCase("origin")) {
                        webHeaders.put(k, " " + hds.get(k));
                    }
                }
            } catch (Throwable th) {
                L.w("shouldInterceptRequest " + th.getMessage());
            }
            WebResourceResponse response = checkIsVideo(url, webHeaders);
            if (response == null) {
                return super.shouldInterceptRequest(view, request);
            } else {
                return response;
            }
        }

        private WebResourceResponse checkIsVideo(String url, HashMap<String, String> headers) {
            L.d("checkIsVideo() url=" + url);
            if (url.endsWith("/favicon.ico")) {
                return new WebResourceResponse("image/png", null, null);
            }
            boolean isAd;
            if (!loadedUrlsMap.containsKey(url)) {
                isAd = AdBlocker.isAd(url);
                loadedUrlsMap.put(url, isAd);
            } else {
                isAd = loadedUrlsMap.get(url);
            }

            if (checkVideoFormat(url)) {
                m_bLoadFound = true;
            }
            playVideo(url, headers);
            stopLoadWebView(false);
            return isAd || m_bLoadFound ? AdBlocker.createEmptyResource() : null;
        }
    }

}
