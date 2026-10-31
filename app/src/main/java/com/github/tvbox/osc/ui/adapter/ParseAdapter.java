package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.bean.ParseBean;
import com.studio.osc.R;

import java.util.ArrayList;

public class ParseAdapter extends BaseQuickAdapter<ParseBean, BaseViewHolder> {
    public ParseAdapter() {
        super(R.layout.item_play_parse, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, ParseBean item) {
        TextView tvParse = helper.getView(R.id.tvParse);
        boolean isDefault = item != null && item.isDefault();
        helper.itemView.setSelected(isDefault);
        if (tvParse != null) {
            tvParse.setVisibility(View.VISIBLE);
            tvParse.setSelected(isDefault);
            tvParse.getPaint().setFakeBoldText(isDefault);
            tvParse.setTextColor(isDefault ? Color.WHITE : mContext.getResources().getColor(R.color.color_CCFFFFFF));
            tvParse.setText(item == null ? "" : item.getName());
        }
    }
}