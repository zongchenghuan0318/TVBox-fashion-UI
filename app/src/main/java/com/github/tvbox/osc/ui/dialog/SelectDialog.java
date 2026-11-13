package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.owen.tvrecyclerview.widget.TvRecyclerView;
import com.studio.osc.R;

import java.util.List;

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
        TextView titleView = findViewById(R.id.title);
        if (titleView != null) {
            titleView.setText(tip);
        }
    }

    public void setSubtitle(String subtitle) {
        TextView subtitleView = findViewById(R.id.subtitle);
        if (subtitleView == null) return;
        if (subtitle != null && !subtitle.trim().isEmpty()) {
            subtitleView.setText(subtitle);
            subtitleView.setVisibility(View.VISIBLE);
        } else {
            subtitleView.setVisibility(View.GONE);
        }
    }

    /**
     * 统一的单选弹窗：选中即回调并自动关闭。select < 0 表示没有已选项。
     */
    public static <T> SelectDialog<T> create(Context context, String tip, List<T> items, int select,
                                             SelectDialogAdapter.SelectDialogInterface<T> listener) {
        return create(context, tip, null, items, select, listener);
    }

    public static <T> SelectDialog<T> create(Context context, String tip, String subtitle, List<T> items, int select,
                                             SelectDialogAdapter.SelectDialogInterface<T> listener) {
        final SelectDialog<T> dialog = new SelectDialog<>(context);
        dialog.setTip(tip);
        String resolvedSubtitle = subtitle;
        if ((resolvedSubtitle == null || resolvedSubtitle.trim().isEmpty()) && items != null && items.size() > 6) {
            resolvedSubtitle = "共 " + items.size() + " 项可选项";
        }
        dialog.setSubtitle(resolvedSubtitle);

        SelectDialogAdapter.SelectDialogInterface<T> wrapped = new SelectDialogAdapter.SelectDialogInterface<T>() {
            @Override
            public void click(T value, int pos) {
                listener.click(value, pos);
                dialog.dismiss();
            }

            @Override
            public String getDisplay(T val) {
                return listener.getDisplay(val);
            }

            @Override
            public String getSubDisplay(T val) {
                return listener.getSubDisplay(val);
            }
        };
        DiffUtil.ItemCallback<T> callback = new DiffUtil.ItemCallback<T>() {
            @Override
            public boolean areItemsTheSame(@NonNull T oldItem, @NonNull T newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull T oldItem, @NonNull T newItem) {
                return oldItem.equals(newItem);
            }
        };
        dialog.setAdapter(wrapped, callback, items, select);
        return dialog;
    }

    /** 创建并显示单选弹窗 */
    public static <T> void pick(Context context, String tip, List<T> items, int select,
                                SelectDialogAdapter.SelectDialogInterface<T> listener) {
        create(context, tip, items, select, listener).show();
    }

    public static <T> void pick(Context context, String tip, String subtitle, List<T> items, int select,
                                SelectDialogAdapter.SelectDialogInterface<T> listener) {
        create(context, tip, subtitle, items, select, listener).show();
    }

    public void setAdapter(SelectDialogAdapter.SelectDialogInterface<T> sourceBeanSelectDialogInterface,
                           DiffUtil.ItemCallback<T> sourceBeanItemCallback, List<T> data, int select) {
        SelectDialogAdapter<T> adapter = new SelectDialogAdapter<>(sourceBeanSelectDialogInterface, sourceBeanItemCallback);
        adapter.setData(data, select);
        TvRecyclerView tvRecyclerView = findViewById(R.id.list);
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
        final int target = (data != null && !data.isEmpty())
                ? Math.max(0, Math.min(select, data.size() - 1))
                : 0;
        tvRecyclerView.setSelectedPosition(target);
        tvRecyclerView.post(() -> {
            try {
                if (data != null && !data.isEmpty()) {
                    tvRecyclerView.scrollToPosition(target);
                    RecyclerView.ViewHolder vh = tvRecyclerView.findViewHolderForAdapterPosition(target);
                    if (vh != null && vh.itemView != null) {
                        vh.itemView.requestFocus();
                        return;
                    }
                }
            } catch (Exception ignored) {
            }
            tvRecyclerView.requestFocus();
        });
    }
}
