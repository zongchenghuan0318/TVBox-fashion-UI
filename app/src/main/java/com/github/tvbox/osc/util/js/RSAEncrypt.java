package com.github.tvbox.osc.util.js;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * RSA 加解密工具（对齐目标 v6.1.7 的 com.github.tvbox.osc.util.js.RSAEncrypt）。
 */
public class RSAEncrypt {
    public static final String ECB_PKCS1_PADDING = "RSA/ECB/PKCS1Padding";
    public static final String KEY_ALGORITHM = "RSA";
    private static final int MAX_DECRYPT_BLOCK = 128;
    private static final int MAX_ENCRYPT_BLOCK = 117;
    public static final String TAG = "RSAEncrypt --> ";

    public static String decryptByPrivateKey(String str, String str2, int i, boolean z) {
        return decryptByPrivateKey(str, str2, ECB_PKCS1_PADDING, i, z);
    }

    public static String decryptByPublicKey(String str, String str2, int i, boolean z) {
        return decryptByPublicKey(str, str2, ECB_PKCS1_PADDING, i, z);
    }

    public static String encryptByPrivateKey(String str, String str2, int i, boolean z) {
        return encryptByPrivateKey(str, str2, ECB_PKCS1_PADDING, i, z);
    }

    public static String encryptByPublicKey(String str, String str2, int i, boolean z) {
        return encryptByPublicKey(str, str2, ECB_PKCS1_PADDING, i, z);
    }

    public static KeyPair getKeyPair(int i) {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(KEY_ALGORITHM);
            keyPairGenerator.initialize(i);
            return keyPairGenerator.genKeyPair();
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    public static PrivateKey getPrivateKey(String str) {
        try {
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePrivate(new PKCS8EncodedKeySpec(DataUtils.base64Decode(str)));
        } catch (NullPointerException unused) {
            handleException(new Exception("私钥数据为空"));
            return null;
        } catch (NoSuchAlgorithmException unused2) {
            handleException(new Exception("无此算法"));
            return null;
        } catch (InvalidKeySpecException unused3) {
            handleException(new Exception("私钥非法"));
            return null;
        }
    }

    public static String getPrivateKeyBase64(PrivateKey privateKey) {
        return DataUtils.base64Encode(privateKey.getEncoded());
    }

    public static PublicKey getPublicKey(String str) {
        try {
            return KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(new X509EncodedKeySpec(DataUtils.base64Decode(str)));
        } catch (NullPointerException unused) {
            handleException(new Exception("公钥数据为空"));
            return null;
        } catch (NoSuchAlgorithmException unused2) {
            handleException(new Exception("无此算法"));
            return null;
        } catch (InvalidKeySpecException unused3) {
            handleException(new Exception("公钥非法"));
            return null;
        }
    }

    public static String getPublicKeyBase64(PublicKey publicKey) {
        return DataUtils.base64Encode(publicKey.getEncoded());
    }

    private static void handleException(Exception exc) {
        exc.printStackTrace();
        String str = TAG;
        Log.e(str, str + exc);
    }

    public static String decryptByPrivateKey(String str, String str2, String str3, int i, boolean z) {
        byte[] doFinal;
        try {
            byte[] base64Decode = DataUtils.base64Decode(str);
            Cipher cipher = Cipher.getInstance(str3);
            RSAPrivateKey rSAPrivateKey = (RSAPrivateKey) getPrivateKey(str2);
            cipher.init(2, rSAPrivateKey);
            if (i == 1) {
                return new String(cipher.doFinal(base64Decode));
            }
            int bitLength = z ? rSAPrivateKey.getModulus().bitLength() / 8 : 128;
            int length = base64Decode.length;
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int i2 = 0;
            int i3 = 0;
            while (true) {
                int i4 = length - i2;
                if (i4 > 0) {
                    if (i4 > bitLength) {
                        doFinal = cipher.doFinal(base64Decode, i2, bitLength);
                    } else {
                        doFinal = cipher.doFinal(base64Decode, i2, i4);
                    }
                    byteArrayOutputStream.write(doFinal, 0, doFinal.length);
                    i3++;
                    i2 = i3 * bitLength;
                } else {
                    byteArrayOutputStream.close();
                    return byteArrayOutputStream.toString("UTF-8");
                }
            }
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    public static String decryptByPublicKey(String str, String str2, String str3, int i, boolean z) {
        byte[] doFinal;
        try {
            byte[] base64Decode = DataUtils.base64Decode(str);
            Cipher cipher = Cipher.getInstance(str3);
            RSAPublicKey rSAPublicKey = (RSAPublicKey) getPublicKey(str2);
            cipher.init(2, rSAPublicKey);
            if (i == 1) {
                return new String(cipher.doFinal(base64Decode));
            }
            int bitLength = z ? rSAPublicKey.getModulus().bitLength() / 8 : 128;
            int length = base64Decode.length;
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int i2 = 0;
            int i3 = 0;
            while (true) {
                int i4 = length - i2;
                if (i4 > 0) {
                    if (i4 > bitLength) {
                        doFinal = cipher.doFinal(base64Decode, i2, bitLength);
                    } else {
                        doFinal = cipher.doFinal(base64Decode, i2, i4);
                    }
                    byteArrayOutputStream.write(doFinal, 0, doFinal.length);
                    i3++;
                    i2 = i3 * bitLength;
                } else {
                    byteArrayOutputStream.close();
                    return byteArrayOutputStream.toString("UTF-8");
                }
            }
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    public static String encryptByPrivateKey(String str, String str2, String str3, int i, boolean z) {
        byte[] doFinal;
        try {
            byte[] bytes = str.getBytes("UTF-8");
            Cipher cipher = Cipher.getInstance(str3);
            RSAPrivateKey rSAPrivateKey = (RSAPrivateKey) getPrivateKey(str2);
            cipher.init(1, rSAPrivateKey);
            if (i == 1) {
                return DataUtils.base64Encode(cipher.doFinal(bytes));
            }
            int bitLength = z ? (rSAPrivateKey.getModulus().bitLength() / 8) - 11 : 117;
            int length = bytes.length;
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int i2 = 0;
            int i3 = 0;
            while (true) {
                int i4 = length - i2;
                if (i4 > 0) {
                    if (i4 > bitLength) {
                        doFinal = cipher.doFinal(bytes, i2, bitLength);
                    } else {
                        doFinal = cipher.doFinal(bytes, i2, i4);
                    }
                    byteArrayOutputStream.write(doFinal, 0, doFinal.length);
                    i3++;
                    i2 = i3 * bitLength;
                } else {
                    byte[] byteArray = byteArrayOutputStream.toByteArray();
                    byteArrayOutputStream.close();
                    return DataUtils.base64Encode(byteArray);
                }
            }
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }

    public static String encryptByPublicKey(String str, String str2, String str3, int i, boolean z) {
        byte[] doFinal;
        try {
            byte[] bytes = str.getBytes("UTF-8");
            Cipher cipher = Cipher.getInstance(str3);
            RSAPublicKey rSAPublicKey = (RSAPublicKey) getPublicKey(str2);
            cipher.init(1, rSAPublicKey);
            if (i == 1) {
                return DataUtils.base64Encode(cipher.doFinal(bytes));
            }
            int bitLength = z ? (rSAPublicKey.getModulus().bitLength() / 8) - 11 : 117;
            int length = bytes.length;
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int i2 = 0;
            int i3 = 0;
            while (true) {
                int i4 = length - i2;
                if (i4 > 0) {
                    if (i4 > bitLength) {
                        doFinal = cipher.doFinal(bytes, i2, bitLength);
                    } else {
                        doFinal = cipher.doFinal(bytes, i2, i4);
                    }
                    byteArrayOutputStream.write(doFinal, 0, doFinal.length);
                    i3++;
                    i2 = i3 * bitLength;
                } else {
                    byte[] byteArray = byteArrayOutputStream.toByteArray();
                    byteArrayOutputStream.close();
                    return DataUtils.base64Encode(byteArray);
                }
            }
        } catch (Exception e) {
            handleException(e);
            return null;
        }
    }
}
