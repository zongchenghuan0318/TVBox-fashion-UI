package com.github.tvbox.osc.util.js;

import android.content.Context;

import com.fongmi.quickjs.bean.Res;
import com.fongmi.quickjs.method.Async;
import com.fongmi.quickjs.method.Console;
import com.fongmi.quickjs.method.Global;
import com.fongmi.quickjs.method.Local;
import com.fongmi.quickjs.utils.JSUtil;
import com.fongmi.quickjs.utils.Module;
import com.github.catvod.utils.Asset;
import com.github.catvod.utils.Json;
import com.github.catvod.utils.UriUtil;
import com.github.catvod.utils.Util;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class SpiderFm extends com.github.catvod.crawler.Spider {

    private final ExecutorService executor;
    private final String key;
    private final String api;

    private QuickJSContext ctx;
    private JSObject jsObject;
    private boolean cat;

    public SpiderFm(String key, String api) {
        this.executor = Executors.newSingleThreadExecutor();
        this.key = key;
        this.api = api;
    }

    private <T> Future<T> submit(Callable<T> callable) {
        return executor.submit(callable);
    }

    private void submit(Runnable runnable) {
        executor.submit(runnable);
    }

    private <T> T await(Future<T> task) throws Exception {
        try {
            return task.get(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            if (!task.isDone()) task.cancel(true);
        }
    }

    private Object call(final String func, final Object... args) {
        Future<CompletableFuture<Object>> task = null;
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            task = submit(new Callable<CompletableFuture<Object>>() {
                @Override
                public CompletableFuture<Object> call() {
                    return Async.run(jsObject, func, args);
                }
            });
            CompletableFuture<Object> result = task.get(15, TimeUnit.SECONDS);
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) return null;
            return result.get(remaining, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Throwable e) {
            return null;
        } finally {
            if (task != null && !task.isDone()) task.cancel(true);
        }
    }

    @Override
    public void init(Context context, String extend) {
        initializeJS();
        try {
            if (cat) {
                call("init", await(submit(new Callable<JSObject>() {
                    @Override
                    public JSObject call() {
                        return cfg(extend);
                    }
                })));
                return;
            }
            Object value = extend;
            try {
                new JSONObject(extend);
                value = await(submit(new Callable<Object>() {
                    @Override
                    public Object call() {
                        return ctx.parse(extend);
                    }
                }));
            } catch (Exception ignored) {
            }
            call("init", new Object[]{value});
        } catch (Throwable ignored) {
        }
    }

    @Override
    public String homeContent(boolean filter) {
        return (String) call("home", filter);
    }

    @Override
    public String homeVideoContent() {
        return (String) call("homeVod");
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        try {
            JSObject obj = await(submit(new Callable<JSObject>() {
                @Override
                public JSObject call() {
                    return JSUtil.toObj(ctx, extend);
                }
            }));
            return (String) call("category", tid, pg, filter, obj);
        } catch (Throwable e) {
            return "";
        }
    }

    @Override
    public String detailContent(List<String> ids) {
        if (ids == null || ids.isEmpty()) return "";
        return (String) call("detail", ids.get(0));
    }

    @Override
    public String searchContent(String keyword, boolean quick) {
        return (String) call("search", keyword, quick);
    }

    @Override
    public String searchContent(String keyword, boolean quick, String pg) {
        return (String) call("search", keyword, quick, pg);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        try {
            JSArray array = await(submit(new Callable<JSArray>() {
                @Override
                public JSArray call() {
                    return JSUtil.toArray(ctx, vipFlags);
                }
            }));
            return (String) call("play", flag, id, array);
        } catch (Throwable e) {
            return "";
        }
    }

    @Override
    public String liveContent() {
        return (String) call("live");
    }

    @Override
    public String action(String action) {
        return (String) call("action", action);
    }

    @Override
    public boolean manualVideoCheck() {
        return Boolean.TRUE.equals(call("sniffer"));
    }

    @Override
    public boolean isVideoFormat(String url) {
        return Boolean.TRUE.equals(call("isVideo", url));
    }

    @Override
    public Object[] proxyLocal(Map<String, String> params) {
        try {
            return "catvod".equals(params.get("from")) ? proxy2(params) : proxy1(params);
        } catch (Throwable e) {
            return null;
        }
    }

    @Override
    public void destroy() {
        try {
            call("destroy");
        } catch (Throwable ignored) {
        }
        try {
            await(submit(new Callable<Object>() {
                @Override
                public Object call() {
                    if (jsObject != null) jsObject.release();
                    if (ctx != null) ctx.destroy();
                    return null;
                }
            }));
        } catch (Throwable ignored) {
        } finally {
            executor.shutdownNow();
        }
    }

    private void initializeJS() {
        try {
            await(submit(new Callable<Object>() {
                @Override
                public Object call() {
                    createCtx();
                    createObj();
                    return null;
                }
            }));
        } catch (Throwable ignored) {
        }
    }

    private void createCtx() {
        QuickJSContext context = QuickJSContext.create();
        ctx = context;
        context.setConsole(new Console());
        ctx.evaluate(Asset.read("js/lib/http.js"));
        Global.create(ctx, executor).setProperty();
        ctx.getGlobalObject().setProperty("local", Local.class);
        ctx.setModuleLoader(new QuickJSContext.BytecodeModuleLoader() {
            @Override
            public byte[] getModuleBytecode(String moduleName) {
                String content = Module.get().fetch(moduleName);
                return content.startsWith("//bb") ? Module.get().bb(content) : SpiderFm.this.ctx.compileModule(content, moduleName);
            }

            @Override
            public String moduleNormalizeName(String baseModuleName, String moduleName) {
                return UriUtil.resolve(baseModuleName, moduleName);
            }
        });
    }

    private void createObj() {
        String spider = "__JS_SPIDER__";
        String global = "globalThis." + spider;
        String content = Module.get().fetch(api);
        cat = content.contains("__jsEvalReturn");
        ctx.evaluateModule(content.replace(spider, global), api);
        ctx.evaluateModule(String.format(Asset.read("js/lib/spider.js"), api));
        jsObject = (JSObject) ctx.getProperty(ctx.getGlobalObject(), spider);
    }

    public JSObject cfg(String extend) {
        try {
            if (Json.isObj(extend)) {
                Object object = ctx.parse(extend);
                if (object instanceof JSObject) return (JSObject) object;
            }
            JSObject object = ctx.createNewJSObject();
            object.setProperty("stype", 3);
            object.setProperty("skey", key == null ? "" : key);
            object.setProperty("ext", extend == null ? "" : extend);
            return object;
        } catch (Throwable e) {
            return ctx.createNewJSObject();
        }
    }

    public Object[] proxy1(final Map<String, String> params) {
        try {
            JSObject object = await(submit(new Callable<JSObject>() {
                @Override
                public JSObject call() {
                    return JSUtil.toObj(ctx, params);
                }
            }));
            Object result = call("proxy", object);
            if (!(result instanceof JSArray)) return null;
            final JSArray proxy = (JSArray) result;
            String json = await(submit(new Callable<String>() {
                @Override
                public String call() {
                    return proxy.stringify();
                }
            }));
            JSONArray array = new JSONArray(json);
            Map<String, String> headers = array.length() > 3 ? Json.toMap(array.optString(3)) : null;
            boolean base64 = array.length() > 4 && array.optInt(4) == 1;
            Object[] objects = new Object[4];
            objects[0] = array.optInt(0);
            objects[1] = array.optString(1);
            objects[2] = getStream(array.opt(2), base64);
            objects[3] = headers;
            return objects;
        } catch (Throwable e) {
            return null;
        }
    }

    public Object[] proxy2(final Map<String, String> params) {
        try {
            final String url = params.get("url");
            final String header = params.get("header");
            JSArray array = await(submit(new Callable<JSArray>() {
                @Override
                public JSArray call() {
                    return JSUtil.toArray(ctx, Arrays.asList(url.split("/")));
                }
            }));
            Object object = await(submit(new Callable<Object>() {
                @Override
                public Object call() {
                    return ctx.parse(header);
                }
            }));
            Object result = call("proxy", array, object);
            Res res = Res.objectFrom(result == null ? "" : result.toString());
            Object[] objects = new Object[3];
            objects[0] = res.getCode();
            objects[1] = res.getContentType();
            objects[2] = res.getStream();
            return objects;
        } catch (Throwable e) {
            return null;
        }
    }

    private ByteArrayInputStream getStream(Object object, boolean base64) {
        if (object instanceof byte[]) return new ByteArrayInputStream((byte[]) object);
        String content = object == null ? "" : object.toString();
        if (base64 && content.contains("base64,")) content = content.split("base64,")[1];
        return new ByteArrayInputStream(base64 ? Util.decode(content) : content.getBytes());
    }
}
