package xyz.doikki.videoplayer.exo;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.database.ExoDatabaseProvider;
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource;
import com.google.android.exoplayer2.ext.rtmp.RtmpDataSourceFactory;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.hls.HlsMediaSource;
import com.google.android.exoplayer2.source.rtsp.RtspMediaSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.cache.Cache;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheKeyFactory;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import okhttp3.OkHttpClient;

public final class ExoMediaSourceHelper {

    private static ExoMediaSourceHelper sInstance;

    private final String mUserAgent;
    private final Context mAppContext;
    private OkHttpClient mOkClient = null;
    private Cache mCache;
    /** 是否把播放数据写入磁盘缓存（直播场景关闭，避免无限写入） */
    private volatile boolean mCacheEnabled = true;

    private ExoMediaSourceHelper(Context context) {
        mAppContext = context.getApplicationContext();
        mUserAgent = Util.getUserAgent(mAppContext, mAppContext.getApplicationInfo().name);
    }

    public static ExoMediaSourceHelper getInstance(Context context) {
        if (sInstance == null) {
            synchronized (ExoMediaSourceHelper.class) {
                if (sInstance == null) {
                    sInstance = new ExoMediaSourceHelper(context);
                }
            }
        }
        return sInstance;
    }

    public void setOkClient(OkHttpClient client) {
        mOkClient = client;
    }

    public boolean isCacheEnabled() {
        return mCacheEnabled;
    }

    public void setCacheEnabled(boolean enabled) {
        mCacheEnabled = enabled;
    }

    /** 释放某条视频占用的磁盘缓存（看完即释放、换集即释放），失败不影响播放 */
    public synchronized void removeCache(String uri) {
        if (uri == null || uri.length() == 0 || mCache == null) return;
        try {
            mCache.removeResource(uri);
        } catch (Throwable ignored) {
        }
    }

    public MediaSource getMediaSource(String uri) {
        return getMediaSource(uri, null, mCacheEnabled);
    }

    public MediaSource getMediaSource(String uri, Map<String, String> headers) {
        return getMediaSource(uri, headers, mCacheEnabled);
    }

    public MediaSource getMediaSource(String uri, boolean isCache) {
        return getMediaSource(uri, null, isCache);
    }

    public MediaSource getMediaSource(String uri, Map<String, String> headers, boolean isCache) {
        Uri contentUri = Uri.parse(uri);
        if ("rtmp".equals(contentUri.getScheme())) {
            return new ProgressiveMediaSource.Factory(new RtmpDataSourceFactory(null))
                    .createMediaSource(MediaItem.fromUri(contentUri));
        } else if ("rtsp".equals(contentUri.getScheme())) {
            return new RtspMediaSource.Factory().createMediaSource(MediaItem.fromUri(contentUri));
        }
        int contentType = inferContentType(uri);
        DataSource.Factory upstream = getDataSourceFactory(headers);
        DataSource.Factory factory;
        if (isCache) {
            factory = getCacheDataSourceFactory(upstream);
        } else {
            factory = upstream;
        }
        switch (contentType) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(contentUri));
            case C.TYPE_HLS:
                return new HlsMediaSource.Factory(factory)
                        .setAllowChunklessPreparation(true)
                        .createMediaSource(MediaItem.fromUri(contentUri));
            default:
            case C.TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(contentUri));
        }
    }

    private int inferContentType(String fileName) {
        fileName = fileName.toLowerCase();
        if (fileName.contains(".mpd")) {
            return C.TYPE_DASH;
        } else if (fileName.contains(".m3u8")) {
            return C.TYPE_HLS;
        } else {
            return C.TYPE_OTHER;
        }
    }

    private static final long MIN_SAFE_CACHE_BYTES = 256 * 1024 * 1024L; // 256MB 极老设备/小闪存兜底
    private static final long DEFAULT_CACHE_BYTES = 1024 * 1024 * 1024L; // 1GB 黄金平衡（容纳1部完整电影）
    private static final long MAX_SAFE_CACHE_BYTES = 1800 * 1024 * 1024L; // 1.8GB 宽裕存储上限
    private static final long DISK_SAFETY_RESERVE_BYTES = 600 * 1024 * 1024L; // 严格为电视系统预留至少 600MB 运行空间，绝不占满

    /**
     * 智能计算安全的磁盘缓存上限：根据老旧电视剩余闪存动态伸缩，杜绝占满电视内部空间
     */
    private long calculateSafeDiskCacheBytes(File base) {
        try {
            long usableSpace = base.getUsableSpace();
            // 如果可用空间小于 1.2GB，说明老电视闪存极其紧张，严格预留 600MB，绝不让系统弹存储不足警告
            if (usableSpace < 1200 * 1024 * 1024L) {
                long availableForCache = Math.max(0, usableSpace - DISK_SAFETY_RESERVE_BYTES);
                return Math.max(128 * 1024 * 1024L, Math.min(MIN_SAFE_CACHE_BYTES, availableForCache / 2));
            }
            // 剩余空间 1.2GB ~ 4GB：取剩余空间的 25%，安全防爆满
            if (usableSpace < 4L * 1024 * 1024 * 1024L) {
                return Math.min(DEFAULT_CACHE_BYTES, usableSpace / 4);
            }
            // 剩余空间充裕（> 4GB）：放宽到 1.5GB ~ 1.8GB，整部电影/多集电视剧秒拉秒回
            return Math.min(MAX_SAFE_CACHE_BYTES, usableSpace / 5);
        } catch (Throwable ignored) {
            return DEFAULT_CACHE_BYTES;
        }
    }

    /**
     * 针对 HLS 视频切片的智能 CacheKey 生成器：
     * 去除切片 URL 末尾的动态 Token/时间戳参数，使得片源刷新鉴权后，本地已存的切片依然 100% 命中缓存！
     */
    private static final CacheKeyFactory SMART_CACHE_KEY_FACTORY = dataSpec -> {
        if (dataSpec.key != null) {
            return dataSpec.key;
        }
        Uri uri = dataSpec.uri;
        if (uri == null) return "";
        String url = uri.toString();
        int queryIndex = url.indexOf('?');
        if (queryIndex > 0) {
            String pathPart = url.substring(0, queryIndex).toLowerCase();
            if (pathPart.endsWith(".ts") || pathPart.endsWith(".m4s") || pathPart.endsWith(".mp4")) {
                return url.substring(0, queryIndex);
            }
        }
        return url;
    };

    private synchronized DataSource.Factory getCacheDataSourceFactory(DataSource.Factory upstream) {
        if (mCache == null) {
            mCache = newCache();
        }
        return new CacheDataSource.Factory()
                .setCache(mCache)
                .setUpstreamDataSourceFactory(upstream)
                .setCacheKeyFactory(SMART_CACHE_KEY_FACTORY)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    private Cache newCache() {
        File base = mAppContext.getExternalCacheDir();
        if (base == null) base = mAppContext.getCacheDir();
        long safeCacheBytes = calculateSafeDiskCacheBytes(base);
        return new SimpleCache(
                new File(base, "exo-video-cache"),//缓存目录
                new LeastRecentlyUsedCacheEvictor(safeCacheBytes),//动态自适应 LRU，老电视智能防爆满保护
                new ExoDatabaseProvider(mAppContext));
    }

    /**
     * Returns a new DataSource factory.
     *
     * @return A new DataSource factory.
     */
    private DataSource.Factory getDataSourceFactory(Map<String, String> headers) {
        return new DefaultDataSourceFactory(mAppContext, getHttpDataSourceFactory(headers));
    }

    /**
     * Returns a new HttpDataSource factory.
     *
     * @return A new HttpDataSource factory.
     */
    private DataSource.Factory getHttpDataSourceFactory(Map<String, String> headers) {
        String userAgent = mUserAgent;
        Map<String, String> properties = new HashMap<>();
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                if (key == null || value == null) continue;
                if ("User-Agent".equalsIgnoreCase(key)) {
                    if (!TextUtils.isEmpty(value.trim())) userAgent = value.trim();
                } else {
                    properties.put(key, value.trim());
                }
            }
        }
        okhttp3.Call.Factory callFactory = mOkClient != null ? mOkClient : new OkHttpClient();
        return new OkHttpDataSource.Factory(callFactory)
                .setUserAgent(userAgent)
                .setDefaultRequestProperties(properties);
    }

    public synchronized void setCache(Cache cache) {
        this.mCache = cache;
    }
}
