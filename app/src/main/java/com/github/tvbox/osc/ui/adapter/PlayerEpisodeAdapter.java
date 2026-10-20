package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.bean.VodInfo;
import com.studio.osc.R;

import java.util.ArrayList;

/**
 * 播放页选集侧栏的剧集列表：单行展示剧集名，当前播放项高亮。
 */
public class PlayerEpisodeAdapter extends BaseQuickAdapter<VodInfo.VodSeries, BaseViewHolder> {

    private int mCurrent = -1;

    public PlayerEpisodeAdapter() {
        super(R.layout.item_player_episode, new ArrayList<>());
    }

    public void setCurrent(int current) {
        if (mCurrent == current) {
            return;
        }
        int old = mCurrent;
        mCurrent = current;
        if (old >= 0 && old < getItemCount()) {
            notifyItemChanged(old);
        }
        if (mCurrent >= 0 && mCurrent < getItemCount()) {
            notifyItemChanged(mCurrent);
        }
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo.VodSeries item) {
        int position = helper.getLayoutPosition();
        TextView name = helper.getView(R.id.tvEpisodeName);
        String text = item == null ? null : item.name;
        if (TextUtils.isEmpty(text)) {
            text = "第" + (position + 1) + "集";
        }
        name.setText(text);
        boolean selected = position == mCurrent;
        helper.itemView.setSelected(selected);
        helper.setVisible(R.id.vEpisodeCurrent, selected);
    }
}
