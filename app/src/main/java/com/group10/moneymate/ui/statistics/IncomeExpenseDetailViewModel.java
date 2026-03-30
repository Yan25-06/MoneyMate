package com.group10.moneymate.ui.statistics;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.group10.moneymate.data.local.dto.CategorySumDTO;
import com.group10.moneymate.data.local.dto.DailyTrendDTO;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.data.repository.WalletRepository;
import com.group10.moneymate.models.TransactionType;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class IncomeExpenseDetailViewModel extends ViewModel {

    private static final String DEFAULT_WALLET_LABEL = "Tổng cộng";
    private static final String UNKNOWN_WALLET_LABEL = "Ví";
    private static final String DAILY_PERIOD_FORMAT = "%Y-%m-%d";

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final String userId;
    private final DetailMode detailMode;
    private final TransactionType selectedTransactionType;
    private final MutableLiveData<StatisticsViewModel.FilterState> filterState = new MutableLiveData<>();
    private final MediatorLiveData<String> walletLabel = new MediatorLiveData<>();
    private final LiveData<Double> totalIncomeAmount;
    private final LiveData<Double> totalExpenseAmount;
    private final MediatorLiveData<Double> headerAmount = new MediatorLiveData<>();
    private final MediatorLiveData<Double> averagePerDay = new MediatorLiveData<>();
    private final LiveData<List<CategoryBreakdownItemUiModel>> categoryItems;
    private final MediatorLiveData<List<PeriodSummaryUiModel>> periodSummaries = new MediatorLiveData<>();
    private final MediatorLiveData<List<ComparisonPointUiModel>> comparisonPoints = new MediatorLiveData<>();
    private final MutableLiveData<DrillDownUiState> drillDownState = new MutableLiveData<>(DrillDownUiState.root());
    private final MediatorLiveData<DrillCategoryRequest> childCategoryRequest = new MediatorLiveData<>();
    private final MediatorLiveData<DrillTransactionRequest> drillTransactionRequest = new MediatorLiveData<>();
    private final LiveData<List<CategoryBreakdownItemUiModel>> childCategoryItems;
    private final LiveData<List<TransactionEntity>> drillDownTransactions;
    private final MediatorLiveData<List<CategoryBreakdownItemUiModel>> visibleCategoryItems = new MediatorLiveData<>();

    private LiveData<WalletEntity> walletSource;
    private LiveData<List<DailyTrendDTO>> incomeDailySource;
    private LiveData<List<DailyTrendDTO>> expenseDailySource;
    private LiveData<List<DailyTrendDTO>> comparisonCurrentSource;
    private LiveData<List<DailyTrendDTO>> comparisonPrevOneSource;
    private LiveData<List<DailyTrendDTO>> comparisonPrevTwoSource;
    private LiveData<List<DailyTrendDTO>> comparisonPrevThreeSource;

    @NonNull
    private List<DailyTrendDTO> latestIncomeDaily = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestExpenseDaily = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestComparisonCurrent = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestComparisonPrevOne = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestComparisonPrevTwo = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestComparisonPrevThree = new ArrayList<>();

    public IncomeExpenseDetailViewModel(@NonNull TransactionRepository transactionRepository,
                                        @NonNull WalletRepository walletRepository,
                                        @NonNull CategoryRepository categoryRepository,
                                        @NonNull String userId,
                                        @Nullable String initialWalletId,
                                        long initialStartDate,
                                        long initialEndDate,
                                        @Nullable String transactionTypeValue) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.categoryRepository = categoryRepository;
        this.userId = userId;
        this.detailMode = "NET".equalsIgnoreCase(transactionTypeValue) ? DetailMode.NET : DetailMode.CATEGORY;
        this.selectedTransactionType = parseTransactionType(transactionTypeValue);

        StatisticsViewModel.FilterState initialFilterState = resolveInitialFilterState(
                initialWalletId,
                initialStartDate,
                initialEndDate
        );
        filterState.setValue(initialFilterState);
        connectWalletLabel(initialFilterState.getWalletId());

        totalIncomeAmount = Transformations.switchMap(filterState, state -> {
            if (state == null) {
                return new MutableLiveData<>(0d);
            }
            return normalizeDouble(transactionRepository.getTotalIncomeFiltered(
                    userId,
                    state.getStartDate(),
                    boundedEndDate(state.getEndDate()),
                    state.getWalletId()
            ));
        });

        totalExpenseAmount = Transformations.switchMap(filterState, state -> {
            if (state == null) {
                return new MutableLiveData<>(0d);
            }
            return normalizeDouble(transactionRepository.getTotalExpenseFiltered(
                    userId,
                    state.getStartDate(),
                    boundedEndDate(state.getEndDate()),
                    state.getWalletId()
            ));
        });

        categoryItems = Transformations.switchMap(filterState, state -> {
            if (state == null || detailMode == DetailMode.NET) {
                return new MutableLiveData<>(new ArrayList<>());
            }
            return mapCategoryItemsLiveData(transactionRepository.getRootCategorySums(
                    userId,
                    selectedTransactionType.name(),
                    state.getStartDate(),
                    boundedEndDate(state.getEndDate()),
                    state.getWalletId()
            ));
        });

        childCategoryRequest.addSource(filterState, value -> refreshDrillDownRequests());
        childCategoryRequest.addSource(drillDownState, value -> refreshDrillDownRequests());
        drillTransactionRequest.addSource(filterState, value -> refreshDrillDownRequests());
        drillTransactionRequest.addSource(drillDownState, value -> refreshDrillDownRequests());

        childCategoryItems = Transformations.switchMap(childCategoryRequest, request -> {
            if (request == null || detailMode == DetailMode.NET) {
                return new MutableLiveData<>(new ArrayList<>());
            }
            return mapCategoryItemsLiveData(transactionRepository.getChildCategorySums(
                    userId,
                    selectedTransactionType.name(),
                    request.getFilterState().getStartDate(),
                    boundedEndDate(request.getFilterState().getEndDate()),
                    request.getFilterState().getWalletId(),
                    request.getRootCategoryId()
            ));
        });

        drillDownTransactions = Transformations.switchMap(drillTransactionRequest, request -> {
            if (request == null || detailMode == DetailMode.NET) {
                return new MutableLiveData<>(new ArrayList<>());
            }
            return transactionRepository.getTransactionsForStatisticsDrillDown(
                    userId,
                    selectedTransactionType.name(),
                    request.getFilterState().getStartDate(),
                    boundedEndDate(request.getFilterState().getEndDate()),
                    request.getFilterState().getWalletId(),
                    request.getCategoryId()
            );
        });

        visibleCategoryItems.addSource(categoryItems, value -> updateVisibleCategoryItems());
        visibleCategoryItems.addSource(childCategoryItems, value -> updateVisibleCategoryItems());
        visibleCategoryItems.addSource(drillDownState, value -> updateVisibleCategoryItems());
        refreshDrillDownRequests();
        updateVisibleCategoryItems();

        headerAmount.addSource(totalIncomeAmount, value -> updateHeaderAmount());
        headerAmount.addSource(totalExpenseAmount, value -> updateHeaderAmount());
        averagePerDay.addSource(headerAmount, value -> updateAveragePerDay());
        averagePerDay.addSource(filterState, value -> updateAveragePerDay());

        periodSummaries.addSource(filterState, state -> {
            reloadPeriodSources();
            rebuildPeriodSummaries();
        });
        comparisonPoints.addSource(filterState, state -> {
            reloadComparisonSources();
            rebuildComparisonPoints();
        });
    }

    @NonNull
    public DetailMode getDetailMode() {
        return detailMode;
    }

    @NonNull
    public TransactionType getSelectedTransactionType() {
        return selectedTransactionType;
    }

    public boolean isNetMode() {
        return detailMode == DetailMode.NET;
    }

    public LiveData<String> getWalletLabel() {
        return walletLabel;
    }

    public LiveData<Double> getTotalIncomeAmount() {
        return totalIncomeAmount;
    }

    public LiveData<Double> getTotalExpenseAmount() {
        return totalExpenseAmount;
    }

    public LiveData<Double> getHeaderAmount() {
        return headerAmount;
    }

    public LiveData<Double> getAveragePerDay() {
        return averagePerDay;
    }

    public LiveData<List<CategoryBreakdownItemUiModel>> getCategoryItems() {
        return visibleCategoryItems;
    }

    public LiveData<List<TransactionEntity>> getDrillDownTransactions() {
        return drillDownTransactions;
    }

    public LiveData<DrillDownUiState> getDrillDownState() {
        return drillDownState;
    }

    @NonNull
    public DrillDownUiState getCurrentDrillDownState() {
        DrillDownUiState current = drillDownState.getValue();
        if (current == null) {
            return DrillDownUiState.root();
        }
        return current;
    }

    public void openChildDrillDown(@NonNull CategoryBreakdownItemUiModel rootItem) {
        if (detailMode == DetailMode.NET || rootItem.getCategoryId() == null) {
            return;
        }
        drillDownState.setValue(DrillDownUiState.child(
                rootItem.getCategoryId(),
                rootItem.getCategoryName()
        ));
    }

    public void openTransactionDrillDown(@NonNull CategoryBreakdownItemUiModel childItem) {
        DrillDownUiState current = getCurrentDrillDownState();
        if (detailMode == DetailMode.NET
                || current.getState() != DrillDownState.CHILD_DONUT
                || current.getRootCategoryId() == null
                || childItem.getCategoryId() == null) {
            return;
        }
        drillDownState.setValue(DrillDownUiState.transaction(
                current.getRootCategoryId(),
                current.getRootCategoryName(),
                childItem.getCategoryId(),
                childItem.getCategoryName()
        ));
    }

    public boolean navigateUpInDrillDown() {
        DrillDownUiState current = getCurrentDrillDownState();
        if (current.getState() == DrillDownState.TRANSACTION_LIST
                && current.getRootCategoryId() != null) {
            drillDownState.setValue(DrillDownUiState.child(
                    current.getRootCategoryId(),
                    current.getRootCategoryName()
            ));
            return true;
        }
        if (current.getState() == DrillDownState.CHILD_DONUT) {
            drillDownState.setValue(DrillDownUiState.root());
            return true;
        }
        return false;
    }

    public LiveData<List<PeriodSummaryUiModel>> getPeriodSummaries() {
        return periodSummaries;
    }

    public LiveData<List<ComparisonPointUiModel>> getComparisonPoints() {
        return comparisonPoints;
    }

    public LiveData<StatisticsViewModel.FilterState> getFilterStateLiveData() {
        return filterState;
    }

    @NonNull
    public StatisticsViewModel.FilterState getCurrentFilterState() {
        StatisticsViewModel.FilterState current = filterState.getValue();
        if (current == null) {
            return StatisticsViewModel.FilterState.createCurrentMonth(null);
        }
        return current;
    }

    public void shiftCurrentPeriod(int direction) {
        filterState.setValue(getCurrentFilterState().shift(direction));
    }

    public void updateWalletFilter(@Nullable String walletId, @Nullable String label) {
        StatisticsViewModel.FilterState updatedState = getCurrentFilterState().withWalletId(walletId);
        filterState.setValue(updatedState);
        connectWalletLabel(walletId);
        if (label != null && !label.trim().isEmpty()) {
            walletLabel.setValue(label);
        }
    }

    public void updatePresetPeriod(@NonNull StatisticsViewModel.PeriodType periodType) {
        StatisticsViewModel.FilterState current = getCurrentFilterState();
        filterState.setValue(StatisticsViewModel.FilterState.createForPeriodType(
                periodType,
                current.getWalletId()
        ));
    }

    public void updateCustomDateRange(long startDate, long endDate) {
        if (endDate < startDate) {
            return;
        }
        StatisticsViewModel.FilterState current = getCurrentFilterState();
        filterState.setValue(StatisticsViewModel.FilterState.createRange(
                current.getWalletId(),
                startDate,
                endDate
        ));
    }

    public boolean shouldShowComparisonCard() {
        return detailMode == DetailMode.CATEGORY
                && getCurrentFilterState().getPeriodType() == StatisticsViewModel.PeriodType.MONTH;
    }

    @NonNull
    public String getHeaderSummaryLabel() {
        if (detailMode == DetailMode.NET) {
            return "Thu nhập ròng";
        }
        return selectedTransactionType == TransactionType.INCOME ? "Tổng khoản thu" : "Tổng khoản chi";
    }

    @NonNull
    public String getDetailToggleTitle() {
        return detailMode == DetailMode.NET ? "Thu nhập ròng" : "Báo cáo theo nhóm";
    }

    public void setReportRootCategory(@NonNull String categoryId, @NonNull String categoryName) {
        if (detailMode == DetailMode.NET) {
            return;
        }
        drillDownState.setValue(DrillDownUiState.child(categoryId, categoryName));
    }

    public void checkCategoryHasChildren(@NonNull String categoryId,
                                         @NonNull CategoryRepository.ChildrenCheckCallback callback) {
        categoryRepository.hasActiveChildrenAsync(categoryId, userId, callback);
    }

    private void refreshDrillDownRequests() {
        DrillDownUiState currentDrillState = getCurrentDrillDownState();
        StatisticsViewModel.FilterState currentFilterState = getCurrentFilterState();

        if (detailMode == DetailMode.NET) {
            childCategoryRequest.setValue(null);
            drillTransactionRequest.setValue(null);
            return;
        }

        if (currentDrillState.getState() == DrillDownState.ROOT_DONUT
                || currentDrillState.getRootCategoryId() == null) {
            childCategoryRequest.setValue(null);
            drillTransactionRequest.setValue(null);
            return;
        }

        childCategoryRequest.setValue(new DrillCategoryRequest(
                currentFilterState,
                currentDrillState.getRootCategoryId()
        ));

        if (currentDrillState.getState() == DrillDownState.TRANSACTION_LIST
                && currentDrillState.getChildCategoryId() != null) {
            drillTransactionRequest.setValue(new DrillTransactionRequest(
                    currentFilterState,
                    currentDrillState.getChildCategoryId()
            ));
            return;
        }

        drillTransactionRequest.setValue(null);
    }

    private void updateVisibleCategoryItems() {
        DrillDownUiState currentDrillState = getCurrentDrillDownState();
        if (currentDrillState.getState() == DrillDownState.ROOT_DONUT) {
            List<CategoryBreakdownItemUiModel> roots = categoryItems.getValue();
            visibleCategoryItems.setValue(roots != null ? roots : new ArrayList<>());
            return;
        }
        if (currentDrillState.getState() == DrillDownState.CHILD_DONUT) {
            List<CategoryBreakdownItemUiModel> children = childCategoryItems.getValue();
            visibleCategoryItems.setValue(children != null ? children : new ArrayList<>());
            return;
        }
        visibleCategoryItems.setValue(new ArrayList<>());
    }

    private void updateHeaderAmount() {
        Double totalIncome = totalIncomeAmount.getValue();
        Double totalExpense = totalExpenseAmount.getValue();
        double income = totalIncome != null ? totalIncome : 0d;
        double expense = totalExpense != null ? totalExpense : 0d;
        if (detailMode == DetailMode.NET) {
            headerAmount.setValue(income - expense);
            return;
        }
        headerAmount.setValue(selectedTransactionType == TransactionType.INCOME ? income : expense);
    }

    private void updateAveragePerDay() {
        if (detailMode == DetailMode.NET) {
            averagePerDay.setValue(0d);
            return;
        }
        Double total = headerAmount.getValue();
        StatisticsViewModel.FilterState current = filterState.getValue();
        if (total == null || current == null) {
            averagePerDay.setValue(0d);
            return;
        }
        if (current.getPeriodType() == StatisticsViewModel.PeriodType.ALL) {
            averagePerDay.setValue(total);
            return;
        }
        long dayCount = Math.max(1L, ChronoUnit.DAYS.between(
                toLocalDate(current.getStartDate()),
                toLocalDate(boundedEndDate(current.getEndDate()))
        ) + 1L);
        averagePerDay.setValue(total / dayCount);
    }

    private void reloadPeriodSources() {
        if (incomeDailySource != null) {
            periodSummaries.removeSource(incomeDailySource);
        }
        if (expenseDailySource != null) {
            periodSummaries.removeSource(expenseDailySource);
        }

        StatisticsViewModel.FilterState current = filterState.getValue();
        if (current == null) {
            latestIncomeDaily = new ArrayList<>();
            latestExpenseDaily = new ArrayList<>();
            return;
        }

        long endDate = boundedEndDate(current.getEndDate());
        incomeDailySource = transactionRepository.getAmountTrend(
                userId,
                TransactionType.INCOME.name(),
                current.getStartDate(),
                endDate,
                current.getWalletId(),
                DAILY_PERIOD_FORMAT
        );
        expenseDailySource = transactionRepository.getAmountTrend(
                userId,
                TransactionType.EXPENSE.name(),
                current.getStartDate(),
                endDate,
                current.getWalletId(),
                DAILY_PERIOD_FORMAT
        );

        periodSummaries.addSource(incomeDailySource, value -> {
            latestIncomeDaily = value != null ? value : new ArrayList<>();
            rebuildPeriodSummaries();
        });
        periodSummaries.addSource(expenseDailySource, value -> {
            latestExpenseDaily = value != null ? value : new ArrayList<>();
            rebuildPeriodSummaries();
        });
    }

    private void rebuildPeriodSummaries() {
        StatisticsViewModel.FilterState current = filterState.getValue();
        if (current == null) {
            periodSummaries.setValue(new ArrayList<>());
            return;
        }

        LocalDate startDate = toLocalDate(current.getStartDate());
        LocalDate endDate = toLocalDate(boundedEndDate(current.getEndDate()));
        Map<LocalDate, Double> incomeMap = toDailyAmountMap(latestIncomeDaily);
        Map<LocalDate, Double> expenseMap = toDailyAmountMap(latestExpenseDaily);
        List<DateBucket> buckets = buildDateBuckets(startDate, endDate, current.getPeriodType());
        List<PeriodSummaryUiModel> items = new ArrayList<>();

        for (DateBucket bucket : buckets) {
            double incomeAmount = sumRange(incomeMap, bucket.getStartDate(), bucket.getEndDate());
            double expenseAmount = sumRange(expenseMap, bucket.getStartDate(), bucket.getEndDate());
            if (incomeAmount == 0d && expenseAmount == 0d && buckets.size() > 1) {
                continue;
            }
            items.add(new PeriodSummaryUiModel(
                    buildBucketLabel(bucket.getStartDate(), bucket.getEndDate(), current.getPeriodType()),
                    toStartMillis(bucket.getStartDate()),
                    toEndMillis(bucket.getEndDate()),
                    incomeAmount,
                    expenseAmount
            ));
        }

        if (items.isEmpty()) {
            items.add(new PeriodSummaryUiModel(
                    buildBucketLabel(startDate, endDate, current.getPeriodType()),
                    toStartMillis(startDate),
                    toEndMillis(endDate),
                    0d,
                    0d
            ));
        }
        periodSummaries.setValue(items);
    }

    private void reloadComparisonSources() {
        detachComparisonSources();

        StatisticsViewModel.FilterState current = filterState.getValue();
        if (current == null || detailMode == DetailMode.NET
                || current.getPeriodType() != StatisticsViewModel.PeriodType.MONTH) {
            resetComparisonData();
            return;
        }

        LocalDate currentMonth = toLocalDate(current.getStartDate()).withDayOfMonth(1);
        LocalDate visibleEnd = toLocalDate(boundedEndDate(current.getEndDate()));

        comparisonCurrentSource = transactionRepository.getAmountTrend(
                userId,
                selectedTransactionType.name(),
                toStartMillis(currentMonth),
                toEndMillis(visibleEnd),
                current.getWalletId(),
                DAILY_PERIOD_FORMAT
        );
        comparisonPrevOneSource = buildPreviousMonthSource(current, currentMonth.minusMonths(1));
        comparisonPrevTwoSource = buildPreviousMonthSource(current, currentMonth.minusMonths(2));
        comparisonPrevThreeSource = buildPreviousMonthSource(current, currentMonth.minusMonths(3));

        comparisonPoints.addSource(comparisonCurrentSource, value -> {
            latestComparisonCurrent = value != null ? value : new ArrayList<>();
            rebuildComparisonPoints();
        });
        comparisonPoints.addSource(comparisonPrevOneSource, value -> {
            latestComparisonPrevOne = value != null ? value : new ArrayList<>();
            rebuildComparisonPoints();
        });
        comparisonPoints.addSource(comparisonPrevTwoSource, value -> {
            latestComparisonPrevTwo = value != null ? value : new ArrayList<>();
            rebuildComparisonPoints();
        });
        comparisonPoints.addSource(comparisonPrevThreeSource, value -> {
            latestComparisonPrevThree = value != null ? value : new ArrayList<>();
            rebuildComparisonPoints();
        });
    }

    @NonNull
    private LiveData<List<DailyTrendDTO>> buildPreviousMonthSource(@NonNull StatisticsViewModel.FilterState current,
                                                                   @NonNull LocalDate month) {
        return transactionRepository.getAmountTrend(
                userId,
                selectedTransactionType.name(),
                toStartMillis(month.withDayOfMonth(1)),
                toEndMillis(month.withDayOfMonth(month.lengthOfMonth())),
                current.getWalletId(),
                DAILY_PERIOD_FORMAT
        );
    }

    private void rebuildComparisonPoints() {
        StatisticsViewModel.FilterState current = filterState.getValue();
        if (current == null || detailMode == DetailMode.NET
                || current.getPeriodType() != StatisticsViewModel.PeriodType.MONTH) {
            comparisonPoints.setValue(new ArrayList<>());
            return;
        }

        LocalDate currentMonth = toLocalDate(current.getStartDate()).withDayOfMonth(1);
        LocalDate visibleEnd = toLocalDate(boundedEndDate(current.getEndDate()));
        int lastVisibleDay = visibleEnd.getDayOfMonth();

        Map<LocalDate, Double> currentMap = toDailyAmountMap(latestComparisonCurrent);
        Map<LocalDate, Double> prevOneMap = toDailyAmountMap(latestComparisonPrevOne);
        Map<LocalDate, Double> prevTwoMap = toDailyAmountMap(latestComparisonPrevTwo);
        Map<LocalDate, Double> prevThreeMap = toDailyAmountMap(latestComparisonPrevThree);

        List<Map<LocalDate, Double>> previousMaps = new ArrayList<>();
        previousMaps.add(prevOneMap);
        previousMaps.add(prevTwoMap);
        previousMaps.add(prevThreeMap);

        List<LocalDate> previousMonths = new ArrayList<>();
        previousMonths.add(currentMonth.minusMonths(1));
        previousMonths.add(currentMonth.minusMonths(2));
        previousMonths.add(currentMonth.minusMonths(3));

        List<ComparisonPointUiModel> items = new ArrayList<>();
        double currentRunning = 0d;
        double lastAverage = 0d;
        for (int dayOfMonth = 1; dayOfMonth <= lastVisibleDay; dayOfMonth++) {
            LocalDate currentDate = currentMonth.withDayOfMonth(dayOfMonth);
            currentRunning += currentMap.getOrDefault(currentDate, 0d);

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
            items.add(new ComparisonPointUiModel(
                    String.format(Locale.getDefault(), "%02d/%02d", currentDate.getDayOfMonth(), currentDate.getMonthValue()),
                    toStartMillis(currentDate),
                    currentRunning,
                    averageValue
            ));
        }
        comparisonPoints.setValue(items);
    }

    private void detachComparisonSources() {
        if (comparisonCurrentSource != null) {
            comparisonPoints.removeSource(comparisonCurrentSource);
            comparisonCurrentSource = null;
        }
        if (comparisonPrevOneSource != null) {
            comparisonPoints.removeSource(comparisonPrevOneSource);
            comparisonPrevOneSource = null;
        }
        if (comparisonPrevTwoSource != null) {
            comparisonPoints.removeSource(comparisonPrevTwoSource);
            comparisonPrevTwoSource = null;
        }
        if (comparisonPrevThreeSource != null) {
            comparisonPoints.removeSource(comparisonPrevThreeSource);
            comparisonPrevThreeSource = null;
        }
    }

    private void resetComparisonData() {
        latestComparisonCurrent = new ArrayList<>();
        latestComparisonPrevOne = new ArrayList<>();
        latestComparisonPrevTwo = new ArrayList<>();
        latestComparisonPrevThree = new ArrayList<>();
        comparisonPoints.setValue(new ArrayList<>());
    }

    @NonNull
    private LiveData<Double> normalizeDouble(@NonNull LiveData<Double> source) {
        MediatorLiveData<Double> result = new MediatorLiveData<>();
        result.addSource(source, value -> result.setValue(value != null ? value : 0d));
        return result;
    }

    @NonNull
    private LiveData<List<CategoryBreakdownItemUiModel>> mapCategoryItemsLiveData(@NonNull LiveData<List<CategorySumDTO>> source) {
        MediatorLiveData<List<CategoryBreakdownItemUiModel>> result = new MediatorLiveData<>();
        result.addSource(source, value -> result.setValue(mapCategoryItems(value)));
        return result;
    }

    @NonNull
    private List<CategoryBreakdownItemUiModel> mapCategoryItems(@Nullable List<CategorySumDTO> source) {
        List<CategoryBreakdownItemUiModel> items = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return items;
        }

        double total = 0d;
        for (CategorySumDTO dto : source) {
            total += dto.getTotalAmount();
        }
        if (total <= 0d) {
            total = 1d;
        }

        for (CategorySumDTO dto : source) {
            double percent = (dto.getTotalAmount() / total) * 100d;
            items.add(new CategoryBreakdownItemUiModel(
                    dto.getCategoryId(),
                    dto.getCategoryName() != null ? dto.getCategoryName() : "Chưa phân loại",
                    dto.getIconName(),
                    dto.getTotalAmount(),
                    percent,
                    dto.getTransactionCount()
            ));
        }
        return items;
    }

    private void connectWalletLabel(@Nullable String walletId) {
        if (walletSource != null) {
            walletLabel.removeSource(walletSource);
            walletSource = null;
        }

        if (walletId == null || walletId.trim().isEmpty()) {
            walletLabel.setValue(DEFAULT_WALLET_LABEL);
            return;
        }

        walletSource = walletRepository.getById(walletId);
        walletLabel.addSource(walletSource, wallet -> {
            if (wallet != null && wallet.getName() != null && !wallet.getName().trim().isEmpty()) {
                walletLabel.setValue(wallet.getName());
                return;
            }
            walletLabel.setValue(UNKNOWN_WALLET_LABEL);
        });
    }

    @NonNull
    private StatisticsViewModel.FilterState resolveInitialFilterState(@Nullable String walletId,
                                                                      long startDate,
                                                                      long endDate) {
        if (startDate <= 0L && endDate == Long.MAX_VALUE) {
            return StatisticsViewModel.FilterState.createAll(walletId);
        }
        if (startDate <= 0L || endDate <= 0L) {
            return StatisticsViewModel.FilterState.createCurrentMonth(walletId);
        }
        LocalDate start = toLocalDate(startDate);
        LocalDate end = toLocalDate(endDate);

        if (start.equals(end)) {
            return StatisticsViewModel.FilterState.createDay(walletId, start);
        }
        if (start.getDayOfWeek() == DayOfWeek.MONDAY
                && end.getDayOfWeek() == DayOfWeek.SUNDAY
                && ChronoUnit.DAYS.between(start, end) == 6L) {
            return StatisticsViewModel.FilterState.createWeek(walletId, start);
        }
        if (start.getDayOfMonth() == 1
                && end.getDayOfMonth() == end.lengthOfMonth()
                && start.getMonth() == end.getMonth()
                && start.getYear() == end.getYear()) {
            return StatisticsViewModel.FilterState.createMonth(walletId, start);
        }
        if (start.getDayOfMonth() == 1
                && (start.getMonthValue() == 1 || start.getMonthValue() == 4
                || start.getMonthValue() == 7 || start.getMonthValue() == 10)
                && end.equals(start.plusMonths(2).withDayOfMonth(start.plusMonths(2).lengthOfMonth()))) {
            return StatisticsViewModel.FilterState.createQuarter(walletId, start);
        }
        if (start.getMonthValue() == 1
                && start.getDayOfMonth() == 1
                && end.getMonthValue() == 12
                && end.getDayOfMonth() == 31
                && start.getYear() == end.getYear()) {
            return StatisticsViewModel.FilterState.createYear(walletId, start);
        }
        return StatisticsViewModel.FilterState.createRange(walletId, startDate, endDate);
    }

    @NonNull
    private TransactionType parseTransactionType(@Nullable String rawValue) {
        if ("INCOME".equalsIgnoreCase(rawValue)) {
            return TransactionType.INCOME;
        }
        return TransactionType.EXPENSE;
    }

    private long boundedEndDate(long rawEndDate) {
        if (rawEndDate == Long.MAX_VALUE) {
            return endOfToday();
        }
        return Math.min(rawEndDate, endOfToday());
    }

    private long endOfToday() {
        return LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() - 1L;
    }

    @NonNull
    private LocalDate toLocalDate(long epochMillis) {
        if (epochMillis <= 0L || epochMillis == Long.MAX_VALUE) {
            return LocalDate.now();
        }
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

    @NonNull
    private Map<LocalDate, Double> toDailyAmountMap(@Nullable List<DailyTrendDTO> source) {
        Map<LocalDate, Double> result = new HashMap<>();
        if (source == null) {
            return result;
        }
        for (DailyTrendDTO dto : source) {
            LocalDate date = toLocalDate(dto.getPeriodStart());
            result.put(date, dto.getTotalAmount());
        }
        return result;
    }

    private double sumRange(@NonNull Map<LocalDate, Double> dailyMap,
                            @NonNull LocalDate startDate,
                            @NonNull LocalDate endDate) {
        double total = 0d;
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            total += dailyMap.getOrDefault(cursor, 0d);
            cursor = cursor.plusDays(1);
        }
        return total;
    }

    @NonNull
    private List<DateBucket> buildDateBuckets(@NonNull LocalDate startDate,
                                              @NonNull LocalDate endDate,
                                              @NonNull StatisticsViewModel.PeriodType periodType) {
        List<DateBucket> buckets = new ArrayList<>();
        if (endDate.isBefore(startDate)) {
            buckets.add(new DateBucket(startDate, startDate));
            return buckets;
        }

        switch (periodType) {
            case DAY:
                buckets.add(new DateBucket(startDate, endDate));
                break;
            case WEEK:
                LocalDate dayCursor = startDate;
                while (!dayCursor.isAfter(endDate)) {
                    buckets.add(new DateBucket(dayCursor, dayCursor));
                    dayCursor = dayCursor.plusDays(1);
                }
                break;
            case MONTH:
                buildWeeklyBuckets(buckets, startDate, endDate);
                break;
            case QUARTER:
            case YEAR:
            case ALL:
                buildMonthlyBuckets(buckets, startDate, endDate);
                break;
            case CUSTOM:
            default:
                long spanDays = ChronoUnit.DAYS.between(startDate, endDate) + 1L;
                if (spanDays <= 14L) {
                    LocalDate customDayCursor = startDate;
                    while (!customDayCursor.isAfter(endDate)) {
                        buckets.add(new DateBucket(customDayCursor, customDayCursor));
                        customDayCursor = customDayCursor.plusDays(1);
                    }
                } else if (spanDays <= 92L) {
                    buildWeeklyBuckets(buckets, startDate, endDate);
                } else {
                    buildMonthlyBuckets(buckets, startDate, endDate);
                }
                break;
        }
        return buckets;
    }

    private void buildWeeklyBuckets(@NonNull List<DateBucket> buckets,
                                    @NonNull LocalDate startDate,
                                    @NonNull LocalDate endDate) {
        LocalDate bucketStart = startDate;
        while (!bucketStart.isAfter(endDate)) {
            int daysToEndOfWeek = DayOfWeek.SUNDAY.getValue() - bucketStart.getDayOfWeek().getValue();
            if (daysToEndOfWeek < 0) {
                daysToEndOfWeek = 0;
            }
            LocalDate bucketEnd = bucketStart.plusDays(daysToEndOfWeek);
            if (bucketEnd.isAfter(endDate)) {
                bucketEnd = endDate;
            }
            buckets.add(new DateBucket(bucketStart, bucketEnd));
            bucketStart = bucketEnd.plusDays(1);
        }
    }

    private void buildMonthlyBuckets(@NonNull List<DateBucket> buckets,
                                     @NonNull LocalDate startDate,
                                     @NonNull LocalDate endDate) {
        LocalDate bucketStart = startDate.withDayOfMonth(1);
        if (bucketStart.isBefore(startDate)) {
            bucketStart = startDate;
        }
        while (!bucketStart.isAfter(endDate)) {
            LocalDate bucketEnd = bucketStart.withDayOfMonth(bucketStart.lengthOfMonth());
            if (bucketEnd.isAfter(endDate)) {
                bucketEnd = endDate;
            }
            if (bucketStart.isBefore(startDate)) {
                bucketStart = startDate;
            }
            buckets.add(new DateBucket(bucketStart, bucketEnd));
            bucketStart = bucketEnd.plusDays(1);
        }
    }

    @NonNull
    private String buildBucketLabel(@NonNull LocalDate startDate,
                                    @NonNull LocalDate endDate,
                                    @NonNull StatisticsViewModel.PeriodType periodType) {
        if (periodType == StatisticsViewModel.PeriodType.QUARTER
                || periodType == StatisticsViewModel.PeriodType.YEAR
                || periodType == StatisticsViewModel.PeriodType.ALL) {
            return String.format(Locale.getDefault(), "%02d/%d", startDate.getMonthValue(), startDate.getYear());
        }
        return String.format(Locale.getDefault(), "%02d/%02d - %02d/%02d",
                startDate.getDayOfMonth(),
                startDate.getMonthValue(),
                endDate.getDayOfMonth(),
                endDate.getMonthValue());
    }

    public enum DrillDownState {
        ROOT_DONUT,
        CHILD_DONUT,
        TRANSACTION_LIST
    }

    public static final class DrillDownUiState {
        @NonNull
        private final DrillDownState state;
        @Nullable
        private final String rootCategoryId;
        @Nullable
        private final String rootCategoryName;
        @Nullable
        private final String childCategoryId;
        @Nullable
        private final String childCategoryName;

        private DrillDownUiState(@NonNull DrillDownState state,
                                 @Nullable String rootCategoryId,
                                 @Nullable String rootCategoryName,
                                 @Nullable String childCategoryId,
                                 @Nullable String childCategoryName) {
            this.state = state;
            this.rootCategoryId = rootCategoryId;
            this.rootCategoryName = rootCategoryName;
            this.childCategoryId = childCategoryId;
            this.childCategoryName = childCategoryName;
        }

        @NonNull
        public static DrillDownUiState root() {
            return new DrillDownUiState(DrillDownState.ROOT_DONUT, null, null, null, null);
        }

        @NonNull
        public static DrillDownUiState child(@NonNull String rootCategoryId,
                                             @Nullable String rootCategoryName) {
            return new DrillDownUiState(DrillDownState.CHILD_DONUT, rootCategoryId, rootCategoryName, null, null);
        }

        @NonNull
        public static DrillDownUiState transaction(@NonNull String rootCategoryId,
                                                   @Nullable String rootCategoryName,
                                                   @NonNull String childCategoryId,
                                                   @Nullable String childCategoryName) {
            return new DrillDownUiState(
                    DrillDownState.TRANSACTION_LIST,
                    rootCategoryId,
                    rootCategoryName,
                    childCategoryId,
                    childCategoryName
            );
        }

        @NonNull
        public DrillDownState getState() {
            return state;
        }

        @Nullable
        public String getRootCategoryId() {
            return rootCategoryId;
        }

        @Nullable
        public String getRootCategoryName() {
            return rootCategoryName;
        }

        @Nullable
        public String getChildCategoryId() {
            return childCategoryId;
        }

        @Nullable
        public String getChildCategoryName() {
            return childCategoryName;
        }
    }

    private static final class DrillCategoryRequest {
        @NonNull
        private final StatisticsViewModel.FilterState filterState;
        @NonNull
        private final String rootCategoryId;

        private DrillCategoryRequest(@NonNull StatisticsViewModel.FilterState filterState,
                                     @NonNull String rootCategoryId) {
            this.filterState = filterState;
            this.rootCategoryId = rootCategoryId;
        }

        @NonNull
        private StatisticsViewModel.FilterState getFilterState() {
            return filterState;
        }

        @NonNull
        private String getRootCategoryId() {
            return rootCategoryId;
        }
    }

    private static final class DrillTransactionRequest {
        @NonNull
        private final StatisticsViewModel.FilterState filterState;
        @NonNull
        private final String categoryId;

        private DrillTransactionRequest(@NonNull StatisticsViewModel.FilterState filterState,
                                        @NonNull String categoryId) {
            this.filterState = filterState;
            this.categoryId = categoryId;
        }

        @NonNull
        private StatisticsViewModel.FilterState getFilterState() {
            return filterState;
        }

        @NonNull
        private String getCategoryId() {
            return categoryId;
        }
    }

    public enum DetailMode {
        NET,
        CATEGORY
    }

    public static class Factory implements ViewModelProvider.Factory {

        private final TransactionRepository transactionRepository;
        private final WalletRepository walletRepository;
        private final CategoryRepository categoryRepository;
        private final String userId;
        @Nullable
        private final String walletId;
        private final long startDate;
        private final long endDate;
        @Nullable
        private final String transactionType;

        public Factory(@NonNull TransactionRepository transactionRepository,
                       @NonNull WalletRepository walletRepository,
                       @NonNull CategoryRepository categoryRepository,
                       @NonNull String userId,
                       @Nullable String walletId,
                       long startDate,
                       long endDate,
                       @Nullable String transactionType) {
            this.transactionRepository = transactionRepository;
            this.walletRepository = walletRepository;
            this.categoryRepository = categoryRepository;
            this.userId = userId;
            this.walletId = walletId;
            this.startDate = startDate;
            this.endDate = endDate;
            this.transactionType = transactionType;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(IncomeExpenseDetailViewModel.class)) {
                return (T) new IncomeExpenseDetailViewModel(
                        transactionRepository,
                        walletRepository,
                        categoryRepository,
                        userId,
                        walletId,
                        startDate,
                        endDate,
                        transactionType
                );
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }

    public static final class CategoryBreakdownItemUiModel {
        @Nullable
        private final String categoryId;
        @NonNull
        private final String categoryName;
        @Nullable
        private final String iconName;
        private final double totalAmount;
        private final double sharePercent;
        private final int transactionCount;

        public CategoryBreakdownItemUiModel(@Nullable String categoryId,
                                            @NonNull String categoryName,
                                            @Nullable String iconName,
                                            double totalAmount,
                                            double sharePercent,
                                            int transactionCount) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.iconName = iconName;
            this.totalAmount = totalAmount;
            this.sharePercent = sharePercent;
            this.transactionCount = transactionCount;
        }

        @Nullable
        public String getCategoryId() {
            return categoryId;
        }

        @NonNull
        public String getCategoryName() {
            return categoryName;
        }

        public String getIconName() {
            return iconName;
        }

        public double getTotalAmount() {
            return totalAmount;
        }

        public double getSharePercent() {
            return sharePercent;
        }

        public int getTransactionCount() {
            return transactionCount;
        }
    }

    public static final class PeriodSummaryUiModel {
        @NonNull
        private final String label;
        private final long startDate;
        private final long endDate;
        private final double incomeAmount;
        private final double expenseAmount;

        public PeriodSummaryUiModel(@NonNull String label,
                                    long startDate,
                                    long endDate,
                                    double incomeAmount,
                                    double expenseAmount) {
            this.label = label;
            this.startDate = startDate;
            this.endDate = endDate;
            this.incomeAmount = incomeAmount;
            this.expenseAmount = expenseAmount;
        }

        @NonNull
        public String getLabel() {
            return label;
        }

        public long getStartDate() {
            return startDate;
        }

        public long getEndDate() {
            return endDate;
        }

        public double getIncomeAmount() {
            return incomeAmount;
        }

        public double getExpenseAmount() {
            return expenseAmount;
        }

        public double getNetAmount() {
            return incomeAmount - expenseAmount;
        }

        public double getPrimaryAmount(@NonNull TransactionType type) {
            return type == TransactionType.INCOME ? incomeAmount : expenseAmount;
        }
    }

    public static final class ComparisonPointUiModel {
        @NonNull
        private final String label;
        private final long dateMillis;
        private final double currentAmount;
        private final double averageAmount;

        public ComparisonPointUiModel(@NonNull String label,
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

    private static final class DateBucket {
        @NonNull
        private final LocalDate startDate;
        @NonNull
        private final LocalDate endDate;

        private DateBucket(@NonNull LocalDate startDate, @NonNull LocalDate endDate) {
            this.startDate = startDate;
            this.endDate = endDate;
        }

        @NonNull
        private LocalDate getStartDate() {
            return startDate;
        }

        @NonNull
        private LocalDate getEndDate() {
            return endDate;
        }
    }
}
