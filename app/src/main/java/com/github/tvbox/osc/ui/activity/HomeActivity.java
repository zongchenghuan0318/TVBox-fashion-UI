package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.DisplayCutout;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsSortXml;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.HomePageAdapter;
import com.github.tvbox.osc.ui.adapter.SortAdapter;
import com.github.tvbox.osc.ui.adapter.SortTopAdapter;
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.fragment.FirstFragment;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.MarqueeTextView;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.GsonUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.github.tvbox.osc.util.MD5;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.SPDataUtil;
import com.lcstudio.commonsurport.util.StringUtil;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.studio.osc.R;
import com.uisupport.UiConstans;
import com.github.tvbox.osc.util.UpdateManager;
import com.uisupport.update.HttpUtil;
import com.uisupport.update.ReportConfig;
import com.uisupport.update.RspReportConfig;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import androidx.lifecycle.ViewModelProvider;
import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * tvbox交流QQ群：879811030
 */
public class HomeActivity extends BaseActivity implements View.OnClickListener {
    private TextView tvDate;
    private SourceViewModel sourceViewModel;
    private TvRecyclerView mMovieSortRV;
    private SortAdapter sortAdapter;
    private NoScrollViewPager mContentViewPager;
    private HomePageAdapter mPageAdapter;
    private List<BaseLazyFragment> fragments = new ArrayList<>();
    private boolean bDownOrUp = false;
    private int mCurrentSelected = 0;
    private int pendingCategoryPosition = -1;

    // 首页四种布局形态支持（0: 经典侧栏, 1: Apple TV 顶栏, 2: Netflix 抽屉, 3: 极简沉浸）
    private int mCurrentLayoutStyle = -1;
    private View sidebarSpacer;
    private View leftNavPanel;
    private View sidebarEdgeShadow;
    private View drawerScrim;
    private View drawerTabHandle;
    private LinearLayout topNavPanel;
    private TextView tvTopBrand;
    private TextView tvTopDate;
    private TvRecyclerView sortTopRV;
    private SortTopAdapter sortTopAdapter;
    private View mTopSortFocusView = null;
    private int mTopSortFocused = 0;
    private boolean isDrawerOpen = false;

    // 触控交互优化支持（手机/平板横屏优化）
    private float mTouchDownX = 0f;
    private float mTouchDownY = 0f;
    private long mTouchDownTime = 0L;
    private boolean mIsEdgeSwipe = false;
    private boolean mIsTopEdgeSwipe = false;

    private final Runnable immersiveTopBarFadeRunnable = new Runnable() {
        @Override
        public void run() {
            if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE && topNavPanel != null) {
                if (topNavPanel.hasFocus()) {
                    scheduleImmersiveTopBarFadeOut(3500L);
                    return;
                }
                int height = topNavPanel.getHeight() > 0 ? topNavPanel.getHeight() : AutoSizeUtils.dp2px(HomeActivity.this, 60);
                topNavPanel.animate()
                        .alpha(0f)
                        .translationY(-height)
                        .setDuration(260)
                        .setInterpolator(new AccelerateInterpolator())
                        .withEndAction(() -> {
                            if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE && topNavPanel != null && !topNavPanel.hasFocus()) {
                                topNavPanel.setVisibility(View.GONE);
                            }
                        })
                        .start();
            }
        }
    };

    private final Runnable switchCategoryRunnable = () -> {
        if (pendingCategoryPosition == this.mSortFocused && this.mSortFocusView != null && this.mSortFocusView.hasFocus()) {
            selectCategory(pendingCategoryPosition);
        } else if (pendingCategoryPosition == this.mTopSortFocused && this.mTopSortFocusView != null && this.mTopSortFocusView.hasFocus()) {
            selectCategory(pendingCategoryPosition);
        }
    };
    private int mSortFocused = 0;
    public View mSortFocusView = null;
    private Handler mHandler;
    private long mExitTime = 0;
    private View mLoadingView;
    private long mShowLoadingTime = 0;
    private String mSortSignature = "";
    private int mLastSyncedPage = -1;
    private ExecutorService mConfigExecutor;
    private String categorySourceIdentity = "";
    private int categoryMetadataAttempts;
    private boolean categoryMetadataPending;
    private final Runnable categoryRetry = this::requestCategoryMetadata;
    private final Runnable categoryMetadataTimeout = this::onCategoryMetadataTimeout;
    private boolean hasScheduledUpdateCheck = false;
    private boolean updateSourceReady = false;
    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            if (mHandler == null) {
                return;
            }
            long now = System.currentTimeMillis();
            String timeStr = StringUtil.getFormatTime_HH_MM(now);
            if (tvDate != null) {
                tvDate.setText(timeStr);
            }
            if (tvTopDate != null) {
                tvTopDate.setText(timeStr);
            }
            // 对齐到下一分钟边界，避免每秒触发 TextView.setText 引发的 UI 主线程 layout/draw 重排重绘
            long delay = 60000L - (now % 60000L) + 50L;
            mHandler.postDelayed(this, delay);
        }
    };
    /** 首页片段只创建一次：ViewPager 按 tag 复用它，页面列表里必须放同一个实例 */
    private FirstFragment mFirstFragment;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_home;
    }

    private void initView() {
        this.tvDate = findViewById(R.id.tvDate);
        mLoadingView = findViewById(R.id.loadingView);
        this.sidebarSpacer = findViewById(R.id.sidebarSpacer);
        this.leftNavPanel = findViewById(R.id.leftNavPanel);
        this.sidebarEdgeShadow = findViewById(R.id.sidebarEdgeShadow);
        this.drawerScrim = findViewById(R.id.drawerScrim);
        if (this.drawerScrim != null) {
            this.drawerScrim.setOnClickListener(v -> closeDrawer());
        }
        this.drawerTabHandle = findViewById(R.id.drawerTabHandle);
        if (this.drawerTabHandle != null) {
            this.drawerTabHandle.setOnClickListener(v -> openDrawer());
        }
        this.topNavPanel = findViewById(R.id.topNavPanel);
        this.tvTopBrand = findViewById(R.id.tvTopBrand);
        this.tvTopDate = findViewById(R.id.tvTopDate);
        this.sortTopRV = findViewById(R.id.sortTopRV);

        if (this.topNavPanel != null) {
            this.topNavPanel.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
                if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE) {
                    boolean hadFocus = isViewOrDescendant(oldFocus, topNavPanel);
                    boolean hasFocus = isViewOrDescendant(newFocus, topNavPanel);
                    if (hasFocus) {
                        mHandler.removeCallbacks(immersiveTopBarFadeRunnable);
                    } else if (hadFocus) {
                        scheduleImmersiveTopBarFadeOut(1000L);
                    }
                }
            });
        }

        // 侧栏操作按钮
        findViewById(R.id.layout_search2).setOnClickListener(this);
        findViewById(R.id.layout_history).setOnClickListener(this);
        findViewById(R.id.layout_live).setOnClickListener(this);
        findViewById(R.id.layout_collect).setOnClickListener(this);
        findViewById(R.id.layout_setting).setOnClickListener(this);

        // 顶部悬浮栏操作按钮
        View topSearch = findViewById(R.id.top_layout_search);
        if (topSearch != null) topSearch.setOnClickListener(this);
        View topHistory = findViewById(R.id.top_layout_history);
        if (topHistory != null) topHistory.setOnClickListener(this);
        View topLive = findViewById(R.id.top_layout_live);
        if (topLive != null) topLive.setOnClickListener(this);
        View topCollect = findViewById(R.id.top_layout_collect);
        if (topCollect != null) topCollect.setOnClickListener(this);
        View topSetting = findViewById(R.id.top_layout_setting);
        if (topSetting != null) topSetting.setOnClickListener(this);

        this.mMovieSortRV = findViewById(R.id.sortRV);
        this.mContentViewPager = findViewById(R.id.viewPager);

        // 禁用整个主容器和左侧导航栏的滚动
        ViewGroup homeContent = findViewById(R.id.homeContent);

        if (homeContent != null) {
            homeContent.setOverScrollMode(View.OVER_SCROLL_NEVER);
            // 内容页不能绘制到左侧导航栏下方
            homeContent.setClipChildren(true);
            homeContent.setClipToPadding(true);
        }
        if (this.mContentViewPager != null) {
            this.mContentViewPager.setClipChildren(true);
            this.mContentViewPager.setClipToPadding(true);
            if (this.mContentViewPager.getParent() instanceof ViewGroup) {
                ViewGroup host = (ViewGroup) this.mContentViewPager.getParent();
                host.setClipChildren(true);
                host.setClipToPadding(true);
            }
        }

        if (leftNavPanel != null) {
            leftNavPanel.setOverScrollMode(View.OVER_SCROLL_NEVER);
            // 允许触控事件自然传递给子视图（RecyclerView与功能按钮）
            leftNavPanel.setOnTouchListener((v, event) -> false);
        }

        initCutoutSafeInsets();
    }

    @Override
    protected void init() {
        // 初始化Web服务器
        ControlManager.get().startServer(getApplicationContext());
        mHandler = new Handler(Looper.getMainLooper());
        mConfigExecutor = Executors.newSingleThreadExecutor();
        EventBus.getDefault().register(this);
        initView();
        mShowLoadingTime = System.currentTimeMillis();
        initSortView();
        initTopSortView();
        applyLayoutStyle(Hawk.get(HawkConfig.HOME_LAYOUT_STYLE, HawkConfig.HOME_LAYOUT_DEFAULT));
        initFirstPager();
        observePages();
        initSiteFile();
        showTime();
        getConfig(this);
        // 兜底：加载异常时也不能一直停在打开画面
        mHandler.postDelayed(() -> {
            hideLoading();
            if (categorySourceIdentity.isEmpty()) requestCategoryMetadata();
        }, 15000);
    }

    public void getConfig(Context context) {
        if (mConfigExecutor == null || mConfigExecutor.isShutdown()) {
            return;
        }
        Context applicationContext = context.getApplicationContext();
        mConfigExecutor.execute(() -> {
            RspReportConfig bean = HttpUtil.getConfig(UiConstans.GET_CONFIG_URL, applicationContext);
            if (bean != null && bean.resultData != null) {
                SPDataUtil spDataUtil = new SPDataUtil(applicationContext);
                spDataUtil.saveObjectValue(UiConstans.PRE_KEY_CONFIG, bean.resultData);
            }
        });
    }

    private void initSortView(){
        this.sortAdapter = new SortAdapter();
        if (this.mMovieSortRV == null) {
            L.e("initSortView() skipped because sortRV is missing from activity_home");
            return;
        }
        // 分类列表纵向排列：首页在最上，热播电影等依次堆在下面
        // 行距压到刚好让 6 个分类完整落在列表可视区内（无滚动范围 → 触摸滑动时整块固定不动）
        this.mMovieSortRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, 1, false));
        this.mMovieSortRV.setSpacingWithMargins(0, AutoSizeUtils.dp2px(this.mContext, 2.0f));
        this.mMovieSortRV.setAdapter(this.sortAdapter);
        
        // 允许嵌套滚动，便于手机触屏用户顺畅上下滑动查看更多分类
        this.mMovieSortRV.setNestedScrollingEnabled(true);

        this.mMovieSortRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !HomeActivity.this.bDownOrUp) {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    textView.getPaint().setFakeBoldText(view.isSelected());
                    textView.setTextColor(HomeActivity.this.getResources().getColorStateList(R.color.selector_home_rail_text));
                    textView.invalidate();
                }
            }

            @Override
            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    changeTap(view, position);
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                selectCategory(position);
                if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_DRAWER && isDrawerOpen) {
                    closeDrawer();
                } else {
                    enterSelectedCategoryContent();
                }
            }
        });
    }

    private void initTopSortView() {
        this.sortTopAdapter = new SortTopAdapter();
        if (this.sortTopRV == null) {
            return;
        }
        this.sortTopRV.setLayoutManager(new V7LinearLayoutManager(this.mContext, 0, false));
        this.sortTopRV.setSpacingWithMargins(AutoSizeUtils.dp2px(this.mContext, 4.0f), 0);
        this.sortTopRV.setAdapter(this.sortTopAdapter);
        // 允许嵌套滚动，便于手机触屏用户横向滑动切换更多分类
        this.sortTopRV.setNestedScrollingEnabled(true);
        this.sortTopRV.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null && !HomeActivity.this.bDownOrUp) {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    if (textView != null) {
                        textView.getPaint().setFakeBoldText(view.isSelected());
                        textView.setTextColor(HomeActivity.this.getResources().getColorStateList(R.color.selector_home_rail_text));
                        textView.invalidate();
                    }
                }
            }

            @Override
            public void onItemSelected(TvRecyclerView tvRecyclerView, View view, int position) {
                if (view != null) {
                    view.animate().scaleX(1.06f).scaleY(1.06f).setInterpolator(new DecelerateInterpolator()).setDuration(200).start();
                    TextView textView = view.findViewById(R.id.tvTitle);
                    if (textView != null) {
                        textView.getPaint().setFakeBoldText(true);
                        textView.setTextColor(getResources().getColor(R.color.color_rail_text_active));
                        textView.invalidate();
                    }
                    mTopSortFocusView = view;
                    mTopSortFocused = position;
                    mHandler.removeCallbacks(switchCategoryRunnable);
                    pendingCategoryPosition = position;
                    if (position != mCurrentSelected) {
                        mHandler.postDelayed(switchCategoryRunnable, 200L);
                    }
                }
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                selectCategory(position);
                enterSelectedCategoryContent();
                if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE) {
                    scheduleImmersiveTopBarFadeOut(800L);
                }
            }
        });
    }

    public void applyLayoutStyle(int mode) {
        this.mCurrentLayoutStyle = mode;
        ViewGroup homeContent = findViewById(R.id.homeContent);

        switch (mode) {
            case HawkConfig.HOME_LAYOUT_SIDEBAR:
                // 1. 经典侧边栏：侧边栏固定常驻，内容区避开 165mm
                if (sidebarSpacer != null) sidebarSpacer.setVisibility(View.VISIBLE);
                if (leftNavPanel != null) {
                    leftNavPanel.setBackgroundResource(R.drawable.shape_home_rail_panel);
                    leftNavPanel.setVisibility(View.VISIBLE);
                    leftNavPanel.setTranslationX(0f);
                }
                if (sidebarEdgeShadow != null) {
                    sidebarEdgeShadow.setVisibility(View.VISIBLE);
                    sidebarEdgeShadow.setTranslationX(0f);
                }
                if (drawerScrim != null) drawerScrim.setVisibility(View.GONE);
                if (drawerTabHandle != null) drawerTabHandle.setVisibility(View.GONE);
                if (topNavPanel != null) topNavPanel.setVisibility(View.GONE);
                if (homeContent != null) homeContent.setPadding(0, 0, 0, 0);
                isDrawerOpen = false;
                break;

            case HawkConfig.HOME_LAYOUT_APPLE_TV:
                // 2. Apple TV 顶栏：侧栏隐藏，内容区全宽铺满，顶部横向悬浮胶囊导航栏常驻
                if (sidebarSpacer != null) sidebarSpacer.setVisibility(View.GONE);
                if (leftNavPanel != null) leftNavPanel.setVisibility(View.GONE);
                if (sidebarEdgeShadow != null) sidebarEdgeShadow.setVisibility(View.GONE);
                if (drawerScrim != null) drawerScrim.setVisibility(View.GONE);
                if (drawerTabHandle != null) drawerTabHandle.setVisibility(View.GONE);
                if (topNavPanel != null) {
                    topNavPanel.setVisibility(View.VISIBLE);
                    topNavPanel.setAlpha(1.0f);
                    topNavPanel.setTranslationY(0f);
                }
                if (homeContent != null) {
                    // 为顶部悬浮胶囊留出 54mm 呼吸空间，避免遮盖内容区顶部
                    homeContent.setPadding(0, AutoSizeUtils.dp2px(this, 54), 0, 0);
                }
                isDrawerOpen = false;
                break;

            case HawkConfig.HOME_LAYOUT_DRAWER:
                // 3. Netflix 动态隐形抽屉：内容区全宽铺满，侧栏平移移出屏幕左侧，按左键滑出
                if (sidebarSpacer != null) sidebarSpacer.setVisibility(View.GONE);
                if (sidebarEdgeShadow != null) sidebarEdgeShadow.setVisibility(View.GONE);
                if (leftNavPanel != null) {
                    leftNavPanel.setBackgroundResource(R.drawable.shape_home_drawer_panel);
                    int panelWidth = leftNavPanel.getWidth() > 0 ? leftNavPanel.getWidth() : getResources().getDimensionPixelSize(R.dimen.vs_165);
                    leftNavPanel.setTranslationX(-panelWidth);
                    leftNavPanel.setVisibility(View.GONE);
                }
                if (drawerScrim != null) {
                    drawerScrim.setVisibility(View.GONE);
                    drawerScrim.setAlpha(0f);
                }
                if (drawerTabHandle != null) drawerTabHandle.setVisibility(View.VISIBLE);
                if (topNavPanel != null) topNavPanel.setVisibility(View.GONE);
                if (homeContent != null) homeContent.setPadding(0, 0, 0, 0);
                isDrawerOpen = false;
                break;

            case HawkConfig.HOME_LAYOUT_IMMERSIVE:
                // 4. 极简全屏画报沉浸：内容区全宽无边框，顶部栏默认沉浸隐形，向上或按菜单键唤出
                if (sidebarSpacer != null) sidebarSpacer.setVisibility(View.GONE);
                if (leftNavPanel != null) leftNavPanel.setVisibility(View.GONE);
                if (sidebarEdgeShadow != null) sidebarEdgeShadow.setVisibility(View.GONE);
                if (drawerScrim != null) drawerScrim.setVisibility(View.GONE);
                if (drawerTabHandle != null) drawerTabHandle.setVisibility(View.GONE);
                if (homeContent != null) homeContent.setPadding(0, 0, 0, 0);
                if (topNavPanel != null) {
                    topNavPanel.setVisibility(View.VISIBLE);
                    topNavPanel.setAlpha(1.0f);
                    topNavPanel.setTranslationY(0f);
                }
                isDrawerOpen = false;
                scheduleImmersiveTopBarFadeOut(3500L);
                break;
        }
    }

    public void openDrawer() {
        if (isDrawerOpen || leftNavPanel == null) return;
        isDrawerOpen = true;
        if (drawerTabHandle != null) drawerTabHandle.setVisibility(View.GONE);
        leftNavPanel.setBackgroundResource(R.drawable.shape_home_drawer_panel);
        leftNavPanel.setVisibility(View.VISIBLE);
        int panelWidth = leftNavPanel.getWidth() > 0 ? leftNavPanel.getWidth() : getResources().getDimensionPixelSize(R.dimen.vs_165);
        leftNavPanel.setTranslationX(-panelWidth);
        leftNavPanel.animate()
                .translationX(0f)
                .setDuration(240)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        if (sidebarEdgeShadow != null) {
            sidebarEdgeShadow.setVisibility(View.GONE);
        }
        if (drawerScrim != null) {
            drawerScrim.setVisibility(View.VISIBLE);
            drawerScrim.setAlpha(0f);
            drawerScrim.animate().alpha(1.0f).setDuration(240).start();
        }
        if (mMovieSortRV != null) {
            mMovieSortRV.setSelection(mCurrentSelected);
            mMovieSortRV.requestFocus();
        }
    }

    public void closeDrawer() {
        if (!isDrawerOpen || leftNavPanel == null) return;
        isDrawerOpen = false;
        if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_DRAWER && drawerTabHandle != null) {
            drawerTabHandle.setVisibility(View.VISIBLE);
        }
        int panelWidth = leftNavPanel.getWidth() > 0 ? leftNavPanel.getWidth() : getResources().getDimensionPixelSize(R.dimen.vs_165);
        leftNavPanel.animate()
                .translationX(-panelWidth)
                .setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (!isDrawerOpen && mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_DRAWER && leftNavPanel != null) {
                        leftNavPanel.setVisibility(View.GONE);
                    }
                })
                .start();
        if (sidebarEdgeShadow != null) {
            sidebarEdgeShadow.setVisibility(View.GONE);
        }
        if (drawerScrim != null) {
            drawerScrim.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                drawerScrim.setVisibility(View.GONE);
            }).start();
        }
        enterSelectedCategoryContent();
    }

    private void initCutoutSafeInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                WindowManager.LayoutParams lp = getWindow().getAttributes();
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                getWindow().setAttributes(lp);
            } catch (Throwable ignored) {
            }

            View decorView = getWindow().getDecorView();
            decorView.setOnApplyWindowInsetsListener((v, insets) -> {
                try {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        int insetLeft = cutout.getSafeInsetLeft();
                        int insetRight = cutout.getSafeInsetRight();

                        // 1. 侧边栏/抽屉：如果左侧有物理挖孔摄像头，为侧栏及拉手增加左内边距/外边距避让
                        if (insetLeft > 0) {
                            if (leftNavPanel != null) {
                                leftNavPanel.setPadding(insetLeft, leftNavPanel.getPaddingTop(), leftNavPanel.getPaddingRight(), leftNavPanel.getPaddingBottom());
                            }
                            if (drawerTabHandle != null && drawerTabHandle.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                                ViewGroup.MarginLayoutParams lpHandle = (ViewGroup.MarginLayoutParams) drawerTabHandle.getLayoutParams();
                                lpHandle.leftMargin = insetLeft;
                                drawerTabHandle.setLayoutParams(lpHandle);
                            }
                        }

                        // 2. 顶部胶囊导航栏：如果左右有挖孔，为顶部胶囊导航栏增加安全避让区
                        if (topNavPanel != null) {
                            int basePaddingLeft = AutoSizeUtils.dp2px(HomeActivity.this, 40);
                            int basePaddingRight = AutoSizeUtils.dp2px(HomeActivity.this, 40);
                            topNavPanel.setPadding(
                                    basePaddingLeft + insetLeft,
                                    topNavPanel.getPaddingTop(),
                                    basePaddingRight + insetRight,
                                    topNavPanel.getPaddingBottom()
                            );
                        }
                    }
                } catch (Throwable ignored) {
                }
                return v.onApplyWindowInsets(insets);
            });
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        float x = ev.getRawX();
        float y = ev.getRawY();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mTouchDownX = x;
                mTouchDownY = y;
                mTouchDownTime = System.currentTimeMillis();
                // 屏幕左侧 80dp 作为边缘滑动手势识别区
                mIsEdgeSwipe = (x <= AutoSizeUtils.dp2px(this, 80));
                // 屏幕顶部 70dp 作为顶部下拉手势识别区
                mIsTopEdgeSwipe = (y <= AutoSizeUtils.dp2px(this, 70));
                break;

            case MotionEvent.ACTION_MOVE:
                float dx = x - mTouchDownX;
                float dy = y - mTouchDownY;

                // 1. Netflix 动态隐形抽屉模式：
                if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_DRAWER) {
                    // a. 抽屉关闭时：左边缘向右划动拉出抽屉
                    if (!isDrawerOpen && mIsEdgeSwipe && dx > AutoSizeUtils.dp2px(this, 45) && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        openDrawer();
                        mIsEdgeSwipe = false;
                        return true;
                    }
                    // b. 抽屉打开时：向左快速划动收起抽屉
                    if (isDrawerOpen && dx < -AutoSizeUtils.dp2px(this, 45) && Math.abs(dx) > Math.abs(dy) * 1.2f) {
                        closeDrawer();
                        return true;
                    }
                }

                // 2. 极简全屏画报沉浸模式：顶部向下滑动拉出顶栏
                if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE) {
                    if (mIsTopEdgeSwipe && dy > AutoSizeUtils.dp2px(this, 35) && Math.abs(dy) > Math.abs(dx) * 1.2f) {
                        revealImmersiveTopBar();
                        mIsTopEdgeSwipe = false;
                        return true;
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                float totalDx = x - mTouchDownX;
                float totalDy = y - mTouchDownY;
                long duration = System.currentTimeMillis() - mTouchDownTime;

                // 3. 极简画报沉浸模式：轻点屏幕上半区（海报背景空白区），切换顶部导航栏显隐
                if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE) {
                    if (Math.abs(totalDx) < AutoSizeUtils.dp2px(this, 15) && Math.abs(totalDy) < AutoSizeUtils.dp2px(this, 15) && duration < 350) {
                        int screenHeight = getResources().getDisplayMetrics().heightPixels;
                        if (y > AutoSizeUtils.dp2px(this, 54) && y < screenHeight * 0.65f) {
                            if (topNavPanel != null) {
                                if (topNavPanel.getVisibility() != View.VISIBLE || topNavPanel.getAlpha() < 0.5f) {
                                    revealImmersiveTopBar();
                                } else {
                                    scheduleImmersiveTopBarFadeOut(0L);
                                }
                            }
                        }
                    }
                }

                // 4. 触屏横向划屏切换分类：在屏幕上半区海报大背景区域左右划动切换上一个/下一个分类
                // （避免与底部的卡片横向滑动产生冲突）
                int screenHeight = getResources().getDisplayMetrics().heightPixels;
                if (mTouchDownY < screenHeight * 0.65f && duration < 500) {
                    if (Math.abs(totalDx) > AutoSizeUtils.dp2px(this, 80) && Math.abs(totalDx) > Math.abs(totalDy) * 1.5f) {
                        if (totalDx < 0) {
                            // 向左划 -> 切换到下一个分类
                            if (mCurrentSelected < fragments.size() - 1) {
                                int target = mCurrentSelected + 1;
                                selectCategory(target);
                                if (mMovieSortRV != null) mMovieSortRV.smoothScrollToPosition(target);
                                if (sortTopRV != null) sortTopRV.smoothScrollToPosition(target);
                            }
                        } else {
                            // 向右划 -> 切换到上一个分类（非抽屉边缘滑动时）
                            if (!mIsEdgeSwipe && mCurrentSelected > 0) {
                                int target = mCurrentSelected - 1;
                                selectCategory(target);
                                if (mMovieSortRV != null) mMovieSortRV.smoothScrollToPosition(target);
                                if (sortTopRV != null) sortTopRV.smoothScrollToPosition(target);
                            }
                        }
                    }
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    public void revealImmersiveTopBar() {
        if (topNavPanel == null) return;
        mHandler.removeCallbacks(immersiveTopBarFadeRunnable);
        topNavPanel.setVisibility(View.VISIBLE);
        topNavPanel.animate()
                .alpha(1.0f)
                .translationY(0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        // 唤出后固定时长（3.5秒）自动收起，极简纯净沉浸
        scheduleImmersiveTopBarFadeOut(3500L);
    }

    public void scheduleImmersiveTopBarFadeOut(long delay) {
        if (mHandler == null) return;
        mHandler.removeCallbacks(immersiveTopBarFadeRunnable);
        if (delay <= 0) {
            immersiveTopBarFadeRunnable.run();
        } else {
            mHandler.postDelayed(immersiveTopBarFadeRunnable, delay);
        }
    }

    private void initFirstPager() {
        mFirstFragment = FirstFragment.newInstance(null);
        fragments.add(mFirstFragment);
        mPageAdapter = new HomePageAdapter(getSupportFragmentManager(), fragments);
        mContentViewPager.setPageTransformer(true, new DefaultTransformer());
        mContentViewPager.setAdapter(mPageAdapter);
        // 禁用预加载相邻页面：只保留当前页，避免其他页面的背景透出来
        // 注意：ViewPager 的 offscreenPageLimit 最小值是 1，设为 0 会被强制改回 1
        // 但结合 NoScrollViewPager 禁止用户切换，实际效果是只有当前页活跃
        mContentViewPager.setOffscreenPageLimit(1);
        mContentViewPager.setCurrentItem(0, true);
        List<MovieSort.SortData> sortDataList = DefaultConfig.adjustSort("", new ArrayList<>(), true);
        sortAdapter.setNewData(sortDataList);
        if (sortTopAdapter != null) {
            sortTopAdapter.setNewData(sortDataList);
        }
        freshViewPager(null);
        if (mMovieSortRV != null) {
            mMovieSortRV.setSelection(0);
        }
        if (sortTopRV != null) {
            sortTopRV.setSelection(0);
        }
    }

    private void showTime() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mClockRunnable);
            mHandler.post(mClockRunnable);
        }
    }

    private void changeTap(View view, int position) {
        bDownOrUp = false;
        view.animate().scaleX(1.06f).scaleY(1.06f).setInterpolator(new DecelerateInterpolator()).setDuration(220).start();
        TextView textView = view.findViewById(R.id.tvTitle);
        textView.getPaint().setFakeBoldText(true);
        textView.setTextColor(getResources().getColor(R.color.color_rail_text_active));
        textView.invalidate();
        if (sortAdapter.getItem(position) != null && !NullUtil.isNull(sortAdapter.getItem(position).filters)) {
            view.findViewById(R.id.tvFilter).setVisibility(View.VISIBLE);
        }
        mSortFocusView = view;
        mSortFocused = position;
        mHandler.removeCallbacks(switchCategoryRunnable);
        pendingCategoryPosition = position;
        if (position != mCurrentSelected) mHandler.postDelayed(switchCategoryRunnable, 200L);
    }

    private void selectCategory(int position) {
        mHandler.removeCallbacks(switchCategoryRunnable);
        pendingCategoryPosition = -1;
        if (position < 0 || position >= fragments.size()) return;
        sortAdapter.setSelectedPosition(position);
        if (sortTopAdapter != null) {
            sortTopAdapter.setSelectedPosition(position);
        }
        if (mCurrentSelected != position) {
            mCurrentSelected = position;
            mContentViewPager.setCurrentItem(position, false);
        }
        syncHeroBackdrop();
    }

    private boolean enterSelectedCategoryContent() {
        if (mCurrentSelected < 0 || mCurrentSelected >= fragments.size()) return false;
        BaseLazyFragment page = fragments.get(mCurrentSelected);
        if (page instanceof GridFragment) return ((GridFragment) page).focusCategoryContent();
        return mContentViewPager.requestFocus();
    }

    private boolean isViewOrDescendant(View child, View parent) {
        if (child == null || parent == null) return false;
        View current = child;
        while (current != null) {
            if (current == parent) return true;
            ViewParent p = current.getParent();
            current = (p instanceof View) ? (View) p : null;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            // 1. 经典侧栏模式 / 动态抽屉模式：焦点在左侧栏分类时向右进入内容区
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (mSortFocusView != null && mSortFocusView.hasFocus()) {
                    selectCategory(mSortFocused);
                    if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_DRAWER && isDrawerOpen) {
                        closeDrawer();
                        return true;
                    }
                    if (enterSelectedCategoryContent()) return true;
                } else if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_DRAWER && isDrawerOpen) {
                    View currentFocus = getCurrentFocus();
                    if (currentFocus != null && isViewOrDescendant(currentFocus, leftNavPanel)) {
                        closeDrawer();
                        return true;
                    }
                }
            }

            // 2. 动态抽屉模式：在内容区最左侧卡片按向左键，滑出侧边栏抽屉
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_DRAWER) {
                if (!isDrawerOpen && mContentViewPager != null && mContentViewPager.hasFocus()) {
                    View currentFocus = getCurrentFocus();
                    if (currentFocus != null) {
                        View leftView = FocusFinder.getInstance().findNextFocus((ViewGroup) mContentViewPager, currentFocus, View.FOCUS_LEFT);
                        if (leftView == null) {
                            openDrawer();
                            return true;
                        }
                    }
                }
            }

            // 3. Apple TV 顶栏模式 & 极简沉浸模式：
            if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_APPLE_TV || mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE) {
                // 3a. 在顶部栏中按向下键进入内容区
                if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (topNavPanel != null && topNavPanel.hasFocus()) {
                        enterSelectedCategoryContent();
                        if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE) {
                            scheduleImmersiveTopBarFadeOut(800L);
                        }
                        return true;
                    }
                }

                // 3b. 在内容区按向上键：如果上方没有内容，唤出并聚焦顶部栏
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    if (mContentViewPager != null && mContentViewPager.hasFocus()) {
                        View currentFocus = getCurrentFocus();
                        if (currentFocus != null) {
                            View upView = FocusFinder.getInstance().findNextFocus((ViewGroup) mContentViewPager, currentFocus, View.FOCUS_UP);
                            if (upView == null) {
                                revealImmersiveTopBar();
                                if (sortTopRV != null) {
                                    sortTopRV.setSelection(mCurrentSelected);
                                    sortTopRV.requestFocus();
                                }
                                return true;
                            }
                        }
                    }
                }
            }

            // 4. 极简沉浸模式：按菜单键直接唤出顶部导航栏
            if (keyCode == KeyEvent.KEYCODE_MENU && mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE) {
                if (topNavPanel != null && (topNavPanel.getVisibility() != View.VISIBLE || topNavPanel.getAlpha() < 0.5f)) {
                    revealImmersiveTopBar();
                    if (sortTopRV != null) {
                        sortTopRV.setSelection(mCurrentSelected);
                        sortTopRV.requestFocus();
                    }
                    return true;
                }
            }
        }

        if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            if (mSortFocusView != null && mSortFocusView.hasFocus()) selectCategory(mSortFocused);
            if (mCurrentSelected >= 0 && mCurrentSelected < fragments.size()
                    && fragments.get(mCurrentSelected) instanceof GridFragment) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    if (((GridFragment) fragments.get(mCurrentSelected)).showFilter()) return true;
                } else if (event.getAction() == KeyEvent.ACTION_UP
                        && sortAdapter.getItem(mCurrentSelected) != null
                        && !sortAdapter.getItem(mCurrentSelected).filters.isEmpty()) {
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void observePages() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.sortResult.observe(this, absXml -> {
            SiteBean source = ApiConfig.getInstance().getHomeBean();
            if (source == null || !String.valueOf(source.getKey())
                    .equals(sourceViewModel.getLastPostedSortSourceKey())) return;
            categoryMetadataPending = false;
            mHandler.removeCallbacks(categoryMetadataTimeout);
            List<MovieSort.SortData> raw = absXml != null && absXml.classes != null
                    && absXml.classes.sortList != null ? absXml.classes.sortList : new ArrayList<>();
            List<MovieSort.SortData> mapped = DefaultConfig.adjustSort(source.getKey(), raw, true);
            List<MovieSort.SortData> cached = readCachedCategories(source);
            int freshCount = availableCategoryCount(mapped);
            for (MovieSort.SortData category : mapped) {
                if (category.available || "my0".equals(category.id)) continue;
                for (MovieSort.SortData previous : cached) {
                    if (previous.available && category.name.equals(previous.name)
                            && String.valueOf(source.getKey()).equals(previous.sourceKey)) {
                        mapped.set(mapped.indexOf(category), previous);
                        break;
                    }
                }
            }
            if (freshCount > 0) Hawk.put(categoryCacheKey(source), new Gson().toJson(mapped));
            applyCategoryList(mapped, absXml);
            if (absXml != null && absXml.videoList != null && mFirstFragment != null) {
                mFirstFragment.setmRecommendVideos(absXml.videoList);
            }
            if (freshCount < 5) scheduleCategoryRetry();
            else mHandler.removeCallbacks(categoryRetry);
            hideLoading();
            // 核心保证：只有默认点播源分类或推荐数据完全载入成功后，才允许排队检查更新
            if (freshCount > 0 || (absXml != null && absXml.videoList != null && !absXml.videoList.isEmpty())) {
                checkUpdateIfSourceReady();
            }
        });
    }

    /**
     * 点播源数据加载后延时检查更新，减少与主页初始加载同时进行的工作。
     */
    private void checkUpdateIfSourceReady() {
        updateSourceReady = true;
        if (hasScheduledUpdateCheck || isFinishing() || isDestroyed()) return;
        hasScheduledUpdateCheck = true;
        mHandler.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                UpdateManager.check(HomeActivity.this, false);
            } else {
                hasScheduledUpdateCheck = false;
            }
        }, 4000L);
    }

    private String categoryCacheKey(SiteBean source) {
        return "fixed_categories_v2_" + MD5.string2MD5(source.getKey() + "|" + source.getApi());
    }

    private List<MovieSort.SortData> readCachedCategories(SiteBean source) {
        try {
            String json = Hawk.get(categoryCacheKey(source), "");
            if (!json.isEmpty()) {
                List<MovieSort.SortData> cached = new Gson().fromJson(json,
                        new TypeToken<List<MovieSort.SortData>>() { }.getType());
                if (cached != null) return cached;
            }
        } catch (Exception e) {
            L.w("分类缓存不可用：" + e.getMessage());
        }
        return new ArrayList<>();
    }

    private int availableCategoryCount(List<MovieSort.SortData> categories) {
        int count = 0;
        for (MovieSort.SortData category : categories) {
            if (category.available && !"my0".equals(category.id)) count++;
        }
        return count;
    }

    private void applyCategoryList(List<MovieSort.SortData> categories, AbsSortXml data) {
        String signature = buildSortSignature(categories);
        if (signature.equals(mSortSignature)) return;
        mSortSignature = signature;
        sortAdapter.setNewData(categories);
        if (sortTopAdapter != null) {
            sortTopAdapter.setNewData(categories);
        }
        freshViewPager(data);
        mCurrentSelected = Math.max(0, Math.min(mCurrentSelected, categories.size() - 1));
        sortAdapter.setSelectedPosition(mCurrentSelected);
        if (sortTopAdapter != null) {
            sortTopAdapter.setSelectedPosition(mCurrentSelected);
        }
        mContentViewPager.setCurrentItem(mCurrentSelected, false);
    }

    public void retryCategoryMetadata() {
        if (categoryMetadataPending) return;
        categoryMetadataAttempts = 0;
        requestCategoryMetadata();
    }

    private void requestCategoryMetadata() {
        if (isFinishing() || isDestroyed() || sourceViewModel == null) return;
        SiteBean source = ApiConfig.getInstance().getHomeBean();
        if (source == null || source.getKey() == null
                || ApiConfig.getInstance().getSiteBean(source.getKey()) == null) return;
        String identity = source.getKey() + "|" + source.getApi();
        if (categoryMetadataPending && identity.equals(categorySourceIdentity)) return;
        if (!identity.equals(categorySourceIdentity)) {
            categoryMetadataPending = false;
            sourceViewModel.cancelSortRequests();
            categorySourceIdentity = identity;
            categoryMetadataAttempts = 0;
            applyCategoryList(DefaultConfig.adjustSort(source.getKey(), readCachedCategories(source), true), null);
        }
        categoryMetadataPending = true;
        categoryMetadataAttempts++;
        mHandler.removeCallbacks(categoryRetry);
        mHandler.removeCallbacks(categoryMetadataTimeout);
        mHandler.postDelayed(categoryMetadataTimeout, 20000L);
        sourceViewModel.getSort(source.getKey());
    }

    private void scheduleCategoryRetry() {
        mHandler.removeCallbacks(categoryRetry);
        if (categoryMetadataAttempts < 3) mHandler.postDelayed(categoryRetry, 2000L);
    }

    private void onCategoryMetadataTimeout() {
        if (!categoryMetadataPending) return;
        categoryMetadataPending = false;
        sourceViewModel.cancelSortRequests();
        scheduleCategoryRetry();
    }

    private String buildSortSignature(List<MovieSort.SortData> sortList) {
        StringBuilder sb = new StringBuilder();
        Gson gson = GsonUtil.getGson();
        for (MovieSort.SortData data : sortList) {
            sb.append(data.sourceKey).append('#').append(data.id).append('#').append(data.name)
                    .append('#').append(data.available).append('#').append(gson.toJson(data.filters)).append(';');
        }
        return sb.toString();
    }

    /**
     * 关闭打开画面：首页数据就绪后淡出，并把焦点交还给左侧分类栏
     */
    private void hideLoading() {
        if (mLoadingView == null || mLoadingView.getVisibility() != View.VISIBLE) {
            return;
        }
        long elapsed = System.currentTimeMillis() - mShowLoadingTime;
        if (elapsed < 600) {
            // 加载过快时也让打开画面停留一下，避免一闪而过
            mHandler.postDelayed(this::hideLoading, 600 - elapsed);
            return;
        }
        mLoadingView.animate().alpha(0f).setDuration(300).withEndAction(new Runnable() {
            @Override
            public void run() {
                mLoadingView.setVisibility(View.GONE);
                if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_APPLE_TV || mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE) {
                    if (sortTopRV != null) {
                        sortTopRV.setSelection(0);
                        sortTopRV.requestFocus();
                    }
                } else {
                    if (mMovieSortRV != null) {
                        // 电视端：进入主页时光标固定停在左侧「首页」分类上
                        mMovieSortRV.setSelection(0);
                        mMovieSortRV.requestFocus();
                    }
                }
            }
        }).start();
    }

    //获取site.file配置
    private void initSiteFile(){
        L.d("initSiteFile() ");
        ApiConfig.getInstance().loadSitesFile(HomeActivity.this, new ApiConfig.LoadConfigCallback() {
            @Override
            public void success() {
                L.d(" loadSitesFile success()");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        initJar();
                    }
                });
            }
        });
    }

    private void initJar() {
        if (!NullUtil.isNull(ApiConfig.getInstance().getmSpider())) {
            ApiConfig.getInstance().loadJar(getBaseContext(), false, ApiConfig.getInstance().getmSpider(), new ApiConfig.LoadConfigCallback() {
                @Override
                public void success() {
                    L.d(" initJar success()");
                    //jar 就绪后才能探测首页站点：jar 下载/加载是异步的，
                    //用固定延时会在 jar 未就绪时把 45 个 csp_* 站点全部探测失败
                    initHomeBean();
                }
            });
        } else {
            initHomeBean();
        }
    }

    private void initHomeBean() {
        ApiConfig.getInstance().initHomeBean(new ApiConfig.InitHomeCallback() {
            @Override
            public void success() {
                L.d(" initHomeBean success()");
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    requestCategoryMetadata();
                });
            }
        });
    }

    private void freshViewPager(AbsSortXml absXml) {
        if (sortAdapter.getData().size() > 0) {
            List<BaseLazyFragment> previousPages = new ArrayList<>(fragments);
            fragments.clear();
            mLastSyncedPage = -1;
            for (MovieSort.SortData data : sortAdapter.getData()) {
                if (data.id.equals("my0")) {
                    //数据源推荐列表始终带进首页片段：选「网站推荐」时直接用，豆瓣被限流时作为兜底
                    // 必须复用首次创建的那个首页片段：ViewPager 的 FragmentPagerAdapter 会按 tag
                    // 复用已注册的实例，列表里换成新实例的话，fragments 里就只剩一个没被使用的空壳，
                    // setHeroVisible 之类针对首页的调用会全部落空
                    if (mFirstFragment == null) {
                        mFirstFragment = FirstFragment.newInstance(absXml == null ? null : absXml.videoList);
                    }
                    fragments.add(mFirstFragment);
                } else {
                    GridFragment page = GridFragment.newInstance(data);
                    for (BaseLazyFragment previous : previousPages) {
                        if (previous instanceof GridFragment && ((GridFragment) previous).getCategoryIdentity()
                                .equals(page.getCategoryIdentity())) {
                            page = (GridFragment) previous;
                            page.updateCategoryData(data);
                            break;
                        }
                    }
                    fragments.add(page);
                }
            }
            mPageAdapter.notifyDataSetChanged();
            // 重建页面后重新同步首页背景层的可见性
            syncHeroBackdrop();
        }
    }

    @Override
    public void onBackPressed() {
        if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_DRAWER && isDrawerOpen) {
            closeDrawer();
            return;
        }

        if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_APPLE_TV || mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE) {
            if (mContentViewPager != null && mContentViewPager.hasFocus()) {
                revealImmersiveTopBar();
                if (sortTopRV != null) {
                    sortTopRV.setSelection(mCurrentSelected);
                    sortTopRV.requestFocus();
                    return;
                }
            } else if (mCurrentSelected != 0) {
                selectCategory(0);
                if (sortTopRV != null) {
                    sortTopRV.setSelection(0);
                    sortTopRV.requestFocus();
                }
                return;
            }
            exit();
            return;
        }

        int i;
        if (this.fragments.size() <= 0 || this.mSortFocused >= this.fragments.size() || (i = this.mSortFocused) < 0) {
            exit();
            return;
        }
        BaseLazyFragment baseLazyFragment = this.fragments.get(i);
        if (baseLazyFragment instanceof GridFragment || baseLazyFragment instanceof FirstFragment) {
            View view = this.mSortFocusView;
            if (view != null && !view.isFocused()) {
                this.mSortFocusView.requestFocus();
            } else if (this.mSortFocused != 0) {
                this.mMovieSortRV.setSelection(0);
            } else {
                exit();
            }
        } else {
            exit();
        }
    }

    private void exit() {
        if (System.currentTimeMillis() - mExitTime < 2000) {
            super.onBackPressed();
        } else {
            mExitTime = System.currentTimeMillis();
            Toast.makeText(mContext, "再按一次返回键退出应用", Toast.LENGTH_SHORT).show();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_PUSH_URL) {
            if (ApiConfig.getInstance().getSiteBean("push_agent") != null) {
                Intent newIntent = new Intent(mContext, DetailActivity.class);
                newIntent.putExtra("id", (String) event.obj);
                newIntent.putExtra("sourceKey", "push_agent");
                newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(newIntent);
            }
        }
    }

    /** 首页海报层独立于 ViewPager，只在首页显示。 */
    private void syncHeroBackdrop() {
        syncHeroBackdrop(mCurrentSelected);
    }

    private void syncHeroBackdrop(int currentIndex) {
        if (fragments == null || fragments.isEmpty()
                || currentIndex < 0 || currentIndex >= fragments.size()) {
            return;
        }
        BaseLazyFragment currentFragment = fragments.get(currentIndex);
        if (currentFragment.getView() != null) {
            currentFragment.getView().setVisibility(View.VISIBLE);
        }
        if (mLastSyncedPage == currentIndex) {
            if (currentFragment instanceof FirstFragment) {
                ((FirstFragment) currentFragment).setHeroVisible(true);
            }
            return;
        }
        if (mLastSyncedPage >= 0 && mLastSyncedPage < fragments.size()) {
            BaseLazyFragment previousFragment = fragments.get(mLastSyncedPage);
            if (previousFragment instanceof FirstFragment) {
                ((FirstFragment) previousFragment).setHeroVisible(false);
            }
            View previousPage = previousFragment.getView();
            if (previousPage != null) {
                previousPage.setVisibility(View.INVISIBLE);
            }
        } else {
            for (int i = 0; i < fragments.size(); i++) {
                if (i == currentIndex) {
                    continue;
                }
                BaseLazyFragment fragment = fragments.get(i);
                if (fragment instanceof FirstFragment) {
                    ((FirstFragment) fragment).setHeroVisible(false);
                }
                View pageView = fragment.getView();
                if (pageView != null) {
                    pageView.setVisibility(View.INVISIBLE);
                }
            }
        }
        if (currentFragment instanceof FirstFragment) {
            ((FirstFragment) currentFragment).setHeroVisible(true);
        }
        mLastSyncedPage = currentIndex;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (updateSourceReady) checkUpdateIfSourceReady();
        showTime();
        // 检查用户是否在设置页修改了首页布局风格，实现即时平滑热切换
        int savedLayout = Hawk.get(HawkConfig.HOME_LAYOUT_STYLE, HawkConfig.HOME_LAYOUT_DEFAULT);
        if (savedLayout != mCurrentLayoutStyle) {
            applyLayoutStyle(savedLayout);
        } else if (mCurrentLayoutStyle == HawkConfig.HOME_LAYOUT_IMMERSIVE) {
            if (topNavPanel != null && topNavPanel.getVisibility() == View.VISIBLE && !topNavPanel.hasFocus()) {
                scheduleImmersiveTopBarFadeOut(3500L);
            }
        }
        // 从其它页面返回时，当前页可能还停在分类页，需要重新同步大背景的显隐
        syncHeroBackdrop();
    }

    @Override
    protected void onPause() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mClockRunnable);
            mHandler.removeCallbacks(switchCategoryRunnable);
            mHandler.removeCallbacks(immersiveTopBarFadeRunnable);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (sourceViewModel != null) sourceViewModel.cancelSortRequests();
        EventBus.getDefault().unregister(this);
//        AppManager.getInstance().appExit(0);
        ControlManager.get().stopServer();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        if (mContentViewPager != null) {
            mContentViewPager.setAdapter(null);
        }
        if (mConfigExecutor != null) {
            mConfigExecutor.shutdownNow();
            mConfigExecutor = null;
        }
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        Intent intent;
        switch (view.getId()) {
            case R.id.layout_search2:
            case R.id.top_layout_search:
                intent = new Intent(mContext, SearchActivity.class);
                startActivity(intent);
                break;
            case R.id.layout_history:
            case R.id.top_layout_history:
                intent = new Intent(mContext, HistoryActivity.class);
                startActivity(intent);
                break;
            case R.id.layout_live:
            case R.id.top_layout_live:
                intent = new Intent(mContext, LivePlayActivity.class);
                startActivity(intent);
                break;
            case R.id.layout_collect:
            case R.id.top_layout_collect:
                intent = new Intent(mContext, CollectActivity.class);
                startActivity(intent);
                break;
            case R.id.layout_setting:
            case R.id.top_layout_setting:
                intent = new Intent(mContext, SettingActivity.class);
                startActivity(intent);
                break;
            default:
                break;
        }
    }

}
