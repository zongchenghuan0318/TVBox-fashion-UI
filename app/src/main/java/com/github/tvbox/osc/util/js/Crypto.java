package com.github.tvbox.osc.util.js;

import android.util.Base64;

import java.security.Key;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES / RSA 加解密工具（对齐目标 v6.1.7 的 com.github.tvbox.osc.util.js.Crypto）。
 * 说明：目标中 URL 安全 Base64 的 {@code -} 替换目标常量由 R8 常量折叠而来，此处还原为字面量 "+"。
 */
public class Crypto {

    private static final String PLUS = "+";

    public static String aes(String str, boolean z, String str2, boolean z2, String str3, String str4, boolean z3) {
        byte[] bytes;
        byte[] bytes2;
        try {
            byte[] bytes3 = str3.getBytes();
            if (bytes3.length < 16) {
                bytes3 = Arrays.copyOf(bytes3, 16);
            }
            if (str4 == null) {
                bytes = new byte[0];
            } else {
                bytes = str4.getBytes();
            }
            if (bytes.length < 16) {
                bytes = Arrays.copyOf(bytes, 16);
            }
            Cipher cipher = Cipher.getInstance(str + "Padding");
            SecretKeySpec secretKeySpec = new SecretKeySpec(bytes3, "AES");
            int i = 1;
            if (str4 == null) {
                if (!z) {
                    i = 2;
                }
                cipher.init(i, secretKeySpec);
            } else {
                if (!z) {
                    i = 2;
                }
                cipher.init(i, secretKeySpec, new IvParameterSpec(bytes));
            }
            if (z2) {
                bytes2 = Base64.decode(str2.replaceAll("_", "/").replaceAll("-", PLUS), 0);
            } else {
                bytes2 = str2.getBytes("UTF-8");
            }
            if (z3) {
                return Base64.encodeToString(cipher.doFinal(bytes2), 2);
            }
            return new String(cipher.doFinal(bytes2), "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private static byte[] concatArrays(byte[] bArr, byte[] bArr2) {
        int length = bArr.length;
        int length2 = bArr2.length;
        byte[] bArr3 = new byte[length + length2];
        System.arraycopy(bArr, 0, bArr3, 0, length);
        System.arraycopy(bArr2, 0, bArr3, length, length2);
        return bArr3;
    }

    private static Key generateKey(boolean z, String str) throws Exception {
        String replace;
        if (z) {
            replace = str.replaceAll("\r\n", "").replaceAll("\n", "").replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "");
        } else {
            replace = str.replaceAll("\r\n", "").replaceAll("\n", "").replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "");
        }
        if (z) {
            return KeyFactory.getInstance(RSAEncrypt.KEY_ALGORITHM).generatePublic(new X509EncodedKeySpec(Base64.decode(replace, 0)));
        }
        return KeyFactory.getInstance(RSAEncrypt.KEY_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(Base64.decode(replace, 0)));
    }

    private static int getModulusLength(Key key) {
        if (key instanceof PublicKey) {
            return ((RSAPublicKey) key).getModulus().bitLength();
        }
        return ((RSAPrivateKey) key).getModulus().bitLength();
    }

    public static String rsa(boolean z, boolean z2, String str, boolean z3, String str2, boolean z4) {
        byte[] bytes;
        int i;
        int i2;
        try {
            Key generateKey = generateKey(z, str2);
            int modulusLength = getModulusLength(generateKey);
            byte[] bArr = new byte[0];
            if (z3) {
                bytes = Base64.decode(str.replaceAll("_", "/").replaceAll("-", PLUS), 0);
            } else {
                bytes = str.getBytes("UTF-8");
            }
            Cipher cipher = Cipher.getInstance(RSAEncrypt.ECB_PKCS1_PADDING);
            if (z2) {
                i = 1;
            } else {
                i = 2;
            }
            cipher.init(i, generateKey);
            if (z2) {
                i2 = (modulusLength / 8) - 11;
            } else {
                i2 = modulusLength / 8;
            }
            int i3 = 0;
            while (i3 < bytes.length) {
                int min = Math.min(i3 + i2, bytes.length);
                int i4 = min - i3;
                byte[] bArr2 = new byte[i4];
                System.arraycopy(bytes, i3, bArr2, 0, i4);
                bArr = concatArrays(bArr, cipher.doFinal(bArr2));
                i3 = min;
            }
            if (z4) {
                return Base64.encodeToString(bArr, 2);
            }
            return new String(bArr, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }
}
