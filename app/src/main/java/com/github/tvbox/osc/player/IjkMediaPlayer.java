package com.github.tvbox.osc.player;

import android.content.Context;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;

import java.util.LinkedHashMap;
import java.util.Map;

import xyz.doikki.videoplayer.ijk.IjkPlayer;
import xyz.doikki.videoplayer.util.PlaybackBufferBudget;

public class IjkMediaPlayer extends IjkPlayer {

    private static final long BUFFER_AHEAD_MS = 120000L;
    private final int targetBufferBytes;

    private IJKCode codec = null;

    public IjkMediaPlayer(Context context, IJKCode codec) {
        super(context);
        this.codec = codec;
        targetBufferBytes = PlaybackBufferBudget.forDevice(context);
    }

    @Override
    public void setOptions() {
        super.setOptions();
        // 网络选项先设置，片源的自定义选项仍可覆盖。
        mMediaPlayer.setOption(1, "dns_cache_clear", 1L);
        mMediaPlayer.setOption(1, "reconnect", 1L);
        mMediaPlayer.setOption(1, "reconnect_streamed", 1L);
        mMediaPlayer.setOption(1, "reconnect_delay_max", 3L);
        mMediaPlayer.setOption(1, "fflags", "fastseek");              // 拖动时跳过冗余逐帧解码，快速定位关键帧
        mMediaPlayer.setOption(1, "buffer_size", 4 * 1024 * 1024L);   // 4MB TCP 接收窗口，跑满宽带吞吐量
        mMediaPlayer.setOption(1, "multiple_requests", 1L);           // 开启 HTTP 复用，避免切片频繁握手
        mMediaPlayer.setOption(1, "analyzeduration", 1500000L);       // 预读分析时间优化为 1.5 秒加速起播
        mMediaPlayer.setOption(1, "probesize", 2 * 1024 * 1024L);     // 探测大小提升至 2MB，保障 4K 高分辨率元数据快速嗅探
        mMediaPlayer.setOption(4, "framedrop", 1L);                  // 丢帧保音画同步
        mMediaPlayer.setOption(4, "soundtouch", 1L);                 // 倍速播放变速不变调
        mMediaPlayer.setOption(4, "skip-calc-frame-rate", 1L);
        // 核心缓冲配置：极速接上 + 120 秒后台长蓄水
        mMediaPlayer.setOption(4, "packet-buffering", 1L);
        mMediaPlayer.setOption(4, "first-high-water-mark-ms", 800L);  // 首播起播蓄水 0.8 秒，瞬间接上
        mMediaPlayer.setOption(4, "next-high-water-mark-ms", 2000L);  // 重新缓冲时蓄水 2.0 秒，快速恢复
        mMediaPlayer.setOption(4, "last-high-water-mark-ms", BUFFER_AHEAD_MS);
        mMediaPlayer.setOption(4, "max-buffer-size", Math.max(targetBufferBytes, 128 * 1024 * 1024)); // 128MB~256MB 超大包缓存
        mMediaPlayer.setOption(4, "accurate-seek-timeout", 200L);

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
                // Respect smaller source settings, but keep large buffers within the device budget.
                applyBufferCeiling(options, "4|last-high-water-mark-ms", BUFFER_AHEAD_MS);
                applyBufferCeiling(options, "4|max-buffer-size", targetBufferBytes);
            }
        }
    }

    private void applyBufferCeiling(LinkedHashMap<String, String> options, String key, long ceiling) {
        String value = options.get(key);
        if (value == null) return;
        try {
            long configured = Long.parseLong(value.trim());
            if (configured > 0 && configured <= ceiling) return;
        } catch (Exception ignored) {
        }
        String[] opt = key.split("\\|");
        try {
            mMediaPlayer.setOption(Integer.parseInt(opt[0].trim()), opt[1].trim(), ceiling);
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
