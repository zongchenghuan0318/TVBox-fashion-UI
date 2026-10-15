package com.github.tvbox.osc.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;

/**
 * Gson 工具类。
 * 对齐目标 v6.1.7：保留 {@code Map<String, Object>} 与
 * {@code List<? extends Map<String, Object>>} 两种 TypeToken 用法。
 */
public class GsonUtil {
    private static final Gson gson = new Gson();

    public static Gson getGson() {
        return gson;
    }

    private static class GsonToMaps {
        static final Type type = new TypeToken<Map<String, Object>>() {
        }.getType();
    }

    private static class GsonToListMaps {
        static final Type type = new TypeToken<List<? extends Map<String, Object>>>() {
        }.getType();
    }

    public static Map<String, Object> jsonToMap(String json) {
        return gson.fromJson(json, GsonToMaps.type);
    }

    public static List<Map<String, Object>> jsonToListMaps(String json) {
        return gson.fromJson(json, GsonToListMaps.type);
    }
}
