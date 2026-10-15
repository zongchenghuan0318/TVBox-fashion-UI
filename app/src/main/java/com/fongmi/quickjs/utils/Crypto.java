package com.fongmi.quickjs.utils;

import android.util.Base64;

import java.nio.charset.Charset;
import java.security.Key;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class Crypto {

    private static final String AES = "AES";
    private static final String RSA = "RSA";
    private static final int AES_BLOCK_SIZE = 16;
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static String md5(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            return toHex(MessageDigest.getInstance("MD5").digest(value.getBytes(UTF_8)));
        } catch (Exception e) {
            return "";
        }
    }

    public static String aes(String mode, boolean encrypt, String input, boolean inputBase64, String key, String iv, boolean outputBase64) {
        try {
            Cipher cipher = Cipher.getInstance(getTransformation(mode, AES));
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, new SecretKeySpec(padParameter(key.getBytes(UTF_8), AES_BLOCK_SIZE), AES), new IvParameterSpec(getIv(iv, AES_BLOCK_SIZE)));
            return encode(cipher.doFinal(decode(input, inputBase64)), outputBase64);
        } catch (Exception e) {
            return "";
        }
    }

    public static String rsa(String mode, boolean pub, boolean encrypt, String input, boolean inputBase64, String key, boolean outputBase64) {
        try {
            Cipher cipher = Cipher.getInstance(getTransformation(mode, RSA));
            cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, generateKey(pub, key));
            return encode(cipher.doFinal(decode(input, inputBase64)), outputBase64);
        } catch (Exception e) {
            return "";
        }
    }

    public static byte[] concatArrays(byte[] first, byte[] second) {
        if (first == null) first = new byte[0];
        if (second == null) second = new byte[0];
        byte[] result = new byte[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    public static Key generateKey(boolean pub, String key) throws Exception {
        byte[] data = decode(key, true);
        KeyFactory factory = KeyFactory.getInstance(RSA);
        return pub ? factory.generatePublic(new X509EncodedKeySpec(data)) : factory.generatePrivate(new PKCS8EncodedKeySpec(data));
    }

    public static int getModulusLength(Key key) {
        if (key instanceof RSAKey) return ((RSAKey) key).getModulus().bitLength();
        return 0;
    }

    private static String getTransformation(String mode, String algorithm) {
        if (mode == null || mode.trim().isEmpty() || AES.equalsIgnoreCase(mode) || RSA.equalsIgnoreCase(mode)) return algorithm;
        if (mode.contains("/")) return mode;
        if ("CBC".equalsIgnoreCase(mode) || "ECB".equalsIgnoreCase(mode)) return algorithm + "/" + mode.toUpperCase() + "/PKCS5Padding";
        return algorithm + "/ECB/" + mode;
    }

    private static byte[] padParameter(byte[] data, int size) {
        if (data == null) data = new byte[0];
        byte[] result = new byte[size];
        System.arraycopy(data, 0, result, 0, Math.min(data.length, size));
        return result;
    }

    private static byte[] getIv(String iv, int size) {
        return padParameter(iv == null ? new byte[0] : iv.getBytes(UTF_8), size);
    }

    private static byte[] decode(String input, boolean base64) {
        if (input == null) return new byte[0];
        return base64 ? Base64.decode(input, Base64.DEFAULT | Base64.NO_WRAP) : input.getBytes(UTF_8);
    }

    private static String encode(byte[] data, boolean base64) {
        return base64 ? Base64.encodeToString(data, Base64.DEFAULT | Base64.NO_WRAP) : new String(data, UTF_8);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) builder.append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
        return builder.toString();
    }
}
