package com.github.tvbox.osc.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.studio.osc.R;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

public class SelectDialogAdapter<T> extends ListAdapter<T, SelectDialogAdapter.SelectViewHolder> {

    class SelectViewHolder extends RecyclerView.ViewHolder {
        public SelectViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    public interface SelectDialogInterface<T> {
        void click(T value, int pos);
        String getDisplay(T val);
    }

    private ArrayList<T> data = new ArrayList<>();

    private int select = 0;

    private SelectDialogInterface dialogInterface = null;

    public SelectDialogAdapter(SelectDialogInterface dialogInterface, DiffUtil.ItemCallback diffCallback) {
        super(diffCallback);
        this.dialogInterface = dialogInterface;
    }

    public void setData(List<T> newData, int defaultSelect) {
        data.clear();
        data.addAll(newData);
        select = defaultSelect;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return data.size();
    }


    @Override
    public SelectDialogAdapter.SelectViewHolder onCreateViewHolder( @NotNull ViewGroup parent, int viewType) {
        return new SelectDialogAdapter.SelectViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_dialog_select, parent, false));
    }

    @Override
    public void onBindViewHolder( @NotNull SelectDialogAdapter.SelectViewHolder holder, int position) {
        T value = data.get(position);
        String name = dialogInterface.getDisplay(value);
        boolean isSelected = position == select;
        if (isSelected) {
            name = "√ " + name;
        }
        TextView tvName = (TextView) holder.itemView.findViewById(R.id.tvName);
        if (tvName != null) {
            tvName.setText(name);
            tvName.setSelected(isSelected);
            tvName.getPaint().setFakeBoldText(isSelected);
            tvName.setTextColor(isSelected ? 0xFFFFFFFF : 0xCCFFFFFF);
        }
        holder.itemView.setSelected(isSelected);
        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (position == select) return;
                notifyItemChanged(select);
                select = position;
                notifyItemChanged(select);
                dialogInterface.click(value, position);
            }
        });
    }
}
