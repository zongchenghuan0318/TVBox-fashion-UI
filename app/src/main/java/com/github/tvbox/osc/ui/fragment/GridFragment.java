package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.os.Parcelable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.adapter.GridAdapter;
import com.github.tvbox.osc.ui.dialog.GridFilterDialog;
import com.github.tvbox.osc.ui.tv.widget.LoadMoreView;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.lcstudio.commonsurport.L;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.studio.osc.R;

import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class GridFragment extends BaseLazyFragment {
    private MovieSort.SortData mSortData = null;
    private TvRecyclerView mGridView;
    private SourceViewModel sourceViewModel;
    private GridFilterDialog mFilterDialog;
    private GridAdapter mGridAdapter;
    private TextView mCategorySummary;
    private TextView mCategoryFilter;
    private TextView mCategoryReset;
    private TextView mCategoryStatus;
    private TextView mCategoryRetry;
    private final Handler categoryHandler = new Handler(Looper.getMainLooper());
    private final Runnable firstPageTimeout = this::onFirstPageTimeout;
    private int mPage = 1;
    private int maxPage = 1;
    private boolean retryWhenResumed;
    private boolean pageRequestInFlight;
    private int activePageRequestId = -1;
    private int lastFocusedPosition;
    private Parcelable savedGridLayoutState;
    private final ArrayList<Movie.Video> cachedVideos = new ArrayList<>();
    private int focusRequestGeneration;

    public static GridFragment newInstance(MovieSort.SortData sortData) {
        return new GridFragment().setArguments(sortData);
    }

    public GridFragment setArguments(MovieSort.SortData sortData) {
        this.mSortData = sortData;
        return this;
    }

    public String getCategoryIdentity() {
        return mSortData == null ? "" : String.valueOf(mSortData.sourceKey)
                + "|" + mSortData.name + "|" + mSortData.id;
    }

    public void updateCategoryData(MovieSort.SortData data) {
        if (mFilterDialog != null && mFilterDialog.isShowing()) mFilterDialog.dismiss();
        if (mSortData != null && mSortData.filterSelect != null) {
            for (MovieSort.SortFilter filter : data.filters) {
                String selected = mSortData.filterSelect.get(filter.key);
                if (selected != null && filter.values != null && filter.values.containsValue(selected)) {
                    data.filterSelect.put(filter.key, selected);
                }
            }
        }
        mSortData = data;
        mFilterDialog = null;
        updateFilterSummary();
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }

    @Override
    protected void init() {
        initView();
        initViewModel();
        if (!cachedVideos.isEmpty()) {
            mGridAdapter.setNewData(new ArrayList<>(cachedVideos));
            cachedVideos.clear();
            showSuccess();
            if (mPage > maxPage) mGridAdapter.loadMoreEnd();
            else mGridAdapter.loadMoreComplete();
            restoreGridScroll();
        } else {
            mPage = 1;
            maxPage = 1;
            resetGridPosition();
            initData();
        }
    }

    private void initView() {
        mCategorySummary = findViewById(R.id.tvCategorySummary);
        mCategoryFilter = findViewById(R.id.tvCategoryFilter);
        mCategoryFilter.setContentDescription("筛选，按菜单键也可打开");
        mCategoryReset = findViewById(R.id.tvCategoryReset);
        mCategoryStatus = findViewById(R.id.tvCategoryStatus);
        mCategoryRetry = findViewById(R.id.tvCategoryRetry);
        mCategoryRetry.setOnClickListener(v -> {
            if (pageRequestInFlight) return;
            if (!mSortData.available && mActivity instanceof HomeActivity) {
                ((HomeActivity) mActivity).retryCategoryMetadata();
                return;
            }
            mPage = 1;
            maxPage = 1;
            resetGridPosition();
            initData();
        });
        mCategoryFilter.setOnClickListener(v -> showFilter());
        mCategoryReset.setOnClickListener(v -> {
            mSortData.filterSelect.clear();
            mFilterDialog = null;
            mPage = 1;
            maxPage = 1;
            resetGridPosition();
            mCategoryFilter.requestFocus();
            updateFilterSummary();
            initData();
        });
        updateFilterSummary();
        mGridView = findViewById(R.id.sortRV);
        mGridView.setHasFixedSize(true);
        mGridAdapter = new GridAdapter();
        mGridView.setAdapter(mGridAdapter);
        // 左侧分类栏占用 250，内容区变窄，列数由 5/6 降为 4/5
        mGridView.setLayoutManager(        new V7GridLayoutManager(this.mContext,
                com.github.tvbox.osc.util.SeniorMode.getGridColumnCount(
                        isBaseOnWidth() ? 4 : 5)));
        mGridAdapter.setOnLoadMoreListener(new BaseQuickAdapter.RequestLoadMoreListener() {
            @Override
            public void onLoadMoreRequested() {
                if (pageRequestInFlight || mPage > maxPage) {
                    return;
                }
                mGridAdapter.setEnableLoadMore(true);
                pageRequestInFlight = true;
                activePageRequestId = sourceViewModel.getMovieList(mSortData, mPage);
            }
        }, mGridView);
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (position >= 0 && position < mGridAdapter.getData().size()) lastFocusedPosition = position;
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {

            }
        });
        mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View focused) {
                if (direction == View.FOCUS_UP) {
                    if (mCategoryFilter.getVisibility() == View.VISIBLE) {
                        mCategoryFilter.requestFocus();
                        return true;
                    }
                }
                return false;
            }
        });
        mGridAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                Movie.Video video = mGridAdapter.getData().get(position);
                //和首页卡片一致：直接拿片名去聚合搜索，不再进详情页
                if (video != null && video.name != null && !video.name.isEmpty()) {
                    Bundle bundle = new Bundle();
                    bundle.putString("title", video.name);
                    jumpActivity(SearchActivity.class, bundle);
                }
            }
        });
        mGridAdapter.setLoadMoreView(new LoadMoreView());
        setLoadSir(mGridView);
    }

    private void initViewModel() {
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        sourceViewModel.listResult.observe(getViewLifecycleOwner(), new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                if (sourceViewModel.getLastPostedCategoryRequestId() != activePageRequestId) return;
                categoryHandler.removeCallbacks(firstPageTimeout);
                pageRequestInFlight = false;
                int count = (absXml == null || absXml.movie == null || absXml.movie.videoList == null)
                        ? -1 : absXml.movie.videoList.size();
                L.w("gridFeed page=" + mPage + " count=" + count + " maxPage=" + maxPage);
                if (absXml == null || absXml.movie == null || absXml.movie.videoList == null) {
                    if (mPage == 1) {
                        showEmpty();
                        showCategoryStatus("加载失败，请检查网络或重试", true);
                    }
                    else mGridAdapter.loadMoreFail();
                    return;
                }
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    if (mPage == 1) {
                        boolean restoreFocus = mCategoryRetry.hasFocus();
                        showSuccess();
                        mGridAdapter.setNewData(absXml.movie.videoList);
                        showCategoryStatus("", false);
                        if (restoreFocus) mGridView.post(() -> focusRememberedItem());
                    } else {
                        mGridAdapter.addData(absXml.movie.videoList);
                    }
                    mPage++;
                    maxPage = absXml.movie.pagecount;
                } else {
                    if (mPage == 1) {
                        showEmpty();
                        showCategoryStatus(mSortData.filters.isEmpty() ? "暂无内容，可稍后重新加载"
                                : "暂无匹配内容，可调整或重置筛选", true);
                        mCategoryRetry.setText("重新加载");
                    }
                }
                if (mPage > maxPage || absXml.movie.videoList.isEmpty()) {
                    mGridAdapter.loadMoreEnd();
                } else {
                    mGridAdapter.loadMoreComplete();
                }
            }
        });
    }

    private void initData() {
        retryWhenResumed = false;
        if (!mSortData.available) {
            pageRequestInFlight = false;
            showEmpty();
            showCategoryStatus("分类信息暂未就绪，可重新加载分类", true);
            mCategoryRetry.setText("重新加载分类");
            return;
        }
        pageRequestInFlight = true;
        showLoading();
        categoryHandler.removeCallbacks(firstPageTimeout);
        if (mCategoryRetry.hasFocus()) {
            mCategoryRetry.setText("加载中…");
        } else {
            showCategoryStatus("", false);
        }
        categoryHandler.postDelayed(firstPageTimeout, 20000L);
        if (mPage == 1) mGridAdapter.setNewData(new ArrayList<>());
        L.w("gridFeed start sort=" + (mSortData == null ? "null" : mSortData.name)
                + " id=" + (mSortData == null ? "" : mSortData.id)
                + " src=" + (ApiConfig.getInstance().getHomeBean() == null ? ""
                : ApiConfig.getInstance().getHomeBean().getKey()));
        activePageRequestId = sourceViewModel.getMovieList(mSortData, mPage);
    }

    @Override
    protected void onFragmentResume() {
        if (mGridAdapter == null && getView() != null) init();
        restoreGridScroll();
        if (retryWhenResumed && mGridAdapter != null) {
            retryWhenResumed = false;
            if (mGridAdapter.getData().isEmpty()) {
                mPage = 1;
                maxPage = 1;
                initData();
            } else {
                pageRequestInFlight = true;
                mGridAdapter.loadMoreComplete();
                activePageRequestId = sourceViewModel.getMovieList(mSortData, mPage);
            }
        }
    }

    public boolean showFilter() {
        if (mGridAdapter == null || mSortData == null || mSortData.filters.isEmpty()) return false;
        if (!mSortData.filters.isEmpty() && mFilterDialog == null) {
            mFilterDialog = new GridFilterDialog(mContext);
            mFilterDialog.setData(mSortData);
            mFilterDialog.setOnDismiss(new GridFilterDialog.Callback() {
                @Override
                public void change() {
                    mPage = 1;
                    maxPage = 1;
                    resetGridPosition();
                    updateFilterSummary();
                    initData();
                }
            });
        }
        if (mFilterDialog != null) {
            mFilterDialog.show();
            return true;
        }
        return false;
    }

    private void updateFilterSummary() {
        if (mCategorySummary == null || mSortData == null) return;
        StringBuilder summary = new StringBuilder(mSortData.name == null ? "分类" : mSortData.name);
        for (MovieSort.SortFilter filter : mSortData.filters) {
            String selected = mSortData.filterSelect.get(filter.key);
            if (selected == null || selected.isEmpty() || filter.values == null) continue;
            for (java.util.Map.Entry<String, String> entry : filter.values.entrySet()) {
                if (selected.equals(entry.getValue())) {
                    summary.append(" · ").append(filter.name).append("：").append(entry.getKey());
                    break;
                }
            }
        }
        mCategorySummary.setText(summary);
        mCategoryFilter.setVisibility(mSortData.filters.isEmpty() ? View.GONE : View.VISIBLE);
        mCategoryReset.setVisibility(mSortData.filterSelect.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void showCategoryStatus(String message, boolean retry) {
        if (mCategoryStatus == null || mCategoryRetry == null) return;
        mCategoryStatus.setText(message);
        mCategoryStatus.setVisibility(message.isEmpty() ? View.GONE : View.VISIBLE);
        mCategoryRetry.setText("重试");
        mCategoryRetry.setVisibility(retry ? View.VISIBLE : View.GONE);
    }

    private void onFirstPageTimeout() {
        if (!pageRequestInFlight || mPage != 1 || mGridAdapter == null) return;
        sourceViewModel.cancelCategoryRequests();
        activePageRequestId = -1;
        pageRequestInFlight = false;
        showEmpty();
        showCategoryStatus("加载超时，可以重试或切换分类", true);
    }

    private void resetGridPosition() {
        lastFocusedPosition = 0;
        savedGridLayoutState = null;
        cachedVideos.clear();
        focusRequestGeneration++;
        if (mGridView != null) mGridView.scrollToPosition(0);
    }

    private void saveGridScroll() {
        if (mGridView != null && mGridView.getLayoutManager() != null) {
            savedGridLayoutState = mGridView.getLayoutManager().onSaveInstanceState();
        }
    }

    private void restoreGridScroll() {
        if (savedGridLayoutState != null && mGridView != null && mGridAdapter != null
                && !mGridAdapter.getData().isEmpty() && mGridView.getLayoutManager() != null) {
            mGridView.getLayoutManager().onRestoreInstanceState(savedGridLayoutState);
            savedGridLayoutState = null;
        }
    }

    public boolean focusRememberedItem() {
        if (mCategoryRetry != null && mCategoryRetry.getVisibility() == View.VISIBLE) {
            return mCategoryRetry.requestFocus();
        }
        if (mGridView == null || mGridAdapter == null || mGridAdapter.getData().isEmpty()
                || pageRequestInFlight && mPage == 1) return false;
        int position = Math.max(0, Math.min(lastFocusedPosition, mGridAdapter.getData().size() - 1));
        int generation = ++focusRequestGeneration;
        TvRecyclerView grid = mGridView;
        grid.scrollToPosition(position);
        grid.post(() -> focusGridItem(grid, position, generation, 0));
        return true;
    }

    public boolean focusCategoryContent() {
        if (focusRememberedItem()) return true;
        return mCategoryFilter != null && mCategoryFilter.getVisibility() == View.VISIBLE
                && mCategoryFilter.requestFocus();
    }

    private void focusGridItem(TvRecyclerView grid, int position, int generation, int retry) {
        if (mGridView != grid || generation != focusRequestGeneration || !isAdded() || !getUserVisibleHint()) return;
        if (grid.findViewHolderForAdapterPosition(position) != null || retry >= 10) {
            grid.setSelection(position);
            grid.requestFocus();
        } else {
            grid.postDelayed(() -> focusGridItem(grid, position, generation, retry + 1), 50L);
        }
    }

    @Override
    protected void onFragmentPause() {
        categoryHandler.removeCallbacks(firstPageTimeout);
        saveGridScroll();
        focusRequestGeneration++;
        retryWhenResumed = pageRequestInFlight
                || (mGridAdapter != null && mGridAdapter.getData().isEmpty());
        pageRequestInFlight = false;
        if (sourceViewModel != null) {
            sourceViewModel.cancelCategoryRequests();
        }
        if (mFilterDialog != null && mFilterDialog.isShowing()) {
            mFilterDialog.dismiss();
        }
    }

    @Override
    public void onDestroyView() {
        categoryHandler.removeCallbacks(firstPageTimeout);
        saveGridScroll();
        focusRequestGeneration++;
        activePageRequestId = -1;
        retryWhenResumed = retryWhenResumed || pageRequestInFlight;
        pageRequestInFlight = false;
        if (sourceViewModel != null) sourceViewModel.cancelCategoryRequests();
        if (mGridAdapter != null) {
            cachedVideos.clear();
            cachedVideos.addAll(mGridAdapter.getData());
        }
        if (mFilterDialog != null && mFilterDialog.isShowing()) {
            mFilterDialog.dismiss();
        }
        mFilterDialog = null;
        mGridView = null;
        mGridAdapter = null;
        mCategorySummary = null;
        mCategoryFilter = null;
        mCategoryReset = null;
        mCategoryStatus = null;
        mCategoryRetry = null;
        super.onDestroyView();
    }
}
