package com.group10.moneymate.utils;

import androidx.annotation.NonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

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

    @NonNull
    public static LocalDate toUtcLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneOffset.UTC)
                .toLocalDate();
    }
}

