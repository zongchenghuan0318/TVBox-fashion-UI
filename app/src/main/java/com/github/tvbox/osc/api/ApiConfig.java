package com.github.tvbox.osc.api;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.Spider;
import com.github.catvod.utils.Util;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.LiveChannelItem;
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
import com.github.tvbox.osc.util.MD5;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.FileUtil;
import com.lcstudio.commonsurport.util.MyFilesManager;
import com.lcstudio.commonsurport.util.NullUtil;
import com.lcstudio.commonsurport.util.SPDataUtil;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;
import com.uisupport.update.Initer;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.IDN;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class ApiConfig {

    /*
      配置文件链接地址，可以自行更改
     */
    public static final String DEFAULT_URL = "https://tv.xn--yhqu5zs87a.top";

    public static final String PRE_KEY_CONFIG = "pre_key_config";
    public static String current_siteUrl= "";
    private static ApiConfig instance = null;
    private final LinkedHashMap<String, SiteBean> mSiteBeanMap;
    private ParseBean mDefaultParseBean;
    private final List<LiveChannelGroup> mLiveChannelGroupList;
    private final List<ParseBean> mParseBeanList;
    private List<String> mVipFlagList;
    private List<IJKCode> mIjkCodes;
    private String mSpider = null;
    /** 站点级 jar：jar url → 本地加载后的 jar 路径（加载失败存空串） */
    private final ConcurrentHashMap<String, String> mSiteJarPaths = new ConcurrentHashMap<>();
    /** 站点级 jar 的按 url 下载锁：避免并发线程重复下载同一个包 */
    private final ConcurrentHashMap<String, Object> mSiteJarLocks = new ConcurrentHashMap<>();
    private final BaseLoader mBaseLoader;
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
        mLiveChannelGroupList = new ArrayList<>();
        mParseBeanList = new ArrayList<>();
        mBaseLoader = new BaseLoader();
    }

    public void loadSitesFile(Activity activity, LoadConfigCallback callback) {
        L.d("loadSitesFile start");
        Context ctx = activity.getBaseContext();
        apiUrl = Hawk.get(HawkConfig.API_URL, "");
        SPDataUtil spDataUtil = new SPDataUtil(ctx);
        String configContent = spDataUtil.getStringValue(PRE_KEY_CONFIG);
        Initer.init(activity);
        new Thread() {
            @Override
            public void run() {
                super.run();
                m_bParsed = false;
                if (!NullUtil.isNull(configContent) && UiHelper.isJson(configContent)) {
                    //测试连接有配置内容
                    current_siteUrl = "user config";
                    parseSiteFile(apiUrl, configContent);
                    callback.success();
                }  else{
                    //对齐 v6.1.7：配置只能来自 Hawk api_url，无 assets 兜底
                    if (NullUtil.isNull(apiUrl)) {
                        L.w("loadSitesFile api_url 为空，无配置可加载");
                        callback.success();
                        return;
                    }
                    String siteUrl = apiUrl;
                    String configKey = "";
                    //单 URL 支持 "url;pk;configKey"，key 取第二段
                    if (siteUrl.contains(";pk;")) {
                        String[] parts = siteUrl.split(";pk;");
                        if (parts.length >= 2) {
                            configKey = parts[1];
                            siteUrl = parts[0];
                        }
                    }
                    if (siteUrl.contains("clan")) {
                        siteUrl = UiHelper.clanToAddress(siteUrl);
                    } else if (!siteUrl.contains("http")) {
                        siteUrl = "http://" + siteUrl;
                    }
                    //中文等非 ASCII 域名须先转 punycode，否则 okhttp 会直接抛 "unexpected url"
                    siteUrl = normalizeUrl(siteUrl);
                    //该配置接口按 UA 分流：只有 okhttp UA 才返回 JSON 配置体，浏览器 UA 只会拿到下载页 HTML
                    String r = getWithOkhttpUA(siteUrl);
                    L.w("loadSitesFile 下载 len=" + r.length() + " head="
                            + r.substring(0, Math.min(60, r.length())));
                    //对齐 v6.1.7：配置体解码（base64 / AES）
                    r = convert(r, configKey);
                    if (!NullUtil.isNull(r) && UiHelper.isJson(r)) {
                        current_siteUrl = siteUrl;
                        parseSiteFile(siteUrl, r);
                    } else {
                        L.w("loadSitesFile 配置下载或解析失败 url=" + siteUrl);
                    }
                    callback.success();
                }
            }
        }.start();
    }

    /**
     * 对齐 v6.1.7 的配置体解码（等价 com.androidx.o0O 的 convert(String,String)）：
     * 1) 已是 JSON 直接返回；
     * 2) 形如 "xxxxxxxx**" 前缀的 base64 体 → 去前 10 个字符后 base64 解码；
     * 3) 含 "2423"/"2324" 标记 → hex 解出 "$#...#$" 为 key、末 13 位为 iv，AES/CBC 解密；
     * 4) 否则若带 configKey → AES/ECB/PKCS7 解密（key 由 configKey 补齐 16 位）。
     */
    private static String convert(String data, String configKey) {
        if (NullUtil.isNull(data)) return "";
        String origin = data;
        try {
            if (isJson(data)) return data;
            Matcher matcher = Pattern.compile("[A-Za-z0]{8}\\*\\*").matcher(data);
            if (matcher.find()) {
                String tag = matcher.group();
                String body = data.substring(data.indexOf(tag) + 10);
                try {
                    data = new String(Base64.decode(body, Base64.DEFAULT), "UTF-8");
                    if (data.contains("2423")) {
                        data = decode2423(data);
                    } else if (configKey != null) {
                        data = decodeEcb(data, configKey);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    data = body;
                }
            } else if (data.contains("2423")) {
                data = decode2423(data);
            }
        } catch (Exception e) {
            e.printStackTrace();
            data = origin;
        }
        return data == null ? "" : data;
    }

    private static String decode2423(String data) throws Exception {
        String body = data.substring(data.indexOf("2324") + 4, data.length() - 26);
        String keyStr = new String(Util.hex2byte(data), "UTF-8").toLowerCase();
        String key = padKey(keyStr.substring(keyStr.indexOf("$#") + 2, keyStr.indexOf("#$")));
        String iv = padKey(keyStr.substring(keyStr.length() - 13));
        return aesCbc(body, key, iv);
    }

    private static String decodeEcb(String data, String configKey) throws Exception {
        if (isJson(data)) return data;
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS7Padding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(padKey(configKey).getBytes(), "AES"));
        return new String(cipher.doFinal(Util.hex2byte(data)));
    }

    private static String aesCbc(String data, String key, String iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS7Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key.getBytes(), "AES"), new IvParameterSpec(iv.getBytes()));
            return new String(cipher.doFinal(Util.hex2byte(data)));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 等价 com.androidx.o00OOOOo.o0000o0：trim 后取前 16 位，不足右侧补 '0'
     */
    private static String padKey(String s) {
        String key = s.trim();
        if (key.length() > 16) return key.substring(0, 16);
        StringBuilder sb = new StringBuilder(key);
        while (sb.length() < 16) sb.append("0");
        return sb.toString();
    }

    private static boolean isJson(String str) {
        try {
            new JSONObject(str);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 统一 UA。该点播配置接口与爬虫 jar 都按 UA 分流：请求头里含 okhttp 才返回 JSON 配置体 /
     * 爬虫 jar，浏览器 UA 只会拿到 FongMi 下载页 HTML。而 OkGoHelper 给 OkGo 加了浏览器
     * 公共头（accept + Chrome UA），aar 的 HttpDoRequest.doGetRequestInOkGo 不带 UA 覆盖，
     * 所以必须自己带上 okhttp UA 请求。
     */
    public static final String UA_OKHTTP = "okhttp/3.12.1";

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
        okhttp3.Request request = new okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", UA_OKHTTP)
                .build();
        try {
            okhttp3.Response response = OkGo.getInstance().getOkHttpClient().newCall(request).execute();
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

        VerifyResult(boolean ok, String message, String json, String url) {
            this.ok = ok;
            this.message = message;
            this.json = json == null ? "" : json;
            this.url = url == null ? "" : url;
        }
    }

    /**
     * 校验配置是否真的可用：下载（或读本地文件 / 用已填内容）→ 解码 → JSON 合法性 → 站点数量。
     * 全过程不修改 ApiConfig 单例状态，避免污染当前正在使用的配置。
     */
    public static VerifyResult verifyConfig(String rawUrl, String inlineContent) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        String content = inlineContent == null ? "" : inlineContent.trim();
        String usedUrl = url;

        if (!NullUtil.isNull(url)) {
            //单 URL 支持 "url;pk;configKey"
            String configKey = "";
            String downloadUrl = url;
            if (url.contains(";pk;")) {
                String[] parts = url.split(";pk;");
                if (parts.length >= 2) {
                    downloadUrl = parts[0];
                    configKey = parts[1];
                }
            }
            boolean localFile = false;
            if (downloadUrl.contains("clan")) {
                usedUrl = normalizeUrl(UiHelper.clanToAddress(downloadUrl));
            } else if (!downloadUrl.startsWith("http")) {
                //与加载逻辑保持一致：非 http 地址先当作本地文件，文件不存在再按域名补 http://
                if (new File(downloadUrl).exists()) {
                    usedUrl = downloadUrl;
                    localFile = true;
                } else {
                    usedUrl = normalizeUrl("http://" + downloadUrl);
                }
            } else {
                usedUrl = normalizeUrl(downloadUrl);
            }
            if (localFile) {
                //本地文件：直接读文件内容
                content = FileUtil.readFile(usedUrl);
                if (NullUtil.isNull(content)) {
                    return new VerifyResult(false, "读取配置文件失败：" + usedUrl, "", usedUrl);
                }
            } else {
                HttpResult http = httpGetWithOkhttpUA(usedUrl);
                VerifyResult fail = httpFail(http);
                if (fail != null) return fail;
                content = convert(http.body, configKey);
            }
        }

        if (NullUtil.isNull(content)) {
            return new VerifyResult(false, "配置地址和配置内容都为空", "", usedUrl);
        }
        if (!isJson(content)) {
            return new VerifyResult(false, "配置内容不是合法 JSON（地址可能已失效或需要特定请求头）", "", usedUrl);
        }
        int[] counts = countSites(content);
        if (counts[0] <= 0) {
            return new VerifyResult(false, "配置里没有任何可用站点（缺少 key / api 字段）", "", usedUrl);
        }
        //与「搜索源管理」口径对齐：列表只列 searchable!=0 的站点，另外还会补上内置采集源
        int extra = countExtraSites(content);
        return new VerifyResult(true, "配置校验通过：共 " + counts[0] + " 个站点，可搜索 " + counts[1]
                + " 个，另有 " + extra + " 个内置采集源（搜索源列表共 " + (counts[1] + extra) + " 个）",
                content, usedUrl);
    }

    /**
     * HTTP 层面失败时给出具体原因；正常则返回 null
     */
    private static VerifyResult httpFail(HttpResult http) {
        if (http.code == -1) {
            String msg = http.error.toLowerCase();
            if (msg.contains("unable to resolve host")) {
                return new VerifyResult(false, "域名无法解析，请检查配置地址是否正确", "", "");
            }
            if (msg.contains("timeout") || msg.contains("timed out")) {
                return new VerifyResult(false, "连接超时，请检查网络后重试", "", "");
            }
            if (msg.contains("failed to connect") || msg.contains("connection refused")) {
                return new VerifyResult(false, "无法连接服务器，请稍后重试", "", "");
            }
            String err = http.error.length() > 60 ? http.error.substring(0, 60) : http.error;
            return new VerifyResult(false, "网络请求失败：" + err, "", "");
        }
        if (http.code < 200 || http.code >= 300) {
            return new VerifyResult(false, "服务器返回 HTTP " + http.code + "，该配置地址不可用", "", "");
        }
        if (NullUtil.isNull(http.body)) {
            return new VerifyResult(false, "服务器返回内容为空", "", "");
        }
        return null;
    }

    /**
     * 统计配置里的站点数量：result[0] = 可用站点总数（key 与 api 都非空，会进站点列表）；
     * result[1] = 其中 searchable != 0（会出现在「搜索源管理」里）的数量
     */
    private static int[] countSites(String json) {
        int[] result = new int[2];
        try {
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            if (obj == null || !obj.has("sites") || !obj.get("sites").isJsonArray()) return result;
            for (JsonElement el : obj.getAsJsonArray("sites")) {
                if (!el.isJsonObject()) continue;
                JsonObject site = el.getAsJsonObject();
                if (!site.has("key") || !site.has("api")) continue;
                if (!site.get("key").isJsonPrimitive() || !site.get("api").isJsonPrimitive()) continue;
                if (NullUtil.isNull(site.get("key").getAsString())) continue;
                if (NullUtil.isNull(site.get("api").getAsString())) continue;
                result[0]++;
                if (DefaultConfig.safeJsonInt(site, "searchable", 1) != 0) result[1]++;
            }
        } catch (Throwable th) {
            L.w("countSites 异常 " + th.getMessage());
        }
        return result;
    }

    /**
     * 内置采集源里「配置中还没有同 api 站点」的个数：这些会追加到站点列表最前，
     * 所以搜索源列表数量 = 配置里可搜索站点数 + 本方法的返回值
     */
    private static int countExtraSites(String json) {
        int count = 0;
        try {
            List<String> apis = new ArrayList<>();
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            if (obj != null && obj.has("sites") && obj.get("sites").isJsonArray()) {
                for (JsonElement el : obj.getAsJsonArray("sites")) {
                    if (!el.isJsonObject()) continue;
                    JsonObject site = el.getAsJsonObject();
                    if (site.has("api") && site.get("api").isJsonPrimitive()) {
                        apis.add(site.get("api").getAsString());
                    }
                }
            }
            for (String[] def : DefaultConfig.EXTRA_SITES) {
                boolean exist = false;
                for (String api : apis) {
                    if (sameApi(api, def[2])) {
                        exist = true;
                        break;
                    }
                }
                if (!exist) count++;
            }
        } catch (Throwable th) {
            L.w("countExtraSites 异常 " + th.getMessage());
        }
        return count;
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
        String[] urls = spider == null ? new String[0] : spider.split(";md5;");
        final String jarUrl = urls.length > 0 ? urls[0].trim() : "";
        final String md5 = urls.length > 1 ? urls[1].trim() : "";
        final Context ctx = cxt.getApplicationContext();
        L.w("loadJar url=" + jarUrl + " md5=" + md5 + " useCache=" + useCache);
        new Thread() {
            @Override
            public void run() {
                boolean ok = false;
                try {
                    if (!NullUtil.isNull(jarUrl)) {
                        File jarDir = new File(ctx.getFilesDir().getAbsolutePath() + "/spider");
                        if (jarDir.exists() || jarDir.mkdirs()) {
                            File jarFile = new File(jarDir, "csp_" + MD5.encode(jarUrl) + ".jar");
                            // 与原实现的 setForceReDownload(false) 一致：已下过就直接复用，不必每次启动重下
                            if ((jarFile.exists() && jarFile.length() > 0) || downloadJar(jarUrl, jarFile)) {
                                ok = mBaseLoader.loadJar(jarFile.getAbsolutePath());
                                L.w("loadJar file=" + jarFile.getName() + " size=" + jarFile.length()
                                        + " md5=" + MD5.getFileMd5(jarFile) + " success=" + ok);
                                if (!ok) {
                                    jarFile.delete();
                                }
                            }
                        } else {
                            L.w("loadJar 创建目录失败 " + jarDir.getAbsolutePath());
                        }
                    }
                } catch (Throwable th) {
                    L.w("loadJar 异常 " + th.getMessage());
                }
                if (!ok) {
                    // 兜底：内置 assets/tvbox.jar（只有 2 个条目，不含 csp_* 站点类）
                    MyFilesManager.copyFileFromAssets(App.getInstance(), "tvbox.jar",
                            App.getInstance().getFilesDir().getAbsolutePath(), "csp.jar");
                    File cspFile = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp.jar");
                    L.w("loadJar 内置兜底 csp.jar size=" + cspFile.length()
                            + " success=" + mBaseLoader.loadJar(cspFile.getAbsolutePath()));
                }
                callback.success();
            }
        }.start();
    }

    /**
     * 显式带 okhttp UA 下载 jar 到目标文件；先写 .tmp 再改名，避免中断留下半截文件
     */
    private static boolean downloadJar(String url, File dest) {
        File tmp = new File(dest.getAbsolutePath() + ".tmp");
        okhttp3.Response response = null;
        InputStream in = null;
        FileOutputStream out = null;
        try {
            okhttp3.Request request = new okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", UA_OKHTTP)
                    .build();
            response = OkGo.getInstance().getOkHttpClient().newCall(request).execute();
            if (!response.isSuccessful() || response.body() == null) {
                L.w("loadJar 下载失败 code=" + response.code() + " url=" + url);
                return false;
            }
            in = response.body().byteStream();
            out = new FileOutputStream(tmp);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            out.flush();
            out.close();
            out = null;
            in.close();
            in = null;
            if (tmp.length() == 0) {
                return false;
            }
            if (dest.exists() && !dest.delete()) {
                L.w("loadJar 旧文件删除失败 " + dest.getAbsolutePath());
            }
            boolean moved = tmp.renameTo(dest);
            L.w("loadJar 下载完成 size=" + dest.length() + " rename=" + moved);
            return moved;
        } catch (Throwable th) {
            L.w("loadJar 下载异常 " + th.getMessage());
            return false;
        } finally {
            try {
                if (out != null) out.close();
            } catch (Exception ignored) {
            }
            try {
                if (in != null) in.close();
            } catch (Exception ignored) {
            }
            if (response != null) response.close();
            if (tmp.exists()) tmp.delete();
        }
    }

    private void parseSiteFile(String apiUrl, String jsonStr) {
        if (NullUtil.isNull(jsonStr)) return;
        if (m_bParsed) return;
        L.d("parseSiteFile() start");
        try {
            mSiteBeanMap.clear();
            JsonObject jsonObject = new Gson().fromJson(jsonStr, JsonObject.class);
            mSpider = DefaultConfig.safeJsonString(jsonObject, "spider", "");
            L.d("mSpider=" + mSpider);
            // 远端站点源
            for (JsonElement opt : jsonObject.get("sites").getAsJsonArray()) {
                JsonObject obj = (JsonObject) opt;
                SiteBean bean = new SiteBean();
                String siteKey = obj.get("key").getAsString().trim();
                bean.setKey(siteKey);
                if(NullUtil.isNull(siteKey)) continue;//空key不要
                bean.setName(obj.get("name").getAsString().trim());
                bean.setType(obj.get("type").getAsInt());
                bean.setApi(obj.get("api").getAsString().trim());
                bean.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
                bean.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
                bean.setFilterable(DefaultConfig.safeJsonInt(obj, "filterable", 1));
                bean.setPlayerUrl(DefaultConfig.safeJsonString(obj, "playUrl", ""));
                bean.setExt(DefaultConfig.safeJsonString(obj, "ext", ""));
                //部分源的类不在全局 spider 里，配置用站点级 jar 单独提供（如 csp_AppRJ 在 feimao1004.jar）
                bean.setJar(DefaultConfig.safeJsonString(obj, "jar", ""));
                bean.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
                L.d(UiHelper.getBeanStr(bean));
                mSiteBeanMap.put(siteKey, bean);
            }

            // 配置文件里多是盘搜类站点，补上内置的主流采集源并排到最前（分类页/搜索用它们）
            addExtraSites();

            // 需要使用vip解析的flag
            mVipFlagList = DefaultConfig.safeJsonStringList(jsonObject, "flags");
            // 解析地址
            for (JsonElement opt : jsonObject.get("parses").getAsJsonArray()) {
                JsonObject obj = (JsonObject) opt;
                ParseBean pb = new ParseBean();
                pb.setName(obj.get("name").getAsString().trim());
                pb.setUrl(obj.get("url").getAsString().trim());
                String ext = obj.has("ext") ? obj.get("ext").getAsJsonObject().toString() : "";
                pb.setExt(ext);
                pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
                mParseBeanList.add(pb);
                L.d(pb.getName() + "\t" + pb.getUrl());
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
            // 直播源
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
            // 广告地址
            JsonArray ads =  jsonObject.getAsJsonArray("ads");
            if(null != ads) {
                for (JsonElement host : ads) {
                    AdBlocker.addAdHost(host.getAsString());
                }
            }
            // IJK解码配置
            boolean foundOldSelect = false;
            String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "");
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
            if (!foundOldSelect && mIjkCodes.size() > 0) {
                mIjkCodes.get(0).selected(true);
            }
        } catch (Exception e) {
            L.e(e);
        }
        m_bParsed = true;
    }

    public void loadLives(JsonArray livesArray) {
        L.d("loadLives()");
        mLiveChannelGroupList.clear();
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        for (JsonElement groupElement : livesArray) {
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
            liveChannelGroup.setGroupIndex(groupIndex++);
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
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                liveChannelItem.setChannelName(obj.get("name").getAsString().trim());
                liveChannelItem.setChannelIndex(channelIndex++);
                liveChannelItem.setChannelNum(++channelNum);
                ArrayList<String> urls = DefaultConfig.safeJsonStringList(obj, "urls");
                L.d("urls="+urls);
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceUrls = new ArrayList<>();
                int sourceIndex = 1;
                for (String url : urls) {
                    L.d("url="+url);
                    String[] splitText = url.split("\\$", 2);
                    sourceUrls.add(splitText[0]);
                    if (splitText.length > 1) {
                        sourceNames.add(splitText[1]);
                    } else {
                        sourceNames.add("源" + Integer.toString(sourceIndex));
                    }

                    sourceIndex++;
                }
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                liveChannelGroup.getLiveChannels().add(liveChannelItem);
            }
            mLiveChannelGroupList.add(liveChannelGroup);
            L.d("live group name:"+liveChannelGroup.getGroupName());
        }
    }

    public String getmSpider() {
        if (null == mSpider) return "";
        return mSpider;
    }

    public Spider getCSP(SiteBean sourceBean) {
        //该源自带爬虫包时先把它准备好，否则类找不到会静默降级成 SpiderNull（表现为搜索结果为空）
        String jarPath = preloadSiteJar(sourceBean.getJar());
        return mBaseLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt(), jarPath);
    }

    /**
     * 下载并加载站点级 jar（配置里 site.jar 字段），返回本地 jar 路径。
     * 同一 url 只允许一个线程下载，其余线程在锁上等它完成——否则并发线程会在 jar 就绪前查类，
     * 全部落到"类找不到"的空结果上。结果按 url 缓存，失败缓存空串避免反复重试。
     */
    public String preloadSiteJar(String jarWithMd5) {
        if (NullUtil.isNull(jarWithMd5)) return "";
        String url = jarWithMd5.split(";md5;")[0].trim();
        if (NullUtil.isNull(url)) return "";
        String cached = mSiteJarPaths.get(url);
        if (cached != null) return cached;
        Object lock = mSiteJarLocks.get(url);
        if (lock == null) {
            mSiteJarLocks.putIfAbsent(url, new Object());
            lock = mSiteJarLocks.get(url);
        }
        synchronized (lock) {
            cached = mSiteJarPaths.get(url);
            if (cached != null) return cached;
            String path = "";
            try {
                File jarDir = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/spider");
                if (jarDir.exists() || jarDir.mkdirs()) {
                    File jarFile = new File(jarDir, "csp_" + MD5.encode(url) + ".jar");
                    if ((jarFile.exists() && jarFile.length() > 0) || downloadJar(url, jarFile)) {
                        if (mBaseLoader.loadExtraJar(jarFile.getAbsolutePath())) {
                            path = jarFile.getAbsolutePath();
                            L.w("preloadSiteJar ok " + url + " size=" + jarFile.length());
                        } else {
                            L.w("preloadSiteJar 加载失败 " + url);
                        }
                    } else {
                        L.w("preloadSiteJar 下载失败 " + url);
                    }
                }
            } catch (Throwable th) {
                L.e(th);
            }
            mSiteJarPaths.put(url, path);
            return path;
        }
    }

    public Object[] proxyLocal(Map param) {
        return mBaseLoader.proxy(param);
    }

    public JSONObject jarExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return mBaseLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jarExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return mBaseLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    public interface LoadConfigCallback {
        void success();
    }

    public interface InitHomeCallback {
        void success();
    }

    public void setDefaultParse(ParseBean parseBean) {
        if (this.mDefaultParseBean != null) {
            this.mDefaultParseBean.setDefault(false);
        }
        this.mDefaultParseBean = parseBean;
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName());
        parseBean.setDefault(true);
    }

    public ParseBean getDefaultParse() {
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
        ArrayList<String> disabled = Hawk.get(HawkConfig.SEARCH_SITE_DISABLED, new ArrayList<String>());
        return disabled == null ? new ArrayList<String>() : disabled;
    }

    public void setSearchSiteDisabled(List<String> keys) {
        Hawk.put(HawkConfig.SEARCH_SITE_DISABLED, keys == null ? new ArrayList<String>() : new ArrayList<>(keys));
    }

    /** 该站点是否被允许参与搜索（未设置或未被取消勾选 → 允许） */
    public boolean isSearchSiteEnabled(String key) {
        if (NullUtil.isNull(key)) return true;
        return !getSearchSiteDisabled().contains(key);
    }

    public List<ParseBean> getmParseBeanList() {
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
        //重新探测期间如果一直没探测成功，兜底沿用上次的首页源，避免分类列表一直空白
        final SiteBean lastBean = cachedHomeBean;
        if (lastBean != null) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(6000);
                    } catch (Throwable ignored) {
                    }
                    if (bInit) return;
                    L.w("initHomeBean 探测未成功，沿用上次首页源 " + lastBean.getKey());
                    bInit = true;
                    setHomeBean(lastBean);
                    callback.success();
                }
            }).start();
        }
        List<SiteBean> beans = new ArrayList<>(mSiteBeanMap.values());
        if (!NullUtil.isNull(beans)) {
            ExecutorService mExecutorService = Executors.newFixedThreadPool(3);
            for (int i = 0; i < beans.size(); i++) {
                L.d("initHomeBean i=" + i);
                SiteBean bean = beans.get(i);
                // searchable=0 的都是工具/配置类站点（我的云盘、本地文件、说明等），不适合当首页源
                if (!bean.isSearchable()) {
                    L.d("initHomeBean skip " + bean.getKey() + " searchable=0");
                    continue;
                }
                mExecutorService.submit(new Runnable() {
                    @Override
                    public void run() {
                        if (bInit) return;//初始过，不再继续
                        int type = bean.getType();
                        if (type == 3) {
                            Spider sp = ApiConfig.getInstance().getCSP(bean);
                            String sortJson = sp == null ? null : sp.homeContent(true);
                            // 至少要有 2 个分类才算影视站点；只有 1 个分类的通常是云盘/配置类站点
                            if (classCount(sortJson) >= 2) {
                                if (bInit) return;//初始过，不再初始
                                setHomeBean(bean);
                                callback.success();
                                mExecutorService.shutdown();
                                bInit = true;
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
                                                if (bInit) return;//初始过，不再初始
                                                OkGo.getInstance().cancelTag("_initHome");
                                                setHomeBean(bean);
                                                callback.success();
                                                bInit = true;
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
    }

    private static boolean sameApi(String api1, String api2) {
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
        return mLiveChannelGroupList;
    }

    public List<IJKCode> getmIjkCodes() {
        return mIjkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, "");
        return getIJKCodec(codeName);
    }

    public IJKCode getIJKCodec(String name) {
        if(NullUtil.isNull(mIjkCodes)){
            return new IJKCode();
        }
        for (IJKCode code : mIjkCodes) {
            if (code.getName().equals(name))
                return code;
        }
        return mIjkCodes.get(0);
    }
}