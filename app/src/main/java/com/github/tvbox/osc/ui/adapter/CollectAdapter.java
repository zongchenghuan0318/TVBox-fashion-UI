package com.github.tvbox.osc.ui.adapter;

import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.util.PosterImageLoader;
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
        String sourceName = homeBean == null ? null : homeBean.getName();
        tvYear.setText(sourceName == null || sourceName.trim().isEmpty() ? "未知片源" : sourceName);
        helper.setVisible(R.id.tvLang, false);
        helper.setVisible(R.id.tvArea, false);
        helper.setVisible(R.id.tvNote, false);
        helper.setText(R.id.tvName, item.name);

        ImageView ivThumb = helper.getView(R.id.ivThumb);
        PosterImageLoader.bind(ivThumb, item.pic);
    }
}
