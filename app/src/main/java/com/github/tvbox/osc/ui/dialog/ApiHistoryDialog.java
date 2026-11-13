package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ApiHistoryDialog extends BaseDialog {
    public ApiHistoryDialog(@NonNull @NotNull Context context) {
        super(context, R.style.CustomDialogStyleDim);
        setContentView(R.layout.dialog_api_history);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public void setTip(String tip) {
        TextView titleView = findViewById(R.id.title);
        if (titleView != null) {
            titleView.setText(tip);
        }
    }

    public void setSubtitle(String subtitle) {
        TextView subView = findViewById(R.id.subtitle);
        if (subView != null && subtitle != null) {
            subView.setText(subtitle);
            subView.setVisibility(subtitle.isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    public void setAdapter(ApiHistoryDialogAdapter.SelectDialogInterface sourceBeanSelectDialogInterface, List<String> data, int select) {
        ApiHistoryDialogAdapter adapter = new ApiHistoryDialogAdapter(sourceBeanSelectDialogInterface);
        adapter.setData(data, select);
        TvRecyclerView tvRecyclerView = findViewById(R.id.list);
        tvRecyclerView.setAdapter(adapter);
        int focusedPosition = select >= 0 && select < data.size() ? select : 0;
        tvRecyclerView.setSelectedPosition(focusedPosition);
        tvRecyclerView.post(() -> {
            tvRecyclerView.scrollToPosition(focusedPosition);
            tvRecyclerView.requestFocus();
        });
    }
}
