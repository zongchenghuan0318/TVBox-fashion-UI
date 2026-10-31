package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.bean.LiveSettingGroup;
import com.studio.osc.R;

import java.util.ArrayList;


/**
 * @author pj567
 * @date :2021/1/12
 * @description:
 */
public class LiveSettingGroupAdapter extends BaseQuickAdapter<LiveSettingGroup, BaseViewHolder> {
    private int selectedGroupIndex = -1;
    private int focusedGroupIndex = -1;

    public LiveSettingGroupAdapter() {
        super(R.layout.item_live_setting_group, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder holder, LiveSettingGroup group) {
        TextView tvGroupName = holder.getView(R.id.tvSettingGroupName);
        tvGroupName.setText(group.getGroupName());
        int groupIndex = group.getGroupIndex();
        boolean isSelected = groupIndex == selectedGroupIndex;
        holder.itemView.setSelected(isSelected);
        tvGroupName.setSelected(isSelected);
        tvGroupName.getPaint().setFakeBoldText(isSelected);
        tvGroupName.setTextColor(isSelected ? Color.WHITE : 0xCCFFFFFF);
    }

    public void setSelectedGroupIndex(int selectedGroupIndex) {
        int preSelectedGroupIndex = this.selectedGroupIndex;
        this.selectedGroupIndex = selectedGroupIndex;
        if (preSelectedGroupIndex != -1)
            notifyItemChanged(preSelectedGroupIndex);
        if (this.selectedGroupIndex != -1)
            notifyItemChanged(this.selectedGroupIndex);
    }

    public int getSelectedGroupIndex() {
        return selectedGroupIndex;
    }

    public void setFocusedGroupIndex(int focusedGroupIndex) {
        this.focusedGroupIndex = focusedGroupIndex;
        if (this.focusedGroupIndex != -1)
            notifyItemChanged(this.focusedGroupIndex);
        else if (this.selectedGroupIndex != -1)
            notifyItemChanged(this.selectedGroupIndex);
    }
}