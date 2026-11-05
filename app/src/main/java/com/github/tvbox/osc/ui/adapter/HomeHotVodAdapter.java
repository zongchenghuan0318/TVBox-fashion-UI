package com.github.tvbox.osc.ui.adapter;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.PosterImageLoader;
import com.studio.osc.R;

import java.util.ArrayList;
import java.util.List;

public class HomeHotVodAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private static final int LOOP_BLOCKS = 1001;
    private boolean looping;

    public HomeHotVodAdapter() {
        super(R.layout.item_user_hot_vod, new ArrayList<>());
    }

    public void setLooping(boolean looping) {
        if (this.looping == looping) {
            return;
        }
        this.looping = looping;
        notifyDataSetChanged();
    }

    public boolean isLooping() {
        return looping;
    }

    public int loopStartPosition() {
        int size = getData().size();
        return looping && size > 1 ? (LOOP_BLOCKS / 2) * size : 0;
    }

    public int realPosition(int position) {
        int size = getData().size();
        if (size == 0 || position < 0) {
            return -1;
        }
        return looping ? position % size : position;
    }

    @Override
    public int getItemCount() {
        int size = getData().size();
        return looping && size > 1 ? size * LOOP_BLOCKS : size;
    }

    @Override
    public Movie.Video getItem(int position) {
        List<Movie.Video> data = getData();
        int realPosition = realPosition(position);
        if (realPosition < 0 || realPosition >= data.size()) {
            return null;
        }
        return data.get(realPosition);
    }

    @Override
    public int getItemViewType(int position) {
        // BaseQuickAdapter normally treats positions beyond mData.size() as
        // footer/load-more rows; virtual positions must remain normal cards.
        return 0;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        ImageView poster = helper.getView(R.id.ivThumb);
        android.animation.ValueAnimator slice = (android.animation.ValueAnimator) poster.getTag(R.id.ivThumb);
        if (slice != null) slice.cancel();
        // 海报圆角：按背景形状裁剪
        helper.getView(R.id.layoutPoster).setClipToOutline(true);
        TextView tvNote = helper.getView(R.id.tvNote);
        if(item.isMore){
            helper.setText(R.id.tvName, item.name);
            ImageView thumbImg = helper.getView(R.id.ivThumb);
            PosterImageLoader.clearAndSetResource(thumbImg, R.drawable.nodata_plus_press);
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
        PosterImageLoader.bind(thumbImg, item.pic);
    }
}
