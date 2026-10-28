package com.github.tvbox.osc.util;

import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.studio.osc.R;

/** Shared poster binding for recycled list items. */
public final class PosterImageLoader {

    private PosterImageLoader() {
    }

    public static void bind(ImageView imageView, String url) {
        Glide.clear(imageView);
        imageView.setImageResource(R.drawable.img_loading_placeholder);
        if (TextUtils.isEmpty(url)) {
            return;
        }
        Glide.with(imageView.getContext())
                .load(GlideImg.model(url))
                .diskCacheStrategy(DiskCacheStrategy.ALL) // 缓存原图与切图，首页/搜索/详情/历史共用图片缓存，避免多页重复下载
                .dontAnimate() // 禁用渐变动画，避免电视列表滑动时闪烁并减轻 GPU 渲染负担
                .placeholder(R.drawable.img_loading_placeholder)
                .error(R.drawable.img_loading_placeholder)
                .into(imageView);
    }

    public static void clearAndSetResource(ImageView imageView, int resourceId) {
        Glide.clear(imageView);
        imageView.setImageResource(resourceId);
    }
}
