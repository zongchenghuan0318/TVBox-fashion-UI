package xyz.doikki.videoplayer.exo;

import android.net.Uri;
import android.util.Log;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.source.hls.playlist.HlsMediaPlaylist;
import com.google.android.exoplayer2.upstream.DataSpec;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheWriter;

import java.io.IOException;
import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 后台把播放点前方一段时间内的 HLS 切片提前写入磁盘缓存。
 * 写入的缓存与播放器共用同一缓存键，之后往后拖动时直接从本地读取。
 */
final class HlsPrefetcher {

    /** 预取范围：播放点前方 15 分钟 */
    static final long PREFETCH_AHEAD_US = 15L * 60L * 1_000_000L;
    private static final long IDLE_SLEEP_MS = 1_000L;

    /** 播放位置的快照提供者（必须线程安全，由播放线程定期刷新） */
    interface PlayheadProvider {
        long positionMs();
    }

    private final CacheDataSource mCacheDataSource;
    private final List<HlsMediaPlaylist.Segment> mSegments;
    private final String mBaseUri;
    private final PlayheadProvider mPlayhead;
    private final Set<String> mDone = new HashSet<>();

    private volatile boolean mReleased;
    private volatile CacheWriter mCurrentWriter;
    private Thread mThread;

    HlsPrefetcher(CacheDataSource cacheDataSource, HlsMediaPlaylist playlist, PlayheadProvider playhead) {
        mCacheDataSource = cacheDataSource;
        mSegments = playlist.segments;
        mBaseUri = playlist.baseUri;
        mPlayhead = playhead;
    }

    void start() {
        mThread = new Thread(this::run, "hls-prefetch");
        mThread.setDaemon(true);
        mThread.start();
    }

    void release() {
        mReleased = true;
        CacheWriter writer = mCurrentWriter;
        if (writer != null) writer.cancel();
        if (mThread != null) mThread.interrupt();
    }

    private void run() {
        while (!mReleased) {
            long posUs = mPlayhead.positionMs() * 1000L;
            int index = indexAt(posUs);
            for (int i = index; i >= 0 && i < mSegments.size() && !mReleased; i++) {
                HlsMediaPlaylist.Segment segment = mSegments.get(i);
                if (segment.relativeStartTimeUs > posUs + PREFETCH_AHEAD_US) break;
                cacheSegment(segment);
            }
            try {
                Thread.sleep(IDLE_SLEEP_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /** 找到包含播放点的切片下标；播放点在首段之前时返回 0 */
    private int indexAt(long posUs) {
        int index = 0;
        for (int i = 0; i < mSegments.size(); i++) {
            if (mSegments.get(i).relativeStartTimeUs <= posUs) {
                index = i;
            } else {
                break;
            }
        }
        return index;
    }

    private void cacheSegment(HlsMediaPlaylist.Segment segment) {
        String url = resolve(segment.url);
        if (url == null || mDone.contains(url)) return;
        Uri uri = Uri.parse(url);
        DataSpec spec = segment.byteRangeLength == C.LENGTH_UNSET
                ? new DataSpec(uri)
                : new DataSpec(uri, segment.byteRangeOffset, segment.byteRangeLength, null);
        CacheWriter writer = new CacheWriter(mCacheDataSource, spec, null, null);
        mCurrentWriter = writer;
        try {
            writer.cache();
            mDone.add(url);
            Log.w("SeekDiag", "prefetched " + segment.relativeStartTimeUs / 1000000L + "s " + url);
        } catch (IOException e) {
            // 网络失败：不记入已完成，下一轮再试
            Log.w("SeekDiag", "prefetch failed " + segment.relativeStartTimeUs / 1000000L + "s " + e);
        } finally {
            mCurrentWriter = null;
        }
    }

    private String resolve(String url) {
        if (url == null) return null;
        if (url.startsWith("http://") || url.startsWith("https://")) return url;
        try {
            return new URI(mBaseUri).resolve(url).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
