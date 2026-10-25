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
        // Apply latency defaults first; source-specific options below retain priority.
        // Keep packet buffering and full stream discovery for unstable sources/subtitles.
        mMediaPlayer.setOption(4, "skip-calc-frame-rate", 1L);
        mMediaPlayer.setOption(4, "next-high-water-mark-ms", 500L);
        mMediaPlayer.setOption(4, "last-high-water-mark-ms", 2000L);
        // Only takes effect when a source enables accurate seeking; avoid the native 5s wait.
        mMediaPlayer.setOption(4, "accurate-seek-timeout", 1000L);
        IJKCode codecTmp = this.codec == null ? ApiConfig.getInstance().getCurrentIJKCode() : this.codec;
        LinkedHashMap<String, String> options = codecTmp.getOption();
        if (options != null) {
            for (String key : options.keySet()) {
                String value = options.get(key);
                String[] opt = key.split("\\|");
                int category = Integer.parseInt(opt[0].trim());
                String name = opt[1].trim();
                try {
                    long valLong = Long.parseLong(value);
                    mMediaPlayer.setOption(category, name, valLong);
                } catch (Exception e) {
                    mMediaPlayer.setOption(category, name, value);
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
