package com.github.tvbox.osc.util.js;

import android.util.Base64;

import java.nio.ByteBuffer;

/**
 * 字节/Base64 辅助工具（对齐目标 v6.1.7 的 com.github.tvbox.osc.util.js.DataUtils）。
 */
public class DataUtils {
    public static byte[] base64Decode(String str) {
        return Base64.decode(str.getBytes(), 2);
    }

    public static String base64Encode(byte[] bArr) {
        return Base64.encodeToString(bArr, 2);
    }

    public static int byte2Int(byte[] bArr) {
        return ByteBuffer.wrap(bArr).getInt();
    }

    public static byte[] int2byte(int i) {
        ByteBuffer allocate = ByteBuffer.allocate(4);
        allocate.putInt(i);
        return allocate.array();
    }
}
