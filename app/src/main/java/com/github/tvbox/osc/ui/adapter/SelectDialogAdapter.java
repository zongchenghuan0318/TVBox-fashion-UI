package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SelectDialogAdapter<T> extends ListAdapter<T, SelectDialogAdapter.SelectViewHolder> {

    static class SelectViewHolder extends RecyclerView.ViewHolder {
        public SelectViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public interface SelectDialogInterface<T> {
        void click(T value, int pos);

        String getDisplay(T val);

        default String getSubDisplay(T val) {
            return null;
        }
    }

    private final ArrayList<T> data = new ArrayList<>();
    private int select = 0;
    private final SelectDialogInterface<T> dialogInterface;

    public SelectDialogAdapter(SelectDialogInterface<T> dialogInterface, DiffUtil.ItemCallback<T> diffCallback) {
        super(diffCallback);
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<T> newData, int defaultSelect) {
        data.clear();
        if (newData != null) {
            data.addAll(newData);
        }
        select = defaultSelect;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }

    @NonNull
    @Override
    public SelectViewHolder onCreateViewHolder(@NotNull ViewGroup parent, int viewType) {
        return new SelectViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dialog_select, parent, false));
    }

    @Override
    public void onBindViewHolder(@NotNull SelectViewHolder holder, int position) {
        T value = data.get(position);
        String name = dialogInterface.getDisplay(value);
        boolean isSelected = position == select;

        TextView tvName = holder.itemView.findViewById(R.id.tvName);
        if (tvName != null) {
            tvName.setText(name);
            tvName.getPaint().setFakeBoldText(isSelected);
            tvName.setTextColor(ContextCompat.getColor(tvName.getContext(),
                    isSelected ? R.color.color_dialog_option_selected : R.color.color_dialog_option));
        }

        TextView tvSub = holder.itemView.findViewById(R.id.tvSub);
        if (tvSub != null) {
            String sub = dialogInterface.getSubDisplay(value);
            if (sub != null && !sub.isEmpty()) {
                tvSub.setText(sub);
                tvSub.setVisibility(View.VISIBLE);
                tvSub.setTextColor(ContextCompat.getColor(tvSub.getContext(),
                        isSelected ? R.color.color_dialog_sub_selected : R.color.color_dialog_sub));
            } else {
                tvSub.setVisibility(View.GONE);
            }
        }

        View badge = holder.itemView.findViewById(R.id.tvSelectedBadge);
        if (badge != null) {
            badge.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        }

        holder.itemView.setSelected(isSelected);
        holder.itemView.setContentDescription(name + (isSelected ? "，当前选中" : ""));
        holder.itemView.setOnClickListener(v -> {
            int adapterPos = holder.getAdapterPosition();
            if (adapterPos < 0 || adapterPos >= data.size()) return;
            if (adapterPos != select) {
                int oldSelect = select;
                select = adapterPos;
                if (oldSelect >= 0) notifyItemChanged(oldSelect);
                notifyItemChanged(select);
            }
            dialogInterface.click(data.get(adapterPos), adapterPos);
        });
    }
}
