package com.fongmi.quickjs.utils;

import com.fongmi.quickjs.bean.Req;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.Util;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Connect {

    private static final String CONTENT_TYPE = "Content-Type";

    public static Call to(String url, Req req) {
        return client(req).newCall(getRequest(url, req, getHeaders(req)));
    }

    private static OkHttpClient client(Req req) {
        int timeout = req.getTimeout();
        return new OkHttpClient.Builder()
                .followRedirects(req.isRedirect())
                .followSslRedirects(req.isRedirect())
                .connectTimeout(timeout, TimeUnit.MILLISECONDS)
                .readTimeout(timeout, TimeUnit.MILLISECONDS)
                .writeTimeout(timeout, TimeUnit.MILLISECONDS)
                .build();
    }

    private static Headers getHeaders(Req req) {
        Map<String, String> safe = new HashMap<>();
        Map<String, String> header = req.getHeader();
        if (header != null) for (String key : header.keySet()) if (key != null && header.get(key) != null) safe.put(key, header.get(key));
        return Headers.of(safe);
    }

    public static JSObject success(QuickJSContext ctx, Req req, Response res) {
        try {
            JSObject jsObject = ctx.createNewJSObject();
            JSObject jsHeader = ctx.createNewJSObject();
            setHeader(ctx, res, jsHeader);
            jsObject.setProperty("code", res.code());
            jsObject.setProperty("headers", jsHeader);
            byte[] bytes = res.body() == null ? new byte[0] : res.body().bytes();
            if (req.getBuffer() == 0) jsObject.setProperty("content", new String(bytes, req.getCharset()));
            if (req.getBuffer() == 1) jsObject.setProperty("content", JSUtil.toArray(ctx, bytes));
            if (req.getBuffer() == 2) jsObject.setProperty("content", Util.base64(bytes));
            if (req.getBuffer() == 3) jsObject.setProperty("content", bytes);
            res.close();
            return jsObject;
        } catch (Exception e) {
            return error(ctx);
        }
    }

    public static JSObject error(QuickJSContext ctx) {
        JSObject jsObject = ctx.createNewJSObject();
        JSObject jsHeader = ctx.createNewJSObject();
        jsObject.setProperty("headers", jsHeader);
        jsObject.setProperty("content", "");
        jsObject.setProperty("code", "");
        return jsObject;
    }

    private static Request getRequest(String url, Req req, Headers headers) {
        if (req.getMethod().equalsIgnoreCase("post")) {
            return new Request.Builder().url(url).headers(headers).post(getPostBody(req, headers.get(CONTENT_TYPE))).build();
        } else if (req.getMethod().equalsIgnoreCase("header")) {
            return new Request.Builder().url(url).headers(headers).head().build();
        } else {
            return new Request.Builder().url(url).headers(headers).get().build();
        }
    }

    private static RequestBody getPostBody(Req req, String contentType) {
        if (req.getData() != null && "json".equals(req.getPostType())) return getJsonBody(req);
        if (req.getData() != null && "form".equals(req.getPostType())) return getFormBody(req);
        if (req.getData() != null && "form-data".equals(req.getPostType())) return getFormDataBody(req);
        if (req.getBody() != null && contentType != null) return RequestBody.create(MediaType.get(contentType), req.getBody());
        return RequestBody.create(MediaType.get("text/plain; charset=utf-8"), new byte[0]);
    }

    private static RequestBody getJsonBody(Req req) {
        return RequestBody.create(MediaType.get("application/json; charset=utf-8"), req.getData().toString());
    }

    private static RequestBody getFormBody(Req req) {
        FormBody.Builder builder = new FormBody.Builder();
        Map<String, String> params = Json.toMap(req.getData());
        if (params != null) for (String key : params.keySet()) builder.add(key, params.get(key) == null ? "" : params.get(key));
        return builder.build();
    }

    private static RequestBody getFormDataBody(Req req) {
        String boundary = "--dio-boundary-" + new SecureRandom().nextInt(42949) + new SecureRandom().nextInt(67296);
        MultipartBody.Builder builder = new MultipartBody.Builder(boundary).setType(MultipartBody.FORM);
        Map<String, String> params = Json.toMap(req.getData());
        if (params != null) for (String key : params.keySet()) builder.addFormDataPart(key, params.get(key) == null ? "" : params.get(key));
        return builder.build();
    }

    private static void setHeader(QuickJSContext ctx, Response res, JSObject object) {
        for (Map.Entry<String, List<String>> entry : res.headers().toMultimap().entrySet()) {
            if (entry.getValue().size() == 1) object.setProperty(entry.getKey(), entry.getValue().get(0));
            if (entry.getValue().size() >= 2) object.setProperty(entry.getKey(), JSUtil.toArray(ctx, entry.getValue()));
        }
    }
}
