package com.group10.moneymate.ui.statistics;

import static org.junit.Assert.assertEquals;

import com.group10.moneymate.data.local.dto.DailyTrendDTO;
import com.group10.moneymate.utils.TimeWindowUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;

public class MonthlyComparisonBuilderTest {

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
    public void build_keepsEachPreviousMonthSeriesAndAverage() {
        LocalDate currentMonth = LocalDate.of(2026, 5, 1);

        List<MonthlyComparisonPoint> points = MonthlyComparisonBuilder.build(
                currentMonth,
                LocalDate.of(2026, 5, 2),
                daily(LocalDate.of(2026, 5, 1), 100d, LocalDate.of(2026, 5, 2), 50d),
                daily(LocalDate.of(2026, 4, 1), 10d, LocalDate.of(2026, 4, 2), 5d),
                daily(LocalDate.of(2026, 3, 1), 20d, LocalDate.of(2026, 3, 2), 10d),
                daily(LocalDate.of(2026, 2, 1), 30d, LocalDate.of(2026, 2, 2), 15d)
        );

        assertEquals(2, points.size());
        MonthlyComparisonPoint dayOne = points.get(0);
        assertEquals(100d, dayOne.getCurrentAmount(), 0.001d);
        assertEquals(30d, dayOne.getAverageAmount(), 0.001d);

        MonthlyComparisonPoint dayTwo = points.get(1);
        assertEquals(150d, dayTwo.getCurrentAmount(), 0.001d);
        assertEquals(30d, dayTwo.getAverageAmount(), 0.001d);
    }

    @Test
    public void build_carriesShortMonthFinalTotalForMissingDayOfMonth() {
        LocalDate currentMonth = LocalDate.of(2026, 5, 1);

        List<MonthlyComparisonPoint> points = MonthlyComparisonBuilder.build(
                currentMonth,
                LocalDate.of(2026, 5, 31),
                Collections.emptyList(),
                daily(LocalDate.of(2026, 4, 30), 300d),
                daily(LocalDate.of(2026, 3, 31), 310d),
                daily(LocalDate.of(2026, 2, 28), 280d)
        );

        MonthlyComparisonPoint dayThirtyOne = points.get(30);
        assertEquals((300d + 310d + 280d) / 3d, dayThirtyOne.getAverageAmount(), 0.001d);
    }

    private List<DailyTrendDTO> daily(Object... dateAmountPairs) {
        List<DailyTrendDTO> items = new ArrayList<>();
        for (int index = 0; index < dateAmountPairs.length; index += 2) {
            DailyTrendDTO dto = new DailyTrendDTO();
            dto.setPeriodStart(TimeWindowUtils.startOfDayLocalDateUtc((LocalDate) dateAmountPairs[index]));
            dto.setTotalAmount((double) dateAmountPairs[index + 1]);
            dto.setTransactionCount(1);
            items.add(dto);
        }
        return items;
    }
}
