package com.github.tvbox.osc.util;

import android.text.TextUtils;
import android.widget.ImageView;

import com.bumptech.glide.Glide;
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
                .placeholder(R.drawable.img_loading_placeholder)
                .error(R.drawable.img_loading_placeholder)
                .into(imageView);
    }

    public static void clearAndSetResource(ImageView imageView, int resourceId) {
        Glide.clear(imageView);
        imageView.setImageResource(resourceId);
    }
}
