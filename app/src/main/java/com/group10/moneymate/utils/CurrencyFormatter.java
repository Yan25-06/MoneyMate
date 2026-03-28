package com.group10.moneymate.utils;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utility class for formatting currency amounts.
 */
public class CurrencyFormatter {

    private static final Locale VIETNAM = new Locale("vi", "VN");

    public static String format(double amount, String currencyCode) {
        NumberFormat formatter;
        switch (currencyCode) {
            case "VND":
                formatter = NumberFormat.getNumberInstance(VIETNAM);
                formatter.setMaximumFractionDigits(0);
                return formatter.format(amount) + " ₫";
            case "USD":
                formatter = NumberFormat.getCurrencyInstance(Locale.US);
                return formatter.format(amount);
            case "EUR":
                formatter = NumberFormat.getCurrencyInstance(Locale.GERMANY);
                return formatter.format(amount);
            default:
                formatter = new DecimalFormat("#,###.##");
                return formatter.format(amount) + " " + currencyCode;
        }
    }

    public static String formatInputAmount(long amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(VIETNAM);
        formatter.setMaximumFractionDigits(0);
        return formatter.format(amount);
    }

    public static String extractDigits(String rawValue) {
        return rawValue == null ? "" : rawValue.replaceAll("[^\\d]", "");
    }

    public static double parseFormattedAmount(String rawValue) throws NumberFormatException {
        String digits = extractDigits(rawValue);
        if (digits.isEmpty()) {
            throw new NumberFormatException("Empty amount");
        }
        return Double.parseDouble(digits);
    }

    public static String formatCompact(double amount) {
        if (amount >= 1_000_000_000) {
            return String.format(Locale.getDefault(), "%.1fB", amount / 1_000_000_000);
        } else if (amount >= 1_000_000) {
            return String.format(Locale.getDefault(), "%.1fM", amount / 1_000_000);
        } else if (amount >= 1_000) {
            return String.format(Locale.getDefault(), "%.1fK", amount / 1_000);
        }
        return String.valueOf((long) amount);
    }
}
