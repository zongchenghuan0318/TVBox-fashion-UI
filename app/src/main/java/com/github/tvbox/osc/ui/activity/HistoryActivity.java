package com.github.tvbox.osc.ui.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.TextView;
import android.widget.Toast;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.ui.adapter.HistoryAdapter;
import com.github.tvbox.osc.ui.dialog.TipDialog;
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
    private boolean delMode = false;

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
        tvDelTip.setVisibility(delMode ? View.VISIBLE : View.GONE);
        tvDel.setTextColor(delMode ? getResources().getColor(R.color.color_FF0057) : Color.WHITE);
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
        historyAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                FastClickCheckUtil.check(view);
                VodInfo vodInfo = historyAdapter.getData().get(position);
                if (vodInfo != null) {
                    if (delMode) {
                        historyAdapter.remove(position);
                        RoomDataManger.deleteVodRecord(vodInfo.sourceKey, vodInfo);
                    } else {
                        Bundle bundle = new Bundle();
                        bundle.putString("id", vodInfo.id);
                        bundle.putString("sourceKey", vodInfo.sourceKey);
                        jumpActivity(DetailActivity.class, bundle);
                    }
                }
            }
        });
    }

    private void initData() {
        List<VodInfo> allVodRecord = RoomDataManger.getAllVodRecord(100);
        historyAdapter.setNewData(allVodRecord);
        boolean empty = allVodRecord == null || allVodRecord.isEmpty();
        tvClearAll.setVisibility(empty ? View.GONE : View.VISIBLE);
        tvDel.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty && delMode) {
            toggleDelMode();
        }
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
                RoomDataManger.deleteAllVodRecord();
                initData();
                Toast.makeText(mContext, "已清空历史记录", Toast.LENGTH_SHORT).show();
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
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onBackPressed() {
        if (delMode) {
            toggleDelMode();
            return;
        }
        super.onBackPressed();
    }
}