package com.group10.moneymate.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;

import com.group10.moneymate.data.local.dto.CategorySumDTO;
import com.group10.moneymate.data.local.dto.DailyTrendDTO;
import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.ui.statistics.MonthlyComparisonBuilder;
import com.group10.moneymate.ui.statistics.MonthlyComparisonPoint;
import com.group10.moneymate.utils.Constants;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HomeViewModel extends AndroidViewModel {

    private static final String DAILY_PERIOD_FORMAT = "%Y-%m-%d";
    private static final String TYPE_EXPENSE = Constants.TYPE_EXPENSE;
    private static final String TYPE_INCOME = Constants.TYPE_INCOME;
    private static final String TYPE_DEBT = Constants.TYPE_DEBT;

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
    private final LiveData<Long> transactionInvalidationKey;
    private final MediatorLiveData<Long> globalRefreshSignal = new MediatorLiveData<>();
    private final LiveData<List<CategoryEntity>> expenseCategories;
    private final LiveData<List<CategoryEntity>> incomeCategories;
    private final LiveData<List<CategoryEntity>> debtCategoryies;
    private final MediatorLiveData<List<MonthlyComparisonPoint>> expenseComparisonPoints = new MediatorLiveData<>();
    private final MediatorLiveData<List<MonthlyComparisonPoint>> incomeComparisonPoints = new MediatorLiveData<>();

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

        transactionInvalidationKey = container.transactionRepository.getTransactionInvalidationKey(userId);
        globalRefreshSignal.setValue(0L);
        globalRefreshSignal.addSource(transactionInvalidationKey,
                value -> globalRefreshSignal.setValue(value != null ? value : 0L));
        globalRefreshSignal.addSource(transactionRepository.getLocalWriteEvents(),
                event -> globalRefreshSignal.setValue(System.currentTimeMillis()));
        globalRefreshSignal.addSource(container.walletRepository.getLocalWriteEvents(),
                event -> globalRefreshSignal.setValue(System.currentTimeMillis()));

        expenseCategories = container.categoryRepository.getCategoriesByType(userId, TYPE_EXPENSE);
        incomeCategories = container.categoryRepository.getCategoriesByType(userId, TYPE_INCOME);
        debtCategoryies = container.categoryRepository.getCategoriesByType(userId, TYPE_DEBT);
        totalBalance = container.walletRepository.getTotalBalance(userId);

        wallets = Transformations.switchMap(globalRefreshSignal,
                ignored -> container.walletRepository.getAllByUserWithBalance(userId));

        long[] currentMonthBounds = getCurrentMonthBounds();
        long[] previousMonthBounds = getPreviousMonthBounds();
        long[] currentWeekBounds = getCurrentWeekBounds();
        long[] previousWeekBounds = getPreviousWeekBounds();

        recentTransactions = Transformations.switchMap(globalRefreshSignal,
                ignored -> container.transactionRepository.getRecentTransactions(userId, 4));

        monthlyIncome = Transformations.switchMap(globalRefreshSignal,
                ignored -> container.transactionRepository.getTotalIncome(
                        userId,
                        currentMonthBounds[0],
                        currentMonthBounds[1]));
        monthlyExpense = Transformations.switchMap(globalRefreshSignal,
                ignored -> container.transactionRepository.getTotalExpense(
                        userId,
                        currentMonthBounds[0],
                        currentMonthBounds[1]));
        previousMonthlyExpense = Transformations.switchMap(globalRefreshSignal,
                ignored -> container.transactionRepository.getTotalExpense(
                        userId,
                        previousMonthBounds[0],
                        previousMonthBounds[1]));
        weeklyExpense = Transformations.switchMap(globalRefreshSignal,
                ignored -> container.transactionRepository.getTotalExpense(
                        userId,
                        currentWeekBounds[0],
                        currentWeekBounds[1]));
        previousWeeklyExpense = Transformations.switchMap(globalRefreshSignal,
                ignored -> container.transactionRepository.getTotalExpense(
                        userId,
                        previousWeekBounds[0],
                        previousWeekBounds[1]));

        monthlyTopExpenseCategories = Transformations.switchMap(globalRefreshSignal,
                ignored -> container.transactionRepository.getCategorySums(
                        userId,
                        TYPE_EXPENSE,
                        currentMonthBounds[0],
                        currentMonthBounds[1],
                        null));
        weeklyTopExpenseCategories = Transformations.switchMap(globalRefreshSignal,
                ignored -> container.transactionRepository.getCategorySums(
                        userId,
                        TYPE_EXPENSE,
                        currentWeekBounds[0],
                        currentWeekBounds[1],
                        null));

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
        return Transformations.switchMap(globalRefreshSignal, ignored -> transactionRepository.getAmountTrend(
                userId,
                type,
                toStartMillis(month.withDayOfMonth(1)),
                toEndMillis(month.withDayOfMonth(month.lengthOfMonth())),
                null,
                DAILY_PERIOD_FORMAT));
    }

    @NonNull
    private LiveData<List<DailyTrendDTO>> createCurrentMonthSource(@NonNull String type,
            @NonNull LocalDate currentMonth,
            @NonNull LocalDate visibleEnd) {
        return Transformations.switchMap(globalRefreshSignal, ignored -> transactionRepository.getAmountTrend(
                userId,
                type,
                toStartMillis(currentMonth),
                toEndMillis(visibleEnd),
                null,
                DAILY_PERIOD_FORMAT));
    }

    private void observeComparisonSource(@NonNull MediatorLiveData<List<MonthlyComparisonPoint>> target,
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
        List<MonthlyComparisonPoint> items = MonthlyComparisonBuilder.build(
                currentMonth,
                visibleEnd,
                getTrendCache(type, 0),
                getTrendCache(type, 1),
                getTrendCache(type, 2),
                getTrendCache(type, 3));

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
    private long[] getCurrentMonthBounds() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        normalizeToStartOfDay(calendar);
        long startOfMonth = calendar.getTimeInMillis();
        calendar.add(Calendar.MONTH, 1);
        calendar.add(Calendar.MILLISECOND, -1);
        long endOfMonth = calendar.getTimeInMillis();
        return new long[] { startOfMonth, endOfMonth };
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
        return new long[] { start, end };
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
        return new long[] { start, end };
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
        return new long[] { startCal.getTimeInMillis(), endCal.getTimeInMillis() };
    }

    private void normalizeToStartOfDay(@NonNull Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
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

    public LiveData<List<CategoryEntity>> getDebtCategoryies() {
        return debtCategoryies;
    }

    public LiveData<List<MonthlyComparisonPoint>> getExpenseComparisonPoints() {
        return expenseComparisonPoints;
    }

    public LiveData<List<MonthlyComparisonPoint>> getIncomeComparisonPoints() {
        return incomeComparisonPoints;
    }

    public void requestRefresh() {
        globalRefreshSignal.setValue(System.currentTimeMillis());
    }
}
