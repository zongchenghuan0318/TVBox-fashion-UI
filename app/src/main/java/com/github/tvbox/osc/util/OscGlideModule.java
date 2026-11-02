package com.github.tvbox.osc.util;

import android.content.Context;

import androidx.annotation.NonNull;

import com.bumptech.glide.GlideBuilder;
import com.bumptech.glide.annotation.GlideModule;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.cache.InternalCacheDiskCacheFactory;
import com.bumptech.glide.module.AppGlideModule;
import com.bumptech.glide.request.RequestOptions;

/**
 * Glide 4.x 全局配置
 * 1. 默认采用 RGB_565 解码：每个像素占用 2 字节（相比 ARGB_8888 的 4 字节显存直降 50%），极大降低 TV 端海报墙 OOM 风险；
 * 2. 统一设定 250MB 磁盘缓存；
 * 3. 禁用 Manifest 解析以加快冷启动初始化。
 */
@GlideModule
public class OscGlideModule extends AppGlideModule {

    @Override
    public void applyOptions(@NonNull Context context, @NonNull GlideBuilder builder) {
        // 全局默认开启 RGB_565：显存直降 50%，显著缓解低配电视盒子 GC 停顿与海报墙滑动的 OOM 风险
        builder.setDefaultRequestOptions(
                new RequestOptions().format(DecodeFormat.PREFER_RGB_565)
        );
        // 配置 250MB 磁盘缓存容量
        int diskCacheSizeBytes = 1024 * 1024 * 250;
        builder.setDiskCache(new InternalCacheDiskCacheFactory(context, diskCacheSizeBytes));
    }

    @Override
    public boolean isManifestParsingEnabled() {
        return false;
    }
}
