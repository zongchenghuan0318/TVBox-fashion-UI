package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.studio.osc.R;

import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

public class SelectDialog<T> extends BaseDialog {
    public SelectDialog(@NonNull Context context) {
        super(context);
        setContentView(R.layout.dialog_select);
    }

    public SelectDialog(@NonNull Context context, int resId) {
        super(context);
        setContentView(resId);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setTip(String tip) {
        ((TextView) findViewById(R.id.title)).setText(tip);
    }

    public void setAdapter(SelectDialogAdapter.SelectDialogInterface<T> sourceBeanSelectDialogInterface, DiffUtil.ItemCallback<T> sourceBeanItemCallback, List<T> data, int select) {
        SelectDialogAdapter<T> adapter = new SelectDialogAdapter(sourceBeanSelectDialogInterface, sourceBeanItemCallback);
        adapter.setData(data, select);
        TvRecyclerView tvRecyclerView = ((TvRecyclerView) findViewById(R.id.list));
        tvRecyclerView.setAdapter(adapter);
        tvRecyclerView.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {}

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                if (itemView != null) {
                    itemView.performClick();
                }
            }
        });
        tvRecyclerView.setSelectedPosition(select);
        tvRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                try {
                    tvRecyclerView.scrollToPosition(select);
                }catch (Exception e){
                }
                // 确保弹窗打开后列表立即获得焦点，防止部分厂商 TV 固件遥控器方向键失效
                tvRecyclerView.requestFocus();
            }
        });
    }
}
