package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.bean.VodInfo;
import com.studio.osc.R;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/22
 * @description:
 */
public class SeriesAdapter extends BaseQuickAdapter<VodInfo.VodSeries, BaseViewHolder> {
    public SeriesAdapter() {
        super(R.layout.item_series, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo.VodSeries item) {
        TextView tvSeries = helper.getView(R.id.tvSeries);
        boolean selected = item != null && item.selected;
        helper.itemView.setSelected(selected);
        tvSeries.setSelected(selected);
        tvSeries.getPaint().setFakeBoldText(selected);
        tvSeries.setTextColor(selected ? Color.WHITE : 0xCCFFFFFF);
        helper.setText(R.id.tvSeries, item == null ? "" : item.name);
    }
}