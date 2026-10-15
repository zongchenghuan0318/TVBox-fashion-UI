package com.fongmi.quickjs.method;

import com.whl.quickjs.wrapper.JSCallFunction;
import com.whl.quickjs.wrapper.JSFunction;
import com.whl.quickjs.wrapper.JSObject;

public class Function {

    private final Object[] args;
    private final JSCallFunction callback;
    private final String name;
    private final JSObject object;
    private Object result;

    private Function(JSObject object, String name, Object[] args) {
        this.object = object;
        this.name = name;
        this.args = args;
        this.callback = new JSCallFunction() {
            @Override
            public Object call(Object... values) {
                result = values != null && values.length > 0 ? values[0] : null;
                return null;
            }
        };
    }

    public static Function call(JSObject object, String name, Object[] args) {
        return new Function(object, name, args);
    }

    public Object call() {
        JSFunction func = object == null ? null : object.getJSFunction(name);
        if (func == null) return null;
        try {
            Object value = func.call(args == null ? new Object[0] : args);
            if (value instanceof JSObject) then(value);
            else result = value;
        } catch (Throwable ignored) {
        } finally {
            try {
                func.release();
            } catch (Throwable ignored) {
            }
        }
        return result;
    }

    public void then(Object value) {
        if (!(value instanceof JSObject)) {
            result = value;
            return;
        }
        JSFunction then = ((JSObject) value).getJSFunction("then");
        if (then == null) {
            result = value;
            return;
        }
        try {
            then.call(callback);
        } catch (Throwable ignored) {
        } finally {
            try {
                then.release();
            } catch (Throwable ignored) {
            }
        }
    }
}
