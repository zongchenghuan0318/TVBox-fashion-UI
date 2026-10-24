package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.studio.osc.R;

import java.util.ArrayList;

public class GridFilterKVAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    private String selectedLabel;

    public void setSelectedLabel(String label) {
        selectedLabel = label;
        notifyDataSetChanged();
    }
    public GridFilterKVAdapter() {
        super(R.layout.item_grid_filter_value, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.filterValue, item);
        boolean selected = item.equals(selectedLabel);
        helper.itemView.setSelected(selected);
        android.widget.TextView text = helper.getView(R.id.filterValue);
        text.getPaint().setFakeBoldText(selected);
        text.setTextColor(text.getResources().getColor(selected
                ? R.color.color_02F8E1 : R.color.color_FFFFFF));
    }
}
