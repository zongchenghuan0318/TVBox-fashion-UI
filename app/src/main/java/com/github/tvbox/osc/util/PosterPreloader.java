package com.github.tvbox.osc.util;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.ListPreloader;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.integration.recyclerview.RecyclerViewPreloader;
import com.bumptech.glide.util.FixedPreloadSizeProvider;

import java.util.Collections;
import java.util.List;

/**
 * 电视端海报墙滚动预加载器
 * 结合 Glide 4 的 RecyclerViewPreloader，在用户用遥控器滚动海报列表时，
 * 提前后台解码后续 6 个格子的海报并存入内存缓存（指定 260x370 RGB_565），
 * 彻底消除遥控器快速滑行时的白块和卡顿。
 */
public final class PosterPreloader {

    public interface PosterProvider {
        int getCount();
        @Nullable
        String getPosterUrl(int position);
    }

    private PosterPreloader() {
    }

    public static RecyclerView.OnScrollListener setup(
            @NonNull Context context,
            @NonNull RecyclerView recyclerView,
            @NonNull PosterProvider posterProvider,
            int maxPreload) {

        ListPreloader.PreloadSizeProvider<String> sizeProvider =
                new FixedPreloadSizeProvider<>(PosterImageLoader.POSTER_WIDTH, PosterImageLoader.POSTER_HEIGHT);

        ListPreloader.PreloadModelProvider<String> modelProvider = new ListPreloader.PreloadModelProvider<String>() {
            @NonNull
            @Override
            public List<String> getPreloadItems(int position) {
                if (position >= 0 && position < posterProvider.getCount()) {
                    String url = posterProvider.getPosterUrl(position);
                    if (!TextUtils.isEmpty(url)) {
                        return Collections.singletonList(url);
                    }
                }
                return Collections.emptyList();
            }

            @Nullable
            @Override
            public RequestBuilder<?> getPreloadRequestBuilder(@NonNull String item) {
                return Glide.with(context)
                        .load(GlideImg.model(item))
                        .apply(PosterImageLoader.getPosterOptions());
            }
        };

        RecyclerViewPreloader<String> preloader = new RecyclerViewPreloader<>(
                Glide.with(context), modelProvider, sizeProvider, maxPreload > 0 ? maxPreload : 6
        );

        recyclerView.addOnScrollListener(preloader);
        return preloader;
    }
}
