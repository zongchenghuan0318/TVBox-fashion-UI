package com.github.catvod.crawler;

import android.content.Context;

import com.github.tvbox.osc.base.App;
import com.lcstudio.commonsurport.L;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import dalvik.system.DexClassLoader;

public class JarLoader {
    private DexClassLoader classLoader = null;
    private ConcurrentHashMap<String, Spider> spidersMap = new ConcurrentHashMap<>();
    private Method proxyFun = null;

    /**
     * 不要在主线程调用我
     */
    public boolean load(String cacheFile) {
        spidersMap.clear();
        proxyFun = null;
        boolean success = false;
        try {
            File cacheDir = new File(App.getInstance().getCacheDir().getAbsolutePath() + "/catvod_csp");
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            classLoader = new DexClassLoader(cacheFile, cacheDir.getAbsolutePath(), null, App.getInstance().getClassLoader());
            int count = 0;
            do {
                try {
                    Class classInit = classLoader.loadClass("com.github.catvod.spider.Init");
                    if (classInit != null) {
                        Method method = classInit.getMethod("init", Context.class);
                        method.invoke(null, App.getInstance());
                        L.d("自定义爬虫代码加载成功!");
                        success = true;
                        try {
                            Class proxy = classLoader.loadClass("com.github.catvod.spider.Proxy");
                            Method mth = proxy.getMethod("proxy", Map.class);
                            proxyFun = mth;
                            L.d("proxyFun="+proxyFun);
                        } catch (Throwable th) {
                           L.e(th);
                        }
                        break;
                    }
                    Thread.sleep(200);
                } catch (Throwable th) {
                    L.e("load "+th.getMessage());
                }
                count++;
            } while (count < 5);
        } catch (Throwable th) {
            L.e(th);
        }
        L.w("JarLoader.load success=" + success + " cacheFile=" + cacheFile);
        return success;
    }

    public Spider getSpider(String key, String cls, String ext) {
        String clsKey = cls.replace("csp_", "");
        if (spidersMap.containsKey(key)) return spidersMap.get(key);
        if (classLoader == null) return new SpiderNull();
        try {
            Spider sp = loadSpider(clsKey);
            sp.init(App.getInstance(), ext);
            spidersMap.put(key, sp);
            return sp;
        } catch (Throwable th) {
            L.e("getSpider "+th.getMessage());
        }
        return new SpiderNull();
    }

    /**
     * 类名可能是护壳 jar 提供的空壳（如 App99Guard，继承 BaseSpiderGuard 并在内部由 Init 原生加载器
     * 按名取真实实现），也可能直接是真实类名（如 csp_AppRJ 用的 AppRJ）。后者不在 jar 外层 dex 里，
     * 只存在于护壳 native 解密出来的 dex 中，所以外层 DexClassLoader 找不到，必须走 Init 的兜底。
     */
    private Spider loadSpider(String clsKey) throws Throwable {
        String fqn = "com.github.catvod.spider." + clsKey;
        try {
            return (Spider) classLoader.loadClass(fqn).newInstance();
        } catch (ClassNotFoundException e) {
            Spider sp = loadByInit(fqn);
            if (sp != null) {
                L.w("getSpider " + fqn + " 外层找不到，已走 Init 原生加载器兜底");
                return sp;
            }
            throw e;
        }
    }

    private Spider loadByInit(String fqn) {
        try {
            Class initCls = classLoader.loadClass("com.github.catvod.spider.Init");
            Method mth = initCls.getMethod("getSpider", String.class);
            Object obj = mth.invoke(null, fqn);
            if (obj instanceof Spider) return (Spider) obj;
        } catch (Throwable th) {
            L.w("getSpider Init 兜底失败 " + th.getMessage());
        }
        return null;
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        try {
            String clsKey = "Json" + key;
            String hotClass = "com.github.catvod.parser." + clsKey;
            Class jsonParserCls = classLoader.loadClass(hotClass);
            Method mth = jsonParserCls.getMethod("parse", LinkedHashMap.class, String.class);
            return (JSONObject) mth.invoke(null, jxs, url);
        } catch (Throwable th) {
            L.e("jsonExt "+th.getMessage());
        }
        return null;
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        try {
            String clsKey = "Mix" + key;
            String hotClass = "com.github.catvod.parser." + clsKey;
            Class jsonParserCls = classLoader.loadClass(hotClass);
            Method mth = jsonParserCls.getMethod("parse", LinkedHashMap.class, String.class, String.class, String.class);
            return (JSONObject) mth.invoke(null, jxs, name, flag, url);
        } catch (Throwable th) {
            L.e("jsonExtMix "+th.getMessage());
        }
        return null;
    }

    public Object[] proxyInvoke(Map params) {
        try {
            if (proxyFun != null) {
                return (Object[]) proxyFun.invoke(null, params);
            }
        } catch (Throwable th) {
            L.e("proxyInvoke "+th.getMessage());
        }
        return null;
    }
}
