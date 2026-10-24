package com.github.tvbox.osc.ui.adapter;

import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PosterImageLoader;
import com.lcstudio.commonsurport.util.NullUtil;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

import java.util.ArrayList;

public class SearchResultAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    public SearchResultAdapter() {
        // 0 列表 1 缩略图
        super(Hawk.get(HawkConfig.SEARCH_VIEW, 1) == 0 ? R.layout.item_search_lite : R.layout.item_search, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video video) {
        SiteBean site = ApiConfig.getInstance().getSiteBean(video.sourceKey);
        String sourceName = site == null || NullUtil.isNull(site.getName()) ? "未知片源" : site.getName();
        // list
        if (Hawk.get(HawkConfig.SEARCH_VIEW, 1) == 0) {
            helper.setText(R.id.tvName, String.format("%s  %s %s %s",
                    sourceName,
                    video.name,
                    NullUtil.isNull(video.type) ? "" : video.type,
                    NullUtil.isNull(video.note) ? "" : video.note));
        } else {// with preview
            helper.setText(R.id.tvName, video.name);
            helper.setText(R.id.tvSite, sourceName);
            helper.setVisible(R.id.tvNote, video.note != null && !video.note.isEmpty());
            if (video.note != null && !video.note.isEmpty()) {
                helper.setText(R.id.tvNote, video.note);
            }
            ImageView ivThumb = helper.getView(R.id.ivThumb);
            PosterImageLoader.bind(ivThumb, video.pic);
        }
    }
}
