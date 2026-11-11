package com.github.tvbox.osc.api;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.widget.Toast;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.ui.activity.UiHelper;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.MyFilesManager;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.SPDataUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;

import java.net.IDN;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ApiConfig {

    /*
      配置文件链接地址，可以自行更改
     */
    public static final String DEFAULT_URL = "https://tv.xn--yhqu5zs87a.top";

    public static final String PRE_KEY_CONFIG = "pre_key_config";
    private static final String STARTUP_CONFIG_CACHE = "startup_config_cache";
    private Runnable pendingStartupRefresh;

    public synchronized void invalidateStartupConfigCache(SPDataUtil data) {
        pendingStartupRefresh = null;
        data.saveStringValue(STARTUP_CONFIG_CACHE, "");
    }

    public void refreshStartupCaches() {
        Runnable refresh;
        synchronized (this) {
            refresh = pendingStartupRefresh;
            pendingStartupRefresh = null;
        }
        if (refresh != null) new Thread(refresh, "refresh-config-cache").start();
        spiderLoader.refreshCachedJar();
    }

    private synchronized void saveStartupConfig(SPDataUtil data, String address,
                                                VerifyResult result, String previousCache) {
        if (!previousCache.equals(data.getStringValue(STARTUP_CONFIG_CACHE, ""))) return;
        JsonObject cache = new JsonObject();
        cache.addProperty("address", address);
        cache.addProperty("url", result.url);
        cache.addProperty("json", result.json);
        data.saveStringValue(STARTUP_CONFIG_CACHE, cache.toString());
    }
    public static String current_siteUrl= "";
    private static ApiConfig instance = null;
    private final LinkedHashMap<String, SiteBean> mSiteBeanMap;
    private final SearchSitePreferences searchSites;
    private ParseBean mDefaultParseBean;
    private JsonObject pendingParsers;
    private final LiveSourceConfig liveSources = new LiveSourceConfig();
    private final List<ParseBean> mParseBeanList;
    private List<String> mVipFlagList;
    private List<IJKCode> mIjkCodes;
    private String mSpider = null;
    private final SpiderLoader spiderLoader = new SpiderLoader();
    private volatile boolean bInit = false;
    private String apiUrl;
    private boolean m_bParsed = false;

    public static ApiConfig getInstance() {
        if (instance == null) {
            synchronized (ApiConfig.class) {
                if (instance == null) {
                    instance = new ApiConfig();
                }
            }
        }
        return instance;
    }

    private ApiConfig() {
        mSiteBeanMap = new LinkedHashMap<>();
        searchSites = new SearchSitePreferences(mSiteBeanMap);
        mParseBeanList = new ArrayList<>();
    }

    /** 当前置顶的点播配置地址；用户没有设置过时使用默认置顶，取消置顶后存为空串 */
    public static String getPinnedConfig() {
        return Hawk.get(HawkConfig.API_PINNED_CONFIG, HawkConfig.DEFAULT_API_PINNED_CONFIG);
    }

    public void loadSitesFile(Activity activity, LoadConfigCallback callback) {
        L.d("loadSitesFile start");
        Context ctx = activity.getBaseContext();
        apiUrl = Hawk.get(HawkConfig.API_URL, "");
        SPDataUtil spDataUtil = new SPDataUtil(ctx);
        String configContent = spDataUtil.getStringValue(PRE_KEY_CONFIG);
        new Thread() {
            @Override
            public void run() {
                super.run();
                m_bParsed = false;
                List<String> candidates = new ArrayList<>();
                if (!NullUtil.isNull(apiUrl)) candidates.add(apiUrl);
                ArrayList<String> savedConfigs = Hawk.get(HawkConfig.API_SAVED_CONFIGS, new ArrayList<String>());
                for (String savedUrl : savedConfigs) {
                    if (!NullUtil.isNull(savedUrl) && !candidates.contains(savedUrl)) candidates.add(savedUrl);
                }
                for (String defaultUrl : HawkConfig.DEFAULT_API_SAVED_CONFIGS) {
                    if (!candidates.contains(defaultUrl)) candidates.add(defaultUrl);
                }
                if (!candidates.contains(DEFAULT_URL)) candidates.add(DEFAULT_URL);
                // 用户明确保存的配置地址优先于置顶地址；置顶只作为未配置时的默认选择
                String pinnedConfig = ApiConfig.getPinnedConfig();
                if (NullUtil.isNull(apiUrl) && !NullUtil.isNull(pinnedConfig)) {
                    candidates.remove(pinnedConfig);
                    candidates.add(0, pinnedConfig);
                }

                String startupCache = spDataUtil.getStringValue(STARTUP_CONFIG_CACHE, "");
                String preferred = candidates.get(0);
                if (!startupCache.isEmpty()) {
                    JsonObject cache;
                    try {
                        cache = new Gson().fromJson(startupCache, JsonObject.class);
                    } catch (RuntimeException e) {
                        cache = null;
                    }
                    if (cache != null && preferred.equals(DefaultConfig.safeJsonString(cache, "address", ""))) {
                        current_siteUrl = DefaultConfig.safeJsonString(cache, "url", preferred);
                        parseSiteFile(current_siteUrl, DefaultConfig.safeJsonString(cache, "json", ""));
                        if (m_bParsed) {
                            apiUrl = preferred;
                            L.d("loadSitesFile 使用启动缓存");
                            // Refresh the next launch's cache without replacing active sources.
                            synchronized (ApiConfig.this) {
                                pendingStartupRefresh = () -> {
                                    VerifyResult result = verifyConfigWithRetry(preferred, "", CONFIG_STARTUP_TIMEOUT_MS);
                                    if (result.ok) saveStartupConfig(spDataUtil, preferred, result, startupCache);
                                };
                            }
                            callback.success();
                            return;
                        }
                    }
                }

                CandidateVerify verified = raceStartupCandidates(candidates, CONFIG_STARTUP_TIMEOUT_MS);
                if (verified != null) {
                    VerifyResult result = verified.result;
                    apiUrl = verified.address;
                    current_siteUrl = result.url;
                    parseSiteFile(result.url, result.json);
                    if (m_bParsed) saveStartupConfig(spDataUtil, verified.address, result, startupCache);
                    if (!verified.address.equals(Hawk.get(HawkConfig.API_URL, ""))) {
                        Hawk.put(HawkConfig.API_URL, verified.address);
                        spDataUtil.saveStringValue(PRE_KEY_CONFIG, result.json);
                        L.w("loadSitesFile 已自动切换可用配置 url=" + verified.address);
                    }
                    callback.success();
                    return;
                }

                if (!NullUtil.isNull(configContent) && UiHelper.isJson(configContent)) {
                    L.w("loadSitesFile 所有配置源均不可用，使用本地缓存");
                    current_siteUrl = "user config";
                    parseSiteFile(apiUrl, configContent);
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "配置源暂时无法连接，已使用本地缓存", Toast.LENGTH_LONG).show());
                } else {
                    L.w("loadSitesFile 所有已保存配置源均不可用，且没有可用缓存");
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "默认及已保存的配置源均无法连接", Toast.LENGTH_LONG).show());
                }
                callback.success();
            }
        }.start();
    }

    /**
     * Applies an already verified configuration immediately instead of running the
     * startup candidate selection again. This is used when the user changes the
     * configuration while the home activity is still alive.
     */
    public void applySitesFile(String apiUrl, String jsonStr, LoadConfigCallback callback) {
        new Thread(() -> {
            synchronized (ApiConfig.this) {
                pendingStartupRefresh = null;
                this.apiUrl = apiUrl;
                current_siteUrl = apiUrl;
                m_bParsed = false;
                parseSiteFile(apiUrl, jsonStr);
                if (m_bParsed && apiUrl.equals(Hawk.get(HawkConfig.API_URL, ""))) {
                    SPDataUtil data = new SPDataUtil(App.getInstance());
                    saveStartupConfig(data, apiUrl, new VerifyResult(true, "", jsonStr, apiUrl),
                            data.getStringValue(STARTUP_CONFIG_CACHE, ""));
                }
            }
            callback.success();
        }, "config-runtime-reload").start();
    }

    /** 候选配置的校验结果，index 是它在候选列表里的优先级（0 为置顶的点播配置） */
    private static final class CandidateVerify {
        final int index;
        final String address;
        final VerifyResult result;

        CandidateVerify(int index, String address, VerifyResult result) {
            this.index = index;
            this.address = address;
            this.result = result;
        }
    }

    /**
     * 启动阶段并行校验候选配置。
     * <p>
     * 串行逐个校验时最坏耗时是「候选数 × 3 次尝试 × 单次超时」，一个置顶源不通就要等十几秒，
     * 后面几个都不通时启动会长时间停在加载页。这里改成所有候选同时发出、每个地址只试一次：
     * 置顶源优先，它成功就用它；它失败则用其余源里最先成功的那个，最坏耗时约等于一次超时。
     * 单地址重试只保留在手动校验配置的路径上。
     */
    private CandidateVerify raceStartupCandidates(List<String> candidates, int timeoutMs) {
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) {
            String only = candidates.get(0);
            VerifyResult result = verifyConfigOnce(only, timeoutMs);
            if (result.ok) return new CandidateVerify(0, only, result);
            L.w("loadSitesFile 配置源不可用 url=" + only + " reason=" + result.message);
            return null;
        }
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "config-startup-verify");
            thread.setDaemon(true);
            return thread;
        };
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, candidates.size()), factory);
        ExecutorCompletionService<CandidateVerify> completion = new ExecutorCompletionService<>(pool);
        List<Future<CandidateVerify>> futures = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            final int index = i;
            final String address = candidates.get(i);
            futures.add(completion.submit(() ->
                    new CandidateVerify(index, address, verifyConfigOnce(address, timeoutMs))));
        }
        CandidateVerify preferred = null;
        CandidateVerify fallback = null;
        try {
            for (int i = 0; i < candidates.size(); i++) {
                CandidateVerify got = completion.take().get();
                if (got.index == 0) {
                    preferred = got;
                    if (got.result.ok) return got;
                } else if (got.result.ok) {
                    if (fallback == null) fallback = got;
                } else {
                    L.w("loadSitesFile 配置源不可用 url=" + got.address + " reason=" + got.result.message);
                }
                // 置顶源已经失败，有备选就收工，不必再等剩下的候选
                if (preferred != null && fallback != null) return fallback;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            L.w("loadSitesFile 并行校验异常 " + e.getMessage());
        } finally {
            for (Future<CandidateVerify> future : futures) {
                future.cancel(true);
            }
            pool.shutdownNow();
        }
        if (preferred != null && !preferred.result.ok) {
            L.w("loadSitesFile 置顶配置不可用 url=" + preferred.address + " reason=" + preferred.result.message);
        }
        return fallback;
    }

    /**
     * 统一 UA。该点播配置接口与爬虫 jar 都按 UA 分流：请求头里含 okhttp 才返回 JSON 配置体 /
     * 爬虫 jar，浏览器 UA 只会拿到 FongMi 下载页 HTML。而 OkGoHelper 给 OkGo 加了浏览器
     * 公共头（accept + Chrome UA），aar 的 HttpDoRequest.doGetRequestInOkGo 不带 UA 覆盖，
     * 所以必须自己带上 okhttp UA 请求。
     */
    public static final String UA_OKHTTP = "okhttp/3.12.1";
    static final int HTTP_CONNECT_TIMEOUT_MS = 5000;
    // 启动时逐个校验配置源的单次超时：比直播源更短，全部不通时不让用户等太久
    private static final int CONFIG_STARTUP_TIMEOUT_MS = 4000;

    /**
     * HTTP 请求结果：code 为响应码（-1 表示请求异常），body 为响应体，error 为异常描述
     */
    public static class HttpResult {
        public final int code;
        public final String body;
        public final String error;

        HttpResult(int code, String body, String error) {
            this.code = code;
            this.body = body == null ? "" : body;
            this.error = error == null ? "" : error;
        }

        public boolean ok() {
            return code >= 200 && code < 300 && !NullUtil.isNull(body);
        }
    }

    /**
     * 用 OkGo 已配置好的 OkHttpClient 直接 GET（绕开 OkGo 的浏览器公共头），带出响应码与异常信息
     */
    public static HttpResult httpGetWithOkhttpUA(String url) {
        return httpGetWithOkhttpUA(url, 0);
    }

    static HttpResult httpGetWithOkhttpUA(String url, int timeoutMs) {
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", UA_OKHTTP)
                .build();
        try {
            okhttp3.OkHttpClient client = OkGo.getInstance().getOkHttpClient();
            if (timeoutMs > 0) {
                client = client.newBuilder()
                        .connectTimeout(Math.min(timeoutMs, HTTP_CONNECT_TIMEOUT_MS), TimeUnit.MILLISECONDS)
                        .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                        .build();
            }
            okhttp3.Response response = client.newCall(request).execute();
            try {
                if (!response.isSuccessful()) {
                    L.w("getWithOkhttpUA 失败 code=" + response.code() + " url=" + url);
                }
                String body = response.body() == null ? "" : response.body().string();
                return new HttpResult(response.code(), body, "");
            } finally {
                response.close();
            }
        } catch (Throwable th) {
            L.w("getWithOkhttpUA 异常 " + th.getMessage() + " url=" + url);
            return new HttpResult(-1, "", th.getMessage() == null ? th.toString() : th.getMessage());
        }
    }

    /**
     * 用 OkGo 已配置好的 OkHttpClient 直接 GET（绕开 OkGo 的浏览器公共头），返回响应体字符串
     */
    public static String getWithOkhttpUA(String url) {
        HttpResult result = httpGetWithOkhttpUA(url);
        return result.ok() ? result.body : "";
    }

    /**
     * 用 OkGo 已配置好的 OkHttpClient 直接 GET，可自定义请求头与超时。
     * 豆瓣接口按出口 IP 风控，需要带上来源页 Referer 与固定的 bid Cookie；
     * aar 的 HttpDoRequest.doGetRequestInOkGo 传不进这两个头，所以这里自己发请求。
     */
    public static String getWithHeaders(String url, java.util.Map<String, String> headers, int timeoutMs) {
        okhttp3.Request.Builder builder = new okhttp3.Request.Builder().url(url);
        if (headers != null) {
            for (java.util.Map.Entry<String, String> entry : headers.entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }
        }
        okhttp3.OkHttpClient client = OkGo.getInstance().getOkHttpClient();
        if (timeoutMs > 0) {
            //newBuilder 复用同一个连接池与线程池，只改超时，开销很小
            client = client.newBuilder()
                    .connectTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .readTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .callTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                    .build();
        }
        try {
            okhttp3.Response response = client.newCall(builder.build()).execute();
            try {
                if (!response.isSuccessful()) {
                    L.w("getWithHeaders 失败 code=" + response.code() + " url=" + url);
                    return null;
                }
                return response.body() == null ? null : response.body().string();
            } finally {
                response.close();
            }
        } catch (Throwable th) {
            L.w("getWithHeaders 异常 " + th.getMessage() + " url=" + url);
            return null;
        }
    }

    /**
     * 地址归一化：域名部分含中文等非 ASCII 字符时转成 punycode（如 http://肥猫.net/ →
     * http://xn--z7x900a.net/），否则 okhttp 的 Request.Builder 会直接抛 "unexpected url"。
     */
    public static String normalizeUrl(String raw) {
        if (NullUtil.isNull(raw)) return raw;
        String url = raw.trim();
        int schemeIdx = url.indexOf("://");
        if (schemeIdx < 0) return url;
        int authStart = schemeIdx + 3;
        int authEnd = url.length();
        for (int i = authStart; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                authEnd = i;
                break;
            }
        }
        String authority = url.substring(authStart, authEnd);
        int at = authority.lastIndexOf('@');
        String userInfo = at >= 0 ? authority.substring(0, at + 1) : "";
        String hostPort = at >= 0 ? authority.substring(at + 1) : authority;
        String host = hostPort;
        String port = "";
        int colon = hostPort.lastIndexOf(':');
        if (colon >= 0 && hostPort.indexOf(']') < 0) {
            host = hostPort.substring(0, colon);
            port = hostPort.substring(colon);
        }
        boolean ascii = true;
        for (int i = 0; i < host.length(); i++) {
            if (host.charAt(i) > 127) {
                ascii = false;
                break;
            }
        }
        if (ascii) return url;
        try {
            String puny = IDN.toASCII(host);
            return url.substring(0, authStart) + userInfo + puny + port + url.substring(authEnd);
        } catch (Exception e) {
            L.w("normalizeUrl 失败 host=" + host);
            return url;
        }
    }

    /**
     * 配置校验结果（只在本地校验，不写 ApiConfig 单例，旧配置零改动）
     */
    public static class VerifyResult {
        public final boolean ok;
        public final String message;  // 展示给用户的成功提示或失败原因
        public final String json;     // 校验通过后的配置体
        public final String url;      // 校验用到的配置地址
        public boolean connectionFailed; // 是否属于连接层失败（可重试）

        VerifyResult(boolean ok, String message, String json, String url) {
            this.ok = ok;
            this.message = message;
            this.json = json == null ? "" : json;
            this.url = url == null ? "" : url;
        }
    }

    public static VerifyResult verifyConfig(String rawUrl, String inlineContent) {
        return ConfigVerifier.verifyConfig(rawUrl, inlineContent);
    }

    public static VerifyResult verifyConfigWithRetry(String rawUrl, String inlineContent, int timeoutMs) {
        return ConfigVerifier.verifyConfigWithRetry(rawUrl, inlineContent, timeoutMs);
    }

    /** 启动竞速用：只尝试一次，不在这个地址上重试 */
    private static VerifyResult verifyConfigOnce(String rawUrl, int timeoutMs) {
        return ConfigVerifier.verifyConfigOnce(rawUrl, "", timeoutMs);
    }

    /**
     * 加载配置里的 spider jar（csp 爬虫包）
     * <p>
     * 原实现走 aar 的 Downloader(FileDownloader)，落盘路径取自 MyFilesManager.getLogPath()，
     * 即公共外部存储 /storage/emulated/0/<pkg>/log/，必须先拿到 WRITE_EXTERNAL_STORAGE；
     * 未授权时下载必然失败（IOException: Create parent directory failed），随后回退到内置
     * assets/tvbox.jar —— 而该内置包只有 2 个条目、不含任何 csp_* 站点类，于是配置里 45 个
     * csp_* 站点全都拿不到实现类，首页站点探测（ApiConfig.initHomeBean）无一成功，左栏只剩「首页」。
     * <p>
     * 现改为下载到应用私有目录 files/spider（不需要任何权限，电视端同样可用），
     * 且所有分支都保证 callback.success() 触发一次，避免调用方链路卡住。
     */
    public void loadJar(Context cxt, boolean useCache, String spider, LoadConfigCallback callback) {
        spiderLoader.loadJar(cxt, useCache, spider, callback);
    }

    private synchronized void parseSiteFile(String apiUrl, String jsonStr) {
        if (NullUtil.isNull(jsonStr)) return;
        if (m_bParsed) return;
        L.d("parseSiteFile() start");
        try {
            mSiteBeanMap.clear();
            mParseBeanList.clear();
            mDefaultParseBean = null;
            pendingParsers = null;
            AdBlocker.clear();
            spiderLoader.clear();
            JsonObject jsonObject = new Gson().fromJson(jsonStr, JsonObject.class);
            mSpider = DefaultConfig.safeJsonString(jsonObject, "spider", "");
            L.d("mSpider=" + mSpider);
            // 远端站点源
            if (jsonObject.has("sites") && jsonObject.get("sites").isJsonArray()) {
                for (JsonElement opt : jsonObject.get("sites").getAsJsonArray()) {
                    try {
                        if (!opt.isJsonObject()) continue;
                        JsonObject obj = opt.getAsJsonObject();
                        String siteKey = DefaultConfig.safeJsonString(obj, "key", "");
                        if (NullUtil.isNull(siteKey)) continue;//空key不要
                        SiteBean bean = new SiteBean();
                        bean.setKey(siteKey);
                        bean.setName(DefaultConfig.safeJsonString(obj, "name", siteKey));
                        bean.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
                        bean.setApi(DefaultConfig.safeJsonString(obj, "api", ""));
                        bean.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
                        bean.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
                        bean.setFilterable(DefaultConfig.safeJsonInt(obj, "filterable", 1));
                        bean.setPlayerUrl(DefaultConfig.safeJsonString(obj, "playUrl", ""));
                        bean.setExt(DefaultConfig.safeJsonString(obj, "ext", ""));
                        //部分源的类不在全局 spider 里，配置用站点级 jar 单独提供（如 csp_AppRJ 在 feimao1004.jar）
                        bean.setJar(DefaultConfig.safeJsonString(obj, "jar", ""));
                        bean.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
                        mSiteBeanMap.put(siteKey, bean);
                    } catch (Throwable th) {
                        L.w("parseSiteFile skip invalid site: " + th.getMessage());
                    }
                }
            }

            // 配置文件里多是盘搜类站点，补上内置的主流采集源并排到最前（分类页/搜索用它们）
            addExtraSites();

            // 需要使用vip解析的flag
            mVipFlagList = DefaultConfig.safeJsonStringList(jsonObject, "flags");
            pendingParsers = new JsonObject();
            if (jsonObject.has("parses")) pendingParsers.add("parses", jsonObject.get("parses"));
            // 直播源
            liveSources.deferConfiguredLives(apiUrl, jsonObject);
            // 广告地址
            JsonArray ads =  jsonObject.getAsJsonArray("ads");
            if(null != ads) {
                for (JsonElement host : ads) {
                    AdBlocker.addAdHost(host.getAsString());
                }
            }
            // IJK解码配置
            boolean foundOldSelect = false;
            String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, HawkConfig.IJK_CODEC_DEFAULT);
            mIjkCodes = new ArrayList<>();
            // ijk 键是可选的（本点播配置就没有），缺失时不能直接 getAsJsonArray 否则 NPE
            JsonArray ijkArr = jsonObject.getAsJsonArray("ijk");
            for (JsonElement opt : ijkArr == null ? new JsonArray() : ijkArr) {
                JsonObject obj = (JsonObject) opt;
                String name = obj.get("group").getAsString();
                LinkedHashMap<String, String> baseOpt = new LinkedHashMap<>();
                for (JsonElement cfg : obj.get("options").getAsJsonArray()) {
                    JsonObject cObj = (JsonObject) cfg;
                    String key = cObj.get("category").getAsString() + "|" + cObj.get("name").getAsString();
                    String val = cObj.get("value").getAsString();
                    baseOpt.put(key, val);
                }
                IJKCode codec = new IJKCode();
                codec.setName(name);
                codec.setOption(baseOpt);
                if (name.equals(ijkCodec) || TextUtils.isEmpty(ijkCodec)) {
                    codec.selected(true);
                    ijkCodec = name;
                    foundOldSelect = true;
                } else {
                    codec.selected(false);
                }
                mIjkCodes.add(codec);
                L.d("IJK name=" + codec.getName());
            }
            if (mIjkCodes.isEmpty()) {
                mIjkCodes.addAll(getDefaultIjkCodes(ijkCodec));
                foundOldSelect = true;
            } else if (!foundOldSelect && mIjkCodes.size() > 0) {
                if (HawkConfig.IJK_CODEC_DEFAULT.equals(ijkCodec)) {
                    IJKCode hardwareCodec = createHardwareCodec();
                    hardwareCodec.selected(true);
                    mIjkCodes.add(0, hardwareCodec);
                } else {
                    mIjkCodes.get(0).selected(true);
                }
            }
        } catch (Exception e) {
            L.e(e);
        }
        searchSites.invalidateSearchSiteOrderCache();
        m_bParsed = true;
    }

    private synchronized void ensureParsers() {
        if (pendingParsers == null) return;
        JsonObject jsonObject = pendingParsers;
        pendingParsers = null;
        // 解析地址
        if (jsonObject.has("parses") && jsonObject.get("parses").isJsonArray()) {
            for (JsonElement opt : jsonObject.get("parses").getAsJsonArray()) {
                try {
                    if (!opt.isJsonObject()) continue;
                    JsonObject obj = opt.getAsJsonObject();
                    ParseBean pb = new ParseBean();
                    pb.setName(DefaultConfig.safeJsonString(obj, "name", ""));
                    pb.setUrl(DefaultConfig.safeJsonString(obj, "url", ""));
                    String ext = obj.has("ext") && obj.get("ext").isJsonObject() ? obj.get("ext").getAsJsonObject().toString() : "";
                    pb.setExt(ext);
                    pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
                    if (!TextUtils.isEmpty(pb.getUrl())) {
                        mParseBeanList.add(pb);
                        L.d(pb.getName() + "\t" + pb.getUrl());
                    }
                } catch (Throwable th) {
                    L.w("parseSiteFile skip invalid parse: " + th.getMessage());
                }
            }
        }
        // 获取默认解析
        if (!NullUtil.isNull(mParseBeanList)) {
            String defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
            if (!TextUtils.isEmpty(defaultParse)) {
                for (ParseBean pb : mParseBeanList) {
                    if (pb.getName().equals(defaultParse)) {
                        setDefaultParse(pb);
                    }
                }
            }
            if (mDefaultParseBean == null) {
                setDefaultParse(mParseBeanList.get(0));
            }
        }
    }

    public void restoreConfiguredLives() {
        liveSources.restoreConfiguredLives();
    }

    public void loadLiveSources(String sourceUrls, LiveSourceCallback callback) {
        liveSources.loadLiveSources(sourceUrls, callback);
    }

    /** Loads URL, inline TVBox JSON, and inline M3U content in priority order. */
    public void loadLiveSourceContents(List<String> sourceContents, LiveSourceCallback callback) {
        liveSources.loadLiveSourceContents(sourceContents, callback);
    }

    public void loadLives(JsonArray livesArray) {
        liveSources.loadLives(livesArray);
    }

    public String getmSpider() {
        if (null == mSpider) return "";
        return mSpider;
    }

    public Spider getCSP(SiteBean sourceBean) {
        return spiderLoader.getCSP(sourceBean);
    }

    /**
     * 下载并加载站点级 jar（配置里 site.jar 字段），返回本地 jar 路径。
     * 同一 url 只允许一个线程下载，其余线程在锁上等它完成——否则并发线程会在 jar 就绪前查类，
     * 全部落到"类找不到"的空结果上。结果按 url 缓存，失败缓存空串避免反复重试。
     */
    public String preloadSiteJar(String jarWithMd5) {
        return spiderLoader.preloadSiteJar(jarWithMd5);
    }

    public Object[] proxyLocal(Map param) {
        return spiderLoader.proxyLocal(param);
    }

    public JSONObject jarExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return spiderLoader.jarExt(key, jxs, url);
    }

    public JSONObject jarExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return spiderLoader.jarExtMix(flag, key, name, jxs, url);
    }

    public interface LoadConfigCallback {
        void success();
    }

    public interface LiveSourceCallback {
        void success(int loadedSources, int failedSources);
        void error(String message);
    }

    public interface InitHomeCallback {
        void success();
    }

    public synchronized void setDefaultParse(ParseBean parseBean) {
        ensureParsers();
        if (this.mDefaultParseBean != null) {
            this.mDefaultParseBean.setDefault(false);
        }
        this.mDefaultParseBean = parseBean;
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName());
        parseBean.setDefault(true);
    }

    public synchronized ParseBean getDefaultParse() {
        ensureParsers();
        return mDefaultParseBean;
    }

    public List<SiteBean> getSiteBeanList() {
        return new ArrayList<>(mSiteBeanMap.values());
    }

    /**
     * 用户设置的不参与搜索的站点 key 列表。
     * 存“被取消勾选”的 key：缺省为空 → 全部站点参与搜索，
     * 这样配置源更新后新增的站点会自动参与，不会漏掉。
     */
    public ArrayList<String> getSearchSiteDisabled() {
        return searchSites.getSearchSiteDisabled();
    }

    public void setSearchSiteDisabled(List<String> keys) {
        searchSites.setSearchSiteDisabled(keys);
    }

    /** 该站点是否被允许参与搜索（未设置或未被取消勾选 → 允许） */
    public boolean isSearchSiteEnabled(String key) {
        return searchSites.isSearchSiteEnabled(key);
    }

    /**
     * 用户在「搜索源管理」里勾选搜索源的先后顺序，勾选顺序即所有搜索界面的搜索与展示顺序。
     * 未登记过顺序的源（配置新增或默认启用）排在最后。
     */
    public ArrayList<String> getSearchSiteOrder() {
        return searchSites.getSearchSiteOrder();
    }

    public void setSearchSiteOrder(List<String> keys) {
        searchSites.setSearchSiteOrder(keys);
    }

    /**
     * 默认搜索源顺序（用户没自定义时使用）：名字命中 DefaultConfig.SEARCH_SITE_PRIORITY
     * 关键词的源排在最前（文采┃秒播 > 非凡┃影视），其余保持站点表顺序。
     */
    public ArrayList<String> getDefaultSearchSiteOrder() {
        return searchSites.getDefaultSearchSiteOrder();
    }

    /** 用户自定义过顺序就尊重用户，否则返回默认顺序（文采秒播 > 非凡影视 > 其余） */
    public ArrayList<String> getSearchSiteOrderOrDefault() {
        return searchSites.getSearchSiteOrderOrDefault();
    }

    /** 参与搜索的站点，按用户勾选顺序排列；没排过序的源接在最后并保持站点表原顺序 */
    public List<SiteBean> getOrderedSearchSiteList() {
        return searchSites.getOrderedSearchSiteList();
    }

    public void invalidateSearchSiteOrderCache() {
        searchSites.invalidateSearchSiteOrderCache();
    }

    /** 站点在搜索结果里的排序键：勾选顺序在前，未登记的按站点表顺序接在最后（O(1) 缓存查询，消除排序时的遍历开销） */
    public int getSearchSiteOrderIndex(String key) {
        return searchSites.getSearchSiteOrderIndex(key);
    }

    public synchronized List<ParseBean> getmParseBeanList() {
        ensureParsers();
        return mParseBeanList;
    }

    public List<String> getmVipFlagList() {
        return mVipFlagList;
    }

    public SiteBean getHomeBean() {
        String userKey = Hawk.get(HawkConfig.USER_HOME_API, "");
        SiteBean userHomeBean = getSiteBean(userKey);//Map有空key，会有冲突
        if (userHomeBean != null) {
            //用户设置的最优先
            L.d("  return userHomeBean; ");
            return userHomeBean;
        }

        String homeKey = Hawk.get(HawkConfig.HOME_API, "");
        SiteBean homeBean = getSiteBean(homeKey);
        if (homeBean != null) {
            //自动设置的其次
            L.d("   return homeBean;");
            return homeBean;
        }
        List<SiteBean> beans = new ArrayList<>(mSiteBeanMap.values());
        if (!NullUtil.isNull(beans)) {
            //都没有设置
            L.d("  beans.get(0);");
            return beans.get(0);
        } else {
            return new SiteBean();
        }
    }

    public void setHomeBean(SiteBean bean) {
        L.d("setHomeBean= "+bean.getKey() + "\t"+bean.getName());
        L.w("setHomeBean key=" + bean.getKey() + " name=" + bean.getName() + " type=" + bean.getType() + " api=" + bean.getApi());
        Hawk.put(HawkConfig.HOME_API, bean.getKey());
    }

    public void setUserHomeBean(SiteBean bean) {
        Hawk.put(HawkConfig.USER_HOME_API, bean.getKey());
    }

    public void initHomeBean(InitHomeCallback callback) {
        String userHomeKey = Hawk.get(HawkConfig.USER_HOME_API, "");
        SiteBean userHomeBean = getSiteBean(userHomeKey);
        if (!NullUtil.isNull(userHomeKey) && null != userHomeBean) {
            //用户设置过home，直接返回
            L.d("initHomeBean userHomeBean callback.success() ");
            callback.success();
            return;
        }
        //已自动选定过、且是正常影视站点时沿用，避免每次启动重新抢答导致左栏分类变化
        String homeKey = Hawk.get(HawkConfig.HOME_API, "");
        SiteBean cachedHomeBean = getSiteBean(homeKey);
        //老缓存可能是盘搜类站点（分类页点进去全是网盘资源），此时不沿用，按新顺序（采集源已排最前）重新探测
        boolean cachedOk = null != cachedHomeBean && cachedHomeBean.isSearchable()
                && !(hasCollectBean() && !isCollectBean(cachedHomeBean));
        if (cachedOk) {
            L.w("initHomeBean 沿用已选首页源 " + homeKey);
            callback.success();
            return;
        }
        //自动设置home
        bInit = false;
        List<SiteBean> beans = new ArrayList<>(mSiteBeanMap.values());
        final ExecutorService homeExecutor = !NullUtil.isNull(beans) ? Executors.newFixedThreadPool(3) : null;
        final SiteBean lastBean = cachedHomeBean;
        final AtomicBoolean homeInitCompleted = new AtomicBoolean(false);
        final Runnable completeHomeInit = new Runnable() {
            @Override
            public void run() {
                if (!homeInitCompleted.compareAndSet(false, true)) return;
                selectHomeBeanOnce(lastBean);
                OkGo.getInstance().cancelTag("_initHome");
                if (homeExecutor != null) {
                    try {
                        homeExecutor.shutdownNow();
                    } catch (Throwable ignored) {
                    }
                }
                callback.success();
            }
        };
        //探测最多等待 6 秒；有旧源则沿用，无旧源也结束启动等待。
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(6000);
                } catch (Throwable ignored) {
                }
                L.w("initHomeBean 探测超时，结束启动等待");
                completeHomeInit.run();
            }
        }).start();
        if (!NullUtil.isNull(beans)) {
            for (int i = 0; i < beans.size(); i++) {
                L.d("initHomeBean i=" + i);
                SiteBean bean = beans.get(i);
                // searchable=0 的都是工具/配置类站点（我的云盘、本地文件、说明等），不适合当首页源
                if (!bean.isSearchable()) {
                    L.d("initHomeBean skip " + bean.getKey() + " searchable=0");
                    continue;
                }
                homeExecutor.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (bInit) return;//初始过，不再继续
                        int type = bean.getType();
                        if (type == 3) {
                            Spider sp = ApiConfig.getInstance().getCSP(bean);
                            String sortJson = sp == null ? null : sp.homeContent(true);
                            // 至少要有 2 个分类才算影视站点；只有 1 个分类的通常是云盘/配置类站点
                            if (classCount(sortJson) >= 2) {
                                if (!selectHomeBeanOnce(bean)) return;
                                completeHomeInit.run();
                            }
                        } else {
                            OkGo.<String>get(bean.getApi())
                                    .tag("_initHome")
                                    .execute(new AbsCallback<String>() {
                                        @Override
                                        public String convertResponse(okhttp3.Response response) throws Throwable {
                                            if (response.body() != null) {
                                                return response.body().string();
                                            } else {
                                                L.d(" initHomeBean() 网络请求失败");
                                                throw new IllegalStateException("网络请求失败");
                                            }
                                        }

                                        @Override
                                        public void onSuccess(Response<String> response) {
                                            String res = response.body();
                                            L.d("initHomeBean " + UiHelper.getBeanStr(bean) + "  xml=" + res);
                                            if (classCount(res) >= 2 || (!NullUtil.isNull(res) && UiHelper.xmlHasClasses(res))) {
                                                if (!selectHomeBeanOnce(bean)) return;
                                                completeHomeInit.run();
                                            }
                                        }

                                        @Override
                                        public void onError(Response<String> response) {
                                            super.onError(response);
                                        }
                                    });
                        }
                    }
                });
            }
        }
    }

    /**
     * 统计站点返回的分类数量：JSON 取 "class"/"classes" 数组长度，解析失败返回 0
     */
    private synchronized boolean selectHomeBeanOnce(SiteBean bean) {
        if (bInit) return false;
        bInit = true;
        if (bean != null) setHomeBean(bean);
        return true;
    }

    private static int classCount(String json) {
        if (NullUtil.isNull(json)) return 0;
        try {
            JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            JsonElement cls = obj.has("class") ? obj.get("class") : obj.get("classes");
            if (cls != null && cls.isJsonArray()) return cls.getAsJsonArray().size();
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /**
     * 补上内置的主流采集源（DefaultConfig.EXTRA_SITES）并排到站点表最前面。
     * 作用有两个：一是首页源自动探测按站点表顺序并发抢答，采集源是纯 HTTP 接口、响应最快，
     * 排在前面就能稳定选中它们而不是盘搜站点；二是「设置 → 首页数据源」和搜索源管理里可选用。
     * 配置里已有同 api 的站点不重复添加，只把它提到最前面。
     */
    private void addExtraSites() {
        LinkedHashMap<String, SiteBean> merged = new LinkedHashMap<>();
        for (String[] def : DefaultConfig.EXTRA_SITES) {
            SiteBean exist = null;
            for (SiteBean bean : mSiteBeanMap.values()) {
                if (sameApi(bean.getApi(), def[2])) {
                    exist = bean;
                    break;
                }
            }
            if (exist != null) {
                merged.put(exist.getKey(), exist);
                continue;
            }
            SiteBean bean = new SiteBean();
            bean.setKey(def[0]);
            bean.setName(def[1]);
            bean.setType(1);
            bean.setApi(def[2]);
            bean.setSearchable(1);
            bean.setQuickSearch(1);
            bean.setFilterable(1);
            L.d("addExtraSites add " + UiHelper.getBeanStr(bean));
            merged.put(def[0], bean);
        }
        merged.putAll(mSiteBeanMap);
        mSiteBeanMap.clear();
        mSiteBeanMap.putAll(merged);
        StringBuilder order = new StringBuilder();
        for (SiteBean bean : mSiteBeanMap.values()) {
            if (order.length() > 0) order.append(",");
            order.append(bean.getKey());
            if (order.length() > 120) break;
        }
        L.w("addExtraSites sites=" + mSiteBeanMap.size() + " first=" + order);
        // 内置采集源上线后，清掉一次旧的自动首页源缓存：老缓存多半是盘搜站点，
        // 分类页点进去全是网盘资源，需要按新顺序（采集源优先）重新探测一次
        if (Hawk.get(HawkConfig.EXTRA_SITE_VER, 0) < 1) {
            Hawk.put(HawkConfig.HOME_API, "");
            Hawk.put(HawkConfig.EXTRA_SITE_VER, 1);
        }
        searchSites.migrate(PINNED_SEARCH_SITE_KEY);
    }

    /** 默认置顶的搜索源 key（配置里的「💥粉猪┃秒播💥」） */
    public static final String PINNED_SEARCH_SITE_KEY = "AppV7_粉猪追剧";

    static boolean sameApi(String api1, String api2) {
        if (NullUtil.isNull(api1) || NullUtil.isNull(api2)) return false;
        return api1.trim().replaceAll("/+$", "").equalsIgnoreCase(api2.trim().replaceAll("/+$", ""));
    }

    /** 采集源（CMS http 接口）：片库是正规影视站的，不是盘搜出来的网盘资源 */
    private static boolean isCollectBean(SiteBean bean) {
        return bean != null && (bean.getType() == 0 || bean.getType() == 1);
    }

    private boolean hasCollectBean() {
        for (SiteBean bean : mSiteBeanMap.values()) {
            if (isCollectBean(bean) && bean.isSearchable()) return true;
        }
        return false;
    }

    public SiteBean getSiteBean(String key) {
        if (!mSiteBeanMap.containsKey(key)) {
            return null;
        }
        return mSiteBeanMap.get(key);
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return liveSources.getChannelGroupList();
    }

    private IJKCode createHardwareCodec() {
        // 硬解码 (Hardware decoding with low-latency and anti-stutter defaults)
        IJKCode hardCodec = new IJKCode();
        hardCodec.setName(HawkConfig.IJK_CODEC_DEFAULT);
        LinkedHashMap<String, String> hardOpt = new LinkedHashMap<>();
        hardOpt.put("4|mediacodec", "1");
        hardOpt.put("4|mediacodec-auto-rotate", "1");
        hardOpt.put("4|mediacodec-handle-resolution-change", "1");
        hardOpt.put("4|mediacodec-hevc", "1");
        hardOpt.put("4|framedrop", "1");
        hardOpt.put("4|packet-buffering", "1");
        hardOpt.put("4|first-high-water-mark-ms", "1200");
        hardOpt.put("4|next-high-water-mark-ms", "2500");
        hardOpt.put("4|last-high-water-mark-ms", "300000");  // 点播最多向前预读 5 分钟
        hardOpt.put("4|max-buffer-size", "100663296");       // 96MB 内存水库
        hardOpt.put("4|accurate-seek-timeout", "300");
        hardOpt.put("1|fflags", "fastseek");
        hardOpt.put("1|dns_cache_clear", "1");
        hardOpt.put("1|reconnect", "1");
        hardCodec.setOption(hardOpt);
        return hardCodec;
    }

    public List<IJKCode> getDefaultIjkCodes(String selectedName) {
        List<IJKCode> list = new ArrayList<>();
        IJKCode hardCodec = createHardwareCodec();

        // 软解码 (Software decoding with low-latency and anti-stutter defaults)
        IJKCode softCodec = new IJKCode();
        softCodec.setName("软解码");
        LinkedHashMap<String, String> softOpt = new LinkedHashMap<>();
        softOpt.put("4|mediacodec", "0");
        softOpt.put("4|framedrop", "1");
        softOpt.put("4|packet-buffering", "1");
        softOpt.put("4|first-high-water-mark-ms", "1200");
        softOpt.put("4|next-high-water-mark-ms", "2500");
        softOpt.put("4|last-high-water-mark-ms", "300000");  // 点播最多向前预读 5 分钟
        softOpt.put("4|max-buffer-size", "100663296");       // 96MB 内存水库
        softOpt.put("4|accurate-seek-timeout", "300");
        softOpt.put("1|fflags", "fastseek");
        softOpt.put("1|dns_cache_clear", "1");
        softOpt.put("1|reconnect", "1");
        softCodec.setOption(softOpt);

        if (!"软解码".equals(selectedName)) {
            hardCodec.selected(true);
            softCodec.selected(false);
        } else {
            softCodec.selected(true);
            hardCodec.selected(false);
        }
        list.add(hardCodec);
        list.add(softCodec);
        return list;
    }

    public List<IJKCode> getmIjkCodes() {
        if (NullUtil.isNull(mIjkCodes) || mIjkCodes.isEmpty()) {
            mIjkCodes = getDefaultIjkCodes(Hawk.get(HawkConfig.IJK_CODEC, HawkConfig.IJK_CODEC_DEFAULT));
        }
        return mIjkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, HawkConfig.IJK_CODEC_DEFAULT);
        return getIJKCodec(codeName);
    }

    public IJKCode getIJKCodec(String name) {
        if (NullUtil.isNull(mIjkCodes) || mIjkCodes.isEmpty()) {
            mIjkCodes = getDefaultIjkCodes(name);
        }
        for (IJKCode code : mIjkCodes) {
            if (code != null && code.getName() != null && code.getName().equals(name)) {
                if (HawkConfig.IJK_CODEC_DEFAULT.equals(name) && (code.getOption() == null
                        || !"1".equals(code.getOption().get("4|mediacodec")))) {
                    return createHardwareCodec();
                }
                return code;
            }
        }
        if (HawkConfig.IJK_CODEC_DEFAULT.equals(name)) return createHardwareCodec();
        return mIjkCodes.isEmpty() ? new IJKCode() : mIjkCodes.get(0);
    }
}
