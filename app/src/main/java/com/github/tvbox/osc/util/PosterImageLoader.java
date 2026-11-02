package com.github.tvbox.osc.util;

import android.content.Context;
import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.studio.osc.R;

/**
 * 电视端海报图片绑定与内存保护统一收口
 * 1. 显式限制采样尺寸 (260x370)，杜绝高分辨率原图（如 2000x3000）直接解码导致显存爆炸；
 * 2. 强制使用 RGB_565 格式解码，内存消耗相比 ARGB_8888 减半；
 * 3. 禁用渐变动画，避免列表滑动时掉帧与 GPU 闪烁；
 * 4. 统一 DiskCacheStrategy.ALL，实现跨页面（首页、搜索、详情、收藏）磁盘缓存复用。
 */
public final class PosterImageLoader {

    /** 电视端海报物理显示标准尺寸（针对 1080P 卡片视口） */
    public static final int POSTER_WIDTH = 260;
    public static final int POSTER_HEIGHT = 370;

    /** 全局复用的海报请求配置，避免每次列表滚动绑定时反复分配配置对象 */
    private static final RequestOptions POSTER_OPTIONS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .dontAnimate()
            .format(DecodeFormat.PREFER_RGB_565)
            .override(POSTER_WIDTH, POSTER_HEIGHT)
            .centerCrop()
            .placeholder(R.drawable.img_loading_placeholder)
            .error(R.drawable.img_loading_placeholder);

    private PosterImageLoader() {
    }

    public static RequestOptions getPosterOptions() {
        return POSTER_OPTIONS;
    }

    public static void bind(ImageView imageView, String url) {
        if (imageView == null) return;
        Context context = imageView.getContext();
        if (context == null) return;

        if (TextUtils.isEmpty(url)) {
            Glide.with(context).clear(imageView);
            imageView.setImageResource(R.drawable.img_loading_placeholder);
            return;
        }

        Glide.with(context)
                .load(GlideImg.model(url))
                .apply(POSTER_OPTIONS)
                .into(imageView);
    }

    public static void clearAndSetResource(ImageView imageView, int resourceId) {
        if (imageView == null) return;
        Context context = imageView.getContext();
        if (context != null) {
            Glide.with(context).clear(imageView);
        }
        imageView.setImageResource(resourceId);
    }

    public static void clear(ImageView imageView) {
        if (imageView == null) return;
        Context context = imageView.getContext();
        if (context != null) {
            Glide.with(context).clear(imageView);
        }
    }
}
