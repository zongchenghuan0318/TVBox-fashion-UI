package com.github.tvbox.osc.util.python;

import com.github.catvod.crawler.Spider;
import com.github.catvod.crawler.SpiderNull;
import com.github.tvbox.osc.base.App;
import com.undcover.freedom.pyramid.Loader;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private final Loader loader = new Loader();
    private volatile String recent;

    public Spider getSpider(String key, String api, String ext) {
        Spider spider = spiders.get(key);
        if (spider == null) {
            synchronized (spiders) {
                spider = spiders.get(key);
                if (spider == null) {
                    try {
                        Spider pySpider = loader.spider(App.getInstance(), key, api);
                        pySpider.init(App.getInstance(), ext);
                        spider = pySpider;
                    } catch (Throwable e) {
                        e.printStackTrace();
                        spider = new SpiderNull();
                    }
                    spiders.put(key, spider);
                }
            }
        }
        recent = key;
        return spider;
    }

    public Spider getSpiderByKey(String key) {
        return key == null ? null : spiders.get(key);
    }

    public Object[] proxyLocal(Map<String, String> params) {
        Spider spider = recent == null ? null : spiders.get(recent);
        return spider == null ? null : spider.proxyLocal(params);
    }

    public void clear() {
        for (Spider spider : spiders.values()) {
            if (spider != null) {
                try {
                    spider.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
        spiders.clear();
        recent = null;
    }
}
