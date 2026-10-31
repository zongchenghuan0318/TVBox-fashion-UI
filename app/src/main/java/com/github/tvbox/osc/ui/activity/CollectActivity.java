package com.github.tvbox.osc.ui.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.CollectAdapter;
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

public class CollectActivity extends BaseActivity implements View.OnClickListener {
    private TextView tvDel;
    private TextView tvClearAll;
    private TextView tvDelTip;
    private TvRecyclerView mGridView;
    private CollectAdapter collectAdapter;
    private TipDialog mClearDialog = null;
    private UndoBar undoBar;
    private boolean delMode = false;
    private int loadGeneration;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_collect;
    }

    @Override
    protected void init() {
        initView();
        initData();
    }

    @Override
    public void onClick(View view) {
        if (R.id.title_collect_tv == view.getId()) {
            finish();
        }
    }

    private void toggleDelMode() {
        delMode = !delMode;
        updateModeHint();
        tvDel.setTextColor(delMode ? getResources().getColor(R.color.color_FF0057) : Color.WHITE);
    }

    private void updateModeHint() {
        boolean hasItems = collectAdapter != null && collectAdapter.getData() != null && !collectAdapter.getData().isEmpty();
        tvDelTip.setText(delMode ? "确定删除 · 返回退出" : "点按打开收藏");
        tvDelTip.setVisibility(hasItems ? View.VISIBLE : View.GONE);
    }

    private void updateDeleteButtons() {
        boolean empty = collectAdapter.getData() == null || collectAdapter.getData().isEmpty();
        tvClearAll.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvDel.setVisibility(empty ? View.GONE : View.VISIBLE);
        updateModeHint();
        if (empty && delMode) toggleDelMode();
    }

    private void deleteCollectItem(int position) {
        loadGeneration++;
        VodCollect vodInfo = collectAdapter.getData().get(position);
        if (vodInfo == null) return;
        collectAdapter.remove(position);
        updateDeleteButtons();
        android.os.AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            RoomDataManger.deleteVodCollect(vodInfo.getId());
        });
        getUndoBar().show("已取消收藏", () -> {
            loadGeneration++;
            int restorePosition = Math.min(position, collectAdapter.getData().size());
            collectAdapter.addData(restorePosition, vodInfo);
            updateDeleteButtons();
            android.os.AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
                RoomDataManger.restoreVodCollect(vodInfo);
            });
        }, mGridView);
    }

    private UndoBar getUndoBar() {
        if (undoBar == null) undoBar = new UndoBar(this);
        return undoBar;
    }

    private void initView() {
        EventBus.getDefault().register(this);
        tvDel = findViewById(R.id.tvDel);
        findViewById(R.id.title_collect_tv).setOnClickListener(this);
        tvClearAll = findViewById(R.id.tvClearAll);
        tvDelTip = findViewById(R.id.tvDelTip);
        mGridView = findViewById(R.id.sortRV);
        mGridView.setHasFixedSize(true);
        mGridView.setLayoutManager(        new V7GridLayoutManager(this.mContext,
                com.github.tvbox.osc.util.SeniorMode.getGridColumnCount(
                        isBaseOnWidth() ? 5 : 6)));
        collectAdapter = new CollectAdapter();
        mGridView.setAdapter(collectAdapter);
        tvDel.setOnClickListener(v -> toggleDelMode());
        tvClearAll.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showClearAllDialog();
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
        collectAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                VodCollect vodInfo = collectAdapter.getData().get(position);
                if (vodInfo != null) {
                    if (delMode) {
                        deleteCollectItem(position);
                    } else {
                        if (ApiConfig.getInstance().getSiteBean(vodInfo.sourceKey) != null) {
                            Bundle bundle = new Bundle();
                            bundle.putString("id", vodInfo.vodId);
                            bundle.putString("sourceKey", vodInfo.sourceKey);
                            jumpActivity(DetailActivity.class, bundle);
                        } else {
                            Intent newIntent = new Intent(mContext, SearchActivity.class);
                            newIntent.putExtra("title", vodInfo.name);
                            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                            startActivity(newIntent);
                        }
                    }
                }
            }
        });
    }

    private void initData() {
        final int generation = ++loadGeneration;
        android.os.AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            List<VodCollect> records = RoomDataManger.getAllVodCollect();
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed() || generation != loadGeneration) return;
                collectAdapter.setNewData(records);
                updateDeleteButtons();
            });
        });
    }

    /**
     * 清空全部收藏前弹窗二次确认，确认后才真正删除
     */
    private void showClearAllDialog() {
        if (collectAdapter.getData() == null || collectAdapter.getData().isEmpty()) {
            Toast.makeText(mContext, "暂无收藏", Toast.LENGTH_SHORT).show();
            return;
        }
        mClearDialog = new TipDialog(mContext, "确定要清空全部收藏吗？", "确定", "取消", new TipDialog.OnListener() {
            @Override
            public void left() {
                mClearDialog.dismiss();
                android.os.AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
                    RoomDataManger.deleteAllVodCollect();
                    runOnUiThread(() -> {
                        if (isFinishing() || isDestroyed()) return;
                        initData();
                        Toast.makeText(mContext, "已清空收藏", Toast.LENGTH_SHORT).show();
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
        if (event.type == RefreshEvent.TYPE_HISTORY_REFRESH || event.type == RefreshEvent.TYPE_COLLECT_REFRESH) {
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
