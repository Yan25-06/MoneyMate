package com.group10.moneymate.ui.statistics;

import androidx.annotation.NonNull;

public final class MonthlyComparisonPoint {

    @NonNull
    private final String label;
    private final long dateMillis;
    private final double currentAmount;
    private final double averageAmount;

    public MonthlyComparisonPoint(@NonNull String label,
                                  long dateMillis,
                                  double currentAmount,
                                  double averageAmount) {
        this.label = label;
        this.dateMillis = dateMillis;
        this.currentAmount = currentAmount;
        this.averageAmount = averageAmount;
    }

    @NonNull
    public String getLabel() {
        return label;
    }

    public long getDateMillis() {
        return dateMillis;
    }

    public double getCurrentAmount() {
        return currentAmount;
    }

    public double getAverageAmount() {
        return averageAmount;
    }
}
