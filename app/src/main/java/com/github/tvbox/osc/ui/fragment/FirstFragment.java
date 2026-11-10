package com.github.tvbox.osc.ui.fragment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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

import android.graphics.drawable.Drawable;
import androidx.annotation.Nullable;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.Glide;
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
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.HomeHotVodAdapter;
import com.github.tvbox.osc.util.PosterPreloader;
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

    private boolean isFolder(Movie.Video video) {
        if (video == null) return false;
        return "folder".equalsIgnoreCase(video.tag) || "floder".equalsIgnoreCase(video.tag);
    }

    private LinearLayout tvLive;
    private LinearLayout tvSearch;
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
    /** 本轮背景图是否已经就绪：防止同一请求重复更新背景 */
    private boolean mHeroShown = false;
    /** 当前卡片的背景已准备好；背景未就绪时不启动自动轮播，避免首屏卡片空转。 */
    private boolean mHeroReady;
    private boolean mLowPowerHome;
    /** 背景推镜比例：每张图接着上一张的当前比例继续推，切图时画面不会突然缩放 */
    private static final float HERO_ZOOM_MIN = 1.03f;
    private static final float HERO_ZOOM_MAX = 1.08f;
    private static final long HERO_ZOOM_DURATION = 6000L;
    private static final long HERO_FADE_DURATION = 220L;
    private static final long PAGE_HERO_TRANSITION_DURATION = 100L;
    /** 正在跑的 hero 动画，换图时统一取消：两个动画半路互相打断会让背景亮度抖一下 */
    private final List<ObjectAnimator> mHeroAnims = new ArrayList<>();
    private Movie.Video mHeroVod;
    private String mHeroPic;
    /**
     * 全屏背景是 1080px 大图放大到屏幕宽再逐像素锐化，解码很重；
     * 快速滚卡时逐个加载会互相拖后腿（背景明显跟不上卡片），
     * 所以先排队，焦点停稳后再真正解码。
     */
    private static final long HERO_IMAGE_SETTLE_DELAY = 65L;
    private final Handler mHeroHandler = new Handler(Looper.getMainLooper());
    private final Runnable mHeroImageRun = new Runnable() {
        @Override
        public void run() {
            if (mHeroVod != null) {
                loadHeroImage(mHeroVod);
            }
        }
    };
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

    // 首页静置自动轮播：电视按键或手机触摸结束后，连续无操作 7.5 秒才切换
    private static final long AUTO_SCROLL_DELAY = 7500L;
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
    // 焦点变化同步背景，轮播仍使用原有计时规则。
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
                mHeroLayer.setAlpha(1f);
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
            posterStage.stop();
            ++mHeroSeq;
            if (!mHeroReady) mHeroPic = null;
            mHeroHandler.removeCallbacks(mHeroImageRun);
            cancelHeroAnim();
            stopAutoScroll();
        } else {
            if (mHeroVod != null && !mHeroReady) bindHero(mHeroVod);
            scheduleAutoScroll();
        }
    }

    public boolean focusContent() {
        if (mHotGridView != null) {
            int pos = mHotGridView.getSelectedPosition();
            if (pos < 0) pos = mFocusedCardPosition;
            if (mHotGridView.getLayoutManager() != null) {
                View card = mHotGridView.getLayoutManager().findViewByPosition(pos);
                if (card != null && card.requestFocus()) {
                    return true;
                }
            }
            return mHotGridView.requestFocus();
        }
        return false;
    }

    private HomeCardMotion mCardMotion = HomeCardMotion.current();

    private void refreshHomeMode() {
        HomeCardMotion style = HomeCardMotion.current();
        if (mCardMotion != style) {
            posterStage.stop();
            int realPosition = mAdapter == null ? 0 : mAdapter.realPosition(mFocusedCardPosition);
            mCardMotion = style;
            if (mAdapter != null) {
                mAdapter.setLooping(true);
                mFocusedCardPosition = mAdapter.isLooping()
                        ? mAdapter.loopStartPosition() + Math.max(0, realPosition)
                        : Math.max(0, realPosition);
            }
            if (mHotGridView != null && mHotGridView.getLayoutManager() instanceof LinearLayoutManager) {
                LinearLayoutManager layout = (LinearLayoutManager)mHotGridView.getLayoutManager();
                int orientation = style == HomeCardMotion.LIFT ? LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL;
                if (layout.getOrientation() != orientation) {
                    mHotGridView.stopScroll();
                    layout.setOrientation(orientation);
                    layout.scrollToPositionWithOffset(mFocusedCardPosition, 0);
                }
            }
            if (mHotGridView != null) {
                mHotGridView.setCenterInViewport(style == HomeCardMotion.STACK || style == HomeCardMotion.FAN);
                mHotGridView.setClipChildren(style == HomeCardMotion.LIFT);
                ((android.view.ViewGroup)mHotGridView.getParent()).setClipChildren(style == HomeCardMotion.LIFT);
                mHotGridView.requestLayout();
                mHotGridView.invalidateItemDecorations();
            }
            updateCardDepth();
        }
        boolean lowPower = Hawk.get(HawkConfig.HOME_LOW_POWER, false);
        if (mLowPowerHome == lowPower) return;
        posterStage.stop();
        mLowPowerHome = lowPower;
        ++mHeroSeq;
        mHeroHandler.removeCallbacks(mHeroImageRun);
        stopAutoScroll();
        cancelHeroAnim();
        if (mHeroFront != null) mHeroFront.setAlpha(1f);
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
        tvLive.setOnFocusChangeListener(focusChangeListener);
        tvSearch.setOnFocusChangeListener(focusChangeListener);

        rootView.getViewTreeObserver().addOnGlobalFocusChangeListener(mFocusChangeListener);
        initGridView();
        setLoadSir(mHotGridView);
        getVideos();
    }

    private void initGridView() {
        // 首页为单排大卡片横向流：焦点随遥控方向横向推进
        mHotGridView.setCenterInViewport(mCardMotion == HomeCardMotion.STACK || mCardMotion == HomeCardMotion.FAN);
        mHotGridView.setClipChildren(mCardMotion == HomeCardMotion.LIFT);
        ((android.view.ViewGroup)mHotGridView.getParent()).setClipChildren(mCardMotion == HomeCardMotion.LIFT);
        mHotGridView.setLayoutManager(new V7LinearLayoutManager(this.mContext,
                mCardMotion == HomeCardMotion.LIFT ? LinearLayoutManager.VERTICAL : LinearLayoutManager.HORIZONTAL, false) {
            @Override public void onLayoutCompleted(RecyclerView.State state) {
                super.onLayoutCompleted(state);
                updateCardDepth();
            }
            @Override protected int getExtraLayoutSpace(RecyclerView.State state) {
                // 重叠后可见的侧卡，其原始布局位置可能还在视口外。
                return Math.max(super.getExtraLayoutSpace(state), getOrientation() == LinearLayoutManager.VERTICAL
                        ? getHeight() / 2 : getWidth());
            }
            @Override public void measureChildWithMargins(View child, int widthUsed, int heightUsed) {
                View poster = child.findViewById(R.id.layoutPoster);
                if (poster != null && getWidth() > 0 && getHeight() > 0) {
                    boolean vertical = getOrientation() == LinearLayoutManager.VERTICAL;
                    int insetX = child.getPaddingLeft() + child.getPaddingRight();
                    int insetY = child.getPaddingTop() + child.getPaddingBottom();
                    boolean layered = mCardMotion == HomeCardMotion.STACK || mCardMotion == HomeCardMotion.FAN;
                    float maxWidth = getWidth() * (vertical ? .44f : layered ? .148f : .28f) - insetX;
                    float maxHeight = getHeight() * mCardMotion.heightFraction - insetY;
                    float aspect = mCardMotion.aspect;
                    int height = Math.max(1, Math.round(Math.min(maxHeight, maxWidth / aspect)));
                    android.view.ViewGroup.LayoutParams params = poster.getLayoutParams();
                    int width = Math.max(1, Math.round(height * aspect));
                    if (params.width != width || params.height != height) {
                        params.width = width; params.height = height;
                        poster.setLayoutParams(params);
                    }
                }
                super.measureChildWithMargins(child, widthUsed, heightUsed);
            }
        });
        mAdapter = new HomeHotVodAdapter();
        mAdapter.setOnItemClickListener((adapter, view, position) -> {
            if (!(mActivity instanceof HomeActivity)
                    || !((HomeActivity) mActivity).isHomeConfigReady()) {
                Toast.makeText(mContext, "正在加载配置和影视源，请稍后再试", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ApiConfig.getInstance().getSiteBeanList().isEmpty()) {
                Toast.makeText(mContext, "配置文件获取中，请稍后", Toast.LENGTH_SHORT).show();
                return;
            }
            Movie.Video vod = ((Movie.Video) adapter.getItem(position));
            if (vod == null) return;
            if (vod.isMore) {
                //点击查看更多电影
                getMoreVideos(mStart);
                mStart += 20;
                return;
            }
            // 触控交互优化：如果点击的不是当前居中/选中的卡片，先将其居中并联动大图与简介预览；
            // 再次点击或点击已居中卡片时，才正式进入搜索/播放
            if (position != mFocusedCardPosition && view.isInTouchMode()) {
                applyCardSelected(view, position);

                return;
            }
            if (!NullUtil.isNull(vod.id) && !NullUtil.isNull(vod.sourceKey)
                    && ApiConfig.getInstance().getSiteBean(vod.sourceKey) != null) {
                if (isFolder(vod)) {
                    Intent folder = new Intent(mContext, SearchActivity.class);
                    folder.putExtra("folderId", vod.id);
                    folder.putExtra("folderSourceKey", vod.sourceKey);
                    folder.putExtra("title", vod.name);
                    mActivity.startActivity(folder);
                    return;
                }
                Intent detail = new Intent(mContext, com.github.tvbox.osc.ui.activity.DetailActivity.class);
                detail.putExtra("id", vod.id);
                detail.putExtra("sourceKey", vod.sourceKey);
                detail.putExtra("note", vod.note);
                detail.putExtra("movieName", vod.name);
                detail.putExtra("poster", vod.pic);
                View poster = view.findViewById(R.id.ivThumb);
                android.app.ActivityOptions options = android.app.ActivityOptions.makeScaleUpAnimation(
                        poster, 0, 0, poster.getWidth(), poster.getHeight());
                mActivity.startActivity(detail, options.toBundle());
                return;
            }
            // 片单可能只提供片名，这时继续使用聚合搜索。
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
                // Scroll position owns all card transforms.
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
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    int count = mAdapter == null ? 0 : mAdapter.getItemCount();
                    int selected = mHotGridView.getSelectedPosition();
                    int delta = keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1;
                    if (mAdapter != null && mAdapter.isLooping() && count > 1
                            && ((delta < 0 && selected <= 0) || (delta > 0 && selected >= count - 1))) {
                        int targetPosition = delta < 0 ? count - 1 : 0;
                        View target = mHotGridView.getLayoutManager() == null
                                ? null
                                : mHotGridView.getLayoutManager().findViewByPosition(targetPosition);
                        applyCardSelected(target, targetPosition);
                        if (target != null && !mHotGridView.isInTouchMode()) {
                            target.requestFocus();
                        }
                        return true;
                    }
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                scheduleAutoScroll();
            }
            return false;
        });
        mHotGridView.setGestureListener(touching -> {
            if (touching) stopAutoScroll();
            else scheduleAutoScroll();
        });
        // Scroll-dependent card effects follow the card currently nearest the screen center.
        mHotGridView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (mHotGridView.isInTouchMode()) updateCenteredCardState();
                updateCardDepth();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (mAdapter != null) {
                        Movie.Video focused = mAdapter.getItem(mFocusedCardPosition);
                        if (focused != null) {
                            bindHero(focused);
                        }
                    }
                    if (!mCarouselInitializing) {
                        scheduleAutoScroll();
                    }
                }
            }
        });
        mHotGridView.addOnLayoutChangeListener((v, left, top, right, bottom,
                                                oldLeft, oldTop, oldRight, oldBottom) -> updateCardDepth());
        mHotGridView.setAdapter(mAdapter);
        mHotGridView.addItemDecoration(new RecyclerView.ItemDecoration() {
            private final android.graphics.Paint paint = new android.graphics.Paint();
            @Override public void onDraw(android.graphics.Canvas canvas, RecyclerView parent, RecyclerView.State state) {
                if (mCardMotion != HomeCardMotion.ORBIT || parent.getChildCount() == 0) return;
                View card = parent.getChildAt(0);
                float cx = mHotGridView.contentCenter();
                float cy = parent.getHeight() * .5f;
                float radius = Math.min(parent.getWidth() * .46f, card.getWidth() * 3.65f);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(Math.max(1f, parent.getResources().getDisplayMetrics().density));
                paint.setAntiAlias(true); paint.setColor(0x5583CCFF);
                canvas.save(); canvas.rotate(-5f, cx, cy);
                canvas.drawOval(cx-radius, cy-card.getHeight()*.22f, cx+radius, cy+card.getHeight()*.22f, paint);
                canvas.restore(); paint.setStyle(android.graphics.Paint.Style.FILL);
            }
            @Override public void onDrawOver(android.graphics.Canvas canvas, RecyclerView parent, RecyclerView.State state) {
                if (mCardMotion != HomeCardMotion.FILM || parent.getChildCount() == 0) return;
                View first = parent.getChildAt(0);
                float unit = Math.max(1f, first.getWidth() / 24f);
                float top = Math.max(0f, (parent.getHeight() - first.getHeight()) * .5f - unit);
                float bottom = Math.min(parent.getHeight()-unit, (parent.getHeight()+first.getHeight()) * .5f);
                paint.setColor(0x99D8D4CC);
                for (float x = first.getLeft() % (unit*3) - unit*3; x < parent.getWidth(); x += unit*3) {
                    canvas.drawRect(x,top,x+unit,top+unit,paint);
                    canvas.drawRect(x,bottom,x+unit,bottom+unit,paint);
                }
            }
        });
        PosterPreloader.setup(mContext, mHotGridView, new PosterPreloader.PosterProvider() {
            @Override
            public int getCount() {
                return mAdapter != null ? mAdapter.getItemCount() : 0;
            }

            @Override
            public String getPosterUrl(int position) {
                if (mAdapter != null) {
                    Movie.Video item = mAdapter.getItem(position);
                    return item != null ? item.pic : null;
                }
                return null;
            }
        }, 6);
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
        if (positionChanged) transitionCard(position);
        mFocusedCardPosition = position;
        updateCardDepth();
        // 选中的卡始终停在屏幕正中，切换一次就居中一次
        if (positionChanged && !mHotGridView.isUserTouching()) {
            centerCardAt(position, card);
        }
        List<Movie.Video> data = mAdapter.getData();
        int realPosition = mAdapter.realPosition(position);
        if (!data.isEmpty() && realPosition >= 0 && realPosition < data.size()) {
            // 背景海报与节目信息跟随当前选中的卡
            bindHero(data.get(realPosition));
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
        int centerX = mHotGridView.contentCenter();
        View nearest = null;
        int nearestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < mHotGridView.getChildCount(); i++) {
            View child = mHotGridView.getChildAt(i);
            if (child == null) {
                continue;
            }
            int distance = Math.abs(mHotGridView.cardCenter(child) - centerX);
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
        if (position < 0 || position >= mAdapter.getItemCount() || position == mFocusedCardPosition) {
            return;
        }
        transitionCard(position);
        mFocusedCardPosition = position;
        mHotGridView.setSelectedPosition(position);
        int realPosition = mAdapter.realPosition(position);
        if (realPosition >= 0 && realPosition < data.size()) {
            bindHero(data.get(realPosition));
        }
    }

    private final PosterStage posterStage = new PosterStage();

    private void transitionCard(int next) {
        if (mCardMotion.usesPosterStage()) {
            if (!mLowPowerHome && mHotGridView.getLayoutManager() != null) {
                posterStage.start(mHotGridView, mCardMotion, mFocusedCardPosition, next, this::updateCardDepth);
            }
            return;
        }
        if (mCardMotion != HomeCardMotion.SLIT || mLowPowerHome) return;
        if (mHotGridView.getLayoutManager() == null) return;
        View old = mHotGridView.getLayoutManager().findViewByPosition(mFocusedCardPosition);
        View target = mHotGridView.getLayoutManager().findViewByPosition(next);
        if (old != null && target != null) HomeCardMotion.slice(old, target);
    }

    private void updateCardDepth() {
        if (mHotGridView == null || mAdapter == null) {
            return;
        }
        float centerX = mHotGridView.contentCenter();
        for (int i = 0; i < mHotGridView.getChildCount(); i++) {
            View child = mHotGridView.getChildAt(i);
            int position = child == null ? -1 : mHotGridView.getChildAdapterPosition(child);
            if (position < 0 || position >= mAdapter.getItemCount()) {
                continue;
            }
            float offset = (mHotGridView.cardCenter(child) - centerX)
                    / Math.max(1f, (mHotGridView.verticalCards() ? child.getHeight() : child.getWidth()) * 1.45f);
            float normalized = Math.max(-1f, Math.min(1f, offset));
            RecyclerView.LayoutParams lp = (RecyclerView.LayoutParams)child.getLayoutParams();
            boolean vertical = mHotGridView.verticalCards();
            RecyclerView.LayoutManager lm = mHotGridView.getLayoutManager();
            int stride = vertical ? lm.getDecoratedMeasuredHeight(child) + lp.topMargin + lp.bottomMargin
                    : lm.getDecoratedMeasuredWidth(child) + lp.leftMargin + lp.rightMargin;
            float displacement = mHotGridView.cardCenter(child) - centerX;
            mCardMotion.apply(child, offset, normalized, position == mFocusedCardPosition,
                    displacement / Math.max(1, stride), displacement);
            if (mCardMotion.usesPosterStage()) posterStage.apply(child, position);

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
        if (mHeroVod != null && mHeroPic == null) bindHero(mHeroVod);
        if (mFeedCancelled && mAdapter != null && (mHistoryLoadPending || mAdapter.getData().isEmpty())) {
            getVideos();
        }
        scheduleAutoScroll();
    }

    @Override
    protected void onFragmentPause() {
        posterStage.stop();
        mViewActive = false;
        ++mHeroSeq;
        if (!mHeroReady) mHeroPic = null;
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
        posterStage.stop();
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
        if (rootView != null && rootView.getViewTreeObserver().isAlive()) {
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
        if (mHotGridView != null && mHotGridView.isUserTouching()) return;
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
        posterStage.stop();
        mCarouselInitializing = true;
        stopAutoScroll();
        mFocusedCardPosition = mAdapter == null ? 0 : mAdapter.loopStartPosition();
        if (mHotGridView != null) {
            mHotGridView.setSelectedPosition(mFocusedCardPosition);
            mHotGridView.centerPosition(mFocusedCardPosition);
            mHotGridView.post(() -> {
                if (mHotGridView == null) {
                    return;
                }
                // setSelection posts a focus-seeking SmoothScroller; it races the initial layout.
                // Record selection only, then center once using our own layout positioning.
                mHotGridView.setSelectedPosition(mFocusedCardPosition);
                mHotGridView.centerPosition(mFocusedCardPosition);
                mHotGridView.post(() -> {
                    if (mHotGridView == null) {
                        return;
                    }
                    mCarouselInitializing = false;
                    View selected = mHotGridView.getLayoutManager() == null
                            ? null
                            : mHotGridView.getLayoutManager().findViewByPosition(mFocusedCardPosition);
                    if (selected != null && !mHotGridView.isInTouchMode() && mHotGridView.hasFocus()) {
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

    /** 设置真实卡片列表；卡片展示位置使用循环映射。 */
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
        mAdapter.setLooping(true);
        mAdapter.setNewData(source);
        mHotGridView.setLoopingBounds(mAdapter.loopStartPosition(), source.size());
    }

    private boolean isSidebarFocused() {
        return (tvLive != null && tvLive.hasFocus())
                || (tvSearch != null && tvSearch.hasFocus());
    }

    /**
     * 自动推进到下一张卡片：正在滚动时跳过（等下一次计时），
     * 焦点停在左栏说明用户正在导航，此时不抢焦点也不轮播
     */
    private void autoScrollNext() {
        if (!mViewActive || !mHeroVisible || mUserOperatingCards
                || mHotGridView == null || mAdapter == null || isSidebarFocused()) return;
        if (getActivity() != null && !getActivity().hasWindowFocus()) {
            scheduleAutoScroll();
            return;
        }
        if (mHotGridView.isScrolling()) {
            return;
        }
        List<Movie.Video> data = mAdapter.getData();
        if (data.size() < 2) return;
        int current = mHotGridView.getSelectedPosition();
        if (current < 0 || current >= mAdapter.getItemCount()) {
            current = mAdapter.isLooping() ? mAdapter.loopStartPosition() : 0;
        }
        int next = current + 1 < mAdapter.getItemCount() ? current + 1
                : (mAdapter.isLooping() ? mAdapter.loopStartPosition() : 0);
        View target = mHotGridView.getLayoutManager() != null
                ? mHotGridView.getLayoutManager().findViewByPosition(next) : null;
        // 能把焦点交给下一张卡就交给它，onItemSelected 会统一处理选中态与居中；
        // 拿不到焦点（触摸模式、卡尚未布局）就自己把选中态补上，轮播不会停在原地
        if (target != null && mHotGridView.hasFocus() && target.requestFocus()) {
            return;
        }
        boolean restoreFocus = target == null && mHotGridView.hasFocus() && !mHotGridView.isInTouchMode();
        applyCardSelected(target, next);
        if (restoreFocus) {
            HomeCardRecyclerView list = mHotGridView;
            list.postOnAnimation(() -> {
                if (!mViewActive || list != mHotGridView || !list.hasFocus()
                        || mFocusedCardPosition != next || list.getLayoutManager() == null) return;
                View selected = list.getLayoutManager().findViewByPosition(next);
                if (selected != null) selected.requestFocus();
            });
        }
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
        int position = holder.getLayoutPosition();
        Movie.Video item = mAdapter.getItem(position);
        if (item == null) return;
        if (!mHotGridView.isScrolling()) bindHero(item);
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
                List<Movie.Video> cached = loadDoubanCache();
                boolean displayed = cached != null && !cached.isEmpty();
                if (displayed) showDoubanVideos(requestId, year, cached);
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
                    if (list0 != null && !list0.isEmpty() && !displayed) {
                        showDoubanVideos(requestId, year, new ArrayList<>(list0));
                        displayed = true;
                    }
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
                if (list0 != null && !list0.isEmpty()) {
                    saveDoubanCache(list0);
                }
                if (!displayed) {
                    mActivity.runOnUiThread(() -> {
                        if (isPageActive() && requestId == mFeedRequestId) loadHotRankFeed(requestId);
                    });
                }
            }
        });
    }

    private void showDoubanVideos(int requestId, int year, List<Movie.Video> videos) {
        mActivity.runOnUiThread(() -> {
            if (!isPageActive() || requestId != mFeedRequestId) return;
            showSuccess();
            setHeader("豆瓣推荐", "豆瓣 · " + (year - 1) + "-" + year + " 评分排序");
            setHomeCards(videos);
            resetCarouselState();
            bindHero(videos.get(0));
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
     * 拿不到才回落到「数据源推荐 → 聚合源 → 空态」这条链
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
                        String json = ApiConfig.getWithHeaders(HOT_RANK_URL + type, null, 10 * 1000);
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
     * 首页推荐兜底链：当前数据源推荐 → 配置里其它聚合源 → 空态
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
            L.w("homeFeed all sources empty");
            mActivity.runOnUiThread(() -> {
                if (!isPageActive() || requestId != mFeedRequestId) return;
                showSuccess();
                setHeroEmpty("暂无推荐", "暂时无法获取内容，请稍后重试或切换首页推荐");

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
        dismissHomeLaunchCover();
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
     * 海报就绪时同步更新文案，快速滚动只处理最后停留的卡片
     */
    private void bindHero(Movie.Video vod) {
        if (vod == null || mHeroBackdrop == null || mHeroBackdropAlt == null || vod.isMore) {
            return;
        }
        boolean samePic = !NullUtil.isNull(vod.pic) && vod.pic.equals(mHeroPic);
        mHeroVod = vod;
        // 焦点回调和列表回调可能传入不同对象；海报地址没变时只更新文案，不重复解码和淡入。
        if (samePic) {
            if (mHeroReady) applyHeroInfo(vod);
            return;
        }
        ++mHeroSeq;
        mHeroPic = vod.pic;
        mHeroHandler.removeCallbacks(mHeroImageRun);
        if (NullUtil.isNull(vod.pic)) {
            cancelHeroAnim();
            ImageView fallback = mHeroFront == mHeroBackdrop ? mHeroBackdropAlt : mHeroBackdrop;
            Glide.with(mContext).clear(fallback);
            fallback.setImageResource(R.drawable.shape_home_hero_placeholder);
            fallback.setAlpha(1f);
            ImageView previous = mHeroFront;
            mHeroFront = fallback;
            clearOutgoingHero(previous);
            applyHeroInfo(vod);
            mHeroReady = true;
            dismissHomeLaunchCover();
            scheduleAutoScroll();
            return;
        }
        mHeroReady = false;
        mHeroHandler.postDelayed(mHeroImageRun, mLowPowerHome ? 120L : HERO_IMAGE_SETTLE_DELAY);
    }

    /** 更新 hero 区的文字信息（片名、备注、元信息、简介） */
    private void applyHeroInfo(Movie.Video vod) {
        String title = NullUtil.isNull(vod.name) ? "" : vod.name;
        if (mCardMotion.usesPosterStage() && !mLowPowerHome && !android.text.TextUtils.equals(title, mHeroTitle.getText())
                && (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled())) {
            mHeroTitle.animate().cancel();
            mHeroTitle.setTranslationY(mHeroTitle.getTextSize() * .12f);
            mHeroTitle.animate().translationY(0f).setDuration(220L).start();
        }
        mHeroTitle.setText(title);
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

    /** 解码完成后直接切换背景层，避免快速切卡时两张海报长时间叠影。 */
    private void loadHeroImage(Movie.Video vod) {
        if (!mViewActive || !mHeroVisible || vod == null || NullUtil.isNull(vod.pic)
                || mHeroBackdrop == null || mHeroBackdropAlt == null) {
            return;
        }
        final int seq = ++mHeroSeq;
        final String pic = vod.pic;
        final ImageView outgoing = mHeroFront;
        final ImageView incoming = outgoing == mHeroBackdrop ? mHeroBackdropAlt : mHeroBackdrop;
        // 停止上一轮推镜动画，并保持当前显示层全亮。
        cancelHeroAnim();
        incoming.setAlpha(0f);
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
        RequestBuilder<Drawable> request = Glide.with(mContext)
                .load(GlideImg.heroModel(pic))
                .error(R.drawable.shape_home_hero_placeholder)
                .dontAnimate();
        boolean television = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_TYPE_MASK)
                == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION;
        if (mLowPowerHome) {
            request = request.override(960, 540).centerCrop();
        } else if (television) {
            // Avoid full-screen CPU sharpening and oversized bitmaps on TV hardware.
            float scale = Math.min(1f, Math.min(1920f / Math.max(1, width), 1080f / Math.max(1, height)));
            request = request.override(width > 0 ? Math.max(1, Math.round(width * scale)) : 1920,
                    height > 0 ? Math.max(1, Math.round(height * scale)) : 1080).centerCrop();
        } else {
            request = request.transform(new SharpenTransformation(mContext));
        }
        if (!mLowPowerHome && !television && width > 0 && height > 0) {
            // 固定目标尺寸，避免按海报原始尺寸解出过大的位图
            request = request.override(width, height);
        }
        request.listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                        if (seq == mHeroSeq) {
                            incoming.setImageResource(R.drawable.shape_home_hero_placeholder);
                            incoming.post(() -> showHeroImage(seq, incoming, outgoing));
                        }
                        return true;
                    }

                    @Override
                    public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target,
                                                   DataSource dataSource, boolean isFirstResource) {
                        if (seq != mHeroSeq) {
                            // 已经换了别的海报，这张旧图别往背景上贴
                            return true;
                        }
                        if (!mHeroShown) {
                            incoming.post(() -> showHeroImage(seq, incoming, outgoing));
                        }
                        return false;
                    }
                })
                .into(incoming);
    }

    private void showHeroImage(int seq, ImageView incoming, ImageView outgoing) {
        if (seq != mHeroSeq || mHeroShown) {
            return;
        }
        mHeroShown = true;
        mHeroReady = true;
        dismissHomeLaunchCover();
        mHeroFront = incoming;
        applyHeroInfo(mHeroVod);
        if (mLowPowerHome) {
            incoming.setAlpha(1f);
            incoming.setScaleX(1f);
            incoming.setScaleY(1f);
            clearOutgoingHero(outgoing);
        } else {
            float from = incoming.getScaleX() > 0f ? incoming.getScaleX() : HERO_ZOOM_MIN;
            float to = from >= (HERO_ZOOM_MIN + HERO_ZOOM_MAX) / 2f ? HERO_ZOOM_MIN : HERO_ZOOM_MAX;
            if (mCardMotion == HomeCardMotion.PORTAL) {
                from = 1.14f;
                to = 1f;
            }
            long zoomDuration = mCardMotion == HomeCardMotion.PORTAL ? 480L : HERO_ZOOM_DURATION;
            startHeroAnim(ObjectAnimator.ofFloat(incoming, "scaleX", from, to), zoomDuration);
            startHeroAnim(ObjectAnimator.ofFloat(incoming, "scaleY", from, to), zoomDuration);
            // 两层叠放时只淡化上层，让下层保持不透明，避免过渡中间发暗。
            boolean fadeOutgoing = outgoing != null && outgoing.getDrawable() != null
                    && incoming == mHeroBackdrop;
            if (fadeOutgoing || outgoing == null) incoming.setAlpha(1f);
            else startHeroAnim(ObjectAnimator.ofFloat(incoming, "alpha", incoming.getAlpha(), 1f), HERO_FADE_DURATION);
            if (outgoing != null && outgoing != incoming && outgoing.getDrawable() != null) {
                ObjectAnimator fadeOut = ObjectAnimator.ofFloat(outgoing, "alpha", outgoing.getAlpha(), fadeOutgoing ? 0f : 1f);
                fadeOut.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (seq == mHeroSeq && mHeroFront == incoming) {
                            clearOutgoingHero(outgoing);
                        }
                    }
                });
                startHeroAnim(fadeOut, HERO_FADE_DURATION);
            }
        }
        if (!mAutoScrollingCards) {
            scheduleAutoScroll();
        }
    }

    private void dismissHomeLaunchCover() {
        if (isAdded() && requireActivity() instanceof HomeActivity) {
            ((HomeActivity) requireActivity()).dismissHomeLaunchCover();
        }
    }

    private void clearOutgoingHero(ImageView outgoing) {
        if (outgoing == null || outgoing == mHeroFront) return;
        if (mContext != null) Glide.with(mContext).clear(outgoing);
        outgoing.setImageDrawable(null);
        outgoing.setAlpha(0f);
    }

    private void startHeroAnim(ObjectAnimator animator, long duration) {
        mHeroAnims.add(animator);
        animator.setDuration(duration).start();
    }

    /** 换图前停止上一轮推镜动画，避免动画抢同一个属性。 */
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
        }
    }
}
