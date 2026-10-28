package com.github.tvbox.osc.player;

import android.content.Context;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.IJKCode;

import java.util.LinkedHashMap;
import java.util.Map;

import xyz.doikki.videoplayer.ijk.IjkPlayer;

public class IjkMediaPlayer extends IjkPlayer {

    private IJKCode codec = null;

    public IjkMediaPlayer(Context context, IJKCode codec) {
        super(context);
        this.codec = codec;
    }

    @Override
    public void setOptions() {
        super.setOptions();
        // 1. 基础默认性能与缓冲调优（秒开与防卡顿）
        mMediaPlayer.setOption(1, "analyzeduration", 2000000L); // 2秒分析时长，大幅加速初始缓冲
        mMediaPlayer.setOption(1, "probesize", 1024 * 1024L);    // 1MB 探测大小
        mMediaPlayer.setOption(1, "http-detect-range-support", 0L); // 避免重复发送 HEAD 请求
        mMediaPlayer.setOption(1, "dns_cache_clear", 1L);
        mMediaPlayer.setOption(1, "reconnect", 1L);
        mMediaPlayer.setOption(1, "reconnect_streamed", 1L);
        mMediaPlayer.setOption(1, "reconnect_delay_max", 3L);
        mMediaPlayer.setOption(4, "max-buffer-size", 15 * 1024 * 1024L); // 15MB 适中缓冲，避免低端机 OOM
        mMediaPlayer.setOption(4, "min-frames", 2L);
        mMediaPlayer.setOption(4, "framedrop", 1L);              // 丢帧保音画同步
        mMediaPlayer.setOption(4, "start-on-prepared", 1L);
        mMediaPlayer.setOption(4, "skip-calc-frame-rate", 1L);
        mMediaPlayer.setOption(4, "next-high-water-mark-ms", 500L);
        mMediaPlayer.setOption(4, "last-high-water-mark-ms", 2000L);
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
            }
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
