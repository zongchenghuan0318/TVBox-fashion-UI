package com.github.tvbox.osc.ui.adapter;

import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.cache.VodCollect;
import com.lcstudio.commonsurport.util.NullUtil;
import com.studio.osc.R;

import java.util.ArrayList;

public class CollectAdapter extends BaseQuickAdapter<VodCollect, BaseViewHolder> {

    public CollectAdapter() {
        super(R.layout.item_grid, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodCollect item) {
        TextView tvYear = helper.getView(R.id.tvYear);
        SiteBean homeBean = ApiConfig.getInstance().getSiteBean(item.sourceKey);
        if (null != homeBean) {
            tvYear.setText(homeBean.getName());
        }
        helper.setVisible(R.id.tvLang, false);
        helper.setVisible(R.id.tvArea, false);
        helper.setVisible(R.id.tvNote, false);
        helper.setText(R.id.tvName, item.name);

        ImageView ivThumb = helper.getView(R.id.ivThumb);
        //由于部分电视机使用glide报错
        if (!NullUtil.isNull(item.pic)) {
            Glide.with(ivThumb.getContext()).load(item.pic)
                    .placeholder(R.drawable.img_loading_placeholder)
                    .error(R.drawable.img_loading_placeholder)
                    .into(ivThumb);
        } else {
            ivThumb.setImageResource(R.drawable.img_loading_placeholder);
        }
    }
}