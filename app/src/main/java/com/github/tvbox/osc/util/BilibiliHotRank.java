package com.github.tvbox.osc.util;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class BilibiliHotRank {
    public static final List<String> SEASON_TYPES = Collections.unmodifiableList(Arrays.asList("5", "2", "1", "4"));
    private static final String URL = "https://api.bilibili.com/pgc/web/rank/list?day=3&season_type=";
    private static final int MAX_VIDEOS = 40;

    private BilibiliHotRank() {
    }

    public static List<Movie.Video> load(String seasonType, int timeoutMs) {
        return parse(ApiConfig.getWithHeaders(URL + seasonType, null, timeoutMs));
    }

    static List<Movie.Video> parse(String json) {
        List<Movie.Video> videos = new ArrayList<>();
        if (json == null || json.isEmpty()) return videos;
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) return videos;
        JsonElement result = root.getAsJsonObject().get("result");
        if (result == null || !result.isJsonObject()) return videos;
        JsonElement list = result.getAsJsonObject().get("list");
        if (list == null || !list.isJsonArray()) return videos;
        JsonArray items = list.getAsJsonArray();
        for (int i = 0; i < items.size() && videos.size() < MAX_VIDEOS; i++) {
            JsonElement item = items.get(i);
            if (!item.isJsonObject()) continue;
            JsonObject row = item.getAsJsonObject();
            String title = stringValue(row.get("title"));
            if (title == null || title.trim().isEmpty()) continue;
            Movie.Video video = new Movie.Video();
            video.name = title;
            video.note = "热榜 TOP" + (i + 1);
            String cover = stringValue(row.get("cover"));
            if (cover != null) {
                video.pic = cover.startsWith("http://") ? "https://" + cover.substring(7) : cover;
            }
            videos.add(video);
        }
        return videos;
    }

    private static String stringValue(JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()
                ? element.getAsString() : null;
    }
}
