package com.github.catvod.utils;

import com.github.tvbox.osc.base.App;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

public class Asset {

    public static InputStream open(String fileName) {
        try {
            return App.getInstance().getAssets().open(fileName.replace("assets://", ""));
        } catch (Exception e) {
            return null;
        }
    }

    public static String read(String fileName) {
        InputStream is = open(fileName);
        if (is == null) return "";
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) out.write(buffer, 0, len);
            return new String(out.toByteArray(), "UTF-8");
        } catch (Exception e) {
            return "";
        } finally {
            try {
                is.close();
            } catch (Exception ignored) {
            }
        }
    }
}
