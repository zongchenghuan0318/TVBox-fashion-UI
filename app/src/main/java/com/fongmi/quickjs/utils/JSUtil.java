package com.fongmi.quickjs.utils;

import com.whl.quickjs.wrapper.JSArray;
import com.whl.quickjs.wrapper.JSObject;
import com.whl.quickjs.wrapper.QuickJSContext;

import java.util.List;
import java.util.Map;

public class JSUtil {

    public static String decodeTo(String charset, JSArray array) {
        try {
            int length = array.length();
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                Object value = array.get(i);
                if (value instanceof Number) bytes[i] = ((Number) value).byteValue();
                else bytes[i] = (byte) Integer.parseInt(String.valueOf(value));
            }
            return new String(bytes, charset);
        } catch (Exception e) {
            return "";
        }
    }

    public static JSArray toArray(QuickJSContext ctx, List<String> items) {
        JSArray array = ctx.createNewJSArray();
        if (items == null || items.isEmpty()) return array;
        for (int i = 0; i < items.size(); i++) array.set(items.get(i), i);
        return array;
    }

    public static JSArray toArray(QuickJSContext ctx, byte[] bytes) {
        JSArray array = ctx.createNewJSArray();
        if (bytes == null || bytes.length == 0) return array;
        for (int i = 0; i < bytes.length; i++) array.set((int) bytes[i], i);
        return array;
    }

    public static JSObject toObj(QuickJSContext ctx, Map<String, String> map) {
        JSObject obj = ctx.createNewJSObject();
        if (map == null || map.isEmpty()) return obj;
        for (String key : map.keySet()) {
            String value = map.get(key);
            if (key != null && value != null) obj.setProperty(key, value);
        }
        return obj;
    }
}
