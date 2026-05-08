package com.group10.moneymate.utils;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Utility để hash và verify passcode an toàn.
 *
 * Thuật toán: Random Salt (16 bytes) + SHA-256
 * Format lưu trữ: "<base64_salt>:<base64_hash>"
 *
 * KHÔNG bao giờ lưu plain text passcode.
 */
public final class PasscodeHasher {

    private static final int SALT_LENGTH_BYTES = 16;
    private static final String SEPARATOR = ":";

    private PasscodeHasher() { /* utility class */ }

    /**
     * Tạo hash từ passcode với salt ngẫu nhiên.
     * @param passcode mã PIN người dùng nhập
     * @return chuỗi lưu trữ dạng "salt:hash" (base64)
     */
    public static String hash(String passcode) {
        byte[] saltBytes = new byte[SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(saltBytes);
        String salt = Base64.encodeToString(saltBytes, Base64.NO_WRAP);
        String hash = sha256(salt + passcode);
        return salt + SEPARATOR + hash;
    }

    /**
     * Xác minh passcode với chuỗi hash đã lưu.
     * @param passcode mã PIN người dùng nhập
     * @param stored   chuỗi lưu trữ dạng "salt:hash"
     * @return true nếu khớp
     */
    public static boolean verify(String passcode, String stored) {
        if (passcode == null || stored == null) return false;
        int sep = stored.indexOf(SEPARATOR);
        if (sep < 0) return false;
        String salt = stored.substring(0, sep);
        String storedHash = stored.substring(sep + 1);
        return storedHash.equals(sha256(salt + passcode));
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(hashBytes, Base64.NO_WRAP);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
