package com.github.tvbox.osc.player;

import android.content.Context;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;

import java.util.LinkedHashMap;
import java.util.Map;

import xyz.doikki.videoplayer.ijk.IjkPlayer;

public class IjkMediaPlayer extends IjkPlayer {

    /** 播放中最多向前缓冲的时长：尽量往前预读，5 分钟内不设更细的停顿 */
    private static final long BUFFER_AHEAD_MS = 300000L;
    /** 缓冲区字节下限，也是低内存设备上的实际取值 */
    private static final long BUFFER_BYTES_MIN = 64 * 1024 * 1024L;
    /** 缓冲区字节上限，避免单个播放器把堆吃满 */
    private static final long BUFFER_BYTES_CAP = 256 * 1024 * 1024L;

    private IJKCode codec = null;

    public IjkMediaPlayer(Context context, IJKCode codec) {
        super(context);
        this.codec = codec;
    }

    @Override
    public void setOptions() {
        super.setOptions();
        // 网络选项先设置，片源的自定义选项仍可覆盖。
        mMediaPlayer.setOption(1, "dns_cache_clear", 1L);
        mMediaPlayer.setOption(1, "reconnect", 1L);
        mMediaPlayer.setOption(1, "reconnect_streamed", 1L);
        mMediaPlayer.setOption(1, "reconnect_delay_max", 3L);
        mMediaPlayer.setOption(4, "framedrop", 1L);              // 丢帧保音画同步
        mMediaPlayer.setOption(4, "skip-calc-frame-rate", 1L);
        // 核心缓冲配置：确保首播起播快且之后不出现“刚播一下就微卡”的蓄水断层
        mMediaPlayer.setOption(4, "packet-buffering", 1L);
        mMediaPlayer.setOption(4, "first-high-water-mark-ms", 1200L); // 首播起播蓄水 1.2 秒（约 0.2~0.3 秒下载完成，快速出画）
        mMediaPlayer.setOption(4, "next-high-water-mark-ms", 2500L);  // 重新缓冲时蓄水 2.5 秒，避免 500ms 短暂循环卡顿
        mMediaPlayer.setOption(4, "last-high-water-mark-ms", BUFFER_AHEAD_MS); // 播放中最多向前缓冲 5 分钟，能拉多少拉多少
        mMediaPlayer.setOption(4, "max-buffer-size", bufferBytes()); // 字节上限随堆大小动态放宽，避免先于时长上限截断
        mMediaPlayer.setOption(4, "accurate-seek-timeout", 1000L);

        IJKCode codecTmp = this.codec == null ? ApiConfig.getInstance().getCurrentIJKCode() : this.codec;
        if (codecTmp != null) {
            LinkedHashMap<String, String> options = codecTmp.getOption();
            if (options != null) {
                for (String key : options.keySet()) {
                    String value = options.get(key);
                    if (key == null || value == null) continue;
                    String[] opt = key.split("\\|");
                    if (opt.length < 2) continue;
                    try {
                        int category = Integer.parseInt(opt[0].trim());
                        String name = opt[1].trim();
                        try {
                            long valLong = Long.parseLong(value.trim());
                            mMediaPlayer.setOption(category, name, valLong);
                        } catch (Exception e) {
                            mMediaPlayer.setOption(category, name, value);
                        }
                    } catch (Exception ignored) {
                    }
                }
                // 片源配置可能把缓冲调得很小，这里只对“小于安全值”的项兜底，配置更大的值仍然生效
                applyBufferFloor(options, "4|last-high-water-mark-ms", BUFFER_AHEAD_MS);
                applyBufferFloor(options, "4|max-buffer-size", bufferBytes());
            }
        }
    }

    /** 按堆上限动态放宽缓冲字节数：取堆的 1/4，夹在 64MB~256MB 之间（已开启 largeHeap） */
    private static long bufferBytes() {
        long quarter = Runtime.getRuntime().maxMemory() / 4;
        return Math.max(BUFFER_BYTES_MIN, Math.min(BUFFER_BYTES_CAP, quarter));
    }

    /** 配置值低于安全值时改回安全值；配置未提供或更大则保持现状 */
    private void applyBufferFloor(LinkedHashMap<String, String> options, String key, long floor) {
        String value = options.get(key);
        if (value == null) return;
        try {
            if (Long.parseLong(value.trim()) >= floor) return;
        } catch (Exception ignored) {
        }
        String[] opt = key.split("\\|");
        try {
            mMediaPlayer.setOption(Integer.parseInt(opt[0].trim()), opt[1].trim(), floor);
        } catch (Exception ignored) {
        }
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        try {
            if (path != null && !TextUtils.isEmpty(path) && path.startsWith("rtsp")) {
                mMediaPlayer.setOption(1, "infbuf", 1);
                mMediaPlayer.setOption(1, "rtsp_transport", "tcp");
                mMediaPlayer.setOption(1, "rtsp_flags", "prefer_tcp");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        super.setDataSource(path, headers);
    }
}
