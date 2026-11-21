package com.github.tvbox.osc.ui.adapter;

import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PosterImageLoader;
import com.lcstudio.commonsurport.util.NullUtil;
import com.orhanobut.hawk.Hawk;
import com.studio.osc.R;

import java.util.ArrayList;

public class SearchResultAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private final boolean listView;

    public SearchResultAdapter() {
        this(Hawk.get(HawkConfig.SEARCH_VIEW, 1) == 0);
    }

    private SearchResultAdapter(boolean listView) {
        super(listView ? R.layout.item_search_lite : R.layout.item_search, new ArrayList<>());
        this.listView = listView;
    }

    public boolean isListView() {
        return listView;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video video) {
        if (helper.itemView != null) {
            helper.itemView.animate().cancel();
            if (!helper.itemView.hasFocus()) {
                helper.itemView.setScaleX(1.0f);
                helper.itemView.setScaleY(1.0f);
            }
        }
        if (video == null) return;
        SiteBean site = ApiConfig.getInstance().getSiteBean(video.sourceKey);
        String sourceName = site == null || NullUtil.isNull(site.getName()) ? "未知片源" : site.getName();
        boolean isDirectory = SearchActivity.isFolderItem(video) || SearchActivity.isAggregateItem(video);
        String siteLabel = isDirectory ? (sourceName + " · 目录") : sourceName;
        // list：片名置前，片源与备注置后，避免长源名把片名挤到截断区
        if (listView) {
            StringBuilder line = new StringBuilder();
            line.append(video.name == null ? "" : video.name);
            line.append("  ·  ").append(siteLabel);
            if (!NullUtil.isNull(video.type)) {
                line.append("  ").append(video.type);
            }
            if (!NullUtil.isNull(video.note)) {
                line.append("  ").append(video.note);
            } else if (isDirectory) {
                line.append("  聚合目录");
            }
            helper.setText(R.id.tvName, line.toString());
        } else {// with preview
            helper.setText(R.id.tvName, video.name == null ? "" : video.name);
            helper.setText(R.id.tvSite, siteLabel);
            String noteText = (video.note != null && !video.note.isEmpty())
                    ? video.note : (isDirectory ? "聚合目录" : "");
            helper.setVisible(R.id.tvNote, !noteText.isEmpty());
            if (!noteText.isEmpty()) {
                helper.setText(R.id.tvNote, noteText);
            }
            ImageView ivThumb = helper.getView(R.id.ivThumb);
            PosterImageLoader.bind(ivThumb, video.pic);
        }
    }
}
