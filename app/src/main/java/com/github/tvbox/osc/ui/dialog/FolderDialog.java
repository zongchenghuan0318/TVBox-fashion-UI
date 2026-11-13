package com.github.tvbox.osc.ui.dialog;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.lifecycle.Observer;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AbsXml;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.ui.adapter.SearchResultAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PosterPreloader;
import com.github.tvbox.osc.util.SeniorMode;
import com.github.tvbox.osc.viewmodel.SourceViewModel;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.owen.tvrecyclerview.widget.V7LinearLayoutManager;
import com.studio.osc.R;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;

/**
 * 聚合目录浮层弹窗：替代整页跳进带废弃键盘的 SearchActivity，
 * 保留原搜索结果在底层，支持子目录层级返回与直达影片详情。
 */
public class FolderDialog extends BaseDialog {

    private static final class Entry {
        final String id;
        final String sourceKey;
        final String title;

        Entry(String id, String sourceKey, String title) {
            this.id = id;
            this.sourceKey = sourceKey;
            this.title = title;
        }
    }

    private final BaseActivity activity;
    private final SourceViewModel sourceViewModel;
    private final Deque<Entry> stack = new ArrayDeque<>();
    private final SearchResultAdapter adapter = new SearchResultAdapter();
    private final TvRecyclerView recyclerView;
    private final TextView titleView;
    private final TextView subtitleView;
    private final View loadingView;
    private final View emptyView;
    private final TextView emptyTipView;
    private int activeRequestId = -1;

    private final Observer<AbsXml> folderObserver = new Observer<AbsXml>() {
        @Override
        public void onChanged(AbsXml absXml) {
            if (!isShowing()) return;
            if (activeRequestId < 0 || sourceViewModel.getLastPostedCategoryRequestId() != activeRequestId) {
                return;
            }
            loadingView.setVisibility(View.GONE);
            Entry top = stack.peek();
            String sourceName = resolveSourceName(top != null ? top.sourceKey : "");
            if (absXml == null || absXml.movie == null || absXml.movie.videoList == null
                    || absXml.movie.videoList.isEmpty()) {
                adapter.setNewData(new ArrayList<>());
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
                emptyTipView.setText("该聚合目录为空或暂时无法加载");
                subtitleView.setText(sourceName + " · 暂无可用资源");
                findViewById(R.id.btnFolderBack).requestFocus();
            } else {
                emptyView.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.setNewData(new ArrayList<>(absXml.movie.videoList));
                subtitleView.setText(sourceName + " · 共 " + adapter.getData().size() + " 项资源");
                recyclerView.scrollToPosition(0);
                recyclerView.post(() -> {
                    recyclerView.setSelection(0);
                    recyclerView.requestFocus();
                });
            }
        }
    };

    public FolderDialog(@NonNull BaseActivity activity, @NonNull SourceViewModel sourceViewModel,
                        @NonNull Movie.Video rootFolder) {
        super(activity, R.style.CustomDialogStyleDim);
        this.activity = activity;
        this.sourceViewModel = sourceViewModel;
        setContentView(R.layout.dialog_folder);

        titleView = findViewById(R.id.tvFolderTitle);
        subtitleView = findViewById(R.id.tvFolderSubtitle);
        loadingView = findViewById(R.id.llFolderLoading);
        emptyView = findViewById(R.id.llFolderEmpty);
        emptyTipView = findViewById(R.id.tvFolderEmptyTip);
        recyclerView = findViewById(R.id.rvFolder);

        findViewById(R.id.btnFolderBack).setOnClickListener(v -> onBackPressed());
        findViewById(R.id.btnFolderClose).setOnClickListener(v -> dismiss());

        recyclerView.setHasFixedSize(true);
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 1) == 0) {
            recyclerView.setLayoutManager(new V7LinearLayoutManager(activity, 1, false));
        } else {
            recyclerView.setLayoutManager(new V7GridLayoutManager(activity,
                    SeniorMode.getGridColumnCount(4)));
        }
        recyclerView.setAdapter(adapter);
        PosterPreloader.setup(activity, recyclerView, new PosterPreloader.PosterProvider() {
            @Override
            public int getCount() {
                return adapter.getData().size();
            }

            @Override
            public String getPosterUrl(int position) {
                if (position >= 0 && position < adapter.getData().size()) {
                    Movie.Video item = adapter.getData().get(position);
                    return item != null ? item.pic : null;
                }
                return null;
            }
        }, 8);

        recyclerView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null) itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(140).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null) itemView.animate().scaleX(1.04f).scaleY(1.04f).setDuration(140).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                openItem(position);
            }
        });
        adapter.setOnItemClickListener((adp, view, position) -> {
            FastClickCheckUtil.check(view);
            openItem(position);
        });

        stack.push(new Entry(rootFolder.id, rootFolder.sourceKey, rootFolder.name));
    }

    @Override
    public void show() {
        super.show();
        sourceViewModel.listResult.observe(activity, folderObserver);
        loadTopEntry();
    }

    @Override
    public void dismiss() {
        sourceViewModel.listResult.removeObserver(folderObserver);
        super.dismiss();
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            stack.pop();
            loadTopEntry();
            return;
        }
        super.onBackPressed();
    }

    private void loadTopEntry() {
        Entry top = stack.peek();
        if (top == null) {
            dismiss();
            return;
        }
        String title = TextUtils.isEmpty(top.title) ? "聚合目录" : top.title;
        String sourceName = resolveSourceName(top.sourceKey);
        titleView.setText(title);
        subtitleView.setText(sourceName + " · 正在加载目录…");

        if (TextUtils.isEmpty(top.id) || TextUtils.isEmpty(top.sourceKey)) {
            loadingView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.GONE);
            emptyView.setVisibility(View.VISIBLE);
            emptyTipView.setText("目录信息不完整");
            return;
        }

        adapter.setNewData(new ArrayList<>());
        recyclerView.setVisibility(View.INVISIBLE);
        emptyView.setVisibility(View.GONE);
        loadingView.setVisibility(View.VISIBLE);
        activeRequestId = sourceViewModel.getFolderList(top.sourceKey, top.id);
    }

    private void openItem(int position) {
        if (position < 0 || position >= adapter.getData().size()) return;
        Movie.Video video = adapter.getData().get(position);
        if (video == null) return;
        if (SearchActivity.isFolderItem(video) || SearchActivity.isAggregateItem(video)) {
            stack.push(new Entry(video.id, video.sourceKey, video.name));
            loadTopEntry();
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString("id", video.id);
        bundle.putString("sourceKey", video.sourceKey);
        bundle.putString("note", video.note);
        bundle.putString("movieName", video.name);
        activity.jumpActivity(DetailActivity.class, bundle);
    }

    private String resolveSourceName(String sourceKey) {
        if (TextUtils.isEmpty(sourceKey)) return "聚合源";
        SiteBean bean = ApiConfig.getInstance().getSiteBean(sourceKey);
        return bean != null && !TextUtils.isEmpty(bean.getName()) ? bean.getName() : sourceKey;
    }
}
