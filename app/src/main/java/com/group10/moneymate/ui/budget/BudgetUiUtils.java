package com.group10.moneymate.ui.budget;

import android.content.Context;
import android.graphics.Color;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.utils.IconProvider;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class BudgetUiUtils {

    private static final Locale VIETNAM = new Locale("vi", "VN");
    private static final NumberFormat INTEGER_CURRENCY_FORMAT;
    static {
        INTEGER_CURRENCY_FORMAT = NumberFormat.getNumberInstance(VIETNAM);
        INTEGER_CURRENCY_FORMAT.setMaximumFractionDigits(0);
        INTEGER_CURRENCY_FORMAT.setMinimumFractionDigits(0);
    }

    private BudgetUiUtils() {
    }

    @NonNull
    public static String formatCurrency(double amount) {
        String prefix = amount < 0d ? "-" : "";
        return prefix + INTEGER_CURRENCY_FORMAT.format(Math.abs(amount)) + " đ";
    }

    @NonNull
    public static String formatCompactCurrency(double amount) {
        double absoluteValue = Math.abs(amount);
        String suffix = "";
        double displayValue = absoluteValue;

        if (absoluteValue >= 1_000_000_000d) {
            displayValue = absoluteValue / 1_000_000_000d;
            suffix = " tỷ đ";
        } else if (absoluteValue >= 1_000_000d) {
            displayValue = absoluteValue / 1_000_000d;
            suffix = " triệu đ";
        } else if (absoluteValue >= 1_000d) {
            displayValue = absoluteValue / 1_000d;
            suffix = " nghìn đ";
        } else {
            return formatCurrency(amount);
        }

        String prefix = amount < 0d ? "-" : "";
        return prefix + formatCompactNumber(displayValue) + suffix;
    }

    @NonNull
    public static String formatDecimalNumber(double amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(VIETNAM);
        formatter.setMaximumFractionDigits(2);
        formatter.setMinimumFractionDigits(0);
        return formatter.format(amount);
    }

    @NonNull
    public static String formatDecimalCurrency(double amount) {
        String prefix = amount < 0d ? "-" : "";
        return prefix + formatDecimalNumber(Math.abs(amount)) + " đ";
    }

    @NonNull
    public static String formatInputAmount(long amount) {
        return INTEGER_CURRENCY_FORMAT.format(amount);
    }

    @NonNull
    public static String formatAxisMoney(double amount) {
        double absoluteValue = Math.abs(amount);
        String prefix = amount < 0d ? "-" : "";
        if (absoluteValue >= 1_000_000_000d) {
            return prefix + formatCompactNumber(absoluteValue / 1_000_000_000d) + " tỷ đ";
        }
        if (absoluteValue >= 1_000_000d) {
            return prefix + formatCompactNumber(absoluteValue / 1_000_000d) + " triệu đ";
        }
        if (absoluteValue >= 1_000d) {
            return prefix + formatCompactNumber(absoluteValue / 1_000d) + " nghìn đ";
        }
        return prefix + INTEGER_CURRENCY_FORMAT.format(absoluteValue) + " đ";
    }

    @NonNull
    public static String formatDateRange(long startDate, long endDate) {
        java.text.SimpleDateFormat dateRangeFormat =
                new java.text.SimpleDateFormat("dd/MM", VIETNAM);
        return dateRangeFormat.format(new Date(startDate))
                + " - "
                + dateRangeFormat.format(new Date(endDate));
    }

    @NonNull
    public static String formatAxisDate(long date) {
        java.text.SimpleDateFormat axisDateFormat =
                new java.text.SimpleDateFormat("dd/MM/yyyy", VIETNAM);
        return axisDateFormat.format(new Date(date));
    }

    public static long startOfDay(long timeMillis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    public static int getDaysLeftInclusive(long endDate) {
        long today = startOfDay(System.currentTimeMillis());
        long budgetEnd = startOfDay(endDate);
        long diff = budgetEnd - today;
        if (diff < 0L) {
            return 0;
        }
        return (int) (diff / (24L * 60L * 60L * 1000L)) + 1;
    }

    public static int getTotalDaysInclusive(@NonNull BudgetEntity budgetEntity) {
        long start = startOfDay(budgetEntity.getStartDate());
        long end = startOfDay(budgetEntity.getEndDate());
        if (end < start) {
            return 1;
        }
        return (int) ((end - start) / (24L * 60L * 60L * 1000L)) + 1;
    }

    public static int getElapsedDays(@NonNull BudgetEntity budgetEntity) {
        int totalDays = getTotalDaysInclusive(budgetEntity);
        int daysLeft = getDaysLeftInclusive(budgetEntity.getEndDate());
        return Math.max(totalDays - daysLeft, 1);
    }

    public static float getTimelineFraction(@NonNull BudgetEntity budgetEntity) {
        long today = startOfDay(System.currentTimeMillis());
        long start = startOfDay(budgetEntity.getStartDate());
        long end = startOfDay(budgetEntity.getEndDate());
        if (end <= start) {
            return 1f;
        }
        if (today <= start) {
            return 0f;
        }
        if (today >= end) {
            return 1f;
        }
        return (float) (today - start) / (float) (end - start);
    }

    public static boolean isActiveToday(@NonNull BudgetEntity budgetEntity) {
        long today = startOfDay(System.currentTimeMillis());
        long start = startOfDay(budgetEntity.getStartDate());
        long end = startOfDay(budgetEntity.getEndDate());
        return today >= start && today <= end;
    }

    @ColorInt
    public static int parseColorOrDefault(@NonNull String colorHex, @ColorInt int fallbackColor) {
        if (colorHex.trim().isEmpty()) {
            return fallbackColor;
        }
        try {
            return Color.parseColor(colorHex);
        } catch (IllegalArgumentException exception) {
            return fallbackColor;
        }
    }

    public static int resolveCategoryIcon(@NonNull Context context,
                                          @NonNull String iconName,
                                          @NonNull String categoryName) {
        if (Constants.isOtherCategoryId(iconName)
                || "ic_category_other".equals(iconName)) {
            return R.drawable.ic_category_other;
        }
        if (!iconName.trim().isEmpty()) {
            int resId = IconProvider.resolveCategoryIcon(context, iconName);
            if (resId != 0) {
                return resId;
            }
        }

        String normalized = categoryName.toLowerCase(VIETNAM);
        if (normalized.contains("ăn") || normalized.contains("food")) {
            return R.drawable.ic_category_spending;
        }
        if (normalized.contains("nhà")
                || normalized.contains("rent")
                || normalized.contains("home")) {
            return R.drawable.outline_home_24;
        }
        if (normalized.contains("mua")
                || normalized.contains("shop")
                || normalized.contains("wallet")) {
            return R.drawable.outline_credit_card_24;
        }
        if (normalized.contains("bill")
                || normalized.contains("hóa đơn")
                || normalized.contains("điện")) {
            return R.drawable.outline_receipt_24;
        }
        if (normalized.contains("travel")
                || normalized.contains("di chuyển")
                || normalized.contains("transport")) {
            return R.drawable.outline_payments_24;
        }
        return R.drawable.outline_account_balance_wallet_24;
    }

    @NonNull
    private static String formatCompactNumber(double value) {
        NumberFormat formatter = NumberFormat.getNumberInstance(VIETNAM);
        formatter.setMaximumFractionDigits(1);
        formatter.setMinimumFractionDigits(0);
        return formatter.format(value);
    }
}
