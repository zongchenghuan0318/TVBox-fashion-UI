package com.github.tvbox.osc.util;

import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AdBlocker {
    private static final List<String> AD_HOSTS = new CopyOnWriteArrayList<>();

    public static void clear() {
        AD_HOSTS.clear();
    }

    public static void addAdHost(String host) {
        if (host != null && !host.trim().isEmpty()) {
            String lower = host.trim().toLowerCase();
            if (!AD_HOSTS.contains(lower)) {
                AD_HOSTS.add(lower);
            }
        }
    }

    public static boolean isAd(String url) {
        if (url == null || AD_HOSTS.isEmpty()) {
            return false;
        }
        String lower = url.toLowerCase();
        for (String adHost : AD_HOSTS) {
            if (lower.contains(adHost)) {
                return true;
            }
        }
        return false;
    }

    public static WebResourceResponse createEmptyResource() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream("".getBytes()));
    }

}