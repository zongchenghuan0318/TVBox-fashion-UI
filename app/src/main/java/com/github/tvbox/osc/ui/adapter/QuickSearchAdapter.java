package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SiteBean;
import com.lcstudio.commonsurport.util.NullUtil;
import com.studio.osc.R;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/23
 * @description:
 */
public class QuickSearchAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    public QuickSearchAdapter() {
        super(R.layout.item_quick_search_lite, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        if (item == null) return;
        SiteBean site = ApiConfig.getInstance().getSiteBean(item.sourceKey);
        String sourceName = site == null || NullUtil.isNull(site.getName()) ? "未知片源" : site.getName();
        // lite
        helper.setText(R.id.tvName, String.format("%s  %s %s %s",
                sourceName,
                item.name == null ? "" : item.name,
                item.type == null ? "" : item.type,
                item.note == null ? "" : item.note));
        // with preview
        /*
        helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvSite, ApiConfig.get().getSource(item.sourceKey).getName());
        helper.setVisible(R.id.tvNote, item.note != null && !item.note.isEmpty());
        if (item.note != null && !item.note.isEmpty()) {
            helper.setText(R.id.tvNote, item.note);
        }
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        if (!TextUtils.isEmpty(item.pic)) {
            Picasso.get()
                    .load(item.pic)
                    .transform(new RoundTransformation(MD5.string2MD5(item.pic + "position=" + helper.getLayoutPosition()))
                            .centerCorp(true)
                            .override(AutoSizeUtils.mm2px(mContext, 300), AutoSizeUtils.mm2px(mContext, 400))
                            .roundRadius(AutoSizeUtils.mm2px(mContext, 10), RoundTransformation.RoundType.ALL))
                    .placeholder(R.drawable.error_loading)
                    .error(R.drawable.error_loading)
                    .into(ivThumb);
        } else {
            ivThumb.setImageResource(R.drawable.error_loading);
        }
        */
    }
}