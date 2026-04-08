package com.group10.moneymate.ai.receipt.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ReceiptData {

    public static final int CONFIDENCE_LOW = 0;
    public static final int CONFIDENCE_MEDIUM = 1;
    public static final int CONFIDENCE_HIGH = 2;
    public static final long UNKNOWN_TIMESTAMP = -1L;

    private final String amount;
    private final long timestamp;
    private final String merchant;
    private final String categoryHint;
    private final List<ReceiptItem> items;
    private final int confidence;

    public ReceiptData(String amount,
                       long timestamp,
                       String merchant,
                       String categoryHint,
                       List<ReceiptItem> items,
                       int confidence) {
        this.amount = sanitize(amount);
        this.timestamp = timestamp;
        this.merchant = sanitize(merchant);
        this.categoryHint = sanitize(categoryHint);
        this.items = items == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(items));
        this.confidence = confidence;
    }

    public static ReceiptData empty() {
        return new ReceiptData(
                "",
                UNKNOWN_TIMESTAMP,
                "",
                "",
                Collections.emptyList(),
                CONFIDENCE_LOW
        );
    }

    public String getAmount() {
        return amount;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getCategoryHint() {
        return categoryHint;
    }

    public List<ReceiptItem> getItems() {
        return items;
    }

    public int getConfidence() {
        return confidence;
    }

    public boolean hasAmount() {
        return !amount.isEmpty();
    }

    public boolean hasTimestamp() {
        return timestamp != UNKNOWN_TIMESTAMP;
    }

    public boolean hasMerchant() {
        return !merchant.isEmpty();
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReceiptData)) {
            return false;
        }
        ReceiptData that = (ReceiptData) other;
        return timestamp == that.timestamp
                && confidence == that.confidence
                && Objects.equals(amount, that.amount)
                && Objects.equals(merchant, that.merchant)
                && Objects.equals(categoryHint, that.categoryHint)
                && Objects.equals(items, that.items);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, timestamp, merchant, categoryHint, items, confidence);
    }
}
