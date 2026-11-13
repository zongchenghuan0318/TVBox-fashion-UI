package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.ui.fragment.HomeCardMotion;
import com.studio.osc.R;

/**
 * 海报切换样式双列选择器：一屏展示 13 种卡片动效及特点说明，避免遥控器长列表反复下翻。
 */
public class HomeCardMotionDialog extends BaseDialog {

    public interface OnPickListener {
        void onPick(HomeCardMotion motion);
    }

    public HomeCardMotionDialog(@NonNull Context context, HomeCardMotion current, OnPickListener listener) {
        super(context);
        setContentView(R.layout.dialog_card_motion);

        HomeCardMotion[] motions = HomeCardMotion.values();
        int currentIndex = 0;
        for (int i = 0; i < motions.length; i++) {
            if (motions[i] == current) {
                currentIndex = i;
                break;
            }
        }

        TextView subtitle = findViewById(R.id.motionSubtitle);
        if (subtitle != null && current != null) {
            subtitle.setText("当前使用：「" + current.label + "」 · 共 " + motions.length + " 种动效，选中后返回首页生效");
        }

        RecyclerView grid = findViewById(R.id.motionGrid);
        GridLayoutManager lm = new GridLayoutManager(context, 2);
        grid.setLayoutManager(lm);
        grid.setAdapter(new MotionAdapter(motions, current, motion -> {
            if (listener != null) {
                listener.onPick(motion);
            }
            dismiss();
        }));

        final int focusPos = currentIndex;
        grid.post(() -> {
            grid.scrollToPosition(focusPos);
            grid.post(() -> {
                RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(focusPos);
                if (vh != null && vh.itemView != null) {
                    vh.itemView.requestFocus();
                } else {
                    grid.requestFocus();
                }
            });
        });
    }

    private static final class MotionAdapter extends RecyclerView.Adapter<MotionAdapter.VH> {
        private final HomeCardMotion[] items;
        private final HomeCardMotion current;
        private final OnPickListener listener;

        MotionAdapter(HomeCardMotion[] items, HomeCardMotion current, OnPickListener listener) {
            this.items = items;
            this.current = current;
            this.listener = listener;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_card_motion, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            HomeCardMotion item = items[position];
            boolean selected = item == current;
            holder.name.setText(item.label);
            holder.tag.setText(item.tag);
            holder.desc.setText(item.desc);
            holder.currentBadge.setVisibility(selected ? View.VISIBLE : View.GONE);
            holder.itemView.setSelected(selected);
            holder.itemView.setContentDescription(item.label + "，" + item.tag + "，" + item.desc + (selected ? "，当前选中" : ""));
            holder.itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onPick(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return items.length;
        }

        static final class VH extends RecyclerView.ViewHolder {
            final TextView name;
            final TextView tag;
            final TextView desc;
            final View currentBadge;

            VH(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.tvMotionName);
                tag = itemView.findViewById(R.id.tvMotionTag);
                desc = itemView.findViewById(R.id.tvMotionDesc);
                currentBadge = itemView.findViewById(R.id.tvMotionCurrent);
            }
        }
    }
}
