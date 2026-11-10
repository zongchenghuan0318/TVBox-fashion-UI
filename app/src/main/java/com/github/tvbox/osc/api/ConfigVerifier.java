package com.github.tvbox.osc.api;

import android.util.Base64;

import com.github.catvod.utils.Util;
import com.github.tvbox.osc.ui.activity.UiHelper;
import com.github.tvbox.osc.util.DefaultConfig;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.FileUtil;
import com.lcstudio.commonsurport.util.NullUtil;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.github.tvbox.osc.api.ApiConfig.HttpResult;
import com.github.tvbox.osc.api.ApiConfig.VerifyResult;

import static com.github.tvbox.osc.api.ApiConfig.httpGetWithOkhttpUA;
import static com.github.tvbox.osc.api.ApiConfig.normalizeUrl;
import static com.github.tvbox.osc.api.ApiConfig.sameApi;

final class ConfigVerifier {
    private ConfigVerifier() {
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
                    L.e(e);
                    data = body;
                }
            } else if (data.contains("2423")) {
                data = decode2423(data);
            }
        } catch (Exception e) {
            L.e(e);
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
            L.e(e);
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
     * 校验配置是否真的可用：下载（或读本地文件 / 用已填内容）→ 解码 → JSON 合法性 → 站点数量。
     * 全过程不修改 ApiConfig 单例状态，避免污染当前正在使用的配置。
     */
    static VerifyResult verifyConfig(String rawUrl, String inlineContent) {
        return verifyConfig(rawUrl, inlineContent, 0);
    }

    private static VerifyResult verifyConfig(String rawUrl, String inlineContent, int timeoutMs) {
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
                HttpResult http = timeoutMs > 0
                        ? httpGetWithOkhttpUA(usedUrl, timeoutMs)
                        : httpGetWithOkhttpUA(usedUrl);
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
                return connectionFail("域名无法解析，请检查配置地址是否正确");
            }
            if (msg.contains("timeout") || msg.contains("timed out")) {
                return connectionFail("连接超时，请检查网络后重试");
            }
            if (msg.contains("failed to connect") || msg.contains("connection refused")) {
                return connectionFail("无法连接服务器，请稍后重试");
            }
            String err = http.error.length() > 60 ? http.error.substring(0, 60) : http.error;
            return connectionFail("网络请求失败：" + err);
        }
        if (http.code < 200 || http.code >= 300) {
            String text = "服务器返回 HTTP " + http.code + "，该配置地址不可用";
            // 5xx、408、429 属于临时性故障，按连接失败处理（会重试）；其余 4xx 重试没有意义
            if (http.code >= 500 || http.code == 408 || http.code == 429) {
                return connectionFail(text);
            }
            return new VerifyResult(false, text, "", "");
        }
        if (NullUtil.isNull(http.body)) {
            return new VerifyResult(false, "服务器返回内容为空", "", "");
        }
        return null;
    }

    /** 连接层面的失败（网络不通、超时、临时性服务器错误）：标记后会被重试 */
    private static VerifyResult connectionFail(String message) {
        VerifyResult result = new VerifyResult(false, message, "", "");
        result.connectionFailed = true;
        return result;
    }

    /** 每个配置源最多尝试的次数；只有连接层面的失败才会重试，内容错误直接判定失败 */
    private static final int CONFIG_CONNECT_ATTEMPTS = 3;
    private static final long CONFIG_RETRY_DELAY_MS = 1000L;

    /**
     * 校验配置源：连接失败时同一地址最多尝试 {@link #CONFIG_CONNECT_ATTEMPTS} 次，
     * 全部失败才返回失败，由调用方切换到下一个配置源。
     * 必须在后台线程调用。
     */
    static VerifyResult verifyConfigWithRetry(String rawUrl, String inlineContent, int timeoutMs) {
        VerifyResult result = null;
        for (int attempt = 1; attempt <= CONFIG_CONNECT_ATTEMPTS; attempt++) {
            result = verifyConfig(rawUrl, inlineContent, timeoutMs);
            if (result.ok || !result.connectionFailed) return result;
            L.w("配置源连接失败 第" + attempt + "/" + CONFIG_CONNECT_ATTEMPTS + " 次 url=" + rawUrl
                    + " reason=" + result.message);
            if (attempt < CONFIG_CONNECT_ATTEMPTS) {
                try {
                    Thread.sleep(CONFIG_RETRY_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return result;
                }
            }
        }
        return result;
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
}
