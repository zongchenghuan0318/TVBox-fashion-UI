package com.github.tvbox.osc.ui.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.IntEvaluator;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LivePlayerManager;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.github.tvbox.osc.bean.LiveSettingItem;
import com.github.tvbox.osc.player.controller.LiveController;
import com.github.tvbox.osc.ui.adapter.LiveChannelGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveChannelItemAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingGroupAdapter;
import com.github.tvbox.osc.ui.adapter.LiveSettingItemAdapter;
import com.github.tvbox.osc.ui.dialog.LivePasswordDialog;
import com.github.tvbox.osc.ui.tv.widget.ViewObj;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.LiveSourceManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.StringUtil;
import com.lcstudio.commonsurport.util.UIUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.studio.osc.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import xyz.doikki.videoplayer.player.VideoView;
import xyz.doikki.videoplayer.util.PrintUtil;

/**
 * tvbox交流QQ群：879811030
 */
public class LivePlayActivity extends BaseActivity {
    private VideoView mVideoView;
    private TextView tvChannelInfo;
    private TextView tvTime;
    private TextView tvNetSpeed;
    private LinearLayout llLeftChannelListLayout;
    private TvRecyclerView mChannelRV;
    private TvRecyclerView mLiveRV;
    private LiveChannelGroupAdapter liveGroupAdapter;
    private LiveChannelItemAdapter liveChannelItemAdapter;

    private LinearLayout rightSettingLayout;
    private TvRecyclerView mSettingRV;
    private TvRecyclerView mSettingItemRV;
    private LiveSettingGroupAdapter liveSettingGroupAdapter;
    private LiveSettingItemAdapter liveSettingItemAdapter;
    private final List<LiveSettingGroup> mSettingGroupList = new ArrayList<>();
    private final List<LiveChannelGroup> mChannelGroupList = new ArrayList<>();
    private Handler mHandler;
    private int groupIndex = 0;
    private int channelIndex = -1;
    private int sourceTimes = 0;
    private int consecutiveChannelFailures = 0;
    private static final int MAX_CONSECUTIVE_AUTO_FAILS = 5;
    private int pendingGroupIndex = -1;
    private int pendingChannelIndex = -1;
    private LiveChannelItem currentLiveChannelItem = null;
    private boolean liveScreenResumed;
    private LivePlayerManager livePlayerManager;
    private final ArrayList<Integer> mPasswordConfirmedList = new ArrayList<>();
    private long mBegin = 0;
    private long firstTimes = 0;

    private TextView tvDigitalNum;
    private final StringBuilder mDigitChannelBuffer = new StringBuilder();
    private final Runnable mCommitDigitalTuneRun = new Runnable() {
        @Override
        public void run() {
            commitDigitalTune();
        }
    };

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_live_play;
    }

    @Override
    protected void init() {
        livePlayerManager = new LivePlayerManager();
        mHandler = new Handler();
        firstTimes = System.currentTimeMillis();
        PrintUtil.saveError("LivePlayActivity", "begin" + StringUtil.getFormat_Date_HH_MM_SS(System.currentTimeMillis()));

        setLoadSir(findViewById(R.id.live_root));
        mVideoView = findViewById(R.id.mVideoView);
        // 直播不写磁盘缓存，避免流式数据无限占用存储
        xyz.doikki.videoplayer.exo.ExoMediaSourceHelper.getInstance(this).setCacheEnabled(false);
        llLeftChannelListLayout = findViewById(R.id.tvLeftChannnelListLayout);
        mChannelRV = findViewById(R.id.mGroupGridView);
        mLiveRV = findViewById(R.id.mChannelGridView);
        rightSettingLayout = findViewById(R.id.tvRightSettingLayout);
        mSettingRV = findViewById(R.id.mSettingGroupView);
        mSettingItemRV = findViewById(R.id.mSettingItemView);
        tvChannelInfo = findViewById(R.id.tvChannel);
        tvDigitalNum = findViewById(R.id.tvDigitalNum);
        tvTime = findViewById(R.id.tvTime);
        tvNetSpeed = findViewById(R.id.tvNetSpeed);

        initVideoView();
        initChannelGroupView();
        initLiveChannelView();
        initSettingGroupView();
        initSettingItemView();
        initSite();
        initLiveSettingGroupList();
    }

    @Override
    protected void onResume() {
        super.onResume();
        liveScreenResumed = true;
        if (mVideoView != null) {
            mVideoView.resume();
        }
        if (currentLiveChannelItem != null) {
            showTime();
            showNetSpeed();
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                if (mDigitChannelBuffer.length() > 0) {
                    cancelDigitalTune();
                }
                showSettingGroup();
            } else if (!isListOrSettingLayoutVisible()) {
                if (isNumberKey(keyCode)) {
                    handleNumberInput(getNumberFromKey(keyCode));
                    return true;
                }
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (mDigitChannelBuffer.length() > 0) {
                            cancelDigitalTune();
                        }
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)) {
                            queueAdjacentChannel(1);
                        } else {
                            queueAdjacentChannel(-1);
                        }
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (mDigitChannelBuffer.length() > 0) {
                            cancelDigitalTune();
                        }
                        if (Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false)) {
                            queueAdjacentChannel(-1);
                        } else {
                            queueAdjacentChannel(1);
                        }
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                        if (mDigitChannelBuffer.length() > 0) {
                            cancelDigitalTune();
                        }
                        sourceTimes = 0;
                        playPreSource();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        if (mDigitChannelBuffer.length() > 0) {
                            cancelDigitalTune();
                        }
                        sourceTimes = 0;
                        playNextSource();
                        return true;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                        if (mDigitChannelBuffer.length() > 0) {
                            commitDigitalTune();
                            return true;
                        }
                        showChannelList();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        showChannelList();
                        return true;
                }
            }
        } else if (event.getAction() == KeyEvent.ACTION_UP) {
            int keyCode = event.getKeyCode();
            if (isNumberKey(keyCode)) {
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    protected void onPause() {
        liveScreenResumed = false;
        cancelPendingChannel();
        cancelDigitalTune();
        if (tvChannelInfo != null) tvChannelInfo.setVisibility(View.INVISIBLE);
        if (mHandler != null) {
            mHandler.removeCallbacks(mUpdateTimeRun);
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
        }
        super.onPause();
        if (mVideoView != null) {
            mVideoView.pause();
        }
    }

    @Override
    public void onBackPressed() {
        if (mDigitChannelBuffer.length() > 0) {
            cancelDigitalTune();
            return;
        }
        if (llLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        }
        else if (rightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        }
        else {
            if (System.currentTimeMillis() - firstTimes > 5000) {
                firstTimes = System.currentTimeMillis();
                UIUtil.showToast(this, "再按一次返回键退出");
                return;
            }

            mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
            mHandler.removeCallbacks(mUpdateNetSpeedRun);

            UiHelper.upPlayResult(getApplicationContext());
            // Return to the existing home activity so its startup loading screen is not shown again.
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mVideoView != null) {
            mVideoView.release();
            mVideoView = null;
        }
        // mUpdateTimeRun 会每秒自我重投递，不清掉会一直持有本 Activity 刷新已销毁的视图
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
    }

    private void showChannelList() {
        if (pendingGroupIndex >= 0) playPendingChannel.run();
        if (rightSettingLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        }
        if (llLeftChannelListLayout.getVisibility() == View.INVISIBLE) {
            //重新载入上一次状态
            liveChannelItemAdapter.setNewData(getGroupLiveChannels(groupIndex));
            if (channelIndex > -1) {
                mLiveRV.scrollToPosition(channelIndex);
            }
            mLiveRV.setSelection(channelIndex);
            mChannelRV.scrollToPosition(groupIndex);
            mChannelRV.setSelection(groupIndex);
            mHandler.postDelayed(mFocusCurrentChannelAndShowChannelList, 200);
        } else {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        }
    }

    private Runnable mFocusCurrentChannelAndShowChannelList = new Runnable() {
        @Override
        public void run() {
            if (mChannelRV.isScrolling() || mLiveRV.isScrolling() || mChannelRV.isComputingLayout() || mLiveRV.isComputingLayout()) {
                mHandler.postDelayed(this, 100);
            } else {
                liveGroupAdapter.setSelectedGroupIndex(groupIndex);
                liveChannelItemAdapter.setSelectedChannelIndex(channelIndex);
                RecyclerView.ViewHolder holder = mLiveRV.findViewHolderForAdapterPosition(channelIndex);
                if (holder != null)
                    holder.itemView.requestFocus();
                llLeftChannelListLayout.setVisibility(View.VISIBLE);
                ViewObj viewObj = new ViewObj(llLeftChannelListLayout, (ViewGroup.MarginLayoutParams) llLeftChannelListLayout.getLayoutParams());
                ObjectAnimator animator = ObjectAnimator.ofObject(viewObj, "marginLeft", new IntEvaluator(), -llLeftChannelListLayout.getLayoutParams().width, 0);
                animator.setDuration(200);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        mHandler.removeCallbacks(mHideChannelListRun);
                    }
                });
                animator.start();
            }
        }
    };

    private long getOverlayHideTimeout() {
        return com.github.tvbox.osc.util.SeniorMode.isEnabled() ? 10000L : 5000L;
    }

    private Runnable mHideChannelListRun = new Runnable() {
        @Override
        public void run() {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) llLeftChannelListLayout.getLayoutParams();
            if (llLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                ViewObj viewObj = new ViewObj(llLeftChannelListLayout, params);
                ObjectAnimator animator = ObjectAnimator.ofObject(viewObj, "marginLeft", new IntEvaluator(), 0, -llLeftChannelListLayout.getLayoutParams().width);
                animator.setDuration(200);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        llLeftChannelListLayout.setVisibility(View.INVISIBLE);
                    }
                });
                animator.start();
            }
        }
    };

    @SuppressLint("RtlHardcoded")
    private void showChannelInfo() {
        showChannelInfo(currentLiveChannelItem);
    }

    private void showChannelInfo(LiveChannelItem channel) {
        L.d("showChannelInfo()");
        if (channel == null) return;
        tvChannelInfo.setText(String.format(Locale.getDefault(), "%03d  %s  %s(%d/%d)",
                channel.getChannelNum(),
                channel.getChannelName(),
                channel.getSourceName(),
                channel.getSourceIndex() + 1,
                channel.getSourceNum()));

        FrameLayout.LayoutParams lParams = new FrameLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (rightSettingLayout.getVisibility() == View.VISIBLE) {
            lParams.gravity = Gravity.LEFT;
            lParams.leftMargin = 60;
            lParams.topMargin = 30;
        } else {
            lParams.gravity = Gravity.RIGHT;
            lParams.rightMargin = 60;
            lParams.topMargin = 30;
        }
        tvChannelInfo.setLayoutParams(lParams);

        tvChannelInfo.setVisibility(View.VISIBLE);
        mHandler.removeCallbacks(mHideChannelInfoRun);
        mHandler.postDelayed(mHideChannelInfoRun, getOverlayHideTimeout());
    }

    private final Runnable mHideChannelInfoRun = new Runnable() {
        @Override
        public void run() {
            tvChannelInfo.setVisibility(View.INVISIBLE);
        }
    };

    private boolean isNumberKey(int keyCode) {
        return (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9)
                || (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9);
    }

    private int getNumberFromKey(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return keyCode - KeyEvent.KEYCODE_0;
        } else if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        }
        return -1;
    }

    private void handleNumberInput(int digit) {
        if (digit < 0) return;
        cancelPendingChannel();
        if (mDigitChannelBuffer.length() >= 4) {
            mDigitChannelBuffer.setLength(0);
        }
        mDigitChannelBuffer.append(digit);
        try {
            int targetNum = Integer.parseInt(mDigitChannelBuffer.toString());
            LiveChannelItem candidate = findChannelItemByNum(targetNum);
            if (tvDigitalNum != null) {
                if (candidate != null) {
                    tvDigitalNum.setText(String.format(Locale.getDefault(), "%s  %s", mDigitChannelBuffer.toString(), candidate.getChannelName()));
                } else {
                    tvDigitalNum.setText(mDigitChannelBuffer.toString());
                }
                tvDigitalNum.setVisibility(View.VISIBLE);
            }
            if (tvChannelInfo != null) {
                tvChannelInfo.setVisibility(View.INVISIBLE);
            }
        } catch (Exception ignored) {
        }
        mHandler.removeCallbacks(mCommitDigitalTuneRun);
        mHandler.postDelayed(mCommitDigitalTuneRun, 1500L);
    }

    private void cancelDigitalTune() {
        mDigitChannelBuffer.setLength(0);
        mHandler.removeCallbacks(mCommitDigitalTuneRun);
        if (tvDigitalNum != null) {
            tvDigitalNum.setVisibility(View.GONE);
        }
    }

    private void commitDigitalTune() {
        mHandler.removeCallbacks(mCommitDigitalTuneRun);
        if (tvDigitalNum != null) {
            tvDigitalNum.setVisibility(View.GONE);
        }
        if (mDigitChannelBuffer.length() == 0) return;
        int targetNum;
        try {
            targetNum = Integer.parseInt(mDigitChannelBuffer.toString());
        } catch (Exception e) {
            mDigitChannelBuffer.setLength(0);
            return;
        }
        mDigitChannelBuffer.setLength(0);

        Integer[] target = findChannelByNum(targetNum);
        if (target != null) {
            int targetGroup = target[0];
            int targetChannel = target[1];
            liveGroupAdapter.setSelectedGroupIndex(targetGroup);
            if (isNeedInputPassword(targetGroup)) {
                showPasswordDialog(targetGroup, targetChannel);
            } else {
                loadChannelGroupDataAndPlay(targetGroup, targetChannel);
            }
        } else {
            Toast.makeText(this, "未找到频道 " + targetNum, Toast.LENGTH_SHORT).show();
        }
    }

    private Integer[] findChannelByNum(int targetNum) {
        if (mChannelGroupList == null || mChannelGroupList.isEmpty()) return null;
        if (groupIndex >= 0 && groupIndex < mChannelGroupList.size()) {
            ArrayList<LiveChannelItem> currentChannels = getGroupLiveChannels(groupIndex);
            if (currentChannels != null) {
                for (int i = 0; i < currentChannels.size(); i++) {
                    LiveChannelItem item = currentChannels.get(i);
                    if (item != null && item.getChannelNum() == targetNum) {
                        return new Integer[]{groupIndex, i};
                    }
                }
            }
        }
        for (int g = 0; g < mChannelGroupList.size(); g++) {
            if (g == groupIndex) continue;
            ArrayList<LiveChannelItem> channels = getGroupLiveChannels(g);
            if (channels != null) {
                for (int i = 0; i < channels.size(); i++) {
                    LiveChannelItem item = channels.get(i);
                    if (item != null && item.getChannelNum() == targetNum) {
                        return new Integer[]{g, i};
                    }
                }
            }
        }
        return null;
    }

    private LiveChannelItem findChannelItemByNum(int targetNum) {
        Integer[] pos = findChannelByNum(targetNum);
        if (pos != null) {
            ArrayList<LiveChannelItem> channels = getGroupLiveChannels(pos[0]);
            if (channels != null && pos[1] >= 0 && pos[1] < channels.size()) {
                return channels.get(pos[1]);
            }
        }
        return null;
    }

    private boolean playChannel(int group, int channel, boolean bChangeSource) {
        L.d("playChannel() group=" + group + " channel=" + channel
                + "\tchangeSource=" + bChangeSource);
        if (!isActivityActive() || mVideoView == null) return false;
        cancelPendingChannel();
        LiveChannelItem targetChannel = currentLiveChannelItem;
        if (bChangeSource) {
            if (targetChannel == null) return false;
        } else {
            if (group < 0 || group >= mChannelGroupList.size()) return false;
            ArrayList<LiveChannelItem> channels = getGroupLiveChannels(group);
            if (channels == null || channel < 0 || channel >= channels.size()) return false;
            targetChannel = channels.get(channel);
            if (targetChannel == null) return false;
        }
        if (targetChannel.getSourceNum() <= 0) {
            Toast.makeText(this, "该频道没有可用线路", Toast.LENGTH_SHORT).show();
            return false;
        }
        if ((group == groupIndex && channel == channelIndex && !bChangeSource)
                || (bChangeSource && targetChannel.getSourceNum() <= 1)) {
            showChannelInfo();
            L.d("playChannel() return  ");
            return true;
        }
        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
        mVideoView.release();
        if (!bChangeSource) {
            sourceTimes = 0;
            consecutiveChannelFailures = 0;
            groupIndex = group;
            channelIndex = channel;
            currentLiveChannelItem = targetChannel;
            Hawk.put(HawkConfig.LIVE_CHANNEL, currentLiveChannelItem.getChannelName());
            livePlayerManager.getLiveChannelPlayer(mVideoView, currentLiveChannelItem.getChannelName());
        }
        L.d(" mVideoView.setUrl ="+currentLiveChannelItem.getUrl());
        mVideoView.setUrl(currentLiveChannelItem.getUrl());
        showChannelInfo();
        mVideoView.start();
        return true;
    }

    private final Runnable playPendingChannel = () -> {
        if (!liveScreenResumed || pendingGroupIndex < 0) return;
        int group = pendingGroupIndex;
        int channel = pendingChannelIndex;
        playChannel(group, channel, false);
    };

    private void cancelPendingChannel() {
        if (mHandler != null) mHandler.removeCallbacks(playPendingChannel);
        pendingGroupIndex = -1;
        pendingChannelIndex = -1;
    }

    private void queueAdjacentChannel(int direction) {
        if (!isCurrentLiveChannelValid()) return;
        int group = pendingGroupIndex >= 0 ? pendingGroupIndex : groupIndex;
        int channel = pendingGroupIndex >= 0 ? pendingChannelIndex : channelIndex;
        Integer[] next = getNextGroupChannel(direction, group, channel);
        if (next == null) {
            Toast.makeText(this, "没有其他可播放频道", Toast.LENGTH_SHORT).show();
            return;
        }
        LiveChannelItem target = getGroupLiveChannels(next[0]).get(next[1]);
        if (target == null || target.getSourceNum() <= 0) {
            Toast.makeText(this, "该频道没有可用线路", Toast.LENGTH_SHORT).show();
            return;
        }
        pendingGroupIndex = next[0];
        pendingChannelIndex = next[1];
        showChannelInfo(target);
        mHandler.removeCallbacks(playPendingChannel);
        mHandler.postDelayed(playPendingChannel, 400);
    }

    private void playAdjacentChannel(int direction) {
        Integer[] groupChannels = getNextGroupChannel(direction);
        if (groupChannels == null) {
            Toast.makeText(this, "没有其他可播放频道", Toast.LENGTH_SHORT).show();
            return;
        }
        playChannel(groupChannels[0], groupChannels[1], false);
    }

    public void playPreSource() {
        if (pendingGroupIndex >= 0) playPendingChannel.run();
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.preSource();
        playChannel(groupIndex, channelIndex, true);
    }

    public void playNextSource() {
        if (pendingGroupIndex >= 0) playPendingChannel.run();
        if (!isCurrentLiveChannelValid()) return;
        currentLiveChannelItem.nextSource();
        playChannel(groupIndex, channelIndex, true);
    }

    //显示设置列表
    private void showSettingGroup() {
        if (pendingGroupIndex >= 0) playPendingChannel.run();
        if (llLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
            mHandler.post(mHideChannelListRun);
        }
        if (rightSettingLayout.getVisibility() == View.INVISIBLE) {
            if (!isCurrentLiveChannelValid()) return;
            //重新载入默认状态
            loadCurrentSourceList();
            liveSettingGroupAdapter.setNewData(mSettingGroupList);
            selectSettingGroup(0, false);
            mSettingRV.scrollToPosition(0);
            mSettingItemRV.scrollToPosition(currentLiveChannelItem.getSourceIndex());
            mHandler.postDelayed(mFocusAndShowSettingGroup, 200);
        } else {
            mHandler.removeCallbacks(mHideSettingLayoutRun);
            mHandler.post(mHideSettingLayoutRun);
        }
    }

    private Runnable mFocusAndShowSettingGroup = new Runnable() {
        @Override
        public void run() {
            if (mSettingRV.isScrolling() || mSettingItemRV.isScrolling() || mSettingRV.isComputingLayout() || mSettingItemRV.isComputingLayout()) {
                mHandler.postDelayed(this, 100);
            } else {
                RecyclerView.ViewHolder holder = mSettingRV.findViewHolderForAdapterPosition(0);
                if (holder != null)
                    holder.itemView.requestFocus();
                rightSettingLayout.setVisibility(View.VISIBLE);
                ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) rightSettingLayout.getLayoutParams();
                if (rightSettingLayout.getVisibility() == View.VISIBLE) {
                    ViewObj viewObj = new ViewObj(rightSettingLayout, params);
                    ObjectAnimator animator = ObjectAnimator.ofObject(viewObj, "marginRight", new IntEvaluator(), -rightSettingLayout.getLayoutParams().width, 0);
                    animator.setDuration(200);
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            mHandler.postDelayed(mHideSettingLayoutRun, getOverlayHideTimeout());
                        }
                    });
                    animator.start();
                }
            }
        }
    };

    private Runnable mHideSettingLayoutRun = new Runnable() {
        @Override
        public void run() {
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) rightSettingLayout.getLayoutParams();
            if (rightSettingLayout.getVisibility() == View.VISIBLE) {
                ViewObj viewObj = new ViewObj(rightSettingLayout, params);
                ObjectAnimator animator = ObjectAnimator.ofObject(viewObj, "marginRight", new IntEvaluator(), 0, -rightSettingLayout.getLayoutParams().width);
                animator.setDuration(200);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        rightSettingLayout.setVisibility(View.INVISIBLE);
                        liveSettingGroupAdapter.setSelectedGroupIndex(-1);
                    }
                });
                animator.start();
            }
        }
    };

    private void initVideoView() {
        L.d("initVideoView()");
        LiveController controller = new LiveController(this);
        controller.setListener(new LiveController.LiveControlListener() {
            @Override
            public boolean singleTap() {
                showChannelList();
                return true;
            }

            @Override
            public void longPress() {
                showSettingGroup();
            }

            @Override
            public void playStateChanged(int playState) {
                L.d(" playStateChanged（） playState=" + playState);
                switch (playState) {
                    case VideoView.STATE_IDLE:
                    case VideoView.STATE_PAUSED:
                        break;
                    case VideoView.STATE_PREPARED:
                    case VideoView.STATE_BUFFERED:
                    case VideoView.STATE_PLAYING:
                        L.d("case VideoView.STATE_BUFFERED");
                        sourceTimes = 0;
                        consecutiveChannelFailures = 0;
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        String channelInfo = tvChannelInfo.getText().toString();
                        PrintUtil.saveSuccess(channelInfo, System.currentTimeMillis()-mBegin);
                        break;
                    case VideoView.STATE_ERROR:
                    case VideoView.STATE_PLAYBACK_COMPLETED:
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.post(mConnectTimeoutChangeSourceRun);
                        break;
                    case VideoView.STATE_PREPARING:
                        L.d("case VideoView.STATE_PREPARING");
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mBegin = System.currentTimeMillis();
                        // 首次建立连接起播采用用户设置的超时换源时长（默认 10 秒，支持 5s~30s 档位调节），
                        // 避免因过短（如 3.5s）在首片解析、握手和缓冲过程中误判死链导致反复跳源
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, getLiveConnectTimeoutMs());
                        break;
                    case VideoView.STATE_BUFFERING:
                        L.d("case VideoView.STATE_BUFFERING");
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mBegin = System.currentTimeMillis();
                        // 播放中途卡顿缓冲同样遵循设定超时，容忍网络短暂抖动恢复
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, getLiveConnectTimeoutMs());
                        break;
                }
            }

            @Override
            public void changeSource(int direction) {
                if (direction > 0) {
                    playNextSource();
                }
                else {
                    playPreSource();
                }
            }
        });
        controller.setCanChangePosition(false);
        controller.setEnableInNormal(true);
        controller.setGestureEnabled(true);
        mVideoView.addControlView(controller);
        mVideoView.setProgressManager(null);
    }

    private final Runnable mConnectTimeoutChangeSourceRun = new Runnable() {
        @Override
        public void run() {
            if (!isActivityActive() || mVideoView == null || currentLiveChannelItem == null) return;
            if (pendingGroupIndex >= 0) {
                deferConnectTimeout();
                return;
            }
            sourceTimes++;
            if (currentLiveChannelItem.getSourceNum() <= sourceTimes) {
                sourceTimes = 0;
                consecutiveChannelFailures++;
                if (consecutiveChannelFailures >= MAX_CONSECUTIVE_AUTO_FAILS) {
                    consecutiveChannelFailures = 0;
                    mHandler.removeCallbacks(this);
                    Toast.makeText(LivePlayActivity.this, "多个频道连接超时，已暂停自动换台", Toast.LENGTH_LONG).show();
                    return;
                }
                playAdjacentChannel(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false) ? -1 : 1);
            } else {
                playNextSource();
            }
        }
    };

    private void deferConnectTimeout() {
        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, 400);
    }

    private long getLiveConnectTimeoutMs() {
        int timeoutIdx = Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1);
        if (timeoutIdx < 0 || timeoutIdx >= 6) {
            timeoutIdx = 1;
        }
        return (timeoutIdx + 1) * 5000L;
    }

    private void initChannelGroupView() {
        L.d("initChannelGroupView()");
        mChannelRV.setHasFixedSize(true);
        mChannelRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveGroupAdapter = new LiveChannelGroupAdapter();
        mChannelRV.setAdapter(liveGroupAdapter);
        mChannelRV.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideChannelListRun);
            }
        });

        //电视
        mChannelRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                selectChannelGroup(position, true, -1);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (isNeedInputPassword(position)) {
                    showPasswordDialog(position, -1);
                }
            }
        });

        //手机/模拟器
        liveGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectChannelGroup(position, false, -1);
            }
        });
    }

    private void selectChannelGroup(int groupIndex, boolean focus, int liveChannelIndex) {
        if (focus) {
            liveGroupAdapter.setFocusedGroupIndex(groupIndex);
            liveChannelItemAdapter.setFocusedChannelIndex(-1);
        }
        if ((groupIndex > -1 && groupIndex != liveGroupAdapter.getSelectedGroupIndex()) || isNeedInputPassword(groupIndex)) {
            liveGroupAdapter.setSelectedGroupIndex(groupIndex);
            if (isNeedInputPassword(groupIndex)) {
                showPasswordDialog(groupIndex, liveChannelIndex);
                return;
            }
            loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
        }
    }

    private void initLiveChannelView() {
        mLiveRV.setHasFixedSize(true);
        mLiveRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveChannelItemAdapter = new LiveChannelItemAdapter();
        mLiveRV.setAdapter(liveChannelItemAdapter);
        mLiveRV.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideChannelListRun);
            }
        });

        //电视
        mLiveRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) return;
                liveGroupAdapter.setFocusedGroupIndex(-1);
                liveChannelItemAdapter.setFocusedChannelIndex(position);
                mHandler.removeCallbacks(mHideChannelListRun);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                clickLiveChannel(position);
            }
        });

        //手机/模拟器
        liveChannelItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickLiveChannel(position);
            }
        });
    }

    private void clickLiveChannel(int position) {
        liveChannelItemAdapter.setSelectedChannelIndex(position);
        playChannel(liveGroupAdapter.getSelectedGroupIndex(), position, false);
    }

    private void initSettingGroupView() {
        mSettingRV.setHasFixedSize(true);
        mSettingRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveSettingGroupAdapter = new LiveSettingGroupAdapter();
        mSettingRV.setAdapter(liveSettingGroupAdapter);
        mSettingRV.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, getOverlayHideTimeout());
            }
        });

        //电视
        mSettingRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                selectSettingGroup(position, true);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });

        //手机/模拟器
        liveSettingGroupAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                selectSettingGroup(position, false);
            }
        });
    }

    private void selectSettingGroup(int position, boolean focus) {
        if (!isCurrentLiveChannelValid()) return;
        if (focus) {
            liveSettingGroupAdapter.setFocusedGroupIndex(position);
            liveSettingItemAdapter.setFocusedItemIndex(-1);
        }
        if (position == liveSettingGroupAdapter.getSelectedGroupIndex() || position < -1)
            return;

        liveSettingGroupAdapter.setSelectedGroupIndex(position);
        liveSettingItemAdapter.setNewData(mSettingGroupList.get(position).getLiveSettingItems());

        switch (position) {
            case 0:
                liveSettingItemAdapter.selectItem(currentLiveChannelItem.getSourceIndex(), true, false);
                break;
            case 1:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerScale(), true, true);
                break;
            case 2:
                liveSettingItemAdapter.selectItem(livePlayerManager.getLivePlayerType(), true, true);
                break;
        }
        int scrollToPosition = liveSettingItemAdapter.getSelectedItemIndex();
        if (scrollToPosition < 0) scrollToPosition = 0;
        mSettingItemRV.scrollToPosition(scrollToPosition);
        mHandler.removeCallbacks(mHideSettingLayoutRun);
        mHandler.postDelayed(mHideSettingLayoutRun, getOverlayHideTimeout());
    }

    private void initSettingItemView() {
        mSettingItemRV.setHasFixedSize(true);
        mSettingItemRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));

        liveSettingItemAdapter = new LiveSettingItemAdapter();
        mSettingItemRV.setAdapter(liveSettingItemAdapter);
        mSettingItemRV.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, getOverlayHideTimeout());
            }
        });

        //电视
        mSettingItemRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position < 0) return;
                liveSettingGroupAdapter.setFocusedGroupIndex(-1);
                liveSettingItemAdapter.setFocusedItemIndex(position);
                mHandler.removeCallbacks(mHideSettingLayoutRun);
                mHandler.postDelayed(mHideSettingLayoutRun, getOverlayHideTimeout());
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                clickSettingItem(position);
            }
        });

        //手机/模拟器
        liveSettingItemAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                clickSettingItem(position);
            }
        });
    }

    private void clickSettingItem(int position) {
        if (!isActivityActive() || mVideoView == null || currentLiveChannelItem == null) return;
        int settingGroupIndex = liveSettingGroupAdapter.getSelectedGroupIndex();
        if (settingGroupIndex < 0 || settingGroupIndex >= mSettingGroupList.size()
                || position < 0
                || position >= mSettingGroupList.get(settingGroupIndex).getLiveSettingItems().size()) {
            return;
        }
        if (settingGroupIndex < 4) {
            if (position == liveSettingItemAdapter.getSelectedItemIndex())
                return;
            liveSettingItemAdapter.selectItem(position, true, true);
        }
        switch (settingGroupIndex) {
            case 0://线路切换
                sourceTimes = 0;
                currentLiveChannelItem.setSourceIndex(position);
                playChannel(groupIndex, channelIndex, true);
                break;
            case 1://画面比例
                livePlayerManager.changeLivePlayerScale(mVideoView, position, currentLiveChannelItem.getChannelName());
                break;
            case 2://播放解码
                mVideoView.release();
                livePlayerManager.changeLivePlayerType(mVideoView, position, currentLiveChannelItem.getChannelName());
                mVideoView.setUrl(currentLiveChannelItem.getUrl());
                mVideoView.start();
                break;
            case 3://超时换源
                Hawk.put(HawkConfig.LIVE_CONNECT_TIMEOUT, position);
                if (mVideoView != null) {
                    int playState = mVideoView.getCurrentPlayState();
                    if (playState == VideoView.STATE_PREPARING || playState == VideoView.STATE_BUFFERING) {
                        mHandler.removeCallbacks(mConnectTimeoutChangeSourceRun);
                        mHandler.postDelayed(mConnectTimeoutChangeSourceRun, getLiveConnectTimeoutMs());
                    }
                }
                break;
            case 4://偏好设置
                boolean select = false;
                switch (position) {
                    case 0:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_TIME, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_TIME, select);
                        showTime();
                        break;
                    case 1:
                        select = !Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false);
                        Hawk.put(HawkConfig.LIVE_SHOW_NET_SPEED, select);
                        showNetSpeed();
                        break;
                    case 2:
                        select = !Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false);
                        Hawk.put(HawkConfig.LIVE_CHANNEL_REVERSE, select);
                        break;
                    case 3:
                        select = !Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false);
                        Hawk.put(HawkConfig.LIVE_CROSS_GROUP, select);
                        break;
                }
                liveSettingItemAdapter.selectItem(position, select, false);
                break;
        }
        mHandler.removeCallbacks(mHideSettingLayoutRun);
        mHandler.postDelayed(mHideSettingLayoutRun, getOverlayHideTimeout());
    }

    private void initSite() {
        ApiConfig apiConfig = ApiConfig.getInstance();
        List<LiveChannelGroup> list = apiConfig.getChannelGroupList();
        if (NullUtil.isNull(list)) {
            apiConfig.loadSitesFile(LivePlayActivity.this, new ApiConfig.LoadConfigCallback() {
                @Override
                public void success() {
                    runOnUiThread(() -> {
                        if (isActivityActive()) {
                            loadLiveSourceOrDefault();
                        }
                    });
                }
            });
        } else {
            loadLiveSourceOrDefault();
        }
    }

    private boolean isActivityActive() {
        return !isFinishing() && !isDestroyed();
    }

    private void loadLiveSourceOrDefault() {
        if (!isActivityActive() || mVideoView == null) return;
        ApiConfig apiConfig = ApiConfig.getInstance();
        LiveSourceManager.migrateIfNeeded();
        List<LiveSourceManager.Source> configured = LiveSourceManager.getSources();
        List<String> sourceContents = new ArrayList<>();
        Set<String> sourceKeys = new HashSet<>();
        for (LiveSourceManager.Source source : configured) {
            if (source.enabled && !TextUtils.isEmpty(source.content)
                    && sourceKeys.add(LiveSourceManager.key(source.content))) {
                sourceContents.add(source.content);
            }
        }
        if (sourceContents.isEmpty()) {
            apiConfig.restoreConfiguredLives();
            initLiveChannelList();
            return;
        }

        showLoading();
        apiConfig.loadLiveSourceContents(sourceContents, new ApiConfig.LiveSourceCallback() {
            @Override
            public void success(int loadedSources, int failedSources) {
                runOnUiThread(() -> {
                    if (!isActivityActive()) return;
                    if (failedSources > 0) {
                        Toast.makeText(LivePlayActivity.this,
                                "已加载 " + loadedSources + " 个直播源，" + failedSources + " 个源加载失败",
                                Toast.LENGTH_LONG).show();
                    }
                    initLiveChannelList();
                });
            }

            @Override
            public void error(String message) {
                runOnUiThread(() -> {
                    if (!isActivityActive()) return;
                    Toast.makeText(LivePlayActivity.this,
                            "直播源加载失败：" + message + "，改用总配置直播源",
                            Toast.LENGTH_LONG).show();
                    apiConfig.restoreConfiguredLives();
                    initLiveChannelList();
                });
            }
        });
    }

    private void initLiveChannelList() {
        if (!isActivityActive() || mVideoView == null) return;
        L.d("initLiveChannelList()");
        List<LiveChannelGroup> list = ApiConfig.getInstance().getChannelGroupList();
        if (NullUtil.isNull(list)) {
            showEmpty();
            Toast.makeText(App.getInstance(), "频道列表为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (list.size() == 1 && list.get(0).getGroupName().startsWith("http://127.0.0.1")) {
            L.d("list.get(0).getGroupName()=" + list.get(0).getGroupName());
            showLoading();
            loadProxyLives(list.get(0).getGroupName());
        } else {
            mChannelGroupList.clear();
            mChannelGroupList.addAll(list);
            showSuccess();
            initLiveState();
        }
    }

    public void loadProxyLives(String url) {
        L.d("loadProxyLives()");
        OkGo.<String>get(url).execute(new AbsCallback<String>() {

            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                return response.body().string();
            }

            @Override
            public void onError(Response<String> response) {
                super.onError(response);
                if (!isActivityActive()) return;
                String error = response == null || response.body() == null
                        ? "网络连接失败" : String.valueOf(response.body());
                L.d("onError " + error);
                showSuccess();
                Toast.makeText(LivePlayActivity.this,
                        "代理频道加载失败：" + error, Toast.LENGTH_LONG).show();
            }

            @Override
            public void onSuccess(Response<String> response) {
                if (!isActivityActive() || mVideoView == null) return;
                L.d("onSuccess "+response.body());
                JsonArray livesArray;
                try {
                    livesArray = new Gson().fromJson(response.body(), JsonArray.class);
                    if (livesArray == null || livesArray.size() == 0) {
                        throw new IllegalStateException("代理没有返回频道");
                    }
                    ApiConfig.getInstance().loadLives(livesArray);
                } catch (Exception e) {
                    showSuccess();
                    Toast.makeText(LivePlayActivity.this,
                            "代理频道数据无效：" + (e.getMessage() == null ? "解析失败" : e.getMessage()),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                List<LiveChannelGroup> list = ApiConfig.getInstance().getChannelGroupList();
                if (list.isEmpty()) {
                    Toast.makeText(App.getInstance(), "频道列表为空", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                mChannelGroupList.clear();
                mChannelGroupList.addAll(list);

                mHandler.post(() -> {
                    if (!isActivityActive() || mVideoView == null) return;
                    LivePlayActivity.this.showSuccess();
                    initLiveState();
                });
            }
        });
    }

    private void initLiveState() {
        if (!isActivityActive() || mVideoView == null) return;
        L.d("initLiveState()");
        String lastChannelName = Hawk.get(HawkConfig.LIVE_CHANNEL, "");

        int lastChannelGroupIndex = -1;
        int lastLiveChannelIndex = -1;
        for (LiveChannelGroup liveChannelGroup : mChannelGroupList) {
            if(!NullUtil.isNull(liveChannelGroup.getLiveChannels())) {
                for (LiveChannelItem liveChannelItem : liveChannelGroup.getLiveChannels()) {
                    if (liveChannelItem.getChannelName().equals(lastChannelName)) {
                        lastChannelGroupIndex = liveChannelGroup.getGroupIndex();
                        lastLiveChannelIndex = liveChannelItem.getChannelIndex();
                        break;
                    }
                }
            }
            if (lastChannelGroupIndex != -1) break;
        }
        if (lastChannelGroupIndex == -1) {
            lastChannelGroupIndex = getFirstNoPasswordChannelGroup();
            if (lastChannelGroupIndex == -1) {
                lastChannelGroupIndex = 0;
            }
            lastLiveChannelIndex = 0;
        }

        livePlayerManager.init(mVideoView);
        showTime();
        showNetSpeed();
        llLeftChannelListLayout.setVisibility(View.INVISIBLE);
        rightSettingLayout.setVisibility(View.INVISIBLE);

        liveGroupAdapter.setNewData(mChannelGroupList);
        selectChannelGroup(lastChannelGroupIndex, false, lastLiveChannelIndex);
    }

    private boolean isListOrSettingLayoutVisible() {
        return llLeftChannelListLayout.getVisibility() == View.VISIBLE || rightSettingLayout.getVisibility() == View.VISIBLE;
    }

    private void initLiveSettingGroupList() {
        ArrayList<String> groupNames = new ArrayList<>(Arrays.asList("线路选择", "画面比例", "播放解码", "超时换源", "偏好设置"));
        ArrayList<ArrayList<String>> itemsArrayList = new ArrayList<>();
        ArrayList<String> sourceItems = new ArrayList<>();
        ArrayList<String> scaleItems = new ArrayList<>(Arrays.asList("默认", "16:9", "4:3", "填充", "原始", "裁剪"));
        ArrayList<String> playerDecoderItems = new ArrayList<>(Arrays.asList("系统", "ijk硬解", "ijk软解", "exo"));
        ArrayList<String> timeoutItems = new ArrayList<>(Arrays.asList("5s", "10s", "15s", "20s", "25s", "30s"));
        ArrayList<String> personalSettingItems = new ArrayList<>(Arrays.asList("显示时间", "显示网速", "换台反转", "跨选分类"));
        itemsArrayList.add(sourceItems);
        itemsArrayList.add(scaleItems);
        itemsArrayList.add(playerDecoderItems);
        itemsArrayList.add(timeoutItems);
        itemsArrayList.add(personalSettingItems);

        mSettingGroupList.clear();
        for (int i = 0; i < groupNames.size(); i++) {
            LiveSettingGroup liveSettingGroup = new LiveSettingGroup();
            ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
            liveSettingGroup.setGroupIndex(i);
            liveSettingGroup.setGroupName(groupNames.get(i));
            for (int j = 0; j < itemsArrayList.get(i).size(); j++) {
                LiveSettingItem liveSettingItem = new LiveSettingItem();
                liveSettingItem.setItemIndex(j);
                liveSettingItem.setItemName(itemsArrayList.get(i).get(j));
                liveSettingItemList.add(liveSettingItem);
            }
            liveSettingGroup.setLiveSettingItems(liveSettingItemList);
            mSettingGroupList.add(liveSettingGroup);
        }
        mSettingGroupList.get(3).getLiveSettingItems().get(Hawk.get(HawkConfig.LIVE_CONNECT_TIMEOUT, 1)).setItemSelected(true);
        mSettingGroupList.get(4).getLiveSettingItems().get(0).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_TIME, false));
        mSettingGroupList.get(4).getLiveSettingItems().get(1).setItemSelected(Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false));
        mSettingGroupList.get(4).getLiveSettingItems().get(2).setItemSelected(Hawk.get(HawkConfig.LIVE_CHANNEL_REVERSE, false));
        mSettingGroupList.get(4).getLiveSettingItems().get(3).setItemSelected(Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false));
    }

    private void loadCurrentSourceList() {
        ArrayList<String> currentSourceNames = currentLiveChannelItem.getChannelSourceNames();
        ArrayList<LiveSettingItem> liveSettingItemList = new ArrayList<>();
        for (int j = 0; j < currentSourceNames.size(); j++) {
            LiveSettingItem liveSettingItem = new LiveSettingItem();
            liveSettingItem.setItemIndex(j);
            liveSettingItem.setItemName(currentSourceNames.get(j));
            liveSettingItemList.add(liveSettingItem);
        }
        mSettingGroupList.get(0).setLiveSettingItems(liveSettingItemList);
    }

    void showTime() {
        L.d("showTime()");
        mHandler.removeCallbacks(mUpdateTimeRun);
        if (Hawk.get(HawkConfig.LIVE_SHOW_TIME, false)) {
            if (liveScreenResumed) mHandler.post(mUpdateTimeRun);
            tvTime.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateTimeRun);
            tvTime.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        L.d("onStop");
    }

    private Runnable mUpdateTimeRun = new Runnable() {
        @Override
        public void run() {
            if (!liveScreenResumed || !isActivityActive()) return;
            Date day=new Date();
            SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
            tvTime.setText(df.format(day));
            mHandler.postDelayed(this, 1000);
        }
    };

    private void showNetSpeed() {
        L.d("showNetSpeed()");
        mHandler.removeCallbacks(mUpdateNetSpeedRun);
        if (Hawk.get(HawkConfig.LIVE_SHOW_NET_SPEED, false)) {
            if (liveScreenResumed) mHandler.post(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.VISIBLE);
        } else {
            mHandler.removeCallbacks(mUpdateNetSpeedRun);
            tvNetSpeed.setVisibility(View.GONE);
        }
    }

    private Runnable mUpdateNetSpeedRun = new Runnable() {
        @Override
        public void run() {
            if (mVideoView == null || !liveScreenResumed || !isActivityActive()) return;
            tvNetSpeed.setText(String.format("%.2fMB/s", (float)mVideoView.getTcpSpeed() / 1024.0 / 1024.0));
            mHandler.postDelayed(this, 1000);
        }
    };

    private void showPasswordDialog(int groupIndex, int liveChannelIndex) {
        if (llLeftChannelListLayout.getVisibility() == View.VISIBLE) {
            mHandler.removeCallbacks(mHideChannelListRun);
        }

        LivePasswordDialog dialog = new LivePasswordDialog(this);
        dialog.setOnListener(new LivePasswordDialog.OnListener() {
            @Override
            public void onChange(String password) {
                if (password.equals(mChannelGroupList.get(groupIndex).getGroupPassword())) {
                    mPasswordConfirmedList.add(groupIndex);
                    loadChannelGroupDataAndPlay(groupIndex, liveChannelIndex);
                } else {
                    Toast.makeText(App.getInstance(), "密码错误", Toast.LENGTH_SHORT).show();
                }

            }

            @Override
            public void onCancel() {
                if (llLeftChannelListLayout.getVisibility() == View.VISIBLE) {
                    int groupIndex = liveGroupAdapter.getSelectedGroupIndex();
                    liveChannelItemAdapter.setNewData(getGroupLiveChannels(groupIndex));
                }
            }
        });
        dialog.show();
    }

    private void loadChannelGroupDataAndPlay(int groupIndex, int liveChannelIndex) {
        L.d("loadChannelGroupDataAndPlay()");
        liveChannelItemAdapter.setNewData(getGroupLiveChannels(groupIndex));
        if (groupIndex == this.groupIndex) {
            if (channelIndex > -1) {
                mLiveRV.scrollToPosition(channelIndex);
            }
            liveChannelItemAdapter.setSelectedChannelIndex(channelIndex);
        }
        else {
            mLiveRV.scrollToPosition(0);
            liveChannelItemAdapter.setSelectedChannelIndex(-1);
        }

        if (liveChannelIndex > -1) {
            clickLiveChannel(liveChannelIndex);
            mChannelRV.scrollToPosition(groupIndex);
            mLiveRV.scrollToPosition(liveChannelIndex);
        }
    }

    private boolean isNeedInputPassword(int groupIndex) {
        if(NullUtil.isNull(mChannelGroupList)
                || mChannelGroupList.get(groupIndex) ==null ||
                mChannelGroupList.get(groupIndex).getGroupPassword()==null) {
            return false;
        }
        return !mChannelGroupList.get(groupIndex).getGroupPassword().isEmpty()
                && !isPasswordConfirmed(groupIndex);
    }

    private boolean isPasswordConfirmed(int groupIndex) {
        for (Integer confirmedNum : mPasswordConfirmedList) {
            if (confirmedNum == groupIndex)
                return true;
        }
        return false;
    }

    private ArrayList<LiveChannelItem> getGroupLiveChannels(int groupIndex) {
        L.d("getLiveChannels()");
        if (!isNeedInputPassword(groupIndex)) {
            return mChannelGroupList.get(groupIndex).getLiveChannels();
        } else {
            return new ArrayList<>();
        }
    }

    private Integer[] getNextGroupChannel(int direction) {
        return getNextGroupChannel(direction, groupIndex, channelIndex);
    }

    private Integer[] getNextGroupChannel(int direction, int groupIndex, int channelIndex) {
        if (mChannelGroupList == null || mChannelGroupList.isEmpty()
                || groupIndex < 0 || groupIndex >= mChannelGroupList.size()) return null;
        int step = direction >= 0 ? 1 : -1;
        ArrayList<LiveChannelItem> currentChannels = getGroupLiveChannels(groupIndex);
        int nextChannel = channelIndex + step;
        if (currentChannels != null && nextChannel >= 0 && nextChannel < currentChannels.size()) {
            return new Integer[]{groupIndex, nextChannel};
        }

        if (!Hawk.get(HawkConfig.LIVE_CROSS_GROUP, false)) {
            if (currentChannels == null || currentChannels.isEmpty()) return null;
            return new Integer[]{groupIndex, step > 0 ? 0 : currentChannels.size() - 1};
        }

        int groupCount = mChannelGroupList.size();
        for (int offset = 1; offset <= groupCount; offset++) {
            int candidateGroup = (groupIndex + step * offset) % groupCount;
            if (candidateGroup < 0) candidateGroup += groupCount;
            ArrayList<LiveChannelItem> channels = getGroupLiveChannels(candidateGroup);
            if (channels != null && !channels.isEmpty()) {
                return new Integer[]{candidateGroup, step > 0 ? 0 : channels.size() - 1};
            }
        }
        return null;
    }

    private int getFirstNoPasswordChannelGroup() {
        for (LiveChannelGroup liveChannelGroup : mChannelGroupList) {
            if (null != liveChannelGroup.getGroupPassword()) {
                if (liveChannelGroup.getGroupPassword().isEmpty()) {
                    return liveChannelGroup.getGroupIndex();
                }
            }
        }
        return -1;
    }

    private boolean isCurrentLiveChannelValid() {
        if (currentLiveChannelItem == null) {
            Toast.makeText(App.getInstance(), "请先选择频道", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
