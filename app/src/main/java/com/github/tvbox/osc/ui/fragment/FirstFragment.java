package com.github.tvbox.osc.ui.fragment;

import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.DrawableRequestBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.GlideDrawable;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.ui.activity.CollectActivity;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.HistoryActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter;
import com.github.tvbox.osc.ui.tv.widget.HomeCardRecyclerView;
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.GlideImg;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.SharpenTransformation;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.http.HttpDoRequest;
import com.lcstudio.commonsurport.util.DateUtil;
import com.lcstudio.commonsurport.util.NullUtil;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.studio.osc.R;
import com.uisupport.UiConstans;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class FirstFragment extends BaseLazyFragment implements View.OnClickListener {

    private LinearLayout tvLive;
    private LinearLayout tvSearch;
    private LinearLayout tvSetting;
    private LinearLayout tvHistory;
    private LinearLayout tvCollect;
    private LinearLayout tvPush;
    private HomeHotVodAdapter mAdapter;
    private List<Movie.Video> mRecommendVideos;
    /** 豆瓣被风控后的冷却截止时间：冷却期内不再直连豆瓣，只用缓存/兜底，避免继续砸接口把限流期拉长 */
    private static long sDoubanBlockedUntil = 0L;
    private static final long DOU_BAN_BLOCK_COOLDOWN_MS = 5 * 60 * 1000L;
    /** 豆瓣翻页上限：按出口 IP 限流，翻页过多必然触发风控，最多只额外拉 2 页 */
    private static final int DOU_BAN_MAX_START = 40;
    private static final int INITIAL_EXTRA_RECOMMENDATIONS = 8;
    private int mStart = 40;
    private HomeCardRecyclerView mHotGridView;
    private ImageView mHeroBackdrop;
    private ImageView mHeroBackdropAlt;
    /** 首页压暗层：与背景海报一样铺满整屏（含侧栏下方），非首页时需一起收起 */
    private View mHeroScrim;
    private View mHeroLayer;
    /** 当前正在显示背景的那层；还没显示过任何海报时为 null */
    private ImageView mHeroFront;
    private int mHeroSeq = 0;
    /** 本轮这张海报是否已经淡入到位：防止重复淡入，也防止低清图反过来盖掉已经清晰的那层 */
    private boolean mHeroShown = false;
    /** 当前卡片的背景已准备好；背景未就绪时不启动自动轮播，避免首屏卡片空转。 */
    private boolean mHeroReady;
    private boolean mLowPowerHome;
    /** 背景推镜的起止比例：每张图接着上一张的当前比例继续推，淡入时画面不会突然缩放 */
    private static final float HERO_ZOOM_MIN = 1.03f;
    private static final float HERO_ZOOM_MAX = 1.08f;
    private static final long HERO_ZOOM_DURATION = 6000L;
    /** 兜底预览层尺寸：只用于「背景先跟上」，分辨率约全屏 1/3，解码快 */
    private static final int HERO_PREVIEW_WIDTH = 786;
    private static final int HERO_PREVIEW_HEIGHT = 360;
    /** 两层背景交叉淡入的时长：够长才看得出「渐变」，太短会像硬切 */
    private static final long HERO_FADE_DURATION = 750L;
    private static final long PAGE_HERO_TRANSITION_DURATION = 100L;
    /** 高清图超过这个时间还没解好，才用低清预览先把背景跟上；正常网络下不会走到 */
    private static final long HERO_PREVIEW_GRACE = 700L;
    /** 正在跑的 hero 动画，换图时统一取消：两个动画半路互相打断会让背景亮度抖一下 */
    private final List<ObjectAnimator> mHeroAnims = new ArrayList<>();
    private Movie.Video mHeroVod;
    private String mHeroPic;
    /**
     * 全屏背景是 1080px 大图放大到屏幕宽再逐像素锐化，解码很重；
     * 快速滚卡时逐个加载会互相拖后腿（背景明显跟不上卡片），
     * 所以先排队，焦点停稳后再真正解码。
     */
    private static final long HERO_IMAGE_SETTLE_DELAY = 130L;
    private final Handler mHeroHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHeroImageRun = new Runnable() {
        @Override
        public void run() {
            if (mHeroVod != null) {
                loadHeroImage(mHeroVod);
            }
        }
    };
    /** 聚焦缩放时长与整排滑动时长保持一致（见 HomeCardRecyclerView），滑动与放大是一个动作 */
    private static final long ITEM_SCALE_DURATION = HomeCardRecyclerView.SCROLL_DURATION;
    private static final float CARD_FRONT_SCALE = 2.0f;
    private static final float CARD_MIN_ALPHA = 0.48f;
    private static final float CARD_ARC_ROTATION = 42f;
    private int mFocusedCardPosition;
    private boolean mUserOperatingCards;
    private boolean mAutoScrollingCards;
    private long mLastAutoScrollAt;
    /** 数据刚替换时先完成一次布局和定位，避免旧焦点回调把首屏拉回第一张。 */
    private boolean mCarouselInitializing;
    private TextView mHeroTitle;
    private TextView mHeroNote;
    private TextView mHeroMeta;
    private TextView mHeroDesc;
    private TextView mHomeTitle;
    private TextView mHomeSub;
    private ExecutorService mExecutorService;
    private SourceViewModel mSourceViewModel;
    private volatile boolean mViewActive;
    private volatile boolean mHeroVisible;
    private volatile int mFeedRequestId;
    private boolean mHistoryLoadPending;
    private boolean mFeedCancelled;

    // 首页静置自动轮播：电视按键或手机触摸结束后，连续无操作 6 秒才切换
    private static final long AUTO_SCROLL_DELAY = 6000L;
    private final Handler mAutoScrollHandler = new Handler(Looper.getMainLooper());
    private final Runnable mAutoScrollRun = new Runnable() {
        @Override
        public void run() {
            if (mAutoScrollingCards || System.currentTimeMillis() - mLastAutoScrollAt < AUTO_SCROLL_DELAY) {
                return;
            }
            mAutoScrollingCards = true;
            mLastAutoScrollAt = System.currentTimeMillis();
            try {
                autoScrollNext();
            } catch (Exception e) {
                // 轮播抛出异常不能中断计时链，否则首页卡片会一直停在原地
                L.e(e);
            }
            mAutoScrollingCards = false;
            if (!mAutoScrollingCards) {
                scheduleAutoScroll();
            }
        }
    };
    // 焦点变化只负责同步背景，不负责重置轮播；否则初始化和自动切换时
    // 焦点的连续变化会反复创建计时器并叠加居中动画。
    private final ViewTreeObserver.OnGlobalFocusChangeListener mFocusChangeListener = (oldFocus, newFocus) -> {
        bindHeroFromFocus(newFocus);
    };

    public static FirstFragment newInstance(List<Movie.Video> recVod) {
        return new FirstFragment().setmRecommendVideos(recVod);
    }

    public FirstFragment setmRecommendVideos(List<Movie.Video> recVod) {
        this.mRecommendVideos = recVod;
        return this;
    }

    /**
     * 显隐首页大背景海报。背景层属于 Activity，不随 ViewPager 页面裁剪。
     */
    public void setHeroVisible(boolean visible) {
        refreshHomeMode();
        if (mHeroVisible == visible && mHeroLayer != null
                && mHeroLayer.getVisibility() == (visible ? View.VISIBLE : View.GONE)) {
            if (visible) {
                scheduleAutoScroll();
            }
            return;
        }
        mHeroVisible = visible;
        if (visible) {
            mViewActive = true;
        } else {
            mViewActive = false;
        }
        if (mHeroLayer != null) {
            mHeroLayer.animate().cancel();
            if (visible) {
                mHeroLayer.setVisibility(View.VISIBLE);
                mHeroLayer.setAlpha(0f);
                mHeroLayer.animate()
                        .alpha(1f)
                        .setDuration(PAGE_HERO_TRANSITION_DURATION)
                        .start();
            } else {
                mHeroLayer.animate()
                        .alpha(0f)
                        .setDuration(PAGE_HERO_TRANSITION_DURATION)
                        .withEndAction(() -> mHeroLayer.setVisibility(View.GONE))
                        .start();
            }
        }
        if (!visible) {
            mHeroHandler.removeCallbacks(mHeroImageRun);
            cancelHeroAnim();
            stopAutoScroll();
        } else {
            scheduleAutoScroll();
        }
    }

    private void refreshHomeMode() {
        boolean lowPower = Hawk.get(HawkConfig.HOME_LOW_POWER, false);
        if (mLowPowerHome == lowPower) return;
        mLowPowerHome = lowPower;
        ++mHeroSeq;
        mHeroHandler.removeCallbacksAndMessages(null);
        stopAutoScroll();
        cancelHeroAnim();
        if (mHeroBackdrop != null) {
            Glide.clear(mHeroBackdrop);
            mHeroBackdrop.setImageDrawable(null);
            mHeroBackdrop.setScaleX(1f);
            mHeroBackdrop.setScaleY(1f);
        }
        if (mHeroBackdropAlt != null) {
            Glide.clear(mHeroBackdropAlt);
            mHeroBackdropAlt.setImageDrawable(null);
            mHeroBackdropAlt.setAlpha(0f);
            mHeroBackdropAlt.setScaleX(1f);
            mHeroBackdropAlt.setScaleY(1f);
        }
        mHeroFront = null;
        mHeroPic = null;
        if (mHeroVod != null) bindHero(mHeroVod);
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_home;
    }

    @Override
    protected void init() {
        mViewActive = true;
        mExecutorService =  Executors.newSingleThreadExecutor();
        mSourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        tvLive = findViewById(R.id.tvLive);
        tvSearch = findViewById(R.id.tvSearch);
        tvSetting = findViewById(R.id.tvSetting);
        tvCollect = findViewById(R.id.tvFavorite);
        tvHistory = findViewById(R.id.tvHistory);
        tvPush = findViewById(R.id.shareLayout);
        mHotGridView = findViewById(R.id.tvHotList);
        mHeroBackdrop = requireActivity().findViewById(R.id.ivHeroBackdrop);
        mHeroBackdropAlt = requireActivity().findViewById(R.id.ivHeroBackdropAlt);
        mHeroScrim = requireActivity().findViewById(R.id.viewHeroScrim);
        mHeroLayer = requireActivity().findViewById(R.id.homeHeroLayer);
        mHeroTitle = findViewById(R.id.tvHeroTitle);
        mHeroNote = findViewById(R.id.tvHeroNote);
        mHeroMeta = findViewById(R.id.tvHeroMeta);
        mHeroDesc = findViewById(R.id.tvHeroDesc);
        mHomeTitle = findViewById(R.id.tvHomeTitle);
        mHomeSub = findViewById(R.id.tvHomeSub);

        tvLive.setOnClickListener(this);
        tvSearch.setOnClickListener(this);
        tvSetting.setOnClickListener(this);
        tvHistory.setOnClickListener(this);
        tvPush.setOnClickListener(this);
        tvCollect.setOnClickListener(this);
        tvLive.setOnFocusChangeListener(focusChangeListener);
        tvSearch.setOnFocusChangeListener(focusChangeListener);
        tvSetting.setOnFocusChangeListener(focusChangeListener);
        tvHistory.setOnFocusChangeListener(focusChangeListener);
        tvPush.setOnFocusChangeListener(focusChangeListener);
        tvCollect.setOnFocusChangeListener(focusChangeListener);

        rootView.getViewTreeObserver().addOnGlobalFocusChangeListener(mFocusChangeListener);
        initGridView();
        setLoadSir(mHotGridView);
        getVideos();
    }

    private void initGridView() {
        // 首页为单排大卡片横向流：焦点随遥控方向横向推进
        mHotGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext, LinearLayoutManager.HORIZONTAL, false));
        mAdapter = new HomeHotVodAdapter();
        mAdapter.setOnItemClickListener((adapter, view, position) -> {
            if (ApiConfig.getInstance().getSiteBeanList().isEmpty()) {
                Toast.makeText(mContext, "配置文件获取中，请稍后", Toast.LENGTH_SHORT).show();
                return;
            }
            Movie.Video vod = ((Movie.Video) adapter.getItem(position));
            if (vod.isMore) {
                //点击查看更多电影
                getMoreVideos(mStart);
                mStart += 20;
                return;
            }
            //首页卡片点击统一进聚合搜索：不管有没有 id，都拿片名去搜
            if (vod.name == null || vod.name.isEmpty()) {
                return;
            }
            Intent newIntent = new Intent(mContext, SearchActivity.class);
            newIntent.putExtra("title", vod.name);
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            mActivity.startActivity(newIntent);
        });

        mAdapter.setOnLoadMoreListener(() -> {
            //滑到底触发
            mAdapter.setEnableLoadMore(true);
            getMoreVideos(mStart);
            mStart += 20;
        }, mHotGridView);
        mAdapter.setLoadMoreView(new LoadMoreView());

        mHotGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                if (mCarouselInitializing) {
                    itemView.animate().cancel();
                    itemView.setScaleX(1f);
                    itemView.setScaleY(1f);
                    return;
                }
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(ITEM_SCALE_DURATION)
                        .setInterpolator(new DecelerateInterpolator(1.8f)).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (mCarouselInitializing) {
                    return;
                }
                // 手动方向键选中的一张卡：与自动轮播走同一条选中逻辑，落点和动画完全一致
                applyCardSelected(itemView, position);
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });
        // 有按键操作就打断自动轮播并重新计时
        mHotGridView.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                mUserOperatingCards = true;
                stopAutoScroll();
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                scheduleAutoScroll();
            }
            return false;
        });
        mHotGridView.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mUserOperatingCards = true;
                stopAutoScroll();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP
                    || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                    mHotGridView.post(() -> mHotGridView.snapToCenterCard());
                }
                scheduleAutoScroll();
            }
            return false;
        });
        // Scroll-dependent card effects follow the card currently nearest the screen center.
        mHotGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                updateCenteredCardState();
                updateCardDepth();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE && !mCarouselInitializing) {
                    scheduleAutoScroll();
                }
            }
        });
        mHotGridView.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                oldLeft, oldTop, oldRight, oldBottom) -> updateCardDepth());
        mHotGridView.setAdapter(mAdapter);
    }

    /**
     * 选中某张卡片的唯一出口：更新卡片层次、居中列表、
     * 同步背景海报与节目信息、重置轮播计时。
     * 手动方向键（onItemSelected）和自动轮播共用它，落点、曲线、时长完全一致。
     */
    private void applyCardSelected(View card, int position) {
        if (mHotGridView == null || mAdapter == null || mCarouselInitializing) {
            return;
        }
        boolean positionChanged = mFocusedCardPosition != position;
        mHotGridView.setSelectedPosition(position);
        mFocusedCardPosition = position;
        updateCardDepth();
        // 选中的卡始终停在屏幕正中，切换一次就居中一次
        if (positionChanged && !mHotGridView.isScrolling()) {
            centerCardAt(position, card);
        }
        List<Movie.Video> data = mAdapter.getData();
        if (!data.isEmpty() && position >= 0 && position < data.size()) {
            // 背景海报与节目信息跟随当前选中的卡
            bindHero(data.get(position));
        }

        if (!mAutoScrollingCards) {
            scheduleAutoScroll();
        }
    }

    /**
     * 手动拖动时，屏幕中央的卡片就是电视光标当前指向的卡片。
     * 这里不启动额外滚动，只同步选中样式和背景，避免拖动过程被焦点逻辑抢滚动。
     */
    private void updateCenteredCardState() {
        if (mHotGridView == null || mAdapter == null || mHotGridView.getChildCount() == 0) {
            return;
        }
        int centerX = mHotGridView.contentCenterX();
        View nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < mHotGridView.getChildCount(); i++) {
            View child = mHotGridView.getChildAt(i);
            if (child == null) {
                continue;
            }
            int distance = Math.abs((child.getLeft() + child.getRight()) / 2 - centerX);
            if (distance < nearestDistance) {
                nearest = child;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            return;
        }
        int position = mHotGridView.getChildAdapterPosition(nearest);
        List<Movie.Video> data = mAdapter.getData();
        if (position < 0 || position >= data.size() || position == mFocusedCardPosition) {
            return;
        }
        mFocusedCardPosition = position;
        mHotGridView.setSelectedPosition(position);
        bindHero(data.get(position));
    }

    private void updateCardDepth() {
        if (mHotGridView == null || mAdapter == null) {
            return;
        }
        float centerX = mHotGridView.contentCenterX();
        for (int i = 0; i < mHotGridView.getChildCount(); i++) {
            View child = mHotGridView.getChildAt(i);
            int position = child == null ? -1 : mHotGridView.getChildAdapterPosition(child);
            if (position < 0 || position >= mAdapter.getData().size()) {
                continue;
            }
            float offset = ((child.getLeft() + child.getRight()) / 2f - centerX)
                    / Math.max(1f, child.getWidth() * 1.45f);
            float normalized = Math.max(-1f, Math.min(1f, offset));
            float angle = normalized * CARD_ARC_ROTATION;
            float depth = (float) Math.cos(Math.toRadians(Math.abs(angle)));
            float scale = position == mFocusedCardPosition ? CARD_FRONT_SCALE : 1f;
            float drop = child.getHeight() * 0.42f * (1f - depth);
            float alpha = Math.max(CARD_MIN_ALPHA, 1f - (1f - depth) * 0.6f);
            child.setPivotX(child.getWidth() / 2f);
            child.setPivotY(child.getHeight() / 2f);
            child.setTranslationY(drop);
            child.setRotationY(angle);
            child.setRotation(-angle * 0.08f);
            child.setScaleX(scale);
            child.setScaleY(scale);
            child.setAlpha(alpha);
            child.setTranslationZ(depth * 60f);
        }
    }

    /** 把指定位置的卡挪到列表正中；卡还没布局时按位置直接落位 */
    private void centerCardAt(int position, View card) {
        if (mHotGridView == null || mHotGridView.isUserTouching()) {
            return;
        }
        if (card != null && card.getWidth() > 0) {
            mHotGridView.centerCard(card);
            return;
        }
        View laidOut = mHotGridView.getLayoutManager() != null
                ? mHotGridView.getLayoutManager().findViewByPosition(position) : null;
        if (laidOut != null) {
            mHotGridView.centerCard(laidOut);
        } else {
            mHotGridView.centerPosition(position);
        }
    }

    /**
     * 按卡片中心到屏幕中心的距离更新大小、明暗和轻微旋转。
     */
    @Override
    protected void onFragmentResume() {
        mViewActive = true;
        refreshHomeMode();
        if (mFeedCancelled && mAdapter != null && (mHistoryLoadPending || mAdapter.getData().isEmpty())) {
            getVideos();
        }
        scheduleAutoScroll();
    }

    @Override
    protected void onFragmentPause() {
        mViewActive = false;
        mFeedRequestId++;
        mFeedCancelled = true;
        if (mSourceViewModel != null) {
            mSourceViewModel.cancelHomeRequests();
        }
        stopAutoScroll();
        mHeroHandler.removeCallbacks(mHeroImageRun);
        cancelHeroAnim();
    }

    @Override
    public void onDestroyView() {
        mViewActive = false;
        mHistoryLoadPending = false;
        mFeedRequestId++;
        if (mSourceViewModel != null) {
            mSourceViewModel.cancelHomeRequests();
        }
        stopAutoScroll();
        mHeroHandler.removeCallbacks(mHeroImageRun);
        cancelHeroAnim();
        if (mHeroLayer != null) {
            mHeroLayer.animate().cancel();
            mHeroLayer.setAlpha(0f);
            mHeroLayer.setVisibility(View.GONE);
        }
        if (mHeroBackdrop != null) {
            mHeroBackdrop.setImageDrawable(null);
        }
        if (mHeroBackdropAlt != null) {
            mHeroBackdropAlt.setImageDrawable(null);
        }
        if (rootView != null) {
            rootView.getViewTreeObserver().removeOnGlobalFocusChangeListener(mFocusChangeListener);
        }
        mHeroBackdrop = null;
        mHeroBackdropAlt = null;
        mHeroScrim = null;
        mHeroLayer = null;
        mHeroPic = null;
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Fragment 退出时释放成员线程池，避免其常驻
        if (mExecutorService != null) {
            mExecutorService.shutdownNow();
        }
    }

    private void scheduleAutoScroll() {
        mAutoScrollHandler.removeCallbacks(mAutoScrollRun);
        if (mAutoScrollingCards || mLowPowerHome) {
            return;
        }
        mUserOperatingCards = false;
        if (mViewActive && mHeroVisible) {
            if (!mHeroReady) {
                return;
            }
            mAutoScrollHandler.postDelayed(mAutoScrollRun, AUTO_SCROLL_DELAY);
        }
    }

    private void stopAutoScroll() {
        mAutoScrollHandler.removeCallbacks(mAutoScrollRun);
        mAutoScrollingCards = false;
    }

    private void resetCarouselState() {
        mCarouselInitializing = true;
        stopAutoScroll();
        mFocusedCardPosition = 0;
        if (mHotGridView != null) {
            mHotGridView.scrollToPosition(mFocusedCardPosition);
            mHotGridView.post(() -> {
                if (mHotGridView == null) {
                    return;
                }
                mHotGridView.setSelection(mFocusedCardPosition);
                mHotGridView.scrollToPosition(mFocusedCardPosition);
                mHotGridView.post(() -> {
                    if (mHotGridView == null) {
                        return;
                    }
                    mCarouselInitializing = false;
                    View selected = mHotGridView.getLayoutManager() == null
                            ? null
                            : mHotGridView.getLayoutManager().findViewByPosition(mFocusedCardPosition);
                    if (selected != null) {
                        selected.requestFocus();
                    }
                    if (!mAutoScrollingCards) {
                        scheduleAutoScroll();
                    }
                });
            });
        } else {
            mCarouselInitializing = false;
            scheduleAutoScroll();
        }
    }

    /** 设置真实卡片列表；自动轮播到末尾后回到第一张。 */
    private void setHomeCards(List<Movie.Video> videos) {
        if (mAdapter == null || videos == null) {
            return;
        }
        mCarouselInitializing = true;
        stopAutoScroll();
        ArrayList<Movie.Video> source = new ArrayList<>();
        for (Movie.Video video : videos) {
            if (video != null && !video.isMore) {
                source.add(video);
            }
        }
        mAdapter.setEnableLoadMore(false);
        mAdapter.setNewData(source);
    }

    /**
     * 自动推进到下一张卡片：正在滚动时跳过（等下一次计时），
     * 焦点停在左栏说明用户正在导航，此时不抢焦点也不轮播
     */
    private void autoScrollNext() {
        if (!mViewActive || !mHeroVisible || mUserOperatingCards
                || mHotGridView == null || mAdapter == null) return;
        if (mHotGridView.isScrolling()) {
            return;
        }
        List<Movie.Video> data = mAdapter.getData();
        if (data.size() < 2) return;
        int current = mHotGridView.getSelectedPosition();
        if (current < 0 || current >= data.size()) {
            current = 0;
        }
        int next = current + 1 < data.size() ? current + 1 : 0;
        View target = mHotGridView.getLayoutManager() != null
                ? mHotGridView.getLayoutManager().findViewByPosition(next) : null;
        // 能把焦点交给下一张卡就交给它，onItemSelected 会统一处理选中态与居中；
        // 拿不到焦点（触摸模式、卡尚未布局）就自己把选中态补上，轮播不会停在原地
        if (target != null && mHotGridView.hasFocus() && target.requestFocus()) {
            return;
        }
        applyCardSelected(target, next);
    }

    private int movieCount() {
        if (mAdapter == null) {
            return 0;
        }
        List<Movie.Video> data = mAdapter.getData();
        int count = data.size();
        while (count > 0 && data.get(count - 1).isMore) {
            count--;
        }
        return count;
    }

    /**
     * 从当前聚焦的控件反查它在卡片列表中的位置，同步背景海报
     */
    private void bindHeroFromFocus(View focused) {
        if (mHotGridView == null || mAdapter == null || focused == null) return;
        // 焦点可能在卡片内部子控件上，必须用 findContainingViewHolder 向上找，
        // getChildAdapterPosition 只接受 RecyclerView 的直接子 View，否则会 ClassCastException
        RecyclerView.ViewHolder holder = mHotGridView.findContainingViewHolder(focused);
        if (holder == null) return;
        List<Movie.Video> data = mAdapter.getData();
        int position = holder.getLayoutPosition();
        if (position < 0 || position >= data.size()) return;
        bindHero(data.get(position));
    }

    private void getVideos() {
        mFeedCancelled = false;
        mHistoryLoadPending = false;
        final int requestId = ++mFeedRequestId;
        int homeSource = Hawk.get(HawkConfig.HOME_REC, 0);
        L.w("homeFeed start homeSource=" + homeSource
                + " recCount=" + (mRecommendVideos == null ? 0 : mRecommendVideos.size()));
        if (homeSource == 1) {
            //网站推荐
            int recCount = mRecommendVideos == null ? 0 : mRecommendVideos.size();
            setHeader("网站推荐", recCount > 0 ? "共 " + recCount + " 部" : "当前接口暂无推荐");
            showSuccess();
            if (recCount > 0) {
                setHomeCards(mRecommendVideos);
                resetCarouselState();
                bindHero(mRecommendVideos.get(0));
            } else {
                //当前数据源的推荐也可能为空（豆瓣系被风控时会这样），必须走同一条兜底链，不能留白页
                loadHotRankFeed(requestId);
            }
        } else if (homeSource == 2) {
            //历史
            mHistoryLoadPending = true;
            showLoading();
            mExecutorService.submit(() -> {
                List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(100);
                List<Movie.Video> vodList = new ArrayList<>();
                for (VodInfo vodInfo : allVodRecord) {
                    Movie.Video vod = new Movie.Video();
                    vod.id = vodInfo.id;
                    vod.sourceKey = vodInfo.sourceKey;
                    vod.name = vodInfo.name;
                    vod.pic = vodInfo.pic;
                    if (vodInfo.playNote != null && !vodInfo.playNote.isEmpty()) {
                        vod.note = "上次看到" + vodInfo.playNote;
                    }
                    vodList.add(vod);
                }
                mActivity.runOnUiThread(() -> {
                    if (!isPageActive() || requestId != mFeedRequestId) return;
                    mHistoryLoadPending = false;
                    setHeader("继续观看", vodList.isEmpty() ? "暂无播放记录" : "最近 " + vodList.size() + " 条播放记录");
                    showSuccess();
                    if (vodList.isEmpty()) {
                        //没有播放记录也不留白页，同样走兜底链
                        setHomeCards(vodList);
                        resetCarouselState();
                        loadHotRankFeed(requestId);
                    } else {
                        setHomeCards(vodList);
                        resetCarouselState();
                        bindHero(vodList.get(0));
                    }
                });
            });
        } else {
            //豆瓣推荐固定放在第一位；豆瓣被风控或断网时，loadDoubanRecommend 内部会依次
            //回落到本地缓存 → 主流热榜 → 站点推荐 → 聚合源，保证首页不留白
            showLoading();
            loadDoubanRecommend(requestId);
        }
    }

    /**
     * 豆瓣推荐层：主页默认就是这一层，直连取不到就用上次成功的本地缓存；
     * 连缓存都没有才回落到热榜 / 站点推荐 / 聚合源，始终不留白页。
     * 豆瓣按出口 IP 风控（返回「检测到有异常请求从您的IP发出」），一旦命中就冷却几分钟不再直连。
     */
    private void loadDoubanRecommend(final int requestId) {
        if (!mViewActive || mExecutorService == null || mExecutorService.isShutdown()) {
            return;
        }
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                if (!mViewActive || requestId != mFeedRequestId) {
                    return;
                }
                int year = DateUtil.getLastNYear(0);
                List<Movie.Video> list0 = null;
                if (isDoubanBlocked()) {
                    //冷却期内不再碰豆瓣接口，直接用缓存，省得把限流期越砸越长
                    L.w("homeFeed douban skip: blocked");
                } else {
                    String doubanUrl = UiConstans.DOU_BAN_URL + (year - 1) + "," + year + "&start=" + 0;
                    //超时压到 10 秒：豆瓣挂起时尽快收尾，别让用户对着转圈等
                    String result = ApiConfig.getWithHeaders(doubanUrl, doubanHeaders(), 10 * 1000);
                    boolean risk = result != null && result.contains("\"msg\"");
                    if (risk) {
                        sDoubanBlockedUntil = System.currentTimeMillis() + DOU_BAN_BLOCK_COOLDOWN_MS;
                    }
                    list0 = parseJson(result);
                    if (list0 != null && !list0.isEmpty()
                            && !isDoubanBlocked() && INITIAL_EXTRA_RECOMMENDATIONS > 0) {
                        String extraUrl = UiConstans.DOU_BAN_URL + (year - 1) + "," + year + "&start=20";
                        String extraResult = ApiConfig.getWithHeaders(extraUrl, doubanHeaders(), 10 * 1000);
                        if (extraResult != null && extraResult.contains("\"msg\"")) {
                            sDoubanBlockedUntil = System.currentTimeMillis() + DOU_BAN_BLOCK_COOLDOWN_MS;
                        } else {
                            List<Movie.Video> extra = parseJson(extraResult);
                            if (extra != null && !extra.isEmpty()) {
                                int remaining = INITIAL_EXTRA_RECOMMENDATIONS;
                                for (Movie.Video video : extra) {
                                    if (remaining == 0) {
                                        break;
                                    }
                                    if (video != null && !containsMovie(list0, video)) {
                                        list0.add(video);
                                        remaining--;
                                    }
                                }
                            }
                        }
                    }
                    L.w("homeFeed douban len=" + (result == null ? -1 : result.length())
                            + " risk=" + risk
                            + " parsed=" + (list0 == null ? -1 : list0.size()));
                }
                final boolean fresh = !NullUtil.isNull(list0) && !list0.isEmpty();
                List<Movie.Video> videos = new ArrayList<>();
                if (fresh) {
                    //成功一次就缓存，豆瓣按 IP 限流（时通时断）时用它兜底
                    saveDoubanCache(list0);
                    videos.addAll(list0);
                } else {
                    List<Movie.Video> cache = loadDoubanCache();
                    L.w("homeFeed douban cache=" + (cache == null ? 0 : cache.size()));
                    if (cache != null && !cache.isEmpty()) {
                        videos.addAll(cache);
                    }
                }
                final List<Movie.Video> resultVideos = videos;
                mActivity.runOnUiThread(() -> {
                    if (!isPageActive() || requestId != mFeedRequestId) return;
                    if (resultVideos.isEmpty()) {
                        //豆瓣直连与缓存都拿不到：回落到热榜 / 站点推荐 / 聚合源，依然不留白页
                        loadHotRankFeed(requestId);
                        return;
                    }
                    showSuccess();
                    setHeader("豆瓣推荐", "豆瓣 · " + (year - 1) + "-" + year + " 评分排序");
                    setHomeCards(resultVideos);
                    resetCarouselState();
                    bindHero(resultVideos.get(0));
                });
            }
        });
    }

    /** 豆瓣按出口 IP 风控：带上来源页 Referer 和固定的 bid Cookie，降低被判定为异常请求的概率 */
    private Map<String, String> doubanHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://movie.douban.com/");
        headers.put("Cookie", "bid=" + doubanBid());
        return headers;
    }

    /** bid 是豆瓣的身份串，首次随机生成后固定保存，每次换新身份反而更容易被判异常请求 */
    private String doubanBid() {
        String bid = Hawk.get(HawkConfig.DOU_BAN_BID, "");
        if (NullUtil.isNull(bid)) {
            String chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
            StringBuilder sb = new StringBuilder();
            java.util.Random random = new java.util.Random();
            for (int i = 0; i < 11; i++) {
                sb.append(chars.charAt(random.nextInt(chars.length())));
            }
            bid = sb.toString();
            Hawk.put(HawkConfig.DOU_BAN_BID, bid);
        }
        return bid;
    }

    private boolean isDoubanBlocked() {
        return System.currentTimeMillis() < sDoubanBlockedUntil;
    }

    private static final String HOT_RANK_URL = "https://api.bilibili.com/pgc/web/rank/list?day=3&season_type=";
    //热榜分类优先级：电视剧 → 电影 → 番剧 → 国创，取第一个有数据的
    private static final String[] HOT_RANK_TYPES = {"5", "2", "1", "4"};
    private static final int HOT_RANK_MAX = 40;

    /**
     * 主流热榜层（B站 PGC 影视/剧集榜）：片名干净、海报由图床直出，作为首页推荐的首选层。
     * 拿不到才回落到「数据源推荐 → 聚合源 → 豆瓣」这条链
     */
    private void loadHotRankFeed(final int requestId) {
        if (!mViewActive || mExecutorService == null || mExecutorService.isShutdown()) {
            return;
        }
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                for (String type : HOT_RANK_TYPES) {
                    if (!mViewActive || requestId != mFeedRequestId) {
                        return;
                    }
                    List<Movie.Video> list = null;
                    try {
                        String json = HttpDoRequest.getInstance(getContext())
                                .doGetRequestInOkGo(HOT_RANK_URL + type, null, true, 10 * 1000);
                        list = parseHotRank(json);
                    } catch (Throwable th) {
                        L.e(th);
                    }
                    L.w("homeFeed hotRank type=" + type + " -> " + (list == null ? -1 : list.size()));
                    if (list != null && !list.isEmpty()) {
                        final List<Movie.Video> videos = list;
                        mActivity.runOnUiThread(() -> {
                            if (!isPageActive() || requestId != mFeedRequestId) return;
                            showFallbackVideos("热门榜单", "正在热播 · 排行榜实时更新", videos);
                        });
                        return;
                    }
                }
                //热榜也没拿到，回到原有兜底链
                mActivity.runOnUiThread(() -> {
                    if (!isPageActive() || requestId != mFeedRequestId) return;
                    showRecommendFallback();
                });
            }
        });
    }

    /**
     * 解析 B站 PGC 榜单：result.list[] 里的 title 是片名、cover 是海报，
     * cover 有 http 的必须换成 https，否则被 cleartext 策略拦掉不显示
     */
    private List<Movie.Video> parseHotRank(String json) {
        List<Movie.Video> videos = new ArrayList<>();
        if (NullUtil.isNull(json)) return videos;
        try {
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            if (obj == null || !obj.has("result") || obj.get("result").isJsonNull()) return videos;
            JsonObject result = obj.getAsJsonObject("result");
            if (result == null || !result.has("list") || result.get("list").isJsonNull()) return videos;
            JsonArray arr = result.getAsJsonArray("list");
            for (int i = 0; i < arr.size() && videos.size() < HOT_RANK_MAX; i++) {
                JsonElement el = arr.get(i);
                if (el == null || !el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                String title = o.has("title") && !o.get("title").isJsonNull() ? o.get("title").getAsString() : null;
                if (NullUtil.isNull(title)) continue;
                Movie.Video vod = new Movie.Video();
                vod.name = title;
                vod.note = "热榜 TOP" + (i + 1);
                if (o.has("cover") && !o.get("cover").isJsonNull()) {
                    String cover = o.get("cover").getAsString();
                    vod.pic = cover.startsWith("http://") ? "https://" + cover.substring(7) : cover;
                }
                videos.add(vod);
            }
        } catch (Throwable th) {
            L.e(th);
        }
        return videos;
    }

    private boolean containsMovie(List<Movie.Video> videos, Movie.Video candidate) {
        if (candidate == null || candidate.name == null) {
            return false;
        }
        for (Movie.Video video : videos) {
            if (video != null && candidate.name.equalsIgnoreCase(video.name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 首页推荐兜底链：当前数据源推荐 → 配置里其它聚合源 → 豆瓣（直连/缓存） → 空态
     */
    private void showRecommendFallback() {
        L.w("homeFeed fallback recCount=" + (mRecommendVideos == null ? 0 : mRecommendVideos.size()));
        if (mRecommendVideos != null && !mRecommendVideos.isEmpty()) {
            showFallbackVideos("网站推荐", "已自动匹配可用的数据源", mRecommendVideos);
            return;
        }
        fillFromOtherSource(mFeedRequestId);
    }

    private void showFallbackVideos(String title, String subTitle, List<Movie.Video> videos) {
        if (!isPageActive() || videos == null || videos.isEmpty()) {
            return;
        }
        //兜底链拿到内容也必须收起 LoadSir 的转圈，否则卡片被加载层遮住
        showSuccess();
        setHeader(title, subTitle);
        setHomeCards(videos);
        resetCarouselState();
        //兜底列表已是一次性全量，不能再按豆瓣分页续拉
        mAdapter.setEnableLoadMore(false);
        //列表换数据后控件可能停在上一份数据的滚动位置，强制回到第一张
        resetCarouselState();
        bindHero(videos.get(0));
    }

    /**
     * 当前数据源（豆瓣系）也取不到时，换配置里别的源来取，保证首页有内容：
     * 优先 http api 源（出图最稳），再试 csp 源；跳过工具类站点（云盘/盘搜没有海报墙）
     */
    private void fillFromOtherSource(final int requestId) {
        List<SiteBean> all = ApiConfig.getInstance().getSiteBeanList();
        SiteBean home = ApiConfig.getInstance().getHomeBean();
        String homeKey = home == null ? "" : home.getKey();
        List<SiteBean> candidates = new ArrayList<>();
        for (int pass = 0; pass < 2 && candidates.isEmpty(); pass++) {
            for (SiteBean bean : all) {
                if (bean == null || NullUtil.isNull(bean.getKey())) continue;
                if (bean.getKey().equals(homeKey) || "豆瓣".equals(bean.getKey())) continue;
                if (!bean.isSearchable()) continue;
                boolean httpApi = bean.getType() == 0 || bean.getType() == 1;
                if ((pass == 0) != httpApi) continue;
                candidates.add(bean);
                if (candidates.size() >= 4) break;
            }
        }
        StringBuilder keys = new StringBuilder();
        for (SiteBean bean : candidates) {
            keys.append(bean.getKey()).append("(type=").append(bean.getType()).append(") ");
        }
        L.w("homeFeed candidates=" + candidates.size() + " [" + keys + "] homeKey=" + homeKey);
        trySource(candidates, 0, requestId);
    }

    private void trySource(final List<SiteBean> candidates, final int index, final int requestId) {
        if (!isPageActive() || requestId != mFeedRequestId) {
            return;
        }
        if (index >= candidates.size()) {
            L.w("homeFeed all sources empty, fallback to douban");
            mActivity.runOnUiThread(() -> {
                if (!isPageActive()) return;
                //聚合源全军覆没，最后一层回落豆瓣（直连→本地缓存），仍无内容才显示空态
                loadDoubanRecommend(requestId);
            });
            return;
        }
        final SiteBean bean = candidates.get(index);
        L.w("homeFeed try#" + index + " key=" + bean.getKey() + " type=" + bean.getType());
        mSourceViewModel.getHomeRecList(bean, new ArrayList<String>(), new SourceViewModel.HomeRecCallback() {
            @Override
            public void done(List<Movie.Video> videos) {
                if (!isPageActive() || requestId != mFeedRequestId) {
                    return;
                }
                L.w("homeFeed try#" + index + " -> " + (videos == null ? -1 : videos.size()));
                if (videos != null && !videos.isEmpty()) {
                    mActivity.runOnUiThread(() -> {
                        if (!isPageActive() || requestId != mFeedRequestId) return;
                        showFallbackVideos(bean.getName() + " 推荐", "已自动匹配可用的数据源", videos);
                    });
                } else {
                    trySource(candidates, index + 1, requestId);
                }
            }
        });
    }

    private boolean isPageActive() {
        return mViewActive && isAdded() && mAdapter != null && mHotGridView != null;
    }

    private void saveDoubanCache(List<Movie.Video> videos) {
        try {
            Hawk.put(HawkConfig.HOME_DOUBAN_CACHE, new Gson().toJson(videos));
        } catch (Throwable th) {
            L.e(th);
        }
    }

    /**
     * Hawk 存对象会丢泛型元素类型（读回变成 LinkedTreeMap，取用时 ClassCastException 闪退），所以统一存 JSON 字符串
     */
    private List<Movie.Video> loadDoubanCache() {
        try {
            String json = Hawk.get(HawkConfig.HOME_DOUBAN_CACHE, "");
            if (NullUtil.isNull(json)) return null;
            List<Movie.Video> cache = new Gson().fromJson(json, new TypeToken<List<Movie.Video>>() {
            }.getType());
            return (cache == null || cache.isEmpty()) ? null : cache;
        } catch (Throwable th) {
            L.e(th);
            return null;
        }
    }

    private void setHeader(String title, String subTitle) {
        if (mHomeTitle != null) {
            mHomeTitle.setText(title);
        }
        if (mHomeSub != null) {
            mHomeSub.setText(subTitle);
        }
    }

    /**
     * 无内容时的安静空状态：不出图，只留标题与一行提示
     */
    private void setHeroEmpty(String title, String tip) {
        mHeroVod = null;
        mHeroPic = null;
        if (mHeroTitle != null) {
            mHeroTitle.setText(title);
        }
        if (mHeroNote != null) {
            mHeroNote.setVisibility(View.GONE);
        }
        if (mHeroMeta != null) {
            mHeroMeta.setVisibility(View.VISIBLE);
            mHeroMeta.setText(tip);
        }
        if (mHeroDesc != null) {
            mHeroDesc.setVisibility(View.GONE);
        }
    }

    /**
     * 用当前焦点节目的海报与信息刷新背景 hero 区：
     * 文案立即跟进卡片（开销小），海报则排队等焦点停稳后加载
     */
    private void bindHero(Movie.Video vod) {
        if (vod == null || mHeroBackdrop == null || mHeroBackdropAlt == null || vod.isMore) {
            return;
        }
        boolean samePic = !NullUtil.isNull(vod.pic) && vod.pic.equals(mHeroPic);
        mHeroVod = vod;
        applyHeroInfo(vod);
        // 焦点回调和列表回调可能传入不同对象；海报地址没变时只更新文案，不重复解码和淡入。
        if (samePic) {
            return;
        }
        mHeroPic = vod.pic;
        mHeroHandler.removeCallbacks(mHeroImageRun);
        if (NullUtil.isNull(vod.pic)) {
            mHeroReady = true;
            scheduleAutoScroll();
            return;
        }
        mHeroReady = false;
        mHeroHandler.postDelayed(mHeroImageRun, mLowPowerHome ? 300L : HERO_IMAGE_SETTLE_DELAY);
        // 顺手把下一张卡的海报预热好：轮播隔 6 秒才切，这段时间足够把它解码进内存缓存，
        // 到时那一次切换就是纯淡入，不会出现「等大图」的空档
        if (!mLowPowerHome) preloadHeroNext(vod);
    }

    /**
     * 预热下一张卡的海报（按轮播方向），让 6 秒后那次切换不用现下载现解码。
     * 请求参数与真正加载时保持一致（同一尺寸、同一锐化），否则命中不了同一份缓存。
     */
    private void preloadHeroNext(Movie.Video vod) {
        if (mAdapter == null || mHeroBackdrop == null) {
            return;
        }
        List<Movie.Video> data = mAdapter.getData();
        int index = data.indexOf(vod);
        int next = index + 1;
        if (next >= data.size()) {
            next = 0;
        }
        if (next < 0 || next >= data.size() || next == index) {
            return;
        }
        Movie.Video nextVod = data.get(next);
        int width = mHeroBackdrop.getWidth();
        int height = mHeroBackdrop.getHeight();
        if (nextVod == null || nextVod.isMore || NullUtil.isNull(nextVod.pic) || width <= 0 || height <= 0) {
            return;
        }
        Glide.with(mContext)
                .load(GlideImg.heroModel(nextVod.pic))
                .override(width, height)
                .bitmapTransform(new SharpenTransformation(mContext))
                .preload(width, height);
    }

    /** 更新 hero 区的文字信息（片名、备注、元信息、简介） */
    private void applyHeroInfo(Movie.Video vod) {
        mHeroTitle.setText(NullUtil.isNull(vod.name) ? "" : vod.name);
        if (NullUtil.isNull(vod.note)) {
            mHeroNote.setVisibility(View.GONE);
        } else {
            mHeroNote.setVisibility(View.VISIBLE);
            mHeroNote.setText(vod.note);
        }
        String meta = buildMeta(vod);
        if (NullUtil.isNull(meta)) {
            mHeroMeta.setVisibility(View.GONE);
        } else {
            mHeroMeta.setVisibility(View.VISIBLE);
            mHeroMeta.setText(meta);
        }
        if (NullUtil.isNull(vod.des)) {
            mHeroDesc.setVisibility(View.GONE);
        } else {
            mHeroDesc.setVisibility(View.VISIBLE);
            mHeroDesc.setText(vod.des);
        }
    }

    /**
     * 加载背景海报。关键是「先解好、再融过去」：
     * 高清图先在看不见的那层解好（下载 + 裁切放大 + 锐化），就绪之后才和旧海报交叉淡入，
     * 旧海报全程保持全亮——中间不会插进一张糊图，也不会出现两层都半透明让背景发暗的一闪。
     * 只有高清图实在慢（网络差）时，才用低清预览先把背景跟上，等高清到了就地变清晰。
     */
    private void loadHeroImage(Movie.Video vod) {
        if (!mViewActive || !mHeroVisible || vod == null || NullUtil.isNull(vod.pic)
                || mHeroBackdrop == null || mHeroBackdropAlt == null) {
            return;
        }
        final int seq = ++mHeroSeq;
        final String pic = vod.pic;
        final ImageView outgoing = mHeroFront;
        final ImageView incoming = mLowPowerHome ? mHeroBackdrop
                : outgoing == mHeroBackdrop ? mHeroBackdropAlt : mHeroBackdrop;
        // 上一轮可能还在淡入：先停手，并让正在显示的那层回到全亮。
        // 两层同时半透明会让整个背景变暗，那正是「中间闪一下」的来源
        cancelHeroAnim();
        incoming.setAlpha(mLowPowerHome ? 1f : 0f);
        if (outgoing != null && outgoing.getDrawable() != null) {
            outgoing.setAlpha(1f);
            // 新图接上旧图当前的推镜比例，淡入时画面不会突然缩放
            incoming.setScaleX(outgoing.getScaleX());
            incoming.setScaleY(outgoing.getScaleY());
        } else {
            incoming.setScaleX(mLowPowerHome ? 1f : HERO_ZOOM_MIN);
            incoming.setScaleY(mLowPowerHome ? 1f : HERO_ZOOM_MIN);
        }
        mHeroShown = false;

        final int width = mHeroBackdrop.getWidth();
        final int height = mHeroBackdrop.getHeight();
        DrawableRequestBuilder<Object> request = Glide.with(mContext)
                .load(GlideImg.heroModel(pic))
                .error(R.drawable.shape_home_hero_placeholder)
                .dontAnimate();
        if (mLowPowerHome) {
            request = request.override(960, 540).centerCrop();
        } else {
            request = request.bitmapTransform(new SharpenTransformation(mContext));
        }
        if (!mLowPowerHome && width > 0 && height > 0) {
            // 和预热请求用同一尺寸，才能命中同一份缓存，切换时不用现解码
            request = request.override(width, height);
        }
        request.listener(new RequestListener<Object, GlideDrawable>() {
                    @Override
                    public boolean onException(Exception e, Object model, Target<GlideDrawable> target, boolean isFirstResource) {
                        // 还没有任何背景时（首次加载就失败）退一张占位图上去，别留一块空背景
                        if (seq == mHeroSeq && mHeroFront == null) {
                            crossfadeHero(seq, incoming, outgoing);
                        }
                        // 已经有旧海报在显示：什么都不做，继续用它垫着，不会闪
                        return true;
                    }

                    @Override
                    public boolean onResourceReady(GlideDrawable resource, Object model, Target<GlideDrawable> target,
                                                   boolean isFromMemoryCache, boolean isFirstResource) {
                        if (seq != mHeroSeq) {
                            // 已经换了别的海报，这张旧图别往背景上贴
                            return true;
                        }
                        if (!mHeroShown) {
                            // 低清预览已经先顶上时只换图不再淡入一次，画面直接变清晰
                            crossfadeHero(seq, incoming, outgoing);
                        }
                        return false;
                    }
                })
                .into(incoming);

        if (mLowPowerHome) return;

        // 高清图还没好就先用低清预览把背景跟上：只淡入一次，等高清到了就地变清晰
        mHeroHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (seq == mHeroSeq && !mHeroShown) {
                    fadeInPreview(seq, incoming, outgoing, pic);
                }
            }
        }, HERO_PREVIEW_GRACE);
    }

    /** 兜底：低清预览图先顶上，让背景跟得住卡片；高清图随后会把这层就地换清楚 */
    private void fadeInPreview(final int seq, final ImageView incoming, final ImageView outgoing, String pic) {
        Glide.with(mContext)
                .load(GlideImg.model(pic))
                .override(HERO_PREVIEW_WIDTH, HERO_PREVIEW_HEIGHT)
                .dontAnimate()
                .listener(new RequestListener<Object, GlideDrawable>() {
                    @Override
                    public boolean onException(Exception e, Object model, Target<GlideDrawable> target, boolean isFirstResource) {
                        return true;
                    }

                    @Override
                    public boolean onResourceReady(GlideDrawable resource, Object model, Target<GlideDrawable> target,
                                                   boolean isFromMemoryCache, boolean isFirstResource) {
                        if (seq != mHeroSeq || mHeroShown) {
                            // 高清图已经上去了（或已经换卡）：这张低清图别再把背景糊回去
                            return true;
                        }
                        crossfadeHero(seq, incoming, outgoing);
                        return false;
                    }
                })
                .into(incoming);
    }

    /**
     * 新海报就绪：和旧海报交叉淡入，并接上旧图的推镜比例继续缓慢推近，
     * 淡入过程里画面既不突然缩放，也不会出现亮度塌陷。
     */
    private void crossfadeHero(int seq, ImageView incoming, ImageView outgoing) {
        if (seq != mHeroSeq || mHeroShown) {
            return;
        }
        mHeroShown = true;
        mHeroReady = true;
        mHeroFront = incoming;
        if (mLowPowerHome) {
            incoming.setAlpha(1f);
            incoming.setScaleX(1f);
            incoming.setScaleY(1f);
            return;
        }
        float from = incoming.getScaleX() > 0f ? incoming.getScaleX() : HERO_ZOOM_MIN;
        float to = from >= (HERO_ZOOM_MIN + HERO_ZOOM_MAX) / 2f ? HERO_ZOOM_MIN : HERO_ZOOM_MAX;
        startHeroAnim(ObjectAnimator.ofFloat(incoming, "alpha", incoming.getAlpha(), 1f), HERO_FADE_DURATION);
        startHeroAnim(ObjectAnimator.ofFloat(incoming, "scaleX", from, to), HERO_ZOOM_DURATION);
        startHeroAnim(ObjectAnimator.ofFloat(incoming, "scaleY", from, to), HERO_ZOOM_DURATION);
        if (outgoing != null && outgoing.getDrawable() != null) {
            startHeroAnim(ObjectAnimator.ofFloat(outgoing, "alpha", outgoing.getAlpha(), 0f), HERO_FADE_DURATION);
        }
        if (!mAutoScrollingCards) {
            scheduleAutoScroll();
        }
    }

    private void startHeroAnim(ObjectAnimator animator, long duration) {
        mHeroAnims.add(animator);
        animator.setDuration(duration).start();
    }

    /** 换图前把上一轮的淡入/推镜全部停掉，否则两个动画抢同一个属性会把画面打断 */
    private void cancelHeroAnim() {
        for (ObjectAnimator animator : mHeroAnims) {
            animator.cancel();
        }
        mHeroAnims.clear();
    }

    /**
     * 拼接 "年份 · 地区 类型 · 主演" 形式的元信息行
     */
    private String buildMeta(Movie.Video vod) {
        StringBuilder sb = new StringBuilder();
        if (vod.year > 0) {
            sb.append(vod.year);
        }
        if (!NullUtil.isNull(vod.area)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(vod.area);
        }
        if (!NullUtil.isNull(vod.type)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(vod.type);
        }
        if (!NullUtil.isNull(vod.actor)) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(vod.actor);
        }
        return sb.toString();
    }

    private void getMoreVideos(int start) {
        if (!mViewActive || mExecutorService == null || mExecutorService.isShutdown()) {
            return;
        }
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                if (!mViewActive) {
                    return;
                }
                //豆瓣按出口 IP 限流：正处于冷却期或已翻到上限就直接收尾，不再砸接口
                if (isDoubanBlocked() || start > DOU_BAN_MAX_START) {
                    mActivity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (mViewActive && mAdapter != null) {
                                mAdapter.loadMoreEnd();
                            }
                        }
                    });
                    return;
                }
                int year = DateUtil.getLastNYear(0);
                String doubanUrl = UiConstans.DOU_BAN_URL + (year - 1) + "," + year + "&start=" + start;
                String result = ApiConfig.getWithHeaders(doubanUrl, doubanHeaders(), 10 * 1000);
                if (result != null && result.contains("\"msg\"")) {
                    sDoubanBlockedUntil = System.currentTimeMillis() + DOU_BAN_BLOCK_COOLDOWN_MS;
                }
                List<Movie.Video> listMore = parseJson(result);
                mActivity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!mViewActive || mAdapter == null) {
                            return;
                        }
                        //UiHelper.showToast(getActivity(), doubanUrl + " result=" + result);
                        L.d(doubanUrl + " resultLength=" + (result == null ? -1 : result.length()));
                        if (NullUtil.isNull(listMore) || listMore.isEmpty()) {
                            //豆瓣被限流或已到底：结束本轮加载，避免继续砸接口加重限流
                            mAdapter.loadMoreEnd();
                            return;
                        }
                        mAdapter.addData(listMore);//解决焦点跳走的问题
                        mAdapter.loadMoreComplete();
                    }
                });
            }
        });
    }

    private ArrayList<Movie.Video> parseJson(String json) {
        ArrayList<Movie.Video> videos = new ArrayList<>();
        try {
            JsonObject infoJson = new Gson().fromJson(json, JsonObject.class);
            //豆瓣风控时会返回 {"msg":"检测到有异常请求从您的IP发出,请登录再试!","r":1}，没有 data 字段
            if (infoJson == null || !infoJson.has("data") || infoJson.get("data").isJsonNull()) {
                return videos;
            }
            JsonArray array = infoJson.getAsJsonArray("data");
            for (JsonElement ele : array) {
                JsonObject obj = (JsonObject) ele;
                Movie.Video vod = new Movie.Video();
                vod.name = obj.get("title").getAsString();
                vod.note = obj.get("rate").getAsString();
                vod.pic = obj.get("cover").getAsString();
                videos.add(vod);
            }
        } catch (Throwable th) {
            L.e(th);
        }
        return videos;
    }

    private View.OnFocusChangeListener focusChangeListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            // TV 端焦点动画：用 DecelerateInterpolator 替换 BounceInterpolator，
            // 时长从 300ms 压缩到 150ms，连按方向键不再出现视觉粘连抖动
            if (hasFocus) {
                v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else {
                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }
    };

    @Override
    public void onClick(View v) {
        FastClickCheckUtil.check(v);
        if (v.getId() == R.id.tvLive) {
            jumpActivity(LivePlayActivity.class);
        } else if (v.getId() == R.id.tvSearch) {
            jumpActivity(SearchActivity.class);
        } else if (v.getId() == R.id.tvSetting) {
            jumpActivity(SettingActivity.class);
        } else if (v.getId() == R.id.tvHistory) {
            jumpActivity(HistoryActivity.class);
        } else if (v.getId() == R.id.shareLayout) {
        } else if (v.getId() == R.id.tvFavorite) {
            jumpActivity(CollectActivity.class);
        }
    }
}
