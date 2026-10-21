package com.github.tvbox.osc.ui.fragment;

import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.ui.activity.SearchActivity;
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
    private int mPage = 1;
    private int maxPage = 1;
    private boolean retryWhenResumed;
    private boolean pageRequestInFlight;

    public static GridFragment newInstance(MovieSort.SortData sortData) {
        return new GridFragment().setArguments(sortData);
    }

    public GridFragment setArguments(MovieSort.SortData sortData) {
        this.mSortData = sortData;
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_grid;
    }

    @Override
    protected void init() {
        initView();
        initViewModel();
        initData();
    }

    private void initView() {
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
                sourceViewModel.getMovieList(mSortData, mPage);
            }
        }, mGridView);
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
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
        sourceViewModel.listResult.observe(this, new Observer<AbsXml>() {
            @Override
            public void onChanged(AbsXml absXml) {
                pageRequestInFlight = false;
                int count = (absXml == null || absXml.movie == null || absXml.movie.videoList == null)
                        ? -1 : absXml.movie.videoList.size();
                L.w("gridFeed page=" + mPage + " count=" + count + " maxPage=" + maxPage);
                if (absXml != null && absXml.movie != null && absXml.movie.videoList != null && absXml.movie.videoList.size() > 0) {
                    if (mPage == 1) {
                        showSuccess();
                        mGridAdapter.setNewData(absXml.movie.videoList);
                    } else {
                        mGridAdapter.addData(absXml.movie.videoList);
                    }
                    mPage++;
                    maxPage = absXml.movie.pagecount;
                } else {
                    if (mPage == 1) {
                        showEmpty();
                    }
                }
                if (mPage > maxPage || absXml == null || absXml.movie == null
                        || absXml.movie.videoList == null || absXml.movie.videoList.isEmpty()) {
                    mGridAdapter.loadMoreEnd();
                } else {
                    mGridAdapter.loadMoreComplete();
                }
            }
        });
    }

    private void initData() {
        retryWhenResumed = false;
        pageRequestInFlight = true;
        showLoading();
        L.w("gridFeed start sort=" + (mSortData == null ? "null" : mSortData.name)
                + " id=" + (mSortData == null ? "" : mSortData.id)
                + " src=" + ApiConfig.getInstance().getHomeBean().getKey());
        sourceViewModel.getMovieList(mSortData, mPage);
    }

    @Override
    protected void onFragmentResume() {
        if (retryWhenResumed && mGridAdapter != null && mGridAdapter.getData().isEmpty()) {
            mPage = 1;
            maxPage = 1;
            retryWhenResumed = false;
            initData();
        }
    }

    public void showFilter() {
        if (!mSortData.filters.isEmpty() && mFilterDialog == null) {
            mFilterDialog = new GridFilterDialog(mContext);
            mFilterDialog.setData(mSortData);
            mFilterDialog.setOnDismiss(new GridFilterDialog.Callback() {
                @Override
                public void change() {
                    mPage = 1;
                    initData();
                }
            });
        }
        if (mFilterDialog != null) {
            mFilterDialog.show();
        }
    }

    @Override
    protected void onFragmentPause() {
        retryWhenResumed = mGridAdapter != null && mGridAdapter.getData().isEmpty();
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
        if (mFilterDialog != null && mFilterDialog.isShowing()) {
            mFilterDialog.dismiss();
        }
        mFilterDialog = null;
        mGridView = null;
        mGridAdapter = null;
        super.onDestroyView();
    }
}