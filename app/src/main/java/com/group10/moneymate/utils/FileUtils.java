package com.group10.moneymate.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;

public final class FileUtils {

    public static final long MAX_RECEIPT_IMAGE_BYTES = 20L * 1024L * 1024L;
    private static final int COPY_BUFFER_SIZE = 8 * 1024;

    private FileUtils() {
    }

    @NonNull
    public static ReceiptImageCopyResult copyReceiptImageToInternalStorage(@NonNull Context context,
                                                                           @NonNull Uri sourceUri)
            throws InvalidReceiptImageException, ReceiptImageTooLargeException, ReceiptImageStorageException {
        ContentResolver contentResolver = context.getContentResolver();
        String mimeType = contentResolver.getType(sourceUri);
        if (mimeType != null && !mimeType.startsWith("image/")) {
            throw new InvalidReceiptImageException();
        }

        File receiptsDirectory = new File(context.getFilesDir(), "receipts");
        if (!receiptsDirectory.exists() && !receiptsDirectory.mkdirs()) {
            throw new ReceiptImageStorageException();
        }

        long declaredSize = queryDeclaredSize(contentResolver, sourceUri);
        if (declaredSize > MAX_RECEIPT_IMAGE_BYTES) {
            throw new ReceiptImageTooLargeException();
        }

        String extension = resolveFileExtension(mimeType, sourceUri);
        File destinationFile = new File(
                receiptsDirectory,
                "receipt_" + System.currentTimeMillis() + extension
        );

        try (InputStream inputStream = contentResolver.openInputStream(sourceUri)) {
            if (inputStream == null) {
                throw new InvalidReceiptImageException();
            }
            try (OutputStream outputStream = new FileOutputStream(destinationFile)) {
                copyWithLimit(inputStream, outputStream, destinationFile);
            }
        } catch (FileNotFoundException exception) {
            deleteQuietly(destinationFile);
            throw new InvalidReceiptImageException();
        } catch (ReceiptImageTooLargeException exception) {
            deleteQuietly(destinationFile);
            throw exception;
        } catch (IOException exception) {
            deleteQuietly(destinationFile);
            throw new ReceiptImageStorageException();
        }

        return new ReceiptImageCopyResult(
                destinationFile.getAbsolutePath(),
                Uri.fromFile(destinationFile).toString()
        );
    }

    private static void copyWithLimit(@NonNull InputStream inputStream,
                                      @NonNull OutputStream outputStream,
                                      @NonNull File destinationFile)
            throws IOException, ReceiptImageTooLargeException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long totalBytes = 0L;
        int bytesRead;

        while ((bytesRead = inputStream.read(buffer)) != -1) {
            totalBytes += bytesRead;
            if (totalBytes > MAX_RECEIPT_IMAGE_BYTES) {
                outputStream.flush();
                deleteQuietly(destinationFile);
                throw new ReceiptImageTooLargeException();
            }
            outputStream.write(buffer, 0, bytesRead);
        }
    }

    private static long queryDeclaredSize(@NonNull ContentResolver contentResolver, @NonNull Uri sourceUri)
            throws InvalidReceiptImageException {
        try (AssetFileDescriptor fileDescriptor = contentResolver.openAssetFileDescriptor(sourceUri, "r")) {
            if (fileDescriptor == null) {
                throw new InvalidReceiptImageException();
            }
            return fileDescriptor.getLength();
        } catch (FileNotFoundException exception) {
            throw new InvalidReceiptImageException();
        } catch (IOException exception) {
            return AssetFileDescriptor.UNKNOWN_LENGTH;
        }
    }

    @NonNull
    private static String resolveFileExtension(@Nullable String mimeType, @NonNull Uri sourceUri) {
        if (mimeType != null) {
            String mimeExtension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType);
            if (mimeExtension != null && !mimeExtension.isEmpty()) {
                return "." + mimeExtension.toLowerCase(Locale.US);
            }
        }

        String path = sourceUri.getPath();
        if (path != null) {
            int extensionIndex = path.lastIndexOf('.');
            if (extensionIndex >= 0 && extensionIndex < path.length() - 1) {
                return path.substring(extensionIndex).toLowerCase(Locale.US);
            }
        }
        return ".jpg";
    }

    private static void deleteQuietly(@NonNull File file) {
        if (file.exists()) {
            // Best-effort cleanup for partial copies.
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    public static final class ReceiptImageCopyResult {
        @NonNull
        private final String internalPath;
        @NonNull
        private final String internalUri;

        public ReceiptImageCopyResult(@NonNull String internalPath, @NonNull String internalUri) {
            this.internalPath = internalPath;
            this.internalUri = internalUri;
        }

        @NonNull
        public String getInternalPath() {
            return internalPath;
        }

        @NonNull
        public String getInternalUri() {
            return internalUri;
        }
    }

    public abstract static class ReceiptImageException extends Exception {
    }

    public static final class InvalidReceiptImageException extends ReceiptImageException {
    }

    public static final class ReceiptImageTooLargeException extends ReceiptImageException {
    }

    public static final class ReceiptImageStorageException extends ReceiptImageException {
    }
}
