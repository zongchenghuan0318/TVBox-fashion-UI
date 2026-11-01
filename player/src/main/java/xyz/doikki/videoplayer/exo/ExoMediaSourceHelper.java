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
import com.google.android.exoplayer2.extractor.ts.DefaultTsPayloadReaderFactory;
import com.google.android.exoplayer2.source.hls.DefaultHlsExtractorFactory;
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
        if (isCache && contentType != C.TYPE_HLS && contentType != C.TYPE_DASH) {
            // 切片流（HLS/DASH）坚决走纯内存 Upstream 直通，彻底消除磁盘写入锁争用、残缺切片与闪存 IO 阻塞
            // 仅对单文件点播（MP4/MKV等）使用磁盘缓存
            factory = getCacheDataSourceFactory(upstream);
        } else {
            factory = upstream;
        }
        switch (contentType) {
            case C.TYPE_DASH:
                return new DashMediaSource.Factory(upstream).createMediaSource(MediaItem.fromUri(contentUri));
            case C.TYPE_HLS:
                DefaultHlsExtractorFactory extractorFactory = new DefaultHlsExtractorFactory(
                        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
                                | DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
                                | DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
                        true);
                return new HlsMediaSource.Factory(upstream)
                        .setExtractorFactory(extractorFactory)
                        .setAllowChunklessPreparation(false)
                        .createMediaSource(MediaItem.fromUri(contentUri));
            default:
            case C.TYPE_OTHER:
                return new ProgressiveMediaSource.Factory(factory).createMediaSource(MediaItem.fromUri(contentUri));
        }
    }

    private int inferContentType(String fileName) {
        if (TextUtils.isEmpty(fileName)) return C.TYPE_OTHER;
        try {
            Uri uri = Uri.parse(fileName);
            String path = uri.getPath();
            if (path != null) {
                path = path.toLowerCase();
                if (path.endsWith(".mpd")) {
                    return C.TYPE_DASH;
                } else if (path.endsWith(".m3u8") || path.endsWith(".hls")) {
                    return C.TYPE_HLS;
                } else if (path.endsWith(".mp4") || path.endsWith(".mkv") || path.endsWith(".flv") || path.endsWith(".avi")) {
                    return C.TYPE_OTHER;
                }
            }
        } catch (Throwable ignored) {
        }
        fileName = fileName.toLowerCase();
        if (fileName.contains(".mpd")) {
            return C.TYPE_DASH;
        } else if (fileName.contains(".m3u8")) {
            return C.TYPE_HLS;
        } else {
            return C.TYPE_OTHER;
        }
    }

    private static final long MIN_SAFE_CACHE_BYTES = 256 * 1024 * 1024L; // 256MB 极小闪存兜底
    private static final long DEFAULT_CACHE_BYTES = 1024 * 1024 * 1024L; // 1GB 基础平衡
    private static final long MAX_SAFE_CACHE_BYTES = 3500 * 1024 * 1024L; // 3.5GB 现代大容量电视存储上限（可完整容纳整部 4K 电影）
    private static final long DISK_SAFETY_RESERVE_BYTES = 800 * 1024 * 1024L; // 严格为电视系统预留至少 800MB 运行空间，绝不占满

    /**
     * 智能计算安全的磁盘缓存上限：根据现代电视剩余闪存动态伸缩，兼顾 4K 大片缓存与存储安全
     */
    private long calculateSafeDiskCacheBytes(File base) {
        try {
            long usableSpace = base.getUsableSpace();
            // 如果可用空间小于 1.5GB，说明闪存紧张，严格预留安全空间
            if (usableSpace < 1500 * 1024 * 1024L) {
                long availableForCache = Math.max(0, usableSpace - DISK_SAFETY_RESERVE_BYTES);
                return Math.max(128 * 1024 * 1024L, Math.min(MIN_SAFE_CACHE_BYTES, availableForCache / 2));
            }
            // 剩余空间 1.5GB ~ 6GB：取剩余空间的 25%
            if (usableSpace < 6L * 1024 * 1024 * 1024L) {
                return Math.min(DEFAULT_CACHE_BYTES, usableSpace / 4);
            }
            // 现代大存储电视（剩余空间 > 6GB）：放宽至 2.5GB ~ 3.5GB，整部 4K 电影拖动零延迟回看
            return Math.min(MAX_SAFE_CACHE_BYTES, usableSpace / 4);
        } catch (Throwable ignored) {
            return DEFAULT_CACHE_BYTES;
        }
    }

    /**
     * 保留完整 URL：查询参数可能区分不同片段或字节范围。
     */
    private static final CacheKeyFactory SMART_CACHE_KEY_FACTORY = dataSpec -> {
        if (dataSpec.key != null) {
            return dataSpec.key;
        }
        Uri uri = dataSpec.uri;
        if (uri == null) return "";
        return uri.toString();
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
