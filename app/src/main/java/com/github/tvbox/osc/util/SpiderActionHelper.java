package com.github.tvbox.osc.util;

import android.app.Activity;
import android.text.TextUtils;
import android.widget.Toast;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SiteBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.dialog.SpiderActionDialog;
import com.lcstudio.commonsurport.L;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 网盘配置中心识别与 TVBox/CatVod Spider.action() 协议分发助手。
 */
public final class SpiderActionHelper {

    private static final String[] CONFIG_KEYWORDS = {
            "配置", "中心", "扫码", "登录", "登陆", "设置", "token", "cookie", "config", "setting", "auth"
    };

    private static final String[] CLOUD_KEYWORDS = {
            "网盘", "云盘", "阿里", "夸克", "quark", "uc", "115", "百度", "天翼", "移动",
            "123", "pikpak", "pan", "drive", "yundisk", "ali", "玩偶", "wogg", "盘搜"
    };

    private SpiderActionHelper() {
    }

    public static boolean isActionItem(Movie.Video video) {
        if (video == null) return false;
        if ("action".equalsIgnoreCase(video.tag)) return true;
        if (!TextUtils.isEmpty(video.action)) return true;
        return looksLikeActionJson(video.id);
    }

    public static String extractActionPayload(Movie.Video video) {
        if (video == null) return "";
        if (!TextUtils.isEmpty(video.action)) return video.action.trim();
        return video.id == null ? "" : video.id.trim();
    }

    public static boolean looksLikeActionJson(String raw) {
        if (raw == null) return false;
        String trimmed = raw.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false;
        try {
            JSONObject obj = new JSONObject(trimmed);
            if (obj.has("actionId") || (!obj.isNull("action") && !obj.optString("action", "").trim().isEmpty())
                    || hasDialogContent(obj)) return true;
            String type = obj.optString("type", "").trim().toLowerCase(Locale.ROOT);
            if (!type.isEmpty() && (obj.has("desc") || obj.has("text"))) return true;
            switch (type) {
                case "input":
                case "edit":
                case "multiinput":
                case "multiinputx":
                case "menu":
                case "select":
                case "url":
                case "webview":
                    return true;
                default:
                    return false;
            }
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasDialogContent(JSONObject obj) {
        return obj.has("imageUrl") || obj.has("qrcode") || obj.has("qr")
                || obj.has("qrCode") || obj.has("option") || obj.has("msg");
    }

    /**
     * 判断站点是否为网盘/配置中心类爬虫源，返回优先级（越大越靠前，0 表示普通站点）。
     */
    public static int getCloudSitePriority(SiteBean site) {
        if (site == null || site.getType() != 3) return 0;
        String text = ((site.getName() == null ? "" : site.getName()) + " "
                + (site.getKey() == null ? "" : site.getKey()) + " "
                + (site.getApi() == null ? "" : site.getApi())).toLowerCase(Locale.ROOT);
        boolean isConfig = containsAny(text, CONFIG_KEYWORDS);
        boolean isCloud = containsAny(text, CLOUD_KEYWORDS);
        boolean nonSearchable = !site.isSearchable();
        if (isConfig && (nonSearchable || isCloud)) return 4;
        if (isConfig) return 3;
        if (nonSearchable && isCloud) return 3;
        if (nonSearchable) return 2;
        if (isCloud) return 1;
        return 0;
    }

    /**
     * 获取当前接口下推荐展示的网盘/配置中心爬虫站点（按优先级排序）；
     * 若接口里没有任何明显配置站，返回所有 type=3 爬虫站方便用户手动选择。
     */
    public static List<SiteBean> getRecommendedCloudSites() {
        List<SiteBean> all = ApiConfig.getInstance().getSiteBeanList();
        List<SiteBean> prioritized = new ArrayList<>();
        List<SiteBean> allSpiders = new ArrayList<>();
        if (all != null) {
            for (SiteBean site : all) {
                if (site == null || site.getType() != 3) continue;
                allSpiders.add(site);
                if (getCloudSitePriority(site) > 0) {
                    prioritized.add(site);
                }
            }
        }
        Collections.sort(prioritized, (a, b) -> Integer.compare(getCloudSitePriority(b), getCloudSitePriority(a)));
        return prioritized.isEmpty() ? allSpiders : prioritized;
    }

    public static List<SiteBean> getAllSpiderSites() {
        List<SiteBean> all = ApiConfig.getInstance().getSiteBeanList();
        List<SiteBean> spiders = new ArrayList<>();
        if (all != null) {
            for (SiteBean site : all) {
                if (site != null && site.getType() == 3) {
                    spiders.add(site);
                }
            }
        }
        Collections.sort(spiders, (a, b) -> Integer.compare(getCloudSitePriority(b), getCloudSitePriority(a)));
        return spiders;
    }

    public static boolean hasCloudConfigSites() {
        return !getRecommendedCloudSites().isEmpty();
    }

    public static String getCloudConfigSummary() {
        List<SiteBean> sites = ApiConfig.getInstance().getSiteBeanList();
        int configCount = 0;
        int spiderCount = 0;
        if (sites != null) {
            for (SiteBean site : sites) {
                if (site == null || site.getType() != 3) continue;
                spiderCount++;
                if (getCloudSitePriority(site) >= 2) {
                    configCount++;
                }
            }
        }
        if (configCount > 0) {
            return configCount + " 个配置入口";
        }
        if (spiderCount > 0) {
            return "扫码登录与管理";
        }
        return "暂无可用网盘源";
    }

    /**
     * 把局域网本机回环地址替换为电视真实局域网 IP，确保手机扫码可直接访问。
     */
    public static String resolveLanUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        String normalized = DefaultConfig.checkReplaceProxy(url.trim());
        String lanBase = ControlManager.get().getAddress(false);
        if (TextUtils.isEmpty(lanBase)) return normalized;
        String lanHost = lanBase.replace("http://", "").replace("https://", "");
        if (lanHost.endsWith("/")) lanHost = lanHost.substring(0, lanHost.length() - 1);
        int port = com.github.tvbox.osc.server.RemoteServer.serverPort;
        normalized = normalized.replace("http://127.0.0.1:" + port, "http://" + lanHost);
        normalized = normalized.replace("http://localhost:" + port, "http://" + lanHost);
        return normalized;
    }

    /**
     * 执行卡片的 action 协议：若 payload 本身是 Action JSON 则直接弹窗，
     * 否则异步调用 spider.action(payload) 并根据返回结果弹窗或提示。
     */
    public static void handleVideoAction(Activity activity, Movie.Video video, Runnable onCompleted) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || video == null) {
            return;
        }
        String payload = extractActionPayload(video);
        String fallbackTitle = TextUtils.isEmpty(video.name) ? "网盘扫码登录" : video.name;
        executeAction(activity, video.sourceKey, payload, fallbackTitle, onCompleted);
    }

    public static void executeAction(Activity activity, String sourceKey, String payload,
                                     String fallbackTitle, Runnable onCompleted) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (TextUtils.isEmpty(sourceKey)) {
            Toast.makeText(activity, "缺少来源信息，无法执行操作", Toast.LENGTH_SHORT).show();
            return;
        }
        String trimmed = payload == null ? "" : payload.trim();
        if (looksLikeActionJson(trimmed)) {
            showActionJson(activity, sourceKey, trimmed, fallbackTitle, onCompleted);
            return;
        }
        invokeSpiderAction(activity, sourceKey, trimmed, fallbackTitle, onCompleted);
    }

    public static void invokeSpiderAction(Activity activity, String sourceKey, String actionPayload,
                                          String fallbackTitle, Runnable onCompleted) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        SiteBean site = ApiConfig.getInstance().getSiteBean(sourceKey);
        if (site == null || site.getType() != 3) {
            Toast.makeText(activity, "当前站点不支持扫码配置动作", Toast.LENGTH_SHORT).show();
            return;
        }
        AppExecutors.io().execute(() -> {
            String result = "";
            Throwable error = null;
            try {
                Spider spider = ApiConfig.getInstance().getCSP(site);
                if (spider != null) {
                    result = spider.action(actionPayload == null ? "" : actionPayload);
                }
            } catch (Throwable th) {
                error = th;
                L.w("Spider.action error site=" + sourceKey + " err=" + th.getMessage());
            }
            final String finalResult = result == null ? "" : result.trim();
            final Throwable finalError = error;
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || activity.isDestroyed()) return;
                if (finalError != null && finalResult.isEmpty()) {
                    Toast.makeText(activity, "执行失败：" + finalError.getMessage(), Toast.LENGTH_SHORT).show();
                    return;
                }
                if (finalResult.isEmpty()) {
                    ApiConfig.getInstance().resetSpiders();
                    if (onCompleted != null) onCompleted.run();
                    return;
                }
                if (looksLikeActionJson(finalResult)) {
                    showActionJson(activity, sourceKey, finalResult, fallbackTitle, onCompleted);
                    return;
                }
                ApiConfig.getInstance().resetSpiders();
                Toast.makeText(activity, finalResult, Toast.LENGTH_LONG).show();
                if (onCompleted != null) onCompleted.run();
            });
        });
    }

    private static void showActionJson(Activity activity, String sourceKey, String jsonStr,
                                       String fallbackTitle, Runnable onCompleted) {
        try {
            JSONObject obj = new JSONObject(jsonStr);
            String type = obj.optString("type", "").trim();
            String directAction = obj.optString("action", "").trim();
            if (type.isEmpty() && !obj.isNull("action") && !directAction.isEmpty() && !hasDialogContent(obj)
                    && !obj.has("desc") && !obj.has("text")) {
                invokeSpiderAction(activity, sourceKey, directAction, fallbackTitle, onCompleted);
                return;
            }
            SpiderActionDialog dialog = new SpiderActionDialog(activity, sourceKey, obj, fallbackTitle, onCompleted);
            dialog.show();
        } catch (Throwable th) {
            L.w("showActionJson error: " + th.getMessage());
            Toast.makeText(activity, jsonStr, Toast.LENGTH_LONG).show();
        }
    }

    private static boolean containsAny(String target, String[] keywords) {
        if (TextUtils.isEmpty(target)) return false;
        for (String kw : keywords) {
            if (target.contains(kw)) return true;
        }
        return false;
    }
}
