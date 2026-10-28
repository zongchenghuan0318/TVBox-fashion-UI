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
    private final ConcurrentHashMap<String, DexClassLoader> extraLoaders = new ConcurrentHashMap<>();
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
            classLoader = newDexClassLoader(cacheFile);
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

    private DexClassLoader newDexClassLoader(String cacheFile) {
        File cacheDir = new File(App.getInstance().getCacheDir().getAbsolutePath() + "/catvod_csp");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        return new DexClassLoader(cacheFile, cacheDir.getAbsolutePath(), null, App.getInstance().getClassLoader());
    }

    /**
     * 加载站点级 jar（配置里 site.jar 指向的包）。这类包里的站点类不在全局 spider jar 中
     * （例如 csp_AppRJ 只存在于 feimao1004.jar），必须各自用独立 DexClassLoader 加载，
     * 否则会与全局 jar 的 Init/Proxy 互相覆盖。已加载过的直接复用。
     */
    public boolean loadExtra(String cacheFile) {
        if (cacheFile == null || cacheFile.isEmpty()) return false;
        if (extraLoaders.containsKey(cacheFile)) return true;
        try {
            extraLoaders.put(cacheFile, newDexClassLoader(cacheFile));
            L.w("JarLoader.loadExtra ok " + cacheFile);
            return true;
        } catch (Throwable th) {
            L.e(th);
        }
        return false;
    }

    public Spider getSpiderByKey(String key) {
        return key == null ? null : spidersMap.get(key);
    }

    public Spider getSpider(String key, String cls, String ext) {
        return getSpider(key, cls, ext, "");
    }

    public Spider getSpider(String key, String cls, String ext, String jarPath) {
        String clsKey = cls.replace("csp_", "");
        if (spidersMap.containsKey(key)) return spidersMap.get(key);
        //站点自带 jar 的类优先从该 jar 取；没有站点 jar 时用全局 spider jar
        DexClassLoader loader = jarPath == null || jarPath.isEmpty() ? null : extraLoaders.get(jarPath);
        if (loader == null) loader = classLoader;
        if (loader == null) return new SpiderNull();
        try {
            Spider sp = loadSpider(loader, clsKey);
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
    private Spider loadSpider(DexClassLoader loader, String clsKey) throws Throwable {
        String fqn = "com.github.catvod.spider." + clsKey;
        try {
            return (Spider) loader.loadClass(fqn).newInstance();
        } catch (ClassNotFoundException e) {
            Spider sp = loadByInit(loader, fqn);
            if (sp != null) {
                L.w("getSpider " + fqn + " 外层找不到，已走 Init 原生加载器兜底");
                return sp;
            }
            throw e;
        }
    }

    private Spider loadByInit(DexClassLoader loader, String fqn) {
        try {
            Class initCls = loader.loadClass("com.github.catvod.spider.Init");
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
