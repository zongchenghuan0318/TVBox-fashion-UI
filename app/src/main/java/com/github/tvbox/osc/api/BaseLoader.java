package com.github.tvbox.osc.api;

import com.github.catvod.crawler.JarLoader;
import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.util.js.SpiderFm;
import com.github.tvbox.osc.util.python.PyLoader;
import com.lcstudio.commonsurport.L;

import org.json.JSONObject;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 三引擎爬虫分发器（等价目标 v6.1.7 的 com.androidx.oOOO000o）
 * <p>
 * api 以 csp_ 开头 → DexClassLoader 动态加载 jar（JarLoader）
 * api 以 .js 结尾  → QuickJS（SpiderFm）
 * api 以 .py 结尾  → Chaquopy Python（PyLoader）
 * 其余             → SpiderNull
 */
public class BaseLoader {

    private final JarLoader jarLoader = new JarLoader();
    private final PyLoader pyLoader = new PyLoader();
    private final ConcurrentHashMap<String, Spider> jsSpiders = new ConcurrentHashMap<>();
    private volatile Spider recentSpider;
    private volatile boolean recentIsJar;

    public boolean loadJar(String path) {
        return jarLoader.load(path);
    }

    /** 加载站点自带爬虫包（与全局 spider jar 各自独立） */
    public boolean loadExtraJar(String path) {
        return jarLoader.loadExtra(path);
    }

    public Spider getSpider(String key, String api, String ext) {
        return getSpider(key, api, ext, "");
    }

    public Spider getSpider(String key, String api, String ext, String jarPath) {
        try {
            if (api.startsWith("csp_")) {
                Spider sp = jarLoader.getSpider(key, api, ext, jarPath);
                recentSpider = sp;
                recentIsJar = true;
                return sp;
            }
            if (api.endsWith(".js")) {
                Spider sp = jsSpider(key, api, ext);
                recentSpider = sp;
                recentIsJar = false;
                return sp;
            }
            if (api.endsWith(".py")) {
                Spider sp = pyLoader.getSpider(key, api, ext);
                recentSpider = sp;
                recentIsJar = false;
                return sp;
            }
            L.d("BaseLoader 未知 api=" + api);
        } catch (Throwable th) {
            L.e(th);
        }
        SpiderNull empty = new SpiderNull();
        recentSpider = empty;
        recentIsJar = false;
        return empty;
    }

    private Spider jsSpider(String key, String api, String ext) {
        Spider spider = jsSpiders.get(key);
        if (spider == null) {
            SpiderFm fm = new SpiderFm(key, api);
            fm.init(App.getInstance(), ext);
            jsSpiders.put(key, fm);
            spider = fm;
        }
        return spider;
    }

    /**
     * 本地代理请求分发：jar 走 Proxy.proxy 反射，js/py 走各自 proxyLocal
     */
    public Object[] proxy(Map<String, String> params) {
        try {
            Spider spider = recentSpider;
            if (spider == null) return null;
            if (recentIsJar) return jarLoader.proxyInvoke(params);
            return spider.proxyLocal(params);
        } catch (Throwable th) {
            L.e(th);
        }
        return null;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    public void clear() {
        jsSpiders.values().forEach(Spider::destroy);
        jsSpiders.clear();
        pyLoader.clear();
        recentSpider = null;
        recentIsJar = false;
    }
}
