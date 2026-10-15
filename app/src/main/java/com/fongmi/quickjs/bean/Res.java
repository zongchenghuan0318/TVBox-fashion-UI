package com.fongmi.quickjs.bean;

import android.text.TextUtils;

import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Res {

    @SerializedName("code")
    private Integer code;
    @SerializedName("buffer")
    private Integer buffer;
    @SerializedName("content")
    private String content;
    @SerializedName("headers")
    private JsonElement headers;

    public static Res objectFrom(String json) {
        try {
            return new Gson().fromJson(json, Res.class);
        } catch (Exception e) {
            return new Res();
        }
    }

    public int getCode() {
        return code == null ? 200 : code;
    }

    public int getBuffer() {
        return buffer == null ? 0 : buffer;
    }

    public String getContent() {
        return TextUtils.isEmpty(content) ? "" : content;
    }

    private JsonElement getHeaders() {
        return headers;
    }

    public Map<String, String> getHeader() {
        try {
            return Json.toMap(getHeaders());
        } catch (Exception e) {
            return null;
        }
    }

    public String getContentType() {
        Map<String, String> header = getHeader();
        if (header == null) return "application/octet-stream";
        List<String> keys = Arrays.asList("Content-Type", "content-type");
        for (String key : keys) if (header.containsKey(key) && header.get(key) != null) return header.get(key);
        return "application/octet-stream";
    }

    public ByteArrayInputStream getStream() {
        if (getBuffer() == 2) return new ByteArrayInputStream(Util.decode(getContent()));
        return new ByteArrayInputStream(getContent().getBytes());
    }
}
