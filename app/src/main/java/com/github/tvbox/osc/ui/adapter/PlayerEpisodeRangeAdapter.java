package com.github.tvbox.osc.ui.adapter;

import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.bean.EpisodeRangeBean;
import com.studio.osc.R;

import java.util.ArrayList;

/**
 * 剧集区间分段 Tab 适配器（例如 1-30、31-60...）
 */
public class PlayerEpisodeRangeAdapter extends BaseQuickAdapter<EpisodeRangeBean, BaseViewHolder> {

    private int selectedIndex = -1;

    public PlayerEpisodeRangeAdapter() {
        super(R.layout.item_player_episode_range, new ArrayList<>());
    }

    public void setSelectedIndex(int index) {
        if (selectedIndex == index) return;
        int old = selectedIndex;
        selectedIndex = index;
        if (old >= 0 && old < getItemCount()) {
            notifyItemChanged(old);
        }
        if (selectedIndex >= 0 && selectedIndex < getItemCount()) {
            notifyItemChanged(selectedIndex);
        }
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    @Override
    protected void convert(BaseViewHolder helper, EpisodeRangeBean item) {
        TextView tv = helper.getView(R.id.tvRangeTitle);
        tv.setText(item != null ? item.title : "");
        boolean isSelected = helper.getLayoutPosition() == selectedIndex;
        helper.itemView.setSelected(isSelected);
    }
}
