package com.group10.moneymate.utils;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class TimeWindowUtils {

    private TimeWindowUtils() {
        // Utility class.
    }

    public static long startOfDayUtc(long epochMillis) {
        return startOfDayUtc(toUtcLocalDate(epochMillis));
    }

    public static long startOfDayUtc(@NonNull LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    public static long startOfMonthUtc(long epochMillis) {
        LocalDate date = toUtcLocalDate(epochMillis);
        LocalDate firstDay = date.withDayOfMonth(1);
        return startOfDayUtc(firstDay);
    }

    public static long startOfDayLocalDateUtc(@NonNull LocalDate localDate) {
        return localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static long endOfDayLocalDateUtc(@NonNull LocalDate localDate) {
        return localDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1L;
    }

    public static long endOfTodayUtc() {
        return endOfDayLocalDateUtc(LocalDate.now(ZoneId.systemDefault()));
    }

    public static long endOfMonthUtc(long epochMillis) {
        LocalDate date = toUtcLocalDate(epochMillis);
        LocalDate lastDay = date.withDayOfMonth(date.lengthOfMonth());
        return startOfDayUtc(lastDay.plusDays(1)) - 1L;
    }

    @NonNull
    public static String formatDateLocal(long epochUtc, @NonNull String pattern) {
        LocalDateTime localDateTime = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(epochUtc),
                ZoneId.systemDefault()
        );
        return localDateTime.format(DateTimeFormatter.ofPattern(pattern));
    }

    @NonNull
    public static LocalDate toUtcLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
    }

    @NonNull
    public static LocalDate toDeviceLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }
}

