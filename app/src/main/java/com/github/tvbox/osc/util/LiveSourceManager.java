package com.github.tvbox.osc.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.hawk.Hawk;
import android.net.Uri;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Persistent live-only source list. Runtime fields are intentionally not persisted. */
public final class LiveSourceManager {
    private static final String REMOVED_APTV_SOURCE =
            "https://raw.githubusercontent.com/Kimentanm/aptv/master/m3u/iptv.m3u";

    public static class Source {
        public String id = "";
        public String name = "";
        public String content = "";
        public boolean enabled = true;
        public boolean builtin;
        public transient String status = "未刷新";
        public transient String reason = "";
    }

    private static final Type TYPE = new TypeToken<ArrayList<Source>>() {}.getType();
    private LiveSourceManager() {}

    public static List<Source> getUserSources() {
        migrateIfNeeded();
        String value = Hawk.get(HawkConfig.LIVE_SOURCE_MANAGER, "");
        if (value == null || value.trim().isEmpty()) return new ArrayList<>();
        try {
            List<Source> list = new Gson().fromJson(value, TYPE);
            if (list == null) return new ArrayList<>();
            boolean changed = false;
            for (int i = list.size() - 1; i >= 0; i--) {
                if (key(REMOVED_APTV_SOURCE).equals(key(list.get(i).content))) {
                    list.remove(i);
                    changed = true;
                }
            }
            for (Source source : list) {
                String mappedName = defaultName(source.content, "");
                if (!mappedName.isEmpty()
                        && (source.name == null || source.name.trim().isEmpty()
                        || source.name.startsWith("直播源 "))) {
                    source.name = mappedName;
                    changed = true;
                }
            }
            if (changed) saveUserSources(list);
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static List<Source> getSources() {
        List<Source> sources = getUserSources();
        if (Hawk.get(HawkConfig.LIVE_SOURCE_UNIFIED, false)) return sources;
        Set<String> seen = new HashSet<>();
        for (Source source : sources) seen.add(key(source.content));
        for (Source source : builtIns()) {
            if (seen.add(key(source.content))) {
                source.name = "直播源 " + (sources.size() + 1);
                sources.add(source);
            }
        }
        saveUserSources(sources);
        Hawk.put(HawkConfig.LIVE_SOURCE_UNIFIED, true);
        return sources;
    }

    public static void saveUserSources(List<Source> sources) {
        Hawk.put(HawkConfig.LIVE_SOURCE_MANAGER, new Gson().toJson(sources));
    }

    public static void migrateIfNeeded() {
        String structured = Hawk.get(HawkConfig.LIVE_SOURCE_MANAGER, "");
        if (structured != null && !structured.trim().isEmpty()) return;
        String old = Hawk.get(HawkConfig.LIVE_SOURCE_URL, "");
        if (old == null || old.trim().isEmpty()) return;
        ArrayList<Source> result = new ArrayList<>();
        int index = 1;
        Set<String> seen = new HashSet<>();
        for (String line : old.split("\\r?\\n")) {
            String content = line.trim();
            if (content.isEmpty() || !seen.add(key(content))) continue;
            Source source = create(defaultName(content, "直播源 " + index++), content, true);
            result.add(source);
        }
        saveUserSources(result);
    }

    public static Source create(String name, String content, boolean enabled) {
        Source source = new Source();
        source.id = "user_" + System.nanoTime();
        source.name = name == null ? "" : name.trim();
        source.content = content == null ? "" : content.trim();
        source.enabled = enabled;
        return source;
    }

    public static String key(String content) {
        if (content == null) return "";
        String value = content.trim().replace("\r\n", "\n").replace('\r', '\n');
        if (value.startsWith("http://") || value.startsWith("https://")) {
            Uri uri = Uri.parse(value);
            return (uri.getHost() == null ? value : uri.getHost().toLowerCase()
                    + ":" + uri.getPort() + uri.getPath() + "?" + uri.getQuery()).trim();
        }
        return value;
    }

    public static List<Source> builtIns() {
        ArrayList<Source> result = new ArrayList<>();
        for (String line : HawkConfig.DEFAULT_LIVE_SOURCE_URLS.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            String content = line.trim();
            Source source = create(defaultName(content, "内置直播源"), content, true);
            source.builtin = true;
            source.id = "builtin_" + result.size();
            result.add(source);
        }
        return result;
    }

    public static String defaultName(String content, String fallback) {
        String normalized = key(content);
        if (normalized.equals(key("https://cdn.jsdelivr.net/gh/Guovin/iptv-api@gd/output/ipv4/result.m3u"))) {
            return "直播源7";
        }
        if (normalized.equals(key("https://cdn.jsdelivr.net/gh/Guovin/iptv-api@gd/output/result.m3u"))) {
            return "频道集合全国";
        }
        if (normalized.equals(key("https://cdn.jsdelivr.net/gh/Guovin/iptv-api@gd/output/ipv6/result.m3u"))) {
            return "频道集合ipv6";
        }
        if (normalized.equals(key("https://iptv-org.github.io/iptv/countries/cn.m3u"))) {
            return "基本无用，英文标题";
        }
        if (normalized.equals(key("sub.ottiptv.cc/huyayqk.m3u"))) {
            return "一起看2";
        }
        if (normalized.equals(key("193.123.86.190:14888/TV/iptv.php"))) {
            return "好用";
        }
        if (normalized.equals(key("sub.ottiptv.cc/douyuyqk.m3u"))) {
            return "一起看直播源";
        }
        return fallback;
    }

    public static String displayName(Source source, int index) {
        return source.name == null || source.name.trim().isEmpty()
                ? "直播源 " + (index + 1) : source.name.trim();
    }
}
