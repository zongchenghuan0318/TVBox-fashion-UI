package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.view.ContextThemeWrapper;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 配置库列表（点播源管理）：
 * - 当前使用中的配置标 √，置顶的配置标 📌 并排在最前；
 * - 每行可置顶/取消置顶；正在使用的配置不能删除，其余删除前需要确认。
 */
public class ApiHistoryDialogAdapter extends ListAdapter<String, ApiHistoryDialogAdapter.SelectViewHolder> {

    class SelectViewHolder extends RecyclerView.ViewHolder {

        public SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
        }
    }

    public interface SelectDialogInterface {
        /** 切换到该配置 */
        void click(String value);

        /** 删除：data 为删除后的完整列表，调用方负责保存 */
        void del(String value, ArrayList<String> data);

        /** 置顶/取消置顶：data 为可能被调整顺序后的完整列表，调用方负责保存 */
        void pin(String value, ArrayList<String> data);
    }

    private ArrayList<String> data = new ArrayList<>();

    private String select = "";

    private SelectDialogInterface dialogInterface = null;

    public ApiHistoryDialogAdapter(SelectDialogInterface dialogInterface) {
        super(new DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                return oldItem.equals(newItem);
            }
        });
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<String> newData, int defaultSelect) {
        data.clear();
        data.addAll(newData);
        // 置顶的配置固定显示在第一位
        String pinned = ApiConfig.getPinnedConfig();
        if (pinned != null && !pinned.isEmpty() && data.remove(pinned)) {
            data.add(0, pinned);
        }
        select = newData.isEmpty() || defaultSelect < 0 || defaultSelect >= newData.size()
                ? "" : newData.get(defaultSelect);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @Override
    public ApiHistoryDialogAdapter.SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new ApiHistoryDialogAdapter.SelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_api_history, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull ApiHistoryDialogAdapter.SelectViewHolder holder, int position) {
        final String value = data.get(position);
        final String pinned = ApiConfig.getPinnedConfig();
        final boolean isCurrent = select.equals(value);
        final boolean isPinned = value.equals(pinned);

        TextView tvName = holder.itemView.findViewById(R.id.tvName);
        TextView tvPin = holder.itemView.findViewById(R.id.tvPin);
        View tvDel = holder.itemView.findViewById(R.id.tvDel);

        String name = (isPinned ? "📌 " : "") + (isCurrent ? "√ " : "") + value;
        tvName.setText(name);
        tvName.setSelected(isCurrent);
        tvName.setContentDescription((isCurrent ? "当前使用：" : "") + (isPinned ? "已置顶：" : "") + value);

        tvPin.setText(isPinned ? "取消置顶" : "置顶");
        tvPin.setContentDescription(isPinned ? "取消置顶" : "置顶，启动时最先尝试");

        tvName.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int previousIndex = data.indexOf(select);
                select = value;
                if (previousIndex >= 0) notifyItemChanged(previousIndex);
                notifyItemChanged(data.indexOf(value));
                dialogInterface.click(value);
            }
        });

        tvPin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialogInterface.pin(value, data);
                notifyDataSetChanged();
            }
        });

        tvDel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isCurrent) {
                    Toast.makeText(v.getContext(), "正在使用的配置不能删除，请先切换到其它配置", Toast.LENGTH_SHORT).show();
                    return;
                }
                new AlertDialog.Builder(new ContextThemeWrapper(v.getContext(), R.style.DarkAlertDialog))
                        .setTitle("删除配置地址？")
                        .setMessage(value)
                        .setNegativeButton("取消", null)
                        .setPositiveButton("删除", (d, w) -> {
                            if (data.remove(value)) {
                                dialogInterface.del(value, data);
                                notifyDataSetChanged();
                            }
                        })
                        .show();
            }
        });
    }
}
