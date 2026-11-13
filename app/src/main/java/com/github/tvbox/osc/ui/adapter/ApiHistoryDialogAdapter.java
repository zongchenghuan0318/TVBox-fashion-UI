package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置库列表（点播源管理）：
 * 使用独立徽标展示“使用中 / 置顶”，删除改用统一风格的 TipDialog 确认。
 */
public class ApiHistoryDialogAdapter extends ListAdapter<String, ApiHistoryDialogAdapter.SelectViewHolder> {

    class SelectViewHolder extends RecyclerView.ViewHolder {
        public SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
        }
    }

    public interface SelectDialogInterface {
        void click(String value);

        void del(String value, ArrayList<String> data);

        void pin(String value, ArrayList<String> data);
    }

    private final ArrayList<String> data = new ArrayList<>();
    private String select = "";
    private final SelectDialogInterface dialogInterface;

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
        if (newData != null) {
            data.addAll(newData);
        }
        String pinned = ApiConfig.getPinnedConfig();
        if (pinned != null && !pinned.isEmpty() && data.remove(pinned)) {
            data.add(0, pinned);
        }
        select = (newData == null || newData.isEmpty() || defaultSelect < 0 || defaultSelect >= newData.size())
                ? "" : newData.get(defaultSelect);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @Override
    public SelectViewHolder onCreateViewHolder(@NonNull @NotNull ViewGroup parent, int viewType) {
        return new SelectViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dialog_api_history, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull @NotNull SelectViewHolder holder, int position) {
        final String value = data.get(position);
        final String pinned = ApiConfig.getPinnedConfig();
        final boolean isCurrent = select.equals(value);
        final boolean isPinned = value.equals(pinned);

        View tvNameRow = holder.itemView.findViewById(R.id.tvNameRow);
        TextView tvName = holder.itemView.findViewById(R.id.tvName);
        View tvPinBadge = holder.itemView.findViewById(R.id.tvPinBadge);
        View tvCurrentBadge = holder.itemView.findViewById(R.id.tvCurrentBadge);
        TextView tvPin = holder.itemView.findViewById(R.id.tvPin);
        View tvDel = holder.itemView.findViewById(R.id.tvDel);

        tvName.setText(value);
        tvNameRow.setSelected(isCurrent);
        tvPinBadge.setVisibility(isPinned ? View.VISIBLE : View.GONE);
        tvCurrentBadge.setVisibility(isCurrent ? View.VISIBLE : View.GONE);
        tvNameRow.setContentDescription((isCurrent ? "当前使用，" : "") + (isPinned ? "已置顶，" : "") + value);

        tvPin.setText(isPinned ? "取消置顶" : "置顶");
        tvPin.setContentDescription(isPinned ? "取消置顶" : "置顶，启动时最先尝试");

        tvDel.setAlpha(isCurrent ? 0.35f : 1f);

        tvNameRow.setOnClickListener(v -> {
            int previousIndex = data.indexOf(select);
            select = value;
            if (previousIndex >= 0) notifyItemChanged(previousIndex);
            notifyItemChanged(data.indexOf(value));
            dialogInterface.click(value);
        });

        tvPin.setOnClickListener(v -> {
            dialogInterface.pin(value, data);
            notifyDataSetChanged();
        });

        tvDel.setOnClickListener(v -> {
            if (isCurrent) {
                Toast.makeText(v.getContext(), "正在使用的配置不能删除，请先切换到其它配置", Toast.LENGTH_SHORT).show();
                return;
            }
            TipDialog.confirm(v.getContext(), "删除配置地址",
                    "确定要删除该配置地址吗？\n" + value, "删除", () -> {
                        if (data.remove(value)) {
                            dialogInterface.del(value, data);
                            notifyDataSetChanged();
                        }
                    });
        });
    }
}
