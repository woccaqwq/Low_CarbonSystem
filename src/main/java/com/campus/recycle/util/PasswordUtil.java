package com.campus.recycle.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/** 与迭代一初始账号兼容的 PBKDF2 工具；密码强度校验由 Service 负责。 */
public final class PasswordUtil {
    private static final int SALT_BYTES = 16;
    private static final int HASH_BYTES = 32;
    private static final int ITERATIONS = 100_000;
    private final SecureRandom random = new SecureRandom();

    /** 创建无口令状态的工具实例，可注入 Service。 */
    public PasswordUtil() {
    }

    /** @return 16 字节随机盐的 Base64 编码，每次注册或改密重新生成 */
    public String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * 派生口令哈希，不修改输入口令的空格或字符。
     * @param rawPassword 非空口令；调用方不得记录到日志
     * @param salt 16 字节盐的 Base64 编码
     * @return 256 位派生结果的 Base64 编码
     * @throws IllegalArgumentException 口令缺失或盐编码、长度不合法
     */
    public String hash(String rawPassword, String salt) {
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new IllegalArgumentException("口令不能为空");
        }
        byte[] saltBytes = decode(salt, SALT_BYTES);
        PBEKeySpec specification = new PBEKeySpec(rawPassword.toCharArray(), saltBytes, ITERATIONS, HASH_BYTES * 8);
        try {
            byte[] derived = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(specification).getEncoded();
            return Base64.getEncoder().encodeToString(derived);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("运行环境不支持口令派生算法", failure);
        } finally {
            specification.clearPassword();
        }
    }

    /**
     * 校验口令，使用常量时间字节比较；损坏的凭据按校验失败处理。
     * @param rawPassword 待验证口令
     * @param hash 数据库存储的 Base64 哈希
     * @param salt 数据库存储的 Base64 盐
     * @return 口令正确为 true，口令错误、缺失或凭据非法为 false
     */
    public boolean verify(String rawPassword, String hash, String salt) {
        try {
            byte[] expected = decode(hash, HASH_BYTES);
            byte[] actual = Base64.getDecoder().decode(hash(rawPassword, salt));
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException invalidCredential) {
            return false;
        }
    }

    private static byte[] decode(String value, int expectedBytes) {
        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("凭据不能为空");
        }
        byte[] decoded = Base64.getDecoder().decode(value);
        if (decoded.length != expectedBytes) {
            throw new IllegalArgumentException("凭据长度不合法");
        }
        return decoded;
    }
}
