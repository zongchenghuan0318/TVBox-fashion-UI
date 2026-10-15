package com.fongmi.quickjs.method;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.Keep;

import com.github.tvbox.osc.base.App;
import com.whl.quickjs.wrapper.JSMethod;

public class Local {

    private static final String NAME = "js_local";

    private static SharedPreferences getPrefers() {
        return App.getInstance().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    private String getKey(String rule, String key) {
        return "cache_" + (TextUtils.isEmpty(rule) ? "" : rule + "_") + key;
    }

    @Keep
    @JSMethod
    public String get(String rule, String key) {
        try {
            return getPrefers().getString(getKey(rule, key), "");
        } catch (Exception e) {
            return "";
        }
    }

    @Keep
    @JSMethod
    public void set(String rule, String key, String value) {
        try {
            getPrefers().edit().putString(getKey(rule, key), value).apply();
        } catch (Exception ignored) {
        }
    }

    @Keep
    @JSMethod
    public void delete(String rule, String key) {
        try {
            getPrefers().edit().remove(getKey(rule, key)).apply();
        } catch (Exception ignored) {
        }
    }
}
