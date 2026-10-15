package com.github.tvbox.osc.util;

import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

/**
 * 豆瓣图床（*.doubanio.com）有防盗链：请求不带豆瓣来源页 Referer 时直接返回 418，
 * 所以豆瓣封面必须补上 Referer 才能加载，其它图床保持原样不动。
 * 另外豆瓣接口下发的都是 s_ratio_poster 小图（约 200px 宽），卡片与全屏背景放大后都很糊，
 * 统一换成同路径的 l_ratio_poster 大图（约 800px 宽），清晰度明显提升。
 */
public class GlideImg {

    private static final String DOUBAN_REFERER = "https://movie.douban.com/";
    private static final String THUMB_SIZE_PATH = "/s_ratio_poster/";
    private static final String LARGE_SIZE_PATH = "/l_ratio_poster/";

    /**
     * 把图片地址转换成 Glide 可用的加载模型
     */
    public static Object model(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String host = url.toLowerCase();
        if (host.contains("doubanio.com") || host.contains("douban.com")) {
            String sharpUrl = url.replace(THUMB_SIZE_PATH, LARGE_SIZE_PATH);
            return new GlideUrl(sharpUrl, new LazyHeaders.Builder()
                    .addHeader("Referer", DOUBAN_REFERER)
                    .build());
        }
        return url;
    }
}
