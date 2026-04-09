package com.group10.moneymate.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Duplicate gate helper for OCR receipts.
 * Policy is deterministic and explainable: SHA-256 of the internal image file,
 * paired with amount and a 2-minute timestamp bucket. Raw OCR text is not used.
 */
public final class ReceiptImageHashUtils {

    private static final int BUFFER_SIZE = 8 * 1024;

    private ReceiptImageHashUtils() {
    }

    @Nullable
    public static String computeSha256(@Nullable String imagePath) throws IOException {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return null;
        }
        File imageFile = new File(imagePath);
        if (!imageFile.exists() || !imageFile.isFile()) {
            return null;
        }

        MessageDigest messageDigest = createSha256Digest();
        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream inputStream = new FileInputStream(imageFile)) {
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                messageDigest.update(buffer, 0, bytesRead);
            }
        }
        return toHex(messageDigest.digest());
    }

    @NonNull
    private static MessageDigest createSha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest unavailable", exception);
        }
    }

    @NonNull
    private static String toHex(@NonNull byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
