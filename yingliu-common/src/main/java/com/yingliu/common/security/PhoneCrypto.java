package com.yingliu.common.security;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * 手机号等敏感标识的加密封装（PRD：手机号保留加密密文 + 精确匹配 HMAC 摘要，后台只读脱敏值）。
 * - HMAC-SHA256 用于精确匹配（唯一索引 uk_phone_hmac）
 * - AES-128-GCM 用于密文存储（带随机 IV，每次加密不同）
 */
public final class PhoneCrypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    private PhoneCrypto() { }

    public static String hmac(String plain, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    public static byte[] encrypt(String plain, String key) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey(key), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);
            return out;
        } catch (Exception e) {
            throw new IllegalStateException("手机号加密失败", e);
        }
    }

    public static String decrypt(byte[] payload, String key) {
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            System.arraycopy(payload, 0, iv, 0, iv.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey(key), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(payload, iv.length, payload.length - iv.length);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("手机号解密失败", e);
        }
    }

    /** AES-128：取密钥字节前 16 字节 */
    private static SecretKeySpec aesKey(String key) {
        byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
        byte[] material = new byte[16];
        System.arraycopy(bytes, 0, material, 0, Math.min(bytes.length, 16));
        return new SecretKeySpec(material, "AES");
    }
}
