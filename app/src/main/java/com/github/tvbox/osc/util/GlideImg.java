package com.github.tvbox.osc.util;

import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;

import java.util.regex.Pattern;

/**
 * 豆瓣图床（*.doubanio.com）有防盗链：请求不带豆瓣来源页 Referer 时直接返回 418，
 * 所以豆瓣封面必须补上 Referer 才能加载，其它图床保持原样不动。
 * 另外豆瓣接口下发的是 s_ratio_poster 小图（270x405），卡片与全屏背景放大后都很糊，
 * 统一换成同路径的 l_ratio_poster 大图（1080x1620，实测已是豆瓣最大档）；
 * 有些源会下发 m_ratio_poster，也一并提到 l 档，避免只有 540px 宽被背景放大后更糊。
 */
public class GlideImg {

    private static final String DOUBAN_REFERER = "https://movie.douban.com/";
    private static final Pattern RATIO_POSTER_SIZE = Pattern.compile("/(?:s|m|l)_ratio_poster/");
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
            String sharpUrl = RATIO_POSTER_SIZE.matcher(url).replaceAll(LARGE_SIZE_PATH);
            return new GlideUrl(sharpUrl, new LazyHeaders.Builder()
                    .addHeader("Referer", DOUBAN_REFERER)
                    .build());
        }
        return url;
    }
}
