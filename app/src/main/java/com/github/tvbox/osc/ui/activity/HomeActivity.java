package com.github.tvbox.osc.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
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
import com.github.tvbox.osc.ui.activity.LivePlayActivity;
import com.github.tvbox.osc.ui.fragment.FirstFragment;
import com.github.tvbox.osc.ui.fragment.GridFragment;
import com.github.tvbox.osc.ui.tv.widget.DefaultTransformer;
import com.github.tvbox.osc.ui.tv.widget.MarqueeTextView;
import com.github.tvbox.osc.ui.tv.widget.NoScrollViewPager;
import com.github.tvbox.osc.util.DefaultConfig;
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
    private final Runnable switchCategoryRunnable = () -> {
        if (pendingCategoryPosition == this.mSortFocused && this.mSortFocusView != null && this.mSortFocusView.hasFocus()) {
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
    private final Runnable mClockRunnable = new Runnable() {
        @Override
        public void run() {
            if (tvDate == null || mHandler == null) {
                return;
            }
            tvDate.setText(StringUtil.getFormatTime_HH_MM(System.currentTimeMillis()));
            mHandler.postDelayed(this, 1000);
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
        findViewById(R.id.layout_search2).setOnClickListener(this);
        findViewById(R.id.layout_history).setOnClickListener(this);
        findViewById(R.id.layout_live).setOnClickListener(this);
        findViewById(R.id.layout_collect).setOnClickListener(this);
        findViewById(R.id.layout_setting).setOnClickListener(this);
        this.mMovieSortRV = findViewById(R.id.sortRV);
        this.mContentViewPager = findViewById(R.id.viewPager);
        
        // 禁用整个主容器和左侧导航栏的滚动
        ViewGroup homeContent = findViewById(R.id.homeContent);
        View leftNavPanel = findViewById(R.id.leftNavPanel);
        
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
            // 禁用触摸滚动，但保留子View的点击功能
            leftNavPanel.setOnTouchListener((v, event) -> {
                // 禁止父容器拦截触摸事件，防止产生滚动
                v.getParent().requestDisallowInterceptTouchEvent(true);
                // 返回true：侧栏区域内子View没消费掉的触摸（例如条目间距、上下内边距这些空档）
                // 由侧栏自己吃掉，避免穿透到下层内容区，点到被侧栏遮住看不见的分类卡片
                return true;
            });
        }
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
        initFirstPager();
        observePages();
        initSiteFile();
        showTime();
        getConfig(this);
        UpdateManager.check(this, false);
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
        
        // 禁用嵌套滚动，使列表固定不可滑动
        this.mMovieSortRV.setNestedScrollingEnabled(false);
        
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
                enterSelectedCategoryContent();
            }
        });
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
        freshViewPager(null);
        if (mMovieSortRV != null) {
            mMovieSortRV.setSelection(0);
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT
                && mSortFocusView != null && mSortFocusView.hasFocus()) {
            selectCategory(mSortFocused);
            if (enterSelectedCategoryContent()) return true;
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
        });
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
        freshViewPager(data);
        mCurrentSelected = Math.max(0, Math.min(mCurrentSelected, categories.size() - 1));
        sortAdapter.setSelectedPosition(mCurrentSelected);
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
        for (MovieSort.SortData data : sortList) {
            sb.append(data.sourceKey).append('#').append(data.id).append('#').append(data.name)
                    .append('#').append(data.available).append('#').append(new Gson().toJson(data.filters)).append(';');
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
                if (mMovieSortRV != null) {
                    // 电视端：进入主页时光标固定停在左侧「首页」分类上
                    mMovieSortRV.setSelection(0);
                    mMovieSortRV.requestFocus();
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
        showTime();
        // 从其它页面返回时，当前页可能还停在分类页，需要重新同步大背景的显隐
        syncHeroBackdrop();
    }

    @Override
    protected void onPause() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mClockRunnable);
            mHandler.removeCallbacks(switchCategoryRunnable);
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
                intent = new Intent(mContext, SearchActivity.class);
                startActivity(intent);
                break;
            case R.id.layout_history:
                intent = new Intent(mContext, HistoryActivity.class);
                startActivity(intent);
                break;
            case R.id.layout_live:
                intent = new Intent(mContext, LivePlayActivity.class);
                startActivity(intent);
                break;
            case R.id.layout_collect:
                intent = new Intent(mContext, CollectActivity.class);
                startActivity(intent);
                break;
            case R.id.layout_setting:
                intent = new Intent(mContext, SettingActivity.class);
                startActivity(intent);
                break;
            default:
                break;
        }
    }

}
