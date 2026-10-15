package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.GlideImg;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;
import com.studio.osc.R;

import java.util.ArrayList;

public class HomeHotVodAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {

    public HomeHotVodAdapter() {
        super(R.layout.item_user_hot_vod, new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        // 海报圆角：按背景形状裁剪
        helper.getView(R.id.layoutPoster).setClipToOutline(true);
        TextView tvNote = helper.getView(R.id.tvNote);
        L.d("item.note="+item.note);
        if(item.isMore){
            helper.setText(R.id.tvName, item.name);
            ImageView thumbImg = helper.getView(R.id.ivThumb);
            thumbImg.setImageResource(R.drawable.nodata_plus_press);
            tvNote.setVisibility(View.GONE);
            return;
        }
        if (item.note == null || item.note.isEmpty()) {
            tvNote.setVisibility(View.GONE);
        } else {
            tvNote.setText(item.note);
            tvNote.setVisibility(View.VISIBLE);
        }
        helper.setText(R.id.tvName, item.name);
        ImageView thumbImg = helper.getView(R.id.ivThumb);
        //由于部分电视机使用glide报错
        if (!NullUtil.isNull(item.pic)) {
            Glide.with(thumbImg.getContext())
                    .load(GlideImg.model(item.pic))
                    .placeholder(R.drawable.img_loading_placeholder)
                    .error(R.drawable.img_loading_placeholder)
                    .into(thumbImg);
        } else {
            thumbImg.setImageResource(R.drawable.img_loading_placeholder);
        }
    }
}