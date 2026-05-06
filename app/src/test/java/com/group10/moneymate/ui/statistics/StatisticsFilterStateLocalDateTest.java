package com.group10.moneymate.ui.statistics;

import static org.junit.Assert.assertEquals;

import com.group10.moneymate.utils.TimeWindowUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.TimeZone;

public class StatisticsFilterStateLocalDateTest {

    private TimeZone originalTimeZone;

    @Before
    public void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
    }

    @After
    public void tearDown() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    public void createMonth_usesDeviceLocalMonthBoundaries() {
        LocalDate mayFirst = LocalDate.of(2026, 5, 1);

        StatisticsViewModel.FilterState mayFilter =
                StatisticsViewModel.FilterState.createMonth(null, mayFirst);

        assertEquals(
                TimeWindowUtils.startOfDayLocalDateUtc(LocalDate.of(2026, 5, 1)),
                mayFilter.getStartDate()
        );
        assertEquals(
                TimeWindowUtils.endOfDayLocalDateUtc(LocalDate.of(2026, 5, 31)),
                mayFilter.getEndDate()
        );
    }

    @Test
    public void shiftMonth_preservesDeviceLocalCalendarMonth() {
        StatisticsViewModel.FilterState mayFilter =
                StatisticsViewModel.FilterState.createMonth(null, LocalDate.of(2026, 5, 1));

        StatisticsViewModel.FilterState aprilFilter = mayFilter.shift(-1);
        StatisticsViewModel.FilterState juneFilter = mayFilter.shift(1);

        assertEquals(
                TimeWindowUtils.startOfDayLocalDateUtc(LocalDate.of(2026, 4, 1)),
                aprilFilter.getStartDate()
        );
        assertEquals(
                TimeWindowUtils.endOfDayLocalDateUtc(LocalDate.of(2026, 4, 30)),
                aprilFilter.getEndDate()
        );
        assertEquals(
                TimeWindowUtils.startOfDayLocalDateUtc(LocalDate.of(2026, 6, 1)),
                juneFilter.getStartDate()
        );
        assertEquals(
                TimeWindowUtils.endOfDayLocalDateUtc(LocalDate.of(2026, 6, 30)),
                juneFilter.getEndDate()
        );
    }
}
