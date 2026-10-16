package com.github.tvbox.osc.util;

import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.server.ControlManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lcstudio.commonsurport.L;
import com.lcstudio.commonsurport.util.NullUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class DefaultConfig {

    /**
     * 首页侧边栏固定分类模板：{显示名, 关键词1, 关键词2...}
     * 数组顺序即显示顺序；关键词按从左到右的优先级匹配（不是按源的分类顺序）。
     * 采集类接口（CMS）的父分类取不到内容（如“电影片”“连续剧”查询返回 0 条），
     * 有内容的都是叶子分类（剧情片、动作片、国产剧…），所以关键词要先用叶子分类，
     * 这样「电影」点进去拿到的才是真片库而不是空白页；
     * 匹配不上的模板项不显示；不在模板里的分类（如“臻彩”）自动消失。
     */
    private static final String[][] FIXED_SORT = {
            {"电影", "剧情片", "动作片", "喜剧片", "爱情片", "电影", "影片"},
            {"电视剧", "国产剧", "内地剧", "连续剧", "电视剧", "剧集", "剧场", "电视"},
            {"综艺", "大陆综艺", "综艺", "娱乐", "真人秀"},
            {"动漫", "国产动漫", "中国动漫", "动漫", "动画", "番剧", "国漫", "日漫"},
            {"纪录片", "记录片", "纪录片", "纪录", "纪实", "探索"},
    };

    /**
     * 内置补充的主流采集源：{key, 显示名, api}
     * 配置文件里正规采集站只有 1 个，其余多是盘搜类站点，分类页点进去基本是网盘资源；
     * 这几个源为 CMS 采集接口（实测内容量 11 万～16 万条、当天有更新、支持关键词搜索），
     * 作为分类页与聚合搜索的优选源。
     */
    public static final String[][] EXTRA_SITES = {
            {"ffzy", "非凡┃影视", "http://cj.ffzyapi.com/api.php/provide/vod/"},
            {"lzi", "量子┃影视", "https://cj.lziapi.com/api.php/provide/vod/"},
            {"bfzy", "暴风┃影视", "https://bfzyapi.com/api.php/provide/vod/"},
            {"jszy", "极速┃影视", "https://jszyapi.com/api.php/provide/vod/"},
    };

    /**
     * 搜索源默认优先级：名字命中关键词的源排在默认顺序最前，数组顺序即优先级（越靠前越高）。
     * 对应配置里的「文采┃秒播」与「非凡┃影视」，即默认 文采秒播 > 非凡影视 > 其余源。
     * 仅在用户没有自定义过搜索源顺序时生效（搜索源管理里重新勾选过则以用户顺序为准）。
     */
    public static final String[] SEARCH_SITE_PRIORITY = {"文采", "非凡"};

    public static List<MovieSort.SortData> adjustSort(String sourceKey, List<MovieSort.SortData> list, boolean withMy) {
        List<MovieSort.SortData> sortList = new ArrayList<>();
        if (list != null) {
            // 先做常规过滤（补齐 filters、剔除“伦理”），得到可用的源分类池
            List<MovieSort.SortData> pool = new ArrayList<>();
            for (MovieSort.SortData sortData : list) {
                if (sortData == null) continue;
                if (sortData.filters == null) {
                    sortData.filters = new ArrayList<>();
                }
                if (!NullUtil.isNull(sortData.name) && sortData.name.contains("伦理")) continue;
                pool.add(sortData);
            }
            // 再按固定模板重排：关键词按优先级取，沿用源对象的 id/filters（保证点进去有内容），仅替换显示名
            for (String[] rule : FIXED_SORT) {
                MovieSort.SortData hit = null;
                for (int k = 1; k < rule.length && hit == null; k++) {
                    for (MovieSort.SortData sortData : pool) {
                        if (NullUtil.isNull(sortData.name)) continue;
                        if (sortData.name.contains(rule[k])) {
                            hit = sortData;
                            break;
                        }
                    }
                }
                if (hit != null) {
                    hit.name = rule[0];
                    sortList.add(hit);
                    pool.remove(hit);
                }
            }
        }
        if (withMy) {
            sortList.add(0, new MovieSort.SortData("my0", "首页"));
        }
        return sortList;
    }

    private static final Pattern snifferMatch = Pattern.compile("http((?!http).){26,}?\\.(m3u8|mp4)\\?.*|http((?!http).){26,}\\.(m3u8|mp4)|http((?!http).){26,}?/m3u8\\?pt=m3u8.*|http((?!http).)*?default\\.ixigua\\.com/.*|http((?!http).)*?cdn-tos[^\\?]*|http((?!http).)*?/obj/tos[^\\?]*|http.*?/player/m3u8play\\.php\\?url=.*|http.*?/player/.*?[pP]lay\\.php\\?url=.*|http.*?/playlist/m3u8/\\?vid=.*|http.*?\\.php\\?type=m3u8&.*|http.*?/download.aspx\\?.*|http.*?/api/up_api.php\\?.*|https.*?\\.66yk\\.cn.*|http((?!http).)*?netease\\.com/file/.*");
    public static boolean isVideoFormat(String url) {
        if (url.contains("=http") || url.contains("=https") || url.contains("=https%3a%2f") || url.contains("=http%3a%2f")) {
            return false;
        }
        if (snifferMatch.matcher(url).find()) {
            L.d("snifferMatch.matcher(url).find()");
            if (url.contains("cdn-tos") && (url.contains(".js") || url.contains(".css"))) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static boolean isVideoFormat2(String url) {
        if (url.startsWith("http") && url.endsWith("m3u8")) {
            return true;
        }
        if (url.startsWith("http") && url.endsWith("mp4")) {
            return true;
        }
        return false;
    }


    public static String safeJsonString(JsonObject obj, String key, String defaultVal) {
        try {
            if (obj.has(key))
                return obj.getAsJsonPrimitive(key).getAsString().trim();
            else
                return defaultVal;
        } catch (Throwable th) {
        }
        return defaultVal;
    }

    public static int safeJsonInt(JsonObject obj, String key, int defaultVal) {
        try {
            if (obj.has(key))
                return obj.getAsJsonPrimitive(key).getAsInt();
            else
                return defaultVal;
        } catch (Throwable th) {
        }
        return defaultVal;
    }

    public static ArrayList<String> safeJsonStringList(JsonObject obj, String key) {
        ArrayList<String> result = new ArrayList<>();
        try {
            if (obj.has(key)) {
                if (obj.get(key).isJsonObject()) {
                    result.add(obj.get(key).getAsString());
                } else {
                    for (JsonElement opt : obj.getAsJsonArray(key)) {
                        result.add(opt.getAsString());
                    }
                }
            }
        } catch (Throwable th) {
        }
        return result;
    }

    public static String checkReplaceProxy(String urlOri) {
        if (urlOri.startsWith("proxy://"))
            return urlOri.replace("proxy://", ControlManager.get().getAddress(true) + "proxy?");
        return urlOri;
    }

}