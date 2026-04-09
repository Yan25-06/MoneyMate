package com.group10.moneymate.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** SHA-256 hash cho passcode 6 số */
public final class PasscodeHasher {

    private PasscodeHasher() {}

    /** Hash passcode — trả về hex string */
    public static String hash(String passcode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(passcode.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 luôn có sẵn trên Android
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    /** So sánh passcode với hash đã lưu */
    public static boolean verify(String passcode, String storedHash) {
        if (passcode == null || storedHash == null) return false;
        return hash(passcode).equals(storedHash);
    }
}