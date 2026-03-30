package com.group10.moneymate.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.group10.moneymate.data.local.dto.CategorySumDTO;
import com.group10.moneymate.data.local.dto.DailyTrendDTO;
import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.Constants;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeViewModel extends AndroidViewModel {

    private static final String DAILY_PERIOD_FORMAT = "%Y-%m-%d";
    private static final String TYPE_EXPENSE = Constants.TYPE_EXPENSE;
    private static final String TYPE_INCOME = Constants.TYPE_INCOME;

    private final TransactionRepository transactionRepository;
    private final String userId;
    private final LiveData<List<WalletWithBalance>> wallets;
    private final LiveData<Double> totalBalance;
    private final LiveData<List<TransactionEntity>> recentTransactions;
    private final LiveData<Double> monthlyIncome;
    private final LiveData<Double> monthlyExpense;
    private final LiveData<Double> previousMonthlyExpense;
    private final LiveData<Double> weeklyExpense;
    private final LiveData<Double> previousWeeklyExpense;
    private final LiveData<List<CategorySumDTO>> monthlyTopExpenseCategories;
    private final LiveData<List<CategorySumDTO>> weeklyTopExpenseCategories;
    private final LiveData<List<CategoryEntity>> expenseCategories;
    private final LiveData<List<CategoryEntity>> incomeCategories;
    private final MediatorLiveData<List<TrendPointUiModel>> expenseComparisonPoints = new MediatorLiveData<>();
    private final MediatorLiveData<List<TrendPointUiModel>> incomeComparisonPoints = new MediatorLiveData<>();

    private LiveData<List<DailyTrendDTO>> expenseCurrentSource;
    private LiveData<List<DailyTrendDTO>> expensePrevOneSource;
    private LiveData<List<DailyTrendDTO>> expensePrevTwoSource;
    private LiveData<List<DailyTrendDTO>> expensePrevThreeSource;
    private LiveData<List<DailyTrendDTO>> incomeCurrentSource;
    private LiveData<List<DailyTrendDTO>> incomePrevOneSource;
    private LiveData<List<DailyTrendDTO>> incomePrevTwoSource;
    private LiveData<List<DailyTrendDTO>> incomePrevThreeSource;

    @NonNull
    private List<DailyTrendDTO> latestExpenseCurrent = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestExpensePrevOne = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestExpensePrevTwo = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestExpensePrevThree = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestIncomeCurrent = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestIncomePrevOne = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestIncomePrevTwo = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestIncomePrevThree = new ArrayList<>();

    public HomeViewModel(@NonNull Application application) {
        super(application);

        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        userId = container.authRepository.getCurrentUserId();
        transactionRepository = container.transactionRepository;

        wallets = container.walletRepository.getAllByUserWithBalance(userId);
        expenseCategories = container.categoryRepository.getCategoriesByType(userId, TYPE_EXPENSE);
        incomeCategories = container.categoryRepository.getCategoriesByType(userId, TYPE_INCOME);
        totalBalance = container.walletRepository.getTotalBalance(userId);

        recentTransactions = container.transactionRepository.getRecentTransactions(userId, 4);

        long[] currentMonthBounds = getCurrentMonthBounds();
        long[] previousMonthBounds = getPreviousMonthBounds();
        long[] currentWeekBounds = getCurrentWeekBounds();
        long[] previousWeekBounds = getPreviousWeekBounds();

        monthlyIncome = container.transactionRepository.getTotalIncome(
                userId,
                currentMonthBounds[0],
                currentMonthBounds[1]
        );
        monthlyExpense = container.transactionRepository.getTotalExpense(
                userId,
                currentMonthBounds[0],
                currentMonthBounds[1]
        );
        previousMonthlyExpense = container.transactionRepository.getTotalExpense(
                userId,
                previousMonthBounds[0],
                previousMonthBounds[1]
        );
        weeklyExpense = container.transactionRepository.getTotalExpense(
                userId,
                currentWeekBounds[0],
                currentWeekBounds[1]
        );
        previousWeeklyExpense = container.transactionRepository.getTotalExpense(
                userId,
                previousWeekBounds[0],
                previousWeekBounds[1]
        );

        monthlyTopExpenseCategories = container.transactionRepository.getCategorySums(
                userId,
                TYPE_EXPENSE,
                currentMonthBounds[0],
                currentMonthBounds[1],
                null
        );
        weeklyTopExpenseCategories = container.transactionRepository.getCategorySums(
                userId,
                TYPE_EXPENSE,
                currentWeekBounds[0],
                currentWeekBounds[1],
                null
        );

        initializeComparisonSources();
    }

    private void initializeComparisonSources() {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate visibleEnd = LocalDate.now();

        expenseCurrentSource = createCurrentMonthSource(TYPE_EXPENSE, currentMonth, visibleEnd);
        expensePrevOneSource = buildPreviousMonthSource(TYPE_EXPENSE, currentMonth.minusMonths(1));
        expensePrevTwoSource = buildPreviousMonthSource(TYPE_EXPENSE, currentMonth.minusMonths(2));
        expensePrevThreeSource = buildPreviousMonthSource(TYPE_EXPENSE, currentMonth.minusMonths(3));

        incomeCurrentSource = createCurrentMonthSource(TYPE_INCOME, currentMonth, visibleEnd);
        incomePrevOneSource = buildPreviousMonthSource(TYPE_INCOME, currentMonth.minusMonths(1));
        incomePrevTwoSource = buildPreviousMonthSource(TYPE_INCOME, currentMonth.minusMonths(2));
        incomePrevThreeSource = buildPreviousMonthSource(TYPE_INCOME, currentMonth.minusMonths(3));

        observeComparisonSource(expenseComparisonPoints, expenseCurrentSource, TYPE_EXPENSE, 0);
        observeComparisonSource(expenseComparisonPoints, expensePrevOneSource, TYPE_EXPENSE, 1);
        observeComparisonSource(expenseComparisonPoints, expensePrevTwoSource, TYPE_EXPENSE, 2);
        observeComparisonSource(expenseComparisonPoints, expensePrevThreeSource, TYPE_EXPENSE, 3);

        observeComparisonSource(incomeComparisonPoints, incomeCurrentSource, TYPE_INCOME, 0);
        observeComparisonSource(incomeComparisonPoints, incomePrevOneSource, TYPE_INCOME, 1);
        observeComparisonSource(incomeComparisonPoints, incomePrevTwoSource, TYPE_INCOME, 2);
        observeComparisonSource(incomeComparisonPoints, incomePrevThreeSource, TYPE_INCOME, 3);
    }

    @NonNull
    private LiveData<List<DailyTrendDTO>> buildPreviousMonthSource(@NonNull String type,
                                                                   @NonNull LocalDate month) {
        return transactionRepository.getAmountTrend(
                userId,
                type,
                toStartMillis(month.withDayOfMonth(1)),
                toEndMillis(month.withDayOfMonth(month.lengthOfMonth())),
                null,
                DAILY_PERIOD_FORMAT
        );
    }

    @NonNull
    private LiveData<List<DailyTrendDTO>> createCurrentMonthSource(@NonNull String type,
                                                                   @NonNull LocalDate currentMonth,
                                                                   @NonNull LocalDate visibleEnd) {
        return transactionRepository.getAmountTrend(
                userId,
                type,
                toStartMillis(currentMonth),
                toEndMillis(visibleEnd),
                null,
                DAILY_PERIOD_FORMAT
        );
    }

    private void observeComparisonSource(@NonNull MediatorLiveData<List<TrendPointUiModel>> target,
                                         @NonNull LiveData<List<DailyTrendDTO>> source,
                                         @NonNull String type,
                                         int slot) {
        target.addSource(source, value -> {
            updateTrendCache(type, slot, value);
            rebuildComparisonPoints(type);
        });
    }

    private void rebuildComparisonPoints(@NonNull String type) {
        LocalDate currentMonth = LocalDate.now().withDayOfMonth(1);
        LocalDate visibleEnd = LocalDate.now();
        int lastVisibleDay = visibleEnd.getDayOfMonth();

        List<DailyTrendDTO> currentSource = getTrendCache(type, 0);
        List<DailyTrendDTO> prevOneSource = getTrendCache(type, 1);
        List<DailyTrendDTO> prevTwoSource = getTrendCache(type, 2);
        List<DailyTrendDTO> prevThreeSource = getTrendCache(type, 3);

        Map<LocalDate, Double> currentMap = toDailyAmountMap(currentSource);
        Map<LocalDate, Double> prevOneMap = toDailyAmountMap(prevOneSource);
        Map<LocalDate, Double> prevTwoMap = toDailyAmountMap(prevTwoSource);
        Map<LocalDate, Double> prevThreeMap = toDailyAmountMap(prevThreeSource);

        List<Map<LocalDate, Double>> previousMaps = new ArrayList<>();
        previousMaps.add(prevOneMap);
        previousMaps.add(prevTwoMap);
        previousMaps.add(prevThreeMap);

        List<LocalDate> previousMonths = new ArrayList<>();
        previousMonths.add(currentMonth.minusMonths(1));
        previousMonths.add(currentMonth.minusMonths(2));
        previousMonths.add(currentMonth.minusMonths(3));

        List<TrendPointUiModel> items = new ArrayList<>();
        double currentRunning = 0d;
        double lastAverage = 0d;
        for (int dayOfMonth = 1; dayOfMonth <= lastVisibleDay; dayOfMonth++) {
            LocalDate currentDate = currentMonth.withDayOfMonth(dayOfMonth);
            currentRunning += currentMap.containsKey(currentDate) ? currentMap.get(currentDate) : 0d;

            double averageTotal = 0d;
            int divisor = 0;
            for (int index = 0; index < previousMonths.size(); index++) {
                LocalDate month = previousMonths.get(index);
                if (dayOfMonth > month.lengthOfMonth()) {
                    continue;
                }
                LocalDate targetDate = month.withDayOfMonth(dayOfMonth);
                Map<LocalDate, Double> monthMap = previousMaps.get(index);
                if (!monthMap.containsKey(targetDate)) {
                    continue;
                }
                averageTotal += sumRange(monthMap, month.withDayOfMonth(1), targetDate);
                divisor++;
            }

            double averageValue = divisor > 0 ? averageTotal / divisor : lastAverage;
            lastAverage = averageValue;
            items.add(new TrendPointUiModel(
                    String.format(Locale.getDefault(), "%02d/%02d", currentDate.getDayOfMonth(), currentDate.getMonthValue()),
                    toStartMillis(currentDate),
                    currentRunning,
                    averageValue
            ));
        }

        if (TYPE_EXPENSE.equals(type)) {
            expenseComparisonPoints.setValue(items);
        } else {
            incomeComparisonPoints.setValue(items);
        }
    }

    private void updateTrendCache(@NonNull String type,
                                  int slot,
                                  @Nullable List<DailyTrendDTO> value) {
        List<DailyTrendDTO> safeValue = value != null ? value : new ArrayList<>();
        if (TYPE_EXPENSE.equals(type)) {
            if (slot == 0) {
                latestExpenseCurrent = safeValue;
            } else if (slot == 1) {
                latestExpensePrevOne = safeValue;
            } else if (slot == 2) {
                latestExpensePrevTwo = safeValue;
            } else {
                latestExpensePrevThree = safeValue;
            }
            return;
        }
        if (slot == 0) {
            latestIncomeCurrent = safeValue;
        } else if (slot == 1) {
            latestIncomePrevOne = safeValue;
        } else if (slot == 2) {
            latestIncomePrevTwo = safeValue;
        } else {
            latestIncomePrevThree = safeValue;
        }
    }

    @NonNull
    private List<DailyTrendDTO> getTrendCache(@NonNull String type, int slot) {
        if (TYPE_EXPENSE.equals(type)) {
            if (slot == 0) {
                return latestExpenseCurrent;
            }
            if (slot == 1) {
                return latestExpensePrevOne;
            }
            if (slot == 2) {
                return latestExpensePrevTwo;
            }
            return latestExpensePrevThree;
        }
        if (slot == 0) {
            return latestIncomeCurrent;
        }
        if (slot == 1) {
            return latestIncomePrevOne;
        }
        if (slot == 2) {
            return latestIncomePrevTwo;
        }
        return latestIncomePrevThree;
    }

    @NonNull
    private Map<LocalDate, Double> toDailyAmountMap(@NonNull List<DailyTrendDTO> source) {
        Map<LocalDate, Double> result = new HashMap<>();
        for (DailyTrendDTO dto : source) {
            result.put(toLocalDate(dto.getPeriodStart()), dto.getTotalAmount());
        }
        return result;
    }

    private double sumRange(@NonNull Map<LocalDate, Double> dailyMap,
                            @NonNull LocalDate startDate,
                            @NonNull LocalDate endDate) {
        double total = 0d;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            total += dailyMap.containsKey(cursor) ? dailyMap.get(cursor) : 0d;
            cursor = cursor.plusDays(1);
        }
        return total;
    }

    @NonNull
    private long[] getCurrentMonthBounds() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        normalizeToStartOfDay(calendar);
        long startOfMonth = calendar.getTimeInMillis();
        calendar.add(Calendar.MONTH, 1);
        calendar.add(Calendar.MILLISECOND, -1);
        long endOfMonth = calendar.getTimeInMillis();
        return new long[]{startOfMonth, endOfMonth};
    }

    @NonNull
    private long[] getPreviousMonthBounds() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        normalizeToStartOfDay(calendar);
        calendar.add(Calendar.MONTH, -1);
        long start = calendar.getTimeInMillis();
        calendar.add(Calendar.MONTH, 1);
        calendar.add(Calendar.MILLISECOND, -1);
        long end = calendar.getTimeInMillis();
        return new long[]{start, end};
    }

    @NonNull
    private long[] getCurrentWeekBounds() {
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        normalizeToStartOfDay(calendar);
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
        }
        long start = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_MONTH, 7);
        calendar.add(Calendar.MILLISECOND, -1);
        long end = calendar.getTimeInMillis();
        return new long[]{start, end};
    }

    @NonNull
    private long[] getPreviousWeekBounds() {
        long[] currentWeekBounds = getCurrentWeekBounds();
        Calendar startCal = Calendar.getInstance();
        startCal.setTimeInMillis(currentWeekBounds[0]);
        startCal.add(Calendar.DAY_OF_MONTH, -7);
        Calendar endCal = Calendar.getInstance();
        endCal.setTimeInMillis(currentWeekBounds[1]);
        endCal.add(Calendar.DAY_OF_MONTH, -7);
        return new long[]{startCal.getTimeInMillis(), endCal.getTimeInMillis()};
    }

    private void normalizeToStartOfDay(@NonNull Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    @NonNull
    private LocalDate toLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    private long toStartMillis(@NonNull LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private long toEndMillis(@NonNull LocalDate date) {
        return date.plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() - 1L;
    }

    public LiveData<List<WalletWithBalance>> getWallets() {
        return wallets;
    }

    public LiveData<Double> getTotalBalance() {
        return totalBalance;
    }

    public LiveData<List<TransactionEntity>> getRecentTransactions() {
        return recentTransactions;
    }

    public LiveData<Double> getMonthlyIncome() {
        return monthlyIncome;
    }

    public LiveData<Double> getMonthlyExpense() {
        return monthlyExpense;
    }

    public LiveData<Double> getPreviousMonthlyExpense() {
        return previousMonthlyExpense;
    }

    public LiveData<Double> getWeeklyExpense() {
        return weeklyExpense;
    }

    public LiveData<Double> getPreviousWeeklyExpense() {
        return previousWeeklyExpense;
    }

    public LiveData<List<CategorySumDTO>> getMonthlyTopExpenseCategories() {
        return monthlyTopExpenseCategories;
    }

    public LiveData<List<CategorySumDTO>> getWeeklyTopExpenseCategories() {
        return weeklyTopExpenseCategories;
    }

    public LiveData<List<CategoryEntity>> getExpenseCategories() {
        return expenseCategories;
    }

    public LiveData<List<CategoryEntity>> getIncomeCategories() {
        return incomeCategories;
    }

    public LiveData<List<TrendPointUiModel>> getExpenseComparisonPoints() {
        return expenseComparisonPoints;
    }

    public LiveData<List<TrendPointUiModel>> getIncomeComparisonPoints() {
        return incomeComparisonPoints;
    }

    public static final class TrendPointUiModel {
        @NonNull
        private final String label;
        private final long dateMillis;
        private final double currentAmount;
        private final double averageAmount;

        public TrendPointUiModel(@NonNull String label,
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
}
