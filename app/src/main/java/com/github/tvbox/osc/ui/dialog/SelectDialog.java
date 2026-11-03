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

    /**
     * 统一的单选弹窗：选中即回调并自动关闭。select < 0 表示没有已选项。
     * 替代各处重复的 Adapter + DiffUtil 样板代码。
     */
    public static <T> SelectDialog<T> create(Context context, String tip, List<T> items, int select,
                                             SelectDialogAdapter.SelectDialogInterface<T> listener) {
        final SelectDialog<T> dialog = new SelectDialog<>(context);
        dialog.setTip(tip);
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
        // 数据由 setData 直接维护，DiffUtil 不参与刷新，这里只需满足接口
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
        tvRecyclerView.setSelectedPosition(Math.max(select, 0));
        tvRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                try {
                    if (select >= 0) tvRecyclerView.scrollToPosition(select);
                }catch (Exception e){
                }
                // 确保弹窗打开后列表立即获得焦点，防止部分厂商 TV 固件遥控器方向键失效
                tvRecyclerView.requestFocus();
            }
        });
    }
}
