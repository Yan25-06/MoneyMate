package com.group10.moneymate.ai.receipt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;

public final class ReceiptScanContract {

    public static final String KEY_IMAGE_PATH = "image_path";
    public static final String KEY_IMAGE_URI = "image_uri";
    public static final String KEY_AMOUNT = "amount";
    public static final String KEY_TIMESTAMP = "timestamp";
    public static final String KEY_MERCHANT = "merchant";
    public static final String KEY_CATEGORY_HINT = "category_hint";
    public static final String KEY_ITEMS_JSON = "items_json";
    public static final String KEY_CONFIDENCE = "confidence";
    public static final String KEY_OCR_HAS_TEXT = "ocr_has_text";
    public static final String KEY_OCR_BLOCK_COUNT = "ocr_block_count";
    public static final String KEY_OCR_LINE_COUNT = "ocr_line_count";
    public static final String KEY_ERROR_CODE = "error_code";
    public static final String KEY_ERROR_STAGE = "error_stage";

    public static final String ERROR_MISSING_IMAGE_INPUT = "missing_image_input";
    public static final String ERROR_INVALID_IMAGE_REFERENCE = "invalid_image_reference";
    public static final String ERROR_IMAGE_NOT_FOUND = "image_not_found";
    public static final String ERROR_IMAGE_DECODE_FAILED = "image_decode_failed";
    public static final String ERROR_IMAGE_PREPROCESS_FAILED = "image_preprocess_failed";
    public static final String ERROR_OCR_TIMEOUT = "ocr_timeout";
    public static final String ERROR_OCR_EXECUTION_FAILED = "ocr_execution_failed";
    public static final String ERROR_PARSER_FAILED = "parser_failed";

    public static final String STAGE_INPUT = "input";
    public static final String STAGE_DECODE = "decode";
    public static final String STAGE_PREPROCESS = "preprocess";
    public static final String STAGE_OCR = "ocr";
    public static final String STAGE_PARSER = "parser";

    public static final int CONFIDENCE_LOW = 0;
    public static final int CONFIDENCE_MEDIUM = 1;
    public static final int CONFIDENCE_HIGH = 2;
    public static final long UNKNOWN_TIMESTAMP = -1L;
    public static final String EMPTY_ITEMS_JSON = "[]";

    private ReceiptScanContract() {
    }

    @NonNull
    public static Data buildSuccessOutput(@NonNull String imagePath,
                                          @NonNull String imageUri,
                                          @Nullable String amount,
                                          long timestamp,
                                          @Nullable String merchant,
                                          @Nullable String categoryHint,
                                          @Nullable String itemsJson,
                                          int confidence,
                                          boolean hasOcrText,
                                          int ocrBlockCount,
                                          int ocrLineCount) {
        return new Data.Builder()
                .putString(KEY_IMAGE_PATH, imagePath)
                .putString(KEY_IMAGE_URI, imageUri)
                .putString(KEY_AMOUNT, sanitize(amount))
                .putLong(KEY_TIMESTAMP, timestamp)
                .putString(KEY_MERCHANT, sanitize(merchant))
                .putString(KEY_CATEGORY_HINT, sanitize(categoryHint))
                .putString(KEY_ITEMS_JSON, sanitizeItemsJson(itemsJson))
                .putInt(KEY_CONFIDENCE, confidence)
                .putBoolean(KEY_OCR_HAS_TEXT, hasOcrText)
                .putInt(KEY_OCR_BLOCK_COUNT, ocrBlockCount)
                .putInt(KEY_OCR_LINE_COUNT, ocrLineCount)
                .build();
    }

    @NonNull
    public static Data buildFailureOutput(@Nullable String imagePath,
                                          @Nullable String imageUri,
                                          @NonNull String errorCode,
                                          @NonNull String errorStage) {
        return new Data.Builder()
                .putString(KEY_IMAGE_PATH, sanitize(imagePath))
                .putString(KEY_IMAGE_URI, sanitize(imageUri))
                .putString(KEY_AMOUNT, "")
                .putLong(KEY_TIMESTAMP, UNKNOWN_TIMESTAMP)
                .putString(KEY_MERCHANT, "")
                .putString(KEY_CATEGORY_HINT, "")
                .putString(KEY_ITEMS_JSON, EMPTY_ITEMS_JSON)
                .putInt(KEY_CONFIDENCE, CONFIDENCE_LOW)
                .putBoolean(KEY_OCR_HAS_TEXT, false)
                .putInt(KEY_OCR_BLOCK_COUNT, 0)
                .putInt(KEY_OCR_LINE_COUNT, 0)
                .putString(KEY_ERROR_CODE, errorCode)
                .putString(KEY_ERROR_STAGE, errorStage)
                .build();
    }

    @NonNull
    private static String sanitize(@Nullable String value) {
        return value == null ? "" : value;
    }

    @NonNull
    private static String sanitizeItemsJson(@Nullable String itemsJson) {
        if (itemsJson == null || itemsJson.trim().isEmpty()) {
            return EMPTY_ITEMS_JSON;
        }
        return itemsJson;
    }
}
