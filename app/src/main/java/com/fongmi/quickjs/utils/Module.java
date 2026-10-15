package com.fongmi.quickjs.utils;

import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.utils.Asset;
import com.github.tvbox.osc.base.App;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.ConcurrentHashMap;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Module {

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public static Module get() {
        return Loader.INSTANCE;
    }

    public String fetch(String name) {
        try {
            if (TextUtils.isEmpty(name)) return "";
            if (cache.containsKey(name)) return cache.get(name);
            if (name.startsWith("http")) cache.put(name, request(name));
            if (name.startsWith("assets")) cache.put(name, Asset.read(name));
            if (name.startsWith("lib/")) cache.put(name, Asset.read("js/" + name));
            String content = cache.get(name);
            return content == null ? "" : content;
        } catch (Exception e) {
            return "";
        }
    }

    public String cache(String name) {
        return fetch(name);
    }

    public String request(String url) {
        try {
            Response res = new OkHttpClient.Builder().build().newCall(new Request.Builder().url(url).build()).execute();
            byte[] data = res.body() == null ? new byte[0] : res.body().bytes();
            res.close();
            OooO00o(file(url), data);
            return new String(data, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    public byte[] bb(String content) {
        try {
            if (content == null) return new byte[0];
            int index = content.indexOf("//bb");
            String value = index == -1 ? content : content.substring(index + 4);
            return Base64.decode(value.trim(), Base64.DEFAULT | Base64.NO_WRAP);
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private File file(String url) {
        return new File(App.getInstance().getCacheDir(), Integer.toHexString(url.hashCode()) + ".js");
    }

    private static void OooO00o(File file, byte[] data) {
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(file);
            out.write(data);
        } catch (Exception ignored) {
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static class Loader {
        private static volatile Module INSTANCE = new Module();
    }
}
