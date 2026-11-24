package com.fongmi.quickjs.method;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

public class Async {

    // 替代 CompletableFuture（API 24+），保持 minSdkVersion 21 兼容
    public static final class Result {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicBoolean done = new AtomicBoolean(false);
        private Object value;
        private Throwable error;

        public static Result completed(Object value) {
            Result result = new Result();
            result.complete(value);
            return result;
        }

        void complete(Object value) {
            if (done.compareAndSet(false, true)) {
                this.value = value;
                latch.countDown();
            }
        }

        void completeExceptionally(Throwable error) {
            if (done.compareAndSet(false, true)) {
                this.error = error;
                latch.countDown();
            }
        }

        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            if (!latch.await(timeout, unit)) {
                throw new TimeoutException();
            }
            if (error != null) {
                throw new ExecutionException(error);
            }
            return value;
        }
    }

    private final Result future;

    private final JSCallFunction success = new JSCallFunction() {
        @Override
        public Object call(Object... args) {
            future.complete(args != null && args.length > 0 ? args[0] : null);
            return null;
        }
    };

    private final JSCallFunction error = new JSCallFunction() {
        @Override
        public Object call(Object... args) {
            String msg = args != null && args.length > 0 && args[0] != null ? args[0].toString() : "";
            future.completeExceptionally(new Exception(msg));
            return null;
        }
    };

    private Async() {
        this.future = new Result();
    }

    public static Result run(JSObject object, String name, Object... args) {
        return new Async().call(object, name, args);
    }

    private Result call(JSObject object, String name, Object... args) {
        JSFunction func = object.getJSFunction(name);
        if (func == null) return empty();
        call(func, args);
        return future;
    }

    private Result empty() {
        future.complete(null);
        return future;
    }

    private void call(JSFunction func, Object... args) {
        try {
            Object result = func.call(args);
            if (result instanceof JSObject) then((JSObject) result);
            else future.complete(result);
        } catch (Throwable e) {
            future.completeExceptionally(e);
        } finally {
            func.release();
        }
    }

    private void then(JSObject promise) {
        JSFunction then = promise.getJSFunction("then");
        if (then == null) {
            future.complete(promise);
        } else {
            consume(then, success);
            consume(promise.getJSFunction("catch"), error);
        }
    }

    private void consume(JSFunction func, JSCallFunction callback) {
        if (func == null) return;
        try {
            func.call(callback);
        } finally {
            func.release();
        }
    }
}
