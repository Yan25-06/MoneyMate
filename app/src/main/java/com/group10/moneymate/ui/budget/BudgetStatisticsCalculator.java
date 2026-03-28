package com.group10.moneymate.ui.budget;

import android.util.Log;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

public final class BudgetStatisticsCalculator {

    private static final String TAG = "BudgetStatistics";
    private static boolean exampleLogged;

    private BudgetStatisticsCalculator() {
    }

    @NonNull
    public static BudgetStatistics calculate(long startDateMillis,
                                             long endDateMillis,
                                             double budgetAmount,
                                             double spentAmount) {
        return calculate(
                toLocalDate(startDateMillis),
                toLocalDate(endDateMillis),
                LocalDate.now(),
                budgetAmount,
                spentAmount
        );
    }

    @NonNull
    public static BudgetStatistics calculate(@NonNull LocalDate startDate,
                                             @NonNull LocalDate endDate,
                                             @NonNull LocalDate today,
                                             double budgetAmount,
                                             double spentAmount) {
        long totalDaysLong = ChronoUnit.DAYS.between(startDate, endDate) + 1L;
        int totalDays = (int) Math.max(totalDaysLong, 1L);

        int daysPassed;
        if (today.isBefore(startDate)) {
            daysPassed = 0;
        } else if (today.isAfter(endDate)) {
            daysPassed = totalDays;
        } else {
            daysPassed = (int) (ChronoUnit.DAYS.between(startDate, today) + 1L);
        }

        int daysRemaining;
        if (!today.isBefore(endDate)) {
            daysRemaining = 0;
        } else if (today.isBefore(startDate)) {
            daysRemaining = totalDays;
        } else {
            daysRemaining = (int) ChronoUnit.DAYS.between(today, endDate);
        }

        double actualDailyAverage = daysPassed == 0
                ? 0d
                : spentAmount / daysPassed;

        double remainingAmount = budgetAmount - spentAmount;
        double recommendedDailySpend;
        if (spentAmount >= budgetAmount) {
            recommendedDailySpend = 0d;
        } else if (daysRemaining == 0) {
            if (today.isEqual(endDate) && remainingAmount > 0d) {
                recommendedDailySpend = remainingAmount;
            } else {
                recommendedDailySpend = 0d;
            }
        } else {
            recommendedDailySpend = remainingAmount / daysRemaining;
        }

        double projectedTotalSpend = actualDailyAverage * totalDays;

        return new BudgetStatistics(
                totalDays,
                daysPassed,
                daysRemaining,
                actualDailyAverage,
                recommendedDailySpend,
                projectedTotalSpend
        );
    }

    public static void logExampleOnce() {
        if (exampleLogged) {
            return;
        }
        exampleLogged = true;
        BudgetStatistics example = calculate(
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 3, 30),
                LocalDate.of(2026, 3, 10),
                3_000_000d,
                1_500_000d
        );
        Log.d(TAG, "Example budget=3000000 spent=1500000 totalDays="
                + example.getTotalDays()
                + " daysPassed=" + example.getDaysPassed()
                + " daysRemaining=" + example.getDaysRemaining()
                + " actualDaily=" + example.getActualDailyAverage()
                + " recommendedDaily=" + example.getRecommendedDailySpend()
                + " projectedTotal=" + example.getProjectedTotalSpend());
    }

    @NonNull
    private static LocalDate toLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    public static final class BudgetStatistics {
        private final int totalDays;
        private final int daysPassed;
        private final int daysRemaining;
        private final double actualDailyAverage;
        private final double recommendedDailySpend;
        private final double projectedTotalSpend;

        private BudgetStatistics(int totalDays,
                                 int daysPassed,
                                 int daysRemaining,
                                 double actualDailyAverage,
                                 double recommendedDailySpend,
                                 double projectedTotalSpend) {
            this.totalDays = totalDays;
            this.daysPassed = daysPassed;
            this.daysRemaining = daysRemaining;
            this.actualDailyAverage = actualDailyAverage;
            this.recommendedDailySpend = recommendedDailySpend;
            this.projectedTotalSpend = projectedTotalSpend;
        }

        public int getTotalDays() {
            return totalDays;
        }

        public int getDaysPassed() {
            return daysPassed;
        }

        public int getDaysRemaining() {
            return daysRemaining;
        }

        public double getActualDailyAverage() {
            return actualDailyAverage;
        }

        public double getRecommendedDailySpend() {
            return recommendedDailySpend;
        }

        public double getProjectedTotalSpend() {
            return projectedTotalSpend;
        }
    }
}
