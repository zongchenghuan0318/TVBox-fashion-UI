package com.github.tvbox.osc.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.HistoryAdapter;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.view.UndoBar;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.owen.tvrecyclerview.widget.V7GridLayoutManager;
import com.studio.osc.R;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

/**
 * @author pj567
 * @date :2021/1/7
 * @description:
 */
public class HistoryActivity extends BaseActivity implements View.OnClickListener {
    private TextView tvDel;
    private TextView tvClearAll;
    private TextView tvDelTip;
    private TvRecyclerView mGridView;
    private HistoryAdapter historyAdapter;
    private TipDialog mClearDialog = null;
    private UndoBar undoBar;
    private boolean delMode = false;
    private int loadGeneration;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_history;
    }

    @Override
    protected void init() {
        initView();
        initData();
    }

    private void toggleDelMode() {
        delMode = !delMode;
        updateModeHint();
        tvDel.setTextColor(delMode ? getResources().getColor(R.color.color_FF0057) : Color.WHITE);
    }

    private void updateModeHint() {
        boolean hasItems = historyAdapter != null && historyAdapter.getData() != null && !historyAdapter.getData().isEmpty();
        tvDelTip.setText(delMode ? "确定删除 · 返回退出" : "确定续播 · 长按详情");
        tvDelTip.setVisibility(hasItems ? View.VISIBLE : View.GONE);
    }

    private void updateDeleteButtons() {
        boolean empty = historyAdapter.getData() == null || historyAdapter.getData().isEmpty();
        tvClearAll.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvDel.setVisibility(empty ? View.GONE : View.VISIBLE);
        updateModeHint();
        if (empty && delMode) toggleDelMode();
    }

    private void deleteHistoryItem(int position) {
        loadGeneration++;
        VodInfo vodInfo = historyAdapter.getData().get(position);
        if (vodInfo == null) return;
        historyAdapter.remove(position);
        updateDeleteButtons();
        android.os.AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo);
        });
        getUndoBar().show("已删除观看记录", () -> {
            loadGeneration++;
            int restorePosition = Math.min(position, historyAdapter.getData().size());
            historyAdapter.addData(restorePosition, vodInfo);
            updateDeleteButtons();
            android.os.AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
                RoomDataManger.insertVodRecord(vodInfo.sourceKey, vodInfo);
            });
        }, mGridView);
    }

    private UndoBar getUndoBar() {
        if (undoBar == null) undoBar = new UndoBar(this);
        return undoBar;
    }

    private void openHistoryItem(VodInfo vodInfo, boolean continuePlayback) {
        if (ApiConfig.getInstance().getSiteBean(vodInfo.sourceKey) == null) {
            Bundle searchBundle = new Bundle();
            searchBundle.putString("title", vodInfo.name);
            Toast.makeText(this, "原片源已移除，正在搜索其他来源", Toast.LENGTH_SHORT).show();
            jumpActivity(SearchActivity.class, searchBundle);
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString("id", vodInfo.id);
        bundle.putString("sourceKey", vodInfo.sourceKey);
        bundle.putBoolean("autoPlayFromHistory", continuePlayback);
        jumpActivity(DetailActivity.class, bundle);
    }

    @Override
    public void onClick(View view) {
        if (R.id.title_history_tv == view.getId()) {
            finish();
        }
    }

    private void initView() {
        EventBus.getDefault().register(this);
        tvDel = findViewById(R.id.tvDel);
        tvClearAll = findViewById(R.id.tvClearAll);
        tvDelTip = findViewById(R.id.tvDelTip);
        mGridView = findViewById(R.id.sortRV);
        findViewById(R.id.title_history_tv).setOnClickListener(this);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(        new V7GridLayoutManager(this.mContext,
                com.github.tvbox.osc.util.SeniorMode.getGridColumnCount(
                        isBaseOnWidth() ? 5 : 6)));
        historyAdapter = new HistoryAdapter();
        mGridView.setAdapter(historyAdapter);
        tvDel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleDelMode();
            }
        });
        tvClearAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FastClickCheckUtil.check(v);
                showClearAllDialog();
            }
        });
        mGridView.setOnInBorderKeyEventListener(new TvRecyclerView.OnInBorderKeyEventListener() {
            @Override
            public boolean onInBorderKeyEvent(int direction, View focused) {
                if (direction == View.FOCUS_UP) {
                    tvClearAll.setFocusable(true);
                    tvDel.setFocusable(true);
                    tvDel.requestFocus();
                }
                return false;
            }
        });
        mGridView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
                itemView.animate().scaleX(1.05f).scaleY(1.05f).setDuration(150)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
            }
        });
        historyAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                VodInfo vodInfo = historyAdapter.getData().get(position);
                if (vodInfo != null) {
                    if (delMode) {
                        deleteHistoryItem(position);
                    } else {
                        openHistoryItem(vodInfo, true);
                    }
                }
            }
        });
        historyAdapter.setOnItemLongClickListener((adapter, view, position) -> {
            FastClickCheckUtil.check(view);
            if (!delMode && position >= 0 && position < historyAdapter.getData().size()) {
                VodInfo vodInfo = historyAdapter.getData().get(position);
                if (vodInfo != null) openHistoryItem(vodInfo, false);
            }
            return true;
        });
    }

    private void initData() {
        final int generation = ++loadGeneration;
        android.os.AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            List<VodInfo> records = RoomDataManger.getAllVodRecord(100);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                historyAdapter.setNewData(records);
                updateDeleteButtons();
            });
        });
    }

    /**
     * 清空全部历史记录前弹窗二次确认，确认后才真正删除
     */
    private void showClearAllDialog() {
        if (historyAdapter.getData() == null || historyAdapter.getData().isEmpty()) {
            Toast.makeText(mContext, "暂无历史记录", Toast.LENGTH_SHORT).show();
            return;
        }
        mClearDialog = new TipDialog(mContext, "确定要清空全部历史记录吗？", "确定", "取消", new TipDialog.OnListener() {
            @Override
            public void left() {
                mClearDialog.dismiss();
                android.os.AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
                    RoomDataManger.deleteAllVodRecord();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        initData();
                        Toast.makeText(mContext, "已清空历史记录", Toast.LENGTH_SHORT).show();
                    });
                });
            }

            @Override
            public void right() {
                mClearDialog.dismiss();
            }

            @Override
            public void cancel() {
                mClearDialog.dismiss();
            }
        });
        mClearDialog.setOnShowListener(dialog -> mClearDialog.findViewById(R.id.leftBtn).requestFocus());
        mClearDialog.show();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH) {
            initData();
        }
    }

    @Override
    protected void onDestroy() {
        if (undoBar != null) undoBar.dismiss(false);
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        if (undoBar != null) undoBar.dismiss(true);
        if (delMode) {
            toggleDelMode();
            return;
        }
        super.onBackPressed();
    }
}
