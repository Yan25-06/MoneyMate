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
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.utils.TimeWindowUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CategoryReportViewModel extends ViewModel {

    private static final String DAILY_PERIOD_FORMAT = "%Y-%m-%d";

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final String userId;
    private final TransactionType selectedTransactionType;
    private final MutableLiveData<StatisticsViewModel.FilterState> filterState = new MutableLiveData<>();
    private final MutableLiveData<String> selectedCategoryId = new MutableLiveData<>();
    private final MediatorLiveData<List<CategoryOptionUiModel>> categoryOptions = new MediatorLiveData<>();
    private final MediatorLiveData<CategoryOptionUiModel> selectedCategory = new MediatorLiveData<>();
    private final MediatorLiveData<Double> totalAmount = new MediatorLiveData<>(0d);
    private final MediatorLiveData<Double> averagePerDay = new MediatorLiveData<>(0d);
    private final MediatorLiveData<List<IncomeExpenseDetailViewModel.PeriodSummaryUiModel>> trendSummaries =
            new MediatorLiveData<>(new ArrayList<>());
    private final MediatorLiveData<List<IncomeExpenseDetailViewModel.ComparisonPointUiModel>> comparisonPoints =
            new MediatorLiveData<>(new ArrayList<>());
    private final MediatorLiveData<ChildQuery> childQuery = new MediatorLiveData<>();
    private final LiveData<List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel>> branchCategoryItems;

    private LiveData<Double> totalAmountSource;
    private LiveData<List<DailyTrendDTO>> currentBranchDailySource;
    private LiveData<List<DailyTrendDTO>> comparisonCurrentSource;
    private LiveData<List<DailyTrendDTO>> comparisonPrevOneSource;
    private LiveData<List<DailyTrendDTO>> comparisonPrevTwoSource;
    private LiveData<List<DailyTrendDTO>> comparisonPrevThreeSource;

    @NonNull
    private List<DailyTrendDTO> latestCurrentDaily = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestComparisonCurrent = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestComparisonPrevOne = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestComparisonPrevTwo = new ArrayList<>();
    @NonNull
    private List<DailyTrendDTO> latestComparisonPrevThree = new ArrayList<>();

    public CategoryReportViewModel(@NonNull TransactionRepository transactionRepository,
                                   @NonNull CategoryRepository categoryRepository,
                                   @NonNull String userId,
                                   @Nullable String initialWalletId,
                                   long initialStartDate,
                                   long initialEndDate,
                                   @Nullable String transactionTypeValue,
                                   @Nullable String initialCategoryId) {
        this.transactionRepository = transactionRepository;
        this.categoryRepository = categoryRepository;
        this.userId = userId;
        this.selectedTransactionType = parseTransactionType(transactionTypeValue);

        filterState.setValue(resolveInitialFilterState(initialWalletId, initialStartDate, initialEndDate));
        selectedCategoryId.setValue(initialCategoryId);

        LiveData<List<CategoryOptionUiModel>> categoryOptionsSource = Transformations.switchMap(
                filterState,
                state -> Transformations.map(
                        categoryRepository.getParentCategoriesWithChildrenByTypeAndWalletIncludingDeleted(
                                userId,
                                selectedTransactionType.name(),
                                state == null ? null : state.getWalletId()
                        ),
                        this::mapCategoryOptions
                )
        );
        categoryOptions.addSource(categoryOptionsSource, items -> {
            List<CategoryOptionUiModel> safeItems = items != null ? items : new ArrayList<>();
            categoryOptions.setValue(safeItems);
            synchronizeSelectedCategory(safeItems);
        });
        selectedCategory.addSource(selectedCategoryId, value -> synchronizeSelectedCategory(categoryOptions.getValue()));
        selectedCategory.addSource(categoryOptions, this::synchronizeSelectedCategory);

        childQuery.addSource(filterState, value -> refreshChildQuery());
        childQuery.addSource(selectedCategoryId, value -> refreshChildQuery());
        branchCategoryItems = Transformations.switchMap(childQuery, query -> {
            if (query == null) {
                return new MutableLiveData<>(new ArrayList<>());
            }
            return Transformations.map(
                    transactionRepository.getCategoryBranchSums(
                            userId,
                            selectedTransactionType.name(),
                            query.filterState.getStartDate(),
                            boundedEndDate(query.filterState.getEndDate()),
                            query.filterState.getWalletId(),
                            query.parentCategoryId
                    ),
                    this::mapChildCategoryItems
            );
        });

        totalAmount.addSource(filterState, value -> reloadTotalAmountSource());
        totalAmount.addSource(selectedCategoryId, value -> reloadTotalAmountSource());
        averagePerDay.addSource(totalAmount, value -> updateAveragePerDay());
        averagePerDay.addSource(filterState, value -> updateAveragePerDay());

        trendSummaries.addSource(filterState, value -> reloadDailySource());
        trendSummaries.addSource(selectedCategoryId, value -> reloadDailySource());

        comparisonPoints.addSource(filterState, value -> reloadComparisonSources());
        comparisonPoints.addSource(selectedCategoryId, value -> reloadComparisonSources());

        refreshChildQuery();
        reloadTotalAmountSource();
        reloadDailySource();
        reloadComparisonSources();
    }

    public LiveData<List<CategoryOptionUiModel>> getCategoryOptions() {
        return categoryOptions;
    }

    public LiveData<CategoryOptionUiModel> getSelectedCategory() {
        return selectedCategory;
    }

    public LiveData<List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel>> getBranchCategoryItems() {
        return branchCategoryItems;
    }

    public LiveData<Double> getTotalAmount() {
        return totalAmount;
    }

    public LiveData<Double> getAveragePerDay() {
        return averagePerDay;
    }

    public LiveData<List<IncomeExpenseDetailViewModel.PeriodSummaryUiModel>> getTrendSummaries() {
        return trendSummaries;
    }

    public LiveData<List<IncomeExpenseDetailViewModel.ComparisonPointUiModel>> getComparisonPoints() {
        return comparisonPoints;
    }

    public LiveData<StatisticsViewModel.FilterState> getFilterStateLiveData() {
        return filterState;
    }

    @NonNull
    public StatisticsViewModel.FilterState getCurrentFilterState() {
        StatisticsViewModel.FilterState current = filterState.getValue();
        return current != null ? current : StatisticsViewModel.FilterState.createCurrentMonth(null);
    }

    @NonNull
    public TransactionType getSelectedTransactionType() {
        return selectedTransactionType;
    }

    @Nullable
    public String getSelectedCategoryId() {
        return selectedCategoryId.getValue();
    }

    public void updateSelectedCategory(@Nullable String categoryId) {
        if (categoryId == null || categoryId.trim().isEmpty()) {
            return;
        }
        if (categoryId.equals(selectedCategoryId.getValue())) {
            return;
        }
        selectedCategoryId.setValue(categoryId);
    }

    public void shiftCurrentPeriod(int direction) {
        filterState.setValue(getCurrentFilterState().shift(direction));
    }

    public void updatePresetPeriod(@NonNull StatisticsViewModel.PeriodType periodType) {
        StatisticsViewModel.FilterState current = getCurrentFilterState();
        filterState.setValue(StatisticsViewModel.FilterState.createForPeriodType(
                periodType,
                current.getWalletId()
        ));
    }

    public void updateCustomRange(long startDate, long endDate) {
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
        return getCurrentFilterState().getPeriodType() == StatisticsViewModel.PeriodType.MONTH;
    }

    private void refreshChildQuery() {
        StatisticsViewModel.FilterState currentFilterState = filterState.getValue();
        String parentCategoryId = selectedCategoryId.getValue();
        if (currentFilterState == null || parentCategoryId == null || parentCategoryId.trim().isEmpty()) {
            childQuery.setValue(null);
            return;
        }
        childQuery.setValue(new ChildQuery(currentFilterState, parentCategoryId));
    }

    private void synchronizeSelectedCategory(@Nullable List<CategoryOptionUiModel> items) {
        if (items == null || items.isEmpty()) {
            selectedCategory.setValue(null);
            return;
        }
        String currentId = selectedCategoryId.getValue();
        CategoryOptionUiModel matched = null;
        for (CategoryOptionUiModel item : items) {
            if (item.getCategoryId().equals(currentId)) {
                matched = item;
                break;
            }
        }
        if (matched == null) {
            matched = items.get(0);
            selectedCategoryId.setValue(matched.getCategoryId());
        }
        selectedCategory.setValue(matched);
    }

    private void reloadTotalAmountSource() {
        if (totalAmountSource != null) {
            totalAmount.removeSource(totalAmountSource);
        }

        StatisticsViewModel.FilterState currentFilter = filterState.getValue();
        String parentCategoryId = selectedCategoryId.getValue();
        if (currentFilter == null || parentCategoryId == null || parentCategoryId.trim().isEmpty()) {
            totalAmount.setValue(0d);
            return;
        }

        totalAmountSource = transactionRepository.getParentCategoryBranchTotalAmount(
                userId,
                selectedTransactionType.name(),
                parentCategoryId,
                currentFilter.getStartDate(),
                boundedEndDate(currentFilter.getEndDate()),
                currentFilter.getWalletId()
        );
        totalAmount.addSource(totalAmountSource, value -> totalAmount.setValue(value != null ? value : 0d));
    }

    private void updateAveragePerDay() {
        Double total = totalAmount.getValue();
        StatisticsViewModel.FilterState currentFilter = filterState.getValue();
        if (total == null || currentFilter == null) {
            averagePerDay.setValue(0d);
            return;
        }
        if (currentFilter.getPeriodType() == StatisticsViewModel.PeriodType.ALL) {
            averagePerDay.setValue(total);
            return;
        }
        long dayCount = Math.max(1L, ChronoUnit.DAYS.between(
                toLocalDate(currentFilter.getStartDate()),
                toLocalDate(boundedEndDate(currentFilter.getEndDate()))
        ) + 1L);
        averagePerDay.setValue(total / dayCount);
    }

    private void reloadDailySource() {
        if (currentBranchDailySource != null) {
            trendSummaries.removeSource(currentBranchDailySource);
            currentBranchDailySource = null;
        }

        StatisticsViewModel.FilterState currentFilter = filterState.getValue();
        String parentCategoryId = selectedCategoryId.getValue();
        if (currentFilter == null || parentCategoryId == null || parentCategoryId.trim().isEmpty()) {
            latestCurrentDaily = new ArrayList<>();
            trendSummaries.setValue(new ArrayList<>());
            return;
        }

        currentBranchDailySource = transactionRepository.getParentCategoryBranchAmountTrend(
                userId,
                selectedTransactionType.name(),
                parentCategoryId,
                currentFilter.getStartDate(),
                boundedEndDate(currentFilter.getEndDate()),
                currentFilter.getWalletId(),
                DAILY_PERIOD_FORMAT
        );
        trendSummaries.addSource(currentBranchDailySource, value -> {
            latestCurrentDaily = value != null ? value : new ArrayList<>();
            rebuildTrendSummaries();
        });
    }

    private void rebuildTrendSummaries() {
        StatisticsViewModel.FilterState currentFilter = filterState.getValue();
        if (currentFilter == null) {
            trendSummaries.setValue(new ArrayList<>());
            return;
        }

        LocalDate startDate = toLocalDate(currentFilter.getStartDate());
        LocalDate endDate = toLocalDate(boundedEndDate(currentFilter.getEndDate()));
        Map<LocalDate, Double> amountMap = toDailyAmountMap(latestCurrentDaily);
        List<DateBucket> buckets = buildDateBuckets(startDate, endDate, currentFilter.getPeriodType());
        List<IncomeExpenseDetailViewModel.PeriodSummaryUiModel> items = new ArrayList<>();

        for (DateBucket bucket : buckets) {
            double amount = sumRange(amountMap, bucket.getStartDate(), bucket.getEndDate());
            if (amount == 0d && buckets.size() > 1) {
                continue;
            }
            items.add(buildSummaryItem(
                    buildBucketLabel(bucket.getStartDate(), bucket.getEndDate(), currentFilter.getPeriodType()),
                    bucket.getStartDate(),
                    bucket.getEndDate(),
                    amount
            ));
        }

        if (items.isEmpty()) {
            items.add(buildSummaryItem(
                    buildBucketLabel(startDate, endDate, currentFilter.getPeriodType()),
                    startDate,
                    endDate,
                    0d
            ));
        }
        trendSummaries.setValue(items);
    }

    private void reloadComparisonSources() {
        detachComparisonSources();

        StatisticsViewModel.FilterState currentFilter = filterState.getValue();
        String parentCategoryId = selectedCategoryId.getValue();
        if (currentFilter == null || parentCategoryId == null || parentCategoryId.trim().isEmpty()
                || currentFilter.getPeriodType() != StatisticsViewModel.PeriodType.MONTH) {
            resetComparisonData();
            return;
        }

        LocalDate currentMonth = toLocalDate(currentFilter.getStartDate()).withDayOfMonth(1);
        LocalDate visibleEnd = toLocalDate(boundedEndDate(currentFilter.getEndDate()));

        comparisonCurrentSource = buildMonthlyComparisonSource(currentFilter, parentCategoryId, currentMonth, visibleEnd);
        comparisonPrevOneSource = buildMonthlyComparisonSource(
                currentFilter,
                parentCategoryId,
                currentMonth.minusMonths(1),
                currentMonth.minusMonths(1).withDayOfMonth(currentMonth.minusMonths(1).lengthOfMonth())
        );
        comparisonPrevTwoSource = buildMonthlyComparisonSource(
                currentFilter,
                parentCategoryId,
                currentMonth.minusMonths(2),
                currentMonth.minusMonths(2).withDayOfMonth(currentMonth.minusMonths(2).lengthOfMonth())
        );
        comparisonPrevThreeSource = buildMonthlyComparisonSource(
                currentFilter,
                parentCategoryId,
                currentMonth.minusMonths(3),
                currentMonth.minusMonths(3).withDayOfMonth(currentMonth.minusMonths(3).lengthOfMonth())
        );

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
    private LiveData<List<DailyTrendDTO>> buildMonthlyComparisonSource(@NonNull StatisticsViewModel.FilterState currentFilter,
                                                                       @NonNull String parentCategoryId,
                                                                       @NonNull LocalDate startDate,
                                                                       @NonNull LocalDate endDate) {
        return transactionRepository.getParentCategoryBranchAmountTrend(
                userId,
                selectedTransactionType.name(),
                parentCategoryId,
                toStartMillis(startDate),
                toEndMillis(endDate),
                currentFilter.getWalletId(),
                DAILY_PERIOD_FORMAT
        );
    }

    private void rebuildComparisonPoints() {
        StatisticsViewModel.FilterState currentFilter = filterState.getValue();
        if (currentFilter == null || currentFilter.getPeriodType() != StatisticsViewModel.PeriodType.MONTH) {
            comparisonPoints.setValue(new ArrayList<>());
            return;
        }

        LocalDate currentMonth = toLocalDate(currentFilter.getStartDate()).withDayOfMonth(1);
        LocalDate visibleEnd = toLocalDate(boundedEndDate(currentFilter.getEndDate()));
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

        List<IncomeExpenseDetailViewModel.ComparisonPointUiModel> items = new ArrayList<>();
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
                averageTotal += sumRange(monthMap, month.withDayOfMonth(1), targetDate);
                divisor++;
            }

            double averageValue = divisor > 0 ? averageTotal / divisor : lastAverage;
            lastAverage = averageValue;
            items.add(new IncomeExpenseDetailViewModel.ComparisonPointUiModel(
                    String.format(Locale.getDefault(), "%02d/%02d",
                            currentDate.getDayOfMonth(),
                            currentDate.getMonthValue()),
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
    private List<CategoryOptionUiModel> mapCategoryOptions(@Nullable List<CategoryEntity> categories) {
        List<CategoryOptionUiModel> items = new ArrayList<>();
        if (categories == null) {
            return items;
        }
        for (CategoryEntity category : categories) {
            items.add(new CategoryOptionUiModel(
                    category.getId(),
                    category.getName(),
                    category.getIconName()
            ));
        }
        return items;
    }

    @NonNull
    private List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel> mapChildCategoryItems(
            @Nullable List<CategorySumDTO> items
    ) {
        List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel> mapped = new ArrayList<>();
        if (items == null || items.isEmpty()) {
            return mapped;
        }
        double total = 0d;
        for (CategorySumDTO item : items) {
            total += item.getTotalAmount();
        }
        for (CategorySumDTO item : items) {
            double share = total > 0d ? (item.getTotalAmount() * 100d / total) : 0d;
            mapped.add(new IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel(
                    item.getCategoryId(),
                    item.getCategoryName(),
                    item.getIconName(),
                    item.isCategoryDeleted(),
                    item.getTotalAmount(),
                    share,
                    item.getTransactionCount()
            ));
        }
        return mapped;
    }

    @NonNull
    private IncomeExpenseDetailViewModel.PeriodSummaryUiModel buildSummaryItem(@NonNull String label,
                                                                               @NonNull LocalDate startDate,
                                                                               @NonNull LocalDate endDate,
                                                                               double amount) {
        return selectedTransactionType == TransactionType.INCOME
                ? new IncomeExpenseDetailViewModel.PeriodSummaryUiModel(
                label,
                toStartMillis(startDate),
                toEndMillis(endDate),
                amount,
                0d
        )
                : new IncomeExpenseDetailViewModel.PeriodSummaryUiModel(
                label,
                toStartMillis(startDate),
                toEndMillis(endDate),
                0d,
                amount
        );
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
    private TransactionType parseTransactionType(@Nullable String value) {
        if ("INCOME".equalsIgnoreCase(value)) {
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
        return TimeWindowUtils.endOfTodayUtc();
    }

    @NonNull
    private LocalDate toLocalDate(long epochMillis) {
        if (epochMillis <= 0L || epochMillis == Long.MAX_VALUE) {
            return LocalDate.now();
        }
        return TimeWindowUtils.toDeviceLocalDate(epochMillis);
    }

    private long toStartMillis(@NonNull LocalDate date) {
        return TimeWindowUtils.startOfDayLocalDateUtc(date);
    }

    private long toEndMillis(@NonNull LocalDate date) {
        return TimeWindowUtils.endOfDayLocalDateUtc(date);
    }

    @NonNull
    private Map<LocalDate, Double> toDailyAmountMap(@Nullable List<DailyTrendDTO> source) {
        Map<LocalDate, Double> result = new HashMap<>();
        if (source == null) {
            return result;
        }
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
            return String.format(Locale.getDefault(), "%02d/%d",
                    startDate.getMonthValue(),
                    startDate.getYear());
        }
        return String.format(Locale.getDefault(), "%02d/%02d - %02d/%02d",
                startDate.getDayOfMonth(),
                startDate.getMonthValue(),
                endDate.getDayOfMonth(),
                endDate.getMonthValue());
    }

    public static final class Factory implements ViewModelProvider.Factory {

        private final TransactionRepository transactionRepository;
        private final CategoryRepository categoryRepository;
        private final String userId;
        @Nullable
        private final String walletId;
        private final long startDate;
        private final long endDate;
        @Nullable
        private final String transactionType;
        @Nullable
        private final String categoryId;

        public Factory(@NonNull TransactionRepository transactionRepository,
                       @NonNull CategoryRepository categoryRepository,
                       @NonNull String userId,
                       @Nullable String walletId,
                       long startDate,
                       long endDate,
                       @Nullable String transactionType,
                       @Nullable String categoryId) {
            this.transactionRepository = transactionRepository;
            this.categoryRepository = categoryRepository;
            this.userId = userId;
            this.walletId = walletId;
            this.startDate = startDate;
            this.endDate = endDate;
            this.transactionType = transactionType;
            this.categoryId = categoryId;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(CategoryReportViewModel.class)) {
                return (T) new CategoryReportViewModel(
                        transactionRepository,
                        categoryRepository,
                        userId,
                        walletId,
                        startDate,
                        endDate,
                        transactionType,
                        categoryId
                );
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }

    private static final class ChildQuery {
        @NonNull
        private final StatisticsViewModel.FilterState filterState;
        @NonNull
        private final String parentCategoryId;

        private ChildQuery(@NonNull StatisticsViewModel.FilterState filterState,
                           @NonNull String parentCategoryId) {
            this.filterState = filterState;
            this.parentCategoryId = parentCategoryId;
        }
    }

    public static final class CategoryOptionUiModel {
        @NonNull
        private final String categoryId;
        @NonNull
        private final String categoryName;
        @Nullable
        private final String iconName;

        public CategoryOptionUiModel(@NonNull String categoryId,
                                     @NonNull String categoryName,
                                     @Nullable String iconName) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
            this.iconName = iconName;
        }

        @NonNull
        public String getCategoryId() {
            return categoryId;
        }

        @NonNull
        public String getCategoryName() {
            return categoryName;
        }

        @Nullable
        public String getIconName() {
            return iconName;
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
        public LocalDate getStartDate() {
            return startDate;
        }

        @NonNull
        public LocalDate getEndDate() {
            return endDate;
        }
    }
}
