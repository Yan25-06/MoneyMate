package com.group10.moneymate.ai.receipt.model;

import java.util.Objects;

public final class ReceiptItem {

    private final String name;
    private final String amount;
    private final String categoryHint;
    private final int confidence;

    public ReceiptItem(String name, String amount, String categoryHint, int confidence) {
        this.name = sanitize(name);
        this.amount = sanitize(amount);
        this.categoryHint = sanitize(categoryHint);
        this.confidence = confidence;
    }

    public String getName() {
        return name;
    }

    public String getAmount() {
        return amount;
    }

    public String getCategoryHint() {
        return categoryHint;
    }

    public int getConfidence() {
        return confidence;
    }

    private static String sanitize(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReceiptItem)) {
            return false;
        }
        ReceiptItem receiptItem = (ReceiptItem) other;
        return confidence == receiptItem.confidence
                && Objects.equals(name, receiptItem.name)
                && Objects.equals(amount, receiptItem.amount)
                && Objects.equals(categoryHint, receiptItem.categoryHint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, amount, categoryHint, confidence);
    }
}
