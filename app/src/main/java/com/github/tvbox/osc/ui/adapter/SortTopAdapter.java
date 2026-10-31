package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.bean.MovieSort;
import com.studio.osc.R;

import java.util.ArrayList;

/**
 * Apple TV 悬浮顶栏横向分类适配器
 */
public class SortTopAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    private int selectedPosition = 0;

    public void setSelectedPosition(int position) {
        int previous = selectedPosition;
        selectedPosition = position;
        if (previous >= 0 && previous < getData().size()) notifyItemChanged(previous);
        if (position != previous && position >= 0 && position < getData().size()) notifyItemChanged(position);
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public SortTopAdapter() {
        super(R.layout.item_home_sort_top, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        helper.setText(R.id.tvTitle, item.name);
        boolean selected = getData().indexOf(item) == selectedPosition;
        helper.itemView.setSelected(selected);
        android.widget.TextView title = helper.getView(R.id.tvTitle);
        if (title != null) {
            title.setSelected(selected);
            title.getPaint().setFakeBoldText(selected || helper.itemView.hasFocus());
            title.setTextColor(title.getResources().getColorStateList(R.color.selector_home_rail_text));
        }
        helper.setVisible(R.id.tvFilter, item.filters != null && !item.filters.isEmpty());
    }
}
