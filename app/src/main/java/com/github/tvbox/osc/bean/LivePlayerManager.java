package com.github.tvbox.osc.bean;

import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.PlayerHelper;
import com.lcstudio.commonsurport.L;
import com.orhanobut.hawk.Hawk;

import org.json.JSONException;
import org.json.JSONObject;

import androidx.annotation.NonNull;
import xyz.doikki.videoplayer.player.VideoView;

public class LivePlayerManager {
    JSONObject defaultPlayerConfig = new JSONObject();
    JSONObject currentPlayerConfig;

    public void init(VideoView videoView) {
        try {
            int playType = Hawk.get(HawkConfig.PLAY_TYPE, HawkConfig.PLAY_TYPE_DEFAULT);
            // 直播以兼容性优先：默认仍走 IJK（flv/ts 等老流支持最好），
            // Exo 只用于点播的磁盘缓存场景；单个频道仍可在直播设置里单独切到 Exo。
            if (playType == HawkConfig.PLAY_TYPE_EXO) playType = HawkConfig.PLAY_TYPE_IJK;
            defaultPlayerConfig.put("pl", playType);
            defaultPlayerConfig.put("ijk", Hawk.get(HawkConfig.IJK_CODEC, "软解码"));
            defaultPlayerConfig.put("pr", Hawk.get(HawkConfig.PLAY_RENDER, HawkConfig.PLAY_RENDER_DEFAULT));
            // 直播默认画面比例为自适应的「默认」，频道仍可单独修改。
            defaultPlayerConfig.put("sc", VideoView.SCREEN_SCALE_DEFAULT);
            defaultPlayerConfig.put("scv", 2);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        getDefaultLiveChannelPlayer(videoView);
    }

    public void getDefaultLiveChannelPlayer(VideoView videoView) {
        PlayerHelper.updateCfg(videoView, defaultPlayerConfig);
        try {
            currentPlayerConfig = new JSONObject(defaultPlayerConfig.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getLiveChannelPlayer(VideoView videoView, String channelName) {
        L.d("getLiveChannelPlayer()");
        JSONObject playerConfig = Hawk.get(channelName, null);
        if (playerConfig == null) {
            if (!currentPlayerConfig.toString().equals(defaultPlayerConfig.toString()))
                getDefaultLiveChannelPlayer(videoView);
            return;
        }
        if (playerConfig.optInt("scv", 0) < 2) {
            try {
                playerConfig.put("sc", VideoView.SCREEN_SCALE_DEFAULT);
                playerConfig.put("scv", 2);
                Hawk.put(channelName, playerConfig);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (playerConfig.toString().equals(currentPlayerConfig.toString()))
            return;

        try {
            if (playerConfig.getInt("pl") == currentPlayerConfig.getInt("pl")
                    && playerConfig.getInt("pr") == currentPlayerConfig.getInt("pr")
                    && playerConfig.getString("ijk").equals(currentPlayerConfig.getString("ijk"))) {
                videoView.setScreenScaleType(playerConfig.getInt("sc"));
            } else {
                PlayerHelper.updateCfg(videoView, playerConfig);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        currentPlayerConfig = playerConfig;
    }

    public int getLivePlayerType() {
        int playerTypeIndex = 0;
        try {
            int playerType = currentPlayerConfig.getInt("pl");
            String ijkCodec = currentPlayerConfig.getString("ijk");
            switch (playerType) {
                case 0:
                    playerTypeIndex = 0;
                    break;
                case 1:
                    if (ijkCodec.equals("硬解码"))
                        playerTypeIndex = 1;
                    else
                        playerTypeIndex = 2;
                    break;
                case 2:
                    playerTypeIndex = 3;
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return playerTypeIndex;
    }

    public int getLivePlayerScale() {
        try {
            return currentPlayerConfig.getInt("sc");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void changeLivePlayerType(VideoView videoView, int playerType, String channelName) {
        JSONObject playerConfig = currentPlayerConfig;
        try {
            switch (playerType) {
                case 0:
                    playerConfig.put("pl", 0);
                    playerConfig.put("ijk", "软解码");
                    break;
                case 1:
                    playerConfig.put("pl", 1);
                    playerConfig.put("ijk", "硬解码");
                    break;
                case 2:
                    playerConfig.put("pl", 1);
                    playerConfig.put("ijk", "软解码");
                    break;
                case 3:
                    playerConfig.put("pl", 2);
                    playerConfig.put("ijk", "软解码");
                    break;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        PlayerHelper.updateCfg(videoView, playerConfig);

        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }

    public void changeLivePlayerScale(@NonNull VideoView videoView, int playerScale, String channelName){
        videoView.setScreenScaleType(playerScale);

        JSONObject playerConfig = currentPlayerConfig;
        try {
            playerConfig.put("sc", playerScale);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (playerConfig.toString().equals(defaultPlayerConfig.toString()))
            Hawk.delete(channelName);
        else
            Hawk.put(channelName, playerConfig);

        currentPlayerConfig = playerConfig;
    }
}