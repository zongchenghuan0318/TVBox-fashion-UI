package com.fongmi.quickjs.method;

import androidx.annotation.Keep;

import com.fongmi.quickjs.bean.Req;
import com.fongmi.quickjs.utils.Connect;
import com.fongmi.quickjs.utils.Crypto;
import com.fongmi.quickjs.utils.JSUtil;
import com.fongmi.quickjs.utils.Parser;
import com.github.catvod.Proxy;
import com.github.catvod.utils.UriUtil;
import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSMethod;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class Global {

    private final QuickJSContext ctx;
    private final ExecutorService executor;
    private final Parser parser;
    private final Timer timer;

    private Global(QuickJSContext ctx, ExecutorService executor) {
        this.ctx = ctx;
        this.executor = executor;
        this.parser = new Parser();
        this.timer = new Timer("quickjs-timer", true);
    }

    public static Global create(QuickJSContext ctx, ExecutorService executor) {
        return new Global(ctx, executor);
    }

    public void setProperty() {
        for (final Method method : getClass().getMethods()) {
            if (!method.isAnnotationPresent(JSMethod.class)) continue;
            ctx.getGlobalObject().setProperty(method.getName(), new com.whl.quickjs.wrapper.JSCallFunction() {
                @Override
                public Object call(Object... args) {
                    return Global.this.OooO00o(method, args);
                }
            });
        }
    }

    private Object OooO00o(Method method, Object[] args) {
        try {
            return method.invoke(this, args);
        } catch (Throwable e) {
            return null;
        }
    }

    private void submit(Runnable runnable) {
        try {
            if (executor.isShutdown()) return;
            executor.submit(runnable);
        } catch (Throwable ignored) {
        }
    }

    @Keep
    @JSMethod
    public String s2t(String text) {
        return text;
    }

    @Keep
    @JSMethod
    public String t2s(String text) {
        return text;
    }

    @Keep
    @JSMethod
    public String getProxy(Boolean local) {
        return Proxy.getUrl(!(local == null || !local)) + "?do=js";
    }

    @Keep
    @JSMethod
    public String js2Proxy(Boolean dynamic, Integer siteType, String siteKey, String url, JSObject headers) {
        String proxy = getProxy(dynamic == null || !dynamic);
        try {
            return proxy + "&from=catvod&siteType=" + siteType + "&siteKey=" + siteKey + "&header=" + URLEncoder.encode(headers.stringify(), "UTF-8") + "&url=" + URLEncoder.encode(url, "UTF-8");
        } catch (Exception e) {
            return proxy;
        }
    }

    @Keep
    @JSMethod
    public Object setTimeout(JSFunction func, Integer delay) {
        if (func == null) return null;
        try {
            func.hold();
            schedule(func, delay == null ? 0 : delay);
        } catch (Throwable ignored) {
        }
        return null;
    }

    public void schedule(JSFunction func, int delay) {
        try {
            timer.schedule(new Timeout(func), Math.max(0, delay));
        } catch (Throwable ignored) {
            try {
                func.release();
            } catch (Throwable ignored2) {
            }
        }
    }

    @Keep
    @JSMethod
    public JSObject _http(String url, JSObject options) {
        try {
            JSFunction complete = options.getJSFunction("complete");
            if (complete == null) return req(url, options);
            requestAsync(url, options, complete);
        } catch (Throwable ignored) {
        }
        return null;
    }

    @Keep
    @JSMethod
    public JSObject req(String url, JSObject options) {
        try {
            Req req = Req.objectFrom(options.stringify());
            Response res = Connect.to(url, req).execute();
            return Connect.success(ctx, req, res);
        } catch (Exception e) {
            return Connect.error(ctx);
        }
    }

    @Keep
    @JSMethod
    public String joinUrl(String parent, String child) {
        return UriUtil.resolve(parent, child);
    }

    @Keep
    @JSMethod
    public String md5X(String text) {
        return Crypto.md5(text);
    }

    @Keep
    @JSMethod
    public String aesX(String mode, boolean encrypt, String input, boolean inBase64, String key, String iv, boolean outBase64) {
        return Crypto.aes(mode, encrypt, input, inBase64, key, iv, outBase64);
    }

    @Keep
    @JSMethod
    public String rsaX(String mode, boolean pub, boolean encrypt, String input, boolean inBase64, String key, boolean outBase64) {
        return Crypto.rsa(mode, pub, encrypt, input, inBase64, key, outBase64);
    }

    @Keep
    @JSMethod
    public String gbkDecode(JSArray array) {
        return JSUtil.decodeTo("GB2312", array);
    }

    @Keep
    @JSMethod
    public String pd(String html, String rule, String url) {
        return parser.parseDomForUrl(html, rule, url);
    }

    @Keep
    @JSMethod
    public JSArray pdfa(String html, String rule) {
        return JSUtil.toArray(ctx, parser.parseDomForArray(html, rule));
    }

    @Keep
    @JSMethod
    public String pdfh(String html, String rule) {
        return parser.parseDomForUrl(html, rule, "");
    }

    @Keep
    @JSMethod
    public JSArray pdfl(String html, String rule, String st, String rt, String url) {
        return JSUtil.toArray(ctx, parser.parseDomForList(html, rule, st, rt, url));
    }

    private void requestAsync(String url, JSObject options, JSFunction complete) {
        try {
            Req req = Req.objectFrom(options.stringify());
            Connect.to(url, req).enqueue(getCallback(complete, req));
        } catch (Throwable e) {
            completeError(complete);
        }
    }

    public Callback getCallback(final JSFunction complete, final Req req) {
        return new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                completeError(complete);
            }

            @Override
            public void onResponse(Call call, Response res) {
                completeSuccess(complete, req, res);
            }
        };
    }

    private void completeSuccess(final JSFunction complete, final Req req, final Response res) {
        submit(new Runnable() {
            @Override
            public void run() {
                try {
                    complete.call(Connect.success(Global.this.ctx, req, res));
                } finally {
                    try {
                        complete.release();
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    private void completeError(final JSFunction complete) {
        submit(new Runnable() {
            @Override
            public void run() {
                try {
                    complete.call(Connect.error(Global.this.ctx));
                } finally {
                    try {
                        complete.release();
                    } catch (Throwable ignored) {
                    }
                }
            }
        });
    }

    private class Timeout extends TimerTask {

        private final JSFunction func;

        private Timeout(JSFunction func) {
            this.func = func;
        }

        @Override
        public void run() {
            try {
                func.call();
            } catch (Throwable ignored) {
            } finally {
                try {
                    func.release();
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
