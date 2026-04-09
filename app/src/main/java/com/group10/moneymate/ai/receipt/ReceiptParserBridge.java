package com.group10.moneymate.ai.receipt;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Data;

import com.google.mlkit.vision.text.Text;
import com.group10.moneymate.ai.receipt.model.ReceiptData;

public interface ReceiptParserBridge {

    @NonNull
    ParseResult parse(@NonNull String imagePath,
                      @NonNull String imageUri,
                      @NonNull Text recognizedText) throws ReceiptParsingException;

    final class ParseResult {
        @Nullable
        private final String amount;
        private final long timestamp;
        @Nullable
        private final String merchant;
        @Nullable
        private final String categoryHint;
        @Nullable
        private final String noteHint;
        @Nullable
        private final String itemsJson;
        private final int confidence;

        public ParseResult(@Nullable String amount,
                           long timestamp,
                           @Nullable String merchant,
                           @Nullable String categoryHint,
                           @Nullable String noteHint,
                           @Nullable String itemsJson,
                           int confidence) {
            this.amount = amount;
            this.timestamp = timestamp;
            this.merchant = merchant;
            this.categoryHint = categoryHint;
            this.noteHint = noteHint;
            this.itemsJson = itemsJson;
            this.confidence = confidence;
        }

        @NonNull
        public Data toOutputData(@NonNull String imagePath,
                                 @NonNull String imageUri,
                                 @Nullable String rawText,
                                 @NonNull String processingSource,
                                 @NonNull String processingDetail,
                                 boolean hasOcrText,
                                 int ocrBlockCount,
                                 int ocrLineCount) {
            return ReceiptScanContract.buildSuccessOutput(
                    imagePath,
                    imageUri,
                    amount,
                    timestamp,
                    merchant,
                    categoryHint,
                    noteHint,
                    itemsJson,
                    rawText,
                    processingSource,
                    processingDetail,
                    confidence,
                    hasOcrText,
                    ocrBlockCount,
                    ocrLineCount
            );
        }

        @NonNull
        public static ParseResult empty() {
            return new ParseResult(
                    "",
                    ReceiptScanContract.UNKNOWN_TIMESTAMP,
                    "",
                    "",
                    "",
                    ReceiptScanContract.EMPTY_ITEMS_JSON,
                    ReceiptScanContract.CONFIDENCE_LOW
            );
        }

        @NonNull
        public static ParseResult fromReceiptData(@NonNull ReceiptData receiptData,
                                                  @NonNull String itemsJson) {
            return new ParseResult(
                    receiptData.getAmount(),
                    receiptData.getTimestamp(),
                    receiptData.getMerchant(),
                    receiptData.getCategoryHint(),
                    receiptData.getNoteHint(),
                    itemsJson,
                    receiptData.getConfidence()
            );
        }
    }

    final class ReceiptParsingException extends Exception {
        public ReceiptParsingException(@NonNull Throwable cause) {
            super(cause);
        }

        public ReceiptParsingException(@NonNull String message) {
            super(message);
        }
    }
}
