package com.github.tvbox.osc.ui.adapter;

import android.view.View;
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
public class SeriesFlagAdapter extends BaseQuickAdapter<VodInfo.VodSeriesFlag, BaseViewHolder> {
    public SeriesFlagAdapter() {
        super(R.layout.item_series_flag, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo.VodSeriesFlag item) {
        View select = helper.getView(R.id.tvSeriesFlagSelect);
        TextView tvSeriesFlag = helper.getView(R.id.tvSeriesFlag);
        boolean selected = item != null && item.selected;
        helper.itemView.setSelected(selected);
        if (select != null) {
            select.setVisibility(selected ? View.VISIBLE : View.GONE);
        }
        if (tvSeriesFlag != null) {
            tvSeriesFlag.getPaint().setFakeBoldText(selected);
            tvSeriesFlag.setText(item == null ? "" : "线路" + item.name);
        }
    }
}