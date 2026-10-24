package com.github.tvbox.osc.ui.adapter;

import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.util.PosterImageLoader;
import com.studio.osc.R;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class HistoryAdapter extends BaseQuickAdapter<VodInfo, BaseViewHolder> {
    public HistoryAdapter() {
        super(R.layout.item_grid, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, VodInfo item) {
        TextView tvYear = helper.getView(R.id.tvYear);
        SiteBean site = ApiConfig.getInstance().getSiteBean(item.sourceKey);
        String sourceName = site == null ? null : site.getName();
        tvYear.setText(site == null ? "片源已移除"
                : sourceName == null || sourceName.trim().isEmpty() ? "未知片源" : sourceName);
        helper.setVisible(R.id.tvLang, false);
        helper.setVisible(R.id.tvArea, false);
        helper.setVisible(R.id.tvNote, false);
        helper.setText(R.id.tvName, item.name);
        // helper.setText(R.id.tvActor, item.actor);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        PosterImageLoader.bind(ivThumb, item.pic);
    }
}
