package com.github.tvbox.osc.api;

import android.net.Uri;
import android.util.Base64;

import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.ui.activity.UiHelper;
import com.github.tvbox.osc.util.DefaultConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lcstudio.commonsurport.L;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.tvbox.osc.api.ApiConfig.HttpResult;
import com.github.tvbox.osc.api.ApiConfig.LiveSourceCallback;

import static com.github.tvbox.osc.api.ApiConfig.HTTP_CONNECT_TIMEOUT_MS;
import static com.github.tvbox.osc.api.ApiConfig.httpGetWithOkhttpUA;
import static com.github.tvbox.osc.api.ApiConfig.normalizeUrl;

final class LiveSourceConfig {
    private static final int LIVE_SOURCE_READ_TIMEOUT_MS = 7000;

    private final List<LiveChannelGroup> mLiveChannelGroupList = new ArrayList<>();
    private final List<LiveChannelGroup> mConfiguredLiveChannelGroupList = new ArrayList<>();
    private JsonObject pendingConfig;
    private String pendingConfigUrl;

    synchronized void deferConfiguredLives(String apiUrl, JsonObject config) {
        pendingConfig = new JsonObject();
        if (config.has("lives")) pendingConfig.add("lives", config.get("lives"));
        pendingConfigUrl = apiUrl;
        mLiveChannelGroupList.clear();
        mConfiguredLiveChannelGroupList.clear();
    }

    private synchronized void ensureConfiguredLives() {
        if (pendingConfig == null) return;
        JsonObject config = pendingConfig;
        pendingConfig = null;
        loadConfiguredLives(pendingConfigUrl, config);
        saveConfiguredLives();
    }

    void loadConfiguredLives(String apiUrl, JsonObject jsonObject) {
        mLiveChannelGroupList.clear();  //修复从后台切换重复加载频道列表
        try {
            String lives = jsonObject.get("lives").getAsJsonArray().toString();
            int index = lives.indexOf("proxy://");
            if (index != -1) {
                int endIndex = lives.lastIndexOf("\"");
                String url = lives.substring(index, endIndex);
                url = DefaultConfig.checkReplaceProxy(url);
                //clan
                String extUrl = Uri.parse(url).getQueryParameter("ext");
                if (extUrl != null && !extUrl.isEmpty()) {
                    String extUrlFix = new String(Base64.decode(extUrl, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                    if (extUrlFix.startsWith("clan://")) {
                        extUrlFix = UiHelper.clanContentFix(UiHelper.clanToAddress(apiUrl), extUrlFix);
                        extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                        url = url.replace(extUrl, extUrlFix);
                    }
                }
                LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
                liveChannelGroup.setGroupName(url);
                mLiveChannelGroupList.add(liveChannelGroup);
                L.d("live channel:" + liveChannelGroup.getGroupName());
            } else {
                loadLives(jsonObject.get("lives").getAsJsonArray());
            }
        } catch (Throwable th) {
           L.w(th.getMessage());
        }
    }

    void saveConfiguredLives() {
        mConfiguredLiveChannelGroupList.clear();
        mConfiguredLiveChannelGroupList.addAll(mLiveChannelGroupList);
    }

    synchronized List<LiveChannelGroup> getChannelGroupList() {
        ensureConfiguredLives();
        return mLiveChannelGroupList;
    }

    synchronized void restoreConfiguredLives() {
        ensureConfiguredLives();
        mLiveChannelGroupList.clear();
        mLiveChannelGroupList.addAll(mConfiguredLiveChannelGroupList);
    }

    void loadLiveSources(String sourceUrls, LiveSourceCallback callback) {
        List<String> values = new ArrayList<>();
        String raw = sourceUrls == null ? "" : sourceUrls.trim();
        if (raw.startsWith("{") || raw.startsWith("[") || raw.startsWith("#EXTM3U")) {
            values.add(raw);
        } else {
            for (String line : raw.split("\\r?\\n")) if (!line.trim().isEmpty()) values.add(line.trim());
        }
        loadLiveSourceContents(values, callback);
    }

    /** Loads URL, inline TVBox JSON, and inline M3U content in priority order. */
    void loadLiveSourceContents(List<String> sourceContents, LiveSourceCallback callback) {
        new Thread(() -> {
            ExecutorService executor = null;
            try {
                if (sourceContents == null || sourceContents.isEmpty()) {
                    throw new IllegalArgumentException("直播源地址为空");
                }
                List<String> uniqueSources = new ArrayList<>();
                Set<String> seen = new LinkedHashSet<>();
                for (String value : sourceContents) {
                    if (value == null || value.trim().isEmpty()) continue;
                    String content = value.trim();
                    String key = content.startsWith("{") || content.startsWith("[") || content.startsWith("#")
                            ? content : getLiveSourceDedupKey(normalizeUrl(content.startsWith("http") ? content : "http://" + content));
                    if (seen.add(key)) uniqueSources.add(content);
                }

                executor = Executors.newFixedThreadPool(Math.min(uniqueSources.size(), 5));
                List<Future<LiveSourceResult>> futures = new ArrayList<>();
                for (String sourceUrl : uniqueSources) {
                    futures.add(executor.submit(() -> fetchLiveSource(sourceUrl)));
                }

                JsonArray mergedLives = new JsonArray();
                List<String> errors = new ArrayList<>();
                int loadedSources = 0;
                for (int i = 0; i < futures.size(); i++) {
                    LiveSourceResult result;
                    try {
                        result = futures.get(i).get(
                                LIVE_SOURCE_READ_TIMEOUT_MS + HTTP_CONNECT_TIMEOUT_MS,
                                TimeUnit.MILLISECONDS);
                    } catch (Exception e) {
                        futures.get(i).cancel(true);
                        errors.add(uniqueSources.get(i) + "：" + e.getMessage());
                        continue;
                    }
                    if (result.error != null) {
                        errors.add(uniqueSources.get(i) + "：" + result.error);
                        continue;
                    }
                    mergeLiveGroups(mergedLives, result.lives);
                    loadedSources++;
                }
                if (mergedLives.size() == 0) {
                    throw new IllegalStateException(errors.isEmpty()
                            ? "所有直播源都没有可用频道"
                            : "没有直播源加载成功：" + errors.get(0));
                }
                loadLives(mergedLives);
                callback.success(loadedSources, errors.size());
            } catch (Exception e) {
                L.w("loadLiveSources 失败：" + e.getMessage());
                callback.error(e.getMessage() == null ? "未知错误" : e.getMessage());
            } finally {
                if (executor != null) {
                    executor.shutdownNow();
                }
            }
        }, "load-live-sources").start();
    }

    private LiveSourceResult fetchLiveSource(String sourceUrl) {
        String value = sourceUrl.trim();
        if (value.startsWith("{") || value.startsWith("[") || value.startsWith("#EXTM3U")) {
            try {
                JsonArray lives = parseLiveJson(value);
                if (lives == null) lives = parseM3uLives(value);
                if (lives == null || lives.size() == 0) throw new IllegalArgumentException("内联内容中没有频道");
                return new LiveSourceResult(lives, null);
            } catch (Exception e) {
                return new LiveSourceResult(null, e.getMessage() == null ? "内联内容解析失败" : e.getMessage());
            }
        }
        if (!value.startsWith("http://") && !value.startsWith("https://")) value = "http://" + value;
        sourceUrl = normalizeUrl(value);
        HttpResult response = httpGetWithOkhttpUA(sourceUrl, LIVE_SOURCE_READ_TIMEOUT_MS);
        if (!response.ok()) {
            String reason = response.error.isEmpty() ? "HTTP " + response.code : response.error;
            return new LiveSourceResult(null, reason);
        }

        try {
            JsonArray sourceLives;
            JsonArray lives = parseLiveJson(response.body);
            if (lives != null) {
                if (lives.size() == 0) {
                    throw new IllegalArgumentException("直播配置中没有频道分组");
                }
                sourceLives = new JsonArray();
                for (JsonElement group : lives) {
                    if (!group.isJsonObject()
                            || !group.getAsJsonObject().has("group")
                            || !group.getAsJsonObject().has("channels")
                            || !group.getAsJsonObject().get("channels").isJsonArray()) {
                        throw new IllegalArgumentException("直播 JSON 分组缺少 group 或 channels");
                    }
                    JsonObject copy = group.getAsJsonObject().deepCopy();
                    for (JsonElement channelElement : copy.getAsJsonArray("channels")) {
                        JsonObject channel = channelElement.getAsJsonObject();
                        JsonArray urls = channel.getAsJsonArray("urls");
                        if (urls == null) channel.add("urls", new JsonArray());
                    }
                    sourceLives.add(copy);
                }
            } else {
                sourceLives = parseM3uLives(response.body);
                if (sourceLives.size() == 0) {
                    throw new IllegalArgumentException("M3U 中未找到频道");
                }
            }
            return new LiveSourceResult(sourceLives, null);
        } catch (Exception e) {
            return new LiveSourceResult(null, e.getMessage() == null ? "直播配置解析失败" : e.getMessage());
        }
    }

    private static class LiveSourceResult {
        final JsonArray lives;
        final String error;

        LiveSourceResult(JsonArray lives, String error) {
            this.lives = lives;
            this.error = error;
        }
    }

    private JsonArray parseLiveJson(String content) {
        try {
            JsonElement root = new JsonParser().parse(content);
            if (root.isJsonArray()) return root.getAsJsonArray();
            if (root.isJsonObject() && root.getAsJsonObject().has("lives")
                    && root.getAsJsonObject().get("lives").isJsonArray()) {
                return root.getAsJsonObject().getAsJsonArray("lives");
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    private JsonArray parseM3uLives(String content) {
        Map<String, JsonArray> groups = new LinkedHashMap<>();
        String channelName = null;
        String groupName = null;
        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String rawLine;
            while ((rawLine = reader.readLine()) != null) {
                String line = rawLine.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#EXTINF")) {
                    int comma = line.lastIndexOf(',');
                    channelName = comma >= 0 ? line.substring(comma + 1).trim() : "";
                    groupName = getM3uAttribute(line, "group-title");
                    if (groupName.isEmpty()) groupName = "其他";
                } else if (!line.startsWith("#") && channelName != null && !channelName.isEmpty()) {
                    JsonArray channels = groups.get(groupName);
                    if (channels == null) {
                        channels = new JsonArray();
                        groups.put(groupName, channels);
                    }
                    JsonObject channel = new JsonObject();
                    channel.addProperty("name", channelName);
                    JsonArray urls = new JsonArray();
                    urls.add(line);
                    channel.add("urls", urls);
                    channels.add(channel);
                    channelName = null;
                    groupName = null;
                }
            }
        } catch (Exception e) {
            L.w("parseM3uLives error: " + e.getMessage());
        }
        JsonArray lives = new JsonArray();
        for (Map.Entry<String, JsonArray> entry : groups.entrySet()) {
            JsonObject group = new JsonObject();
            group.addProperty("group", entry.getKey());
            group.add("channels", entry.getValue());
            lives.add(group);
        }
        return lives;
    }

    private void mergeLiveGroups(JsonArray target, JsonArray source) {
        Map<String, JsonObject> groupsByName = new LinkedHashMap<>();
        for (JsonElement element : target) {
            JsonObject group = element.getAsJsonObject();
            groupsByName.put(group.get("group").getAsString(), group);
        }
        for (JsonElement element : source) {
            JsonObject sourceGroup = element.getAsJsonObject();
            String name = sourceGroup.get("group").getAsString().trim();
            JsonObject targetGroup = groupsByName.get(name);
            if (targetGroup == null) {
                targetGroup = new JsonObject();
                targetGroup.addProperty("group", name);
                targetGroup.add("channels", new JsonArray());
                target.add(targetGroup);
                groupsByName.put(name, targetGroup);
            }
            mergeLiveChannels(targetGroup.getAsJsonArray("channels"),
                    sourceGroup.getAsJsonArray("channels"));
        }
    }

    private void mergeLiveChannels(JsonArray target, JsonArray source) {
        Map<String, JsonObject> channelsByName = new LinkedHashMap<>();
        for (JsonElement element : target) {
            JsonObject channel = element.getAsJsonObject();
            channelsByName.put(channel.get("name").getAsString().trim().toLowerCase(), channel);
        }
        for (JsonElement element : source) {
            JsonObject sourceChannel = element.getAsJsonObject();
            String name = sourceChannel.get("name").getAsString().trim();
            String key = name.toLowerCase();
            JsonObject targetChannel = channelsByName.get(key);
            if (targetChannel == null) {
                targetChannel = sourceChannel.deepCopy();
                targetChannel.addProperty("name", name);
                target.add(targetChannel);
                channelsByName.put(key, targetChannel);
                continue;
            }

            JsonArray targetUrls = targetChannel.getAsJsonArray("urls");
            if (targetUrls == null) {
                targetUrls = new JsonArray();
                targetChannel.add("urls", targetUrls);
            }
            JsonArray sourceUrls = sourceChannel.getAsJsonArray("urls");
            if (sourceUrls == null) continue;
            Set<String> existingUrls = new LinkedHashSet<>();
            for (JsonElement url : targetUrls) {
                existingUrls.add(getLiveUrlKey(url.getAsString()));
            }
            for (JsonElement url : sourceUrls) {
                String value = url.getAsString();
                if (existingUrls.add(getLiveUrlKey(value))) targetUrls.add(value);
            }
        }
    }

    private String getLiveUrlKey(String value) {
        String[] parts = value.split("\\$", 2);
        return parts[0].trim();
    }

    private String getM3uAttribute(String line, String attribute) {
        Matcher matcher = Pattern.compile("(?:^|\\s)" + Pattern.quote(attribute)
                + "=\"([^\"]*)\"").matcher(line);
        return matcher.find() ? matcher.group(1).trim() : "";
    }

    private String getLiveSourceDedupKey(String url) {
        Uri uri = Uri.parse(url);
        return String.valueOf(uri.getHost()) + ":" + uri.getPort()
                + uri.getPath() + "?" + uri.getQuery();
    }

    synchronized void loadLives(JsonArray livesArray) {
        ensureConfiguredLives();
        L.d("loadLives()");
        mLiveChannelGroupList.clear();
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        for (JsonElement groupElement : livesArray) {
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
            liveChannelGroup.setGroupIndex(groupIndex);
            String groupName = ((JsonObject) groupElement).get("group").getAsString().trim();
            String[] splitGroupName = groupName.split("_", 2);
            liveChannelGroup.setGroupName(splitGroupName[0]);
            if (splitGroupName.length > 1) {
                liveChannelGroup.setGroupPassword(splitGroupName[1]);
            } else {
                liveChannelGroup.setGroupPassword("");
            }
            channelIndex = 0;
            for (JsonElement channelElement : ((JsonObject) groupElement).get("channels").getAsJsonArray()) {
                JsonObject obj = (JsonObject) channelElement;
                ArrayList<String> urls = DefaultConfig.safeJsonStringList(obj, "urls");
                L.d("urls="+urls);
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceUrls = new ArrayList<>();
                int sourceIndex = 1;
                for (String url : urls) {
                    L.d("url="+url);
                    String[] splitText = url.split("\\$", 2);
                    String sourceUrl = splitText[0].trim();
                    if (sourceUrl.isEmpty()) continue;
                    sourceUrls.add(sourceUrl);
                    if (splitText.length > 1) {
                        sourceNames.add(splitText[1]);
                    } else {
                        sourceNames.add("源" + Integer.toString(sourceIndex));
                    }

                    sourceIndex++;
                }
                if (sourceUrls.isEmpty()) continue;
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                liveChannelItem.setChannelName(obj.get("name").getAsString().trim());
                liveChannelItem.setChannelIndex(channelIndex++);
                liveChannelItem.setChannelNum(++channelNum);
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                liveChannelGroup.getLiveChannels().add(liveChannelItem);
            }
            if (!liveChannelGroup.getLiveChannels().isEmpty()) {
                mLiveChannelGroupList.add(liveChannelGroup);
                groupIndex++;
                L.d("live group name:"+liveChannelGroup.getGroupName());
            }
        }
    }
}
