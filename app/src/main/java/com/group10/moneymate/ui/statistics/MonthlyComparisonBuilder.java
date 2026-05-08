package com.group10.moneymate.ui.statistics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.group10.moneymate.data.local.dto.DailyTrendDTO;
import com.group10.moneymate.utils.TimeWindowUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MonthlyComparisonBuilder {

    private MonthlyComparisonBuilder() {
        // Utility class.
    }

    @NonNull
    public static List<MonthlyComparisonPoint> build(@NonNull LocalDate currentMonth,
                                                     @NonNull LocalDate visibleEnd,
                                                     @Nullable List<DailyTrendDTO> currentDaily,
                                                     @Nullable List<DailyTrendDTO> previousOneDaily,
                                                     @Nullable List<DailyTrendDTO> previousTwoDaily,
                                                     @Nullable List<DailyTrendDTO> previousThreeDaily) {
        Map<LocalDate, Double> currentMap = toDailyAmountMap(currentDaily);
        Map<LocalDate, Double> previousOneMap = toDailyAmountMap(previousOneDaily);
        Map<LocalDate, Double> previousTwoMap = toDailyAmountMap(previousTwoDaily);
        Map<LocalDate, Double> previousThreeMap = toDailyAmountMap(previousThreeDaily);

        LocalDate previousOneMonth = currentMonth.minusMonths(1);
        LocalDate previousTwoMonth = currentMonth.minusMonths(2);
        LocalDate previousThreeMonth = currentMonth.minusMonths(3);
        int lastVisibleDay = Math.min(visibleEnd.getDayOfMonth(), currentMonth.lengthOfMonth());

        double prevOneFullTotal = runningTotalThroughDay(
                previousOneMap, previousOneMonth, previousOneMonth.lengthOfMonth());
        double prevTwoFullTotal = runningTotalThroughDay(
                previousTwoMap, previousTwoMonth, previousTwoMonth.lengthOfMonth());
        double prevThreeFullTotal = runningTotalThroughDay(
                previousThreeMap, previousThreeMonth, previousThreeMonth.lengthOfMonth());
        double fullMonthAverage = (prevOneFullTotal + prevTwoFullTotal + prevThreeFullTotal) / 3d;

        List<MonthlyComparisonPoint> points = new ArrayList<>();
        double currentRunning = 0d;
        for (int dayOfMonth = 1; dayOfMonth <= lastVisibleDay; dayOfMonth++) {
            LocalDate currentDate = currentMonth.withDayOfMonth(dayOfMonth);
            currentRunning += currentMap.getOrDefault(currentDate, 0d);

            points.add(new MonthlyComparisonPoint(
                    String.format(Locale.getDefault(), "%02d/%02d",
                            currentDate.getDayOfMonth(),
                            currentDate.getMonthValue()),
                    TimeWindowUtils.startOfDayLocalDateUtc(currentDate),
                    currentRunning,
                    fullMonthAverage
            ));
        }
        return points;
    }

    private static double runningTotalThroughDay(@NonNull Map<LocalDate, Double> dailyMap,
                                                 @NonNull LocalDate month,
                                                 int dayOfMonth) {
        int cappedDay = Math.min(dayOfMonth, month.lengthOfMonth());
        double total = 0d;
        for (int cursorDay = 1; cursorDay <= cappedDay; cursorDay++) {
            total += dailyMap.getOrDefault(month.withDayOfMonth(cursorDay), 0d);
        }
        return total;
    }

    @NonNull
    private static Map<LocalDate, Double> toDailyAmountMap(@Nullable List<DailyTrendDTO> source) {
        Map<LocalDate, Double> result = new HashMap<>();
        if (source == null) {
            return result;
        }
        for (DailyTrendDTO dto : source) {
            result.put(TimeWindowUtils.toDeviceLocalDate(dto.getPeriodStart()), dto.getTotalAmount());
        }
        return result;
    }
}
