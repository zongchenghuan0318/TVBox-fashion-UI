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

public class ApiHistoryDialogAdapter extends ListAdapter<String, ApiHistoryDialogAdapter.SelectViewHolder> {

    class SelectViewHolder extends RecyclerView.ViewHolder {

        public SelectViewHolder(@NonNull @NotNull View itemView) {
            super(itemView);
        }
    }

    public interface SelectDialogInterface {
        void click(String value);

        void del(String value, ArrayList<String> data);
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
        select = data.isEmpty() || defaultSelect < 0 || defaultSelect >= data.size()
                ? "" : data.get(defaultSelect);
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
        String value = data.get(position);
        String name = value;
        if (select.equals(value))
            name = "√ " + name;
        ((TextView) holder.itemView.findViewById(R.id.tvName)).setText(name);
        holder.itemView.findViewById(R.id.tvName).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                int previousIndex = data.indexOf(select);
                select = value;
                if (previousIndex >= 0) notifyItemChanged(previousIndex);
                notifyItemChanged(data.indexOf(value));
                dialogInterface.click(value);
            }
        });
        holder.itemView.findViewById(R.id.tvDel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (select.equals(value))
                    return;
                int index = data.indexOf(value);
                if (index < 0) return;
                data.remove(value);
                notifyItemRemoved(index);
                select = "";
                dialogInterface.del(value, data);
            }
        });
    }
}
