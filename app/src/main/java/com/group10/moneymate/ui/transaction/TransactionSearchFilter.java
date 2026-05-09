package com.group10.moneymate.ui.transaction;

import androidx.annotation.Nullable;

public final class TransactionSearchFilter {
    @Nullable
    public String keyword;

    // SỐ TIỀN
    public AmountMode amountMode = AmountMode.ALL;
    public double amountValue = 0;
    public double amountMin = 0;
    public double amountMax = 0;

    // THỜI GIAN
    public TimeMode timeMode = TimeMode.ALL;
    public long timeValue = 0;
    public long timeStart = 0;
    public long timeEnd = 0;

    // VÍ & DANH MỤC
    @Nullable
    public String walletId;
    @Nullable
    public String walletLabel;
    @Nullable
    public String categoryId;
    @Nullable
    public String categoryLabel;

    public enum AmountMode {
        ALL, GT, LT, EQ, BETWEEN
    }

    public enum TimeMode {
        ALL, AFTER, BEFORE, ON, BETWEEN
    }
    
    public TransactionSearchFilter() {
    }
}
