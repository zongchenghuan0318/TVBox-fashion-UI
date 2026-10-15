package com.github.tvbox.osc.util.js;

/**
 * 去除 UTF-8 BOM 头的小工具（对齐目标 v6.1.7 的 com.github.tvbox.osc.util.js.UTF8BOMFighter）。
 */
public class UTF8BOMFighter {
    private static final byte[] UTF8_BOM_BYTES = {-17, -69, -65};

    private UTF8BOMFighter() {
    }

    public static String removeUTF8BOM(String str) {
        byte[] bytes = str.getBytes();
        if (bytes.length > 3) {
            byte b = bytes[0];
            byte[] bArr = UTF8_BOM_BYTES;
            if (b == bArr[0] && bytes[1] == bArr[1] && bytes[2] == bArr[2]) {
                return new String(bytes, 3, bytes.length - 3);
            }
        }
        return str;
    }

    public static byte[] removeUTF8BOM(byte[] bArr) {
        if (bArr.length > 3) {
            byte b = bArr[0];
            byte[] bArr2 = UTF8_BOM_BYTES;
            if (b == bArr2[0] && bArr[1] == bArr2[1] && bArr[2] == bArr2[2]) {
                byte[] bArr3 = new byte[bArr.length - 3];
                System.arraycopy(bArr, 3, bArr3, 0, bArr.length - 3);
                return bArr3;
            }
        }
        return bArr;
    }
}
