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
import com.group10.moneymate.data.local.dto.NetIncomeDTO;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.data.repository.WalletRepository;
import com.group10.moneymate.models.TransactionType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StatisticsViewModel extends ViewModel {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final String userId;
    private final MutableLiveData<FilterState> filterState = new MutableLiveData<>();
    private final MutableLiveData<String> walletLabel =
            new MutableLiveData<>("Tổng cộng");
    private final MutableLiveData<TransactionType> selectedReportType =
            new MutableLiveData<>(TransactionType.INCOME);

    private final LiveData<Double> headerBalance;
    private final LiveData<WalletEntity> selectedWallet;
    private final LiveData<NetIncomeDTO> netIncomeSummary;
    private final LiveData<List<CategorySliceUiModel>> incomeCategorySums;
    private final LiveData<List<CategorySliceUiModel>> expenseCategorySums;
    private final LiveData<Double> totalIncomeAmount;
    private final LiveData<Double> totalExpenseAmount;

    public StatisticsViewModel(@NonNull TransactionRepository transactionRepository,
                               @NonNull WalletRepository walletRepository,
                               @NonNull String userId) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.userId = userId;
        filterState.setValue(FilterState.createCurrentMonth(null));

        headerBalance = Transformations.switchMap(filterState, state -> {
            if (state == null) {
                return new MutableLiveData<>(0d);
            }
            if (state.getWalletId() == null) {
                return normalizeDouble(walletRepository.getTotalBalance(userId));
            }
            return mapWalletBalance(walletRepository.getById(state.getWalletId()));
        });

        selectedWallet = Transformations.switchMap(filterState, state -> {
            if (state == null || state.getWalletId() == null || state.getWalletId().trim().isEmpty()) {
                return new MutableLiveData<>(null);
            }
            return walletRepository.getById(state.getWalletId());
        });

        netIncomeSummary = Transformations.switchMap(filterState, state -> {
            if (state == null) {
                return new MutableLiveData<>(null);
            }
            return transactionRepository.getNetIncomeSummary(
                    userId,
                    state.getStartDate(),
                    state.getEndDate(),
                    state.getWalletId(),
                    state.getDisplayLabel()
            );
        });

        totalIncomeAmount = Transformations.switchMap(filterState, state -> {
            if (state == null) {
                return new MutableLiveData<>(0d);
            }
            return normalizeDouble(transactionRepository.getTotalIncomeFiltered(
                    userId,
                    state.getStartDate(),
                    state.getEndDate(),
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
                    state.getEndDate(),
                    state.getWalletId()
            ));
        });

        incomeCategorySums = Transformations.switchMap(filterState, state -> {
            if (state == null) {
                return new MutableLiveData<>(new ArrayList<>());
            }
            return mapCategorySliceLiveData(transactionRepository.getCategorySums(
                    userId,
                    TransactionType.INCOME.name(),
                    state.getStartDate(),
                    state.getEndDate(),
                    state.getWalletId()
            ));
        });

        expenseCategorySums = Transformations.switchMap(filterState, state -> {
            if (state == null) {
                return new MutableLiveData<>(new ArrayList<>());
            }
            return mapCategorySliceLiveData(transactionRepository.getCategorySums(
                    userId,
                    TransactionType.EXPENSE.name(),
                    state.getStartDate(),
                    state.getEndDate(),
                    state.getWalletId()
            ));
        });
    }

    public LiveData<Double> getHeaderBalance() {
        return headerBalance;
    }

    public LiveData<NetIncomeDTO> getNetIncomeSummary() {
        return netIncomeSummary;
    }

    public LiveData<WalletEntity> getSelectedWallet() {
        return selectedWallet;
    }

    public LiveData<FilterState> getFilterStateLiveData() {
        return filterState;
    }

    public LiveData<String> getWalletLabel() {
        return walletLabel;
    }

    public LiveData<TransactionType> getSelectedReportTypeLiveData() {
        return selectedReportType;
    }

    public LiveData<List<CategorySliceUiModel>> getIncomeCategorySums() {
        return incomeCategorySums;
    }

    public LiveData<List<CategorySliceUiModel>> getExpenseCategorySums() {
        return expenseCategorySums;
    }

    public LiveData<Double> getTotalIncomeAmount() {
        return totalIncomeAmount;
    }

    public LiveData<Double> getTotalExpenseAmount() {
        return totalExpenseAmount;
    }

    public void setSelectedReportType(@NonNull TransactionType transactionType) {
        TransactionType current = selectedReportType.getValue();
        if (current == transactionType) {
            return;
        }
        selectedReportType.setValue(transactionType);
    }

    @NonNull
    public TransactionType getSelectedReportType() {
        TransactionType current = selectedReportType.getValue();
        return current != null ? current : TransactionType.INCOME;
    }

    @NonNull
    public FilterState getCurrentFilterState() {
        FilterState current = filterState.getValue();
        return current != null ? current : FilterState.createCurrentMonth(null);
    }

    public void shiftCurrentPeriod(int direction) {
        FilterState current = getCurrentFilterState();
        filterState.setValue(current.shift(direction));
    }

    public void resetToCurrentMonth() {
        FilterState current = getCurrentFilterState();
        filterState.setValue(FilterState.createForPeriodType(current.getPeriodType(), current.getWalletId()));
    }

    public void updateWalletFilter(@Nullable String walletId, @Nullable String label) {
        FilterState current = getCurrentFilterState();
        filterState.setValue(current.withWalletId(walletId));
        if (label == null || label.trim().isEmpty()) {
            walletLabel.setValue("Tổng cộng");
            return;
        }
        walletLabel.setValue(label);
    }

    public void updateCustomDateRange(long startDate, long endDate) {
        if (endDate < startDate) {
            return;
        }
        FilterState current = getCurrentFilterState();
        filterState.setValue(FilterState.createRange(
                current.getWalletId(),
                startDate,
                endDate
        ));
    }

    public void updatePresetPeriod(@NonNull PeriodType periodType) {
        FilterState current = getCurrentFilterState();
        filterState.setValue(FilterState.createForPeriodType(periodType, current.getWalletId()));
    }

    public void applyExternalFilter(@Nullable String walletId,
                                    @Nullable String walletLabelValue,
                                    long startDate,
                                    long endDate,
                                    @Nullable String periodTypeName) {
        FilterState nextState;
        PeriodType parsedPeriodType = parsePeriodType(periodTypeName);
        if (parsedPeriodType == PeriodType.ALL) {
            nextState = FilterState.createAll(walletId);
        } else if (startDate > 0L && endDate > 0L && endDate >= startDate) {
            if (parsedPeriodType != null && parsedPeriodType != PeriodType.CUSTOM) {
                nextState = FilterState.fromExplicitRange(walletId, startDate, endDate, parsedPeriodType);
            } else {
                nextState = FilterState.createRange(walletId, startDate, endDate);
            }
        } else if (parsedPeriodType != null) {
            nextState = FilterState.createForPeriodType(parsedPeriodType, walletId);
        } else {
            nextState = FilterState.createCurrentMonth(walletId);
        }
        filterState.setValue(nextState);
        walletLabel.setValue(walletLabelValue == null || walletLabelValue.trim().isEmpty()
                ? "Tổng cộng"
                : walletLabelValue);
    }

    @NonNull
    private LiveData<Double> normalizeDouble(@NonNull LiveData<Double> source) {
        MediatorLiveData<Double> result = new MediatorLiveData<>();
        result.addSource(source, value -> result.setValue(value != null ? value : 0d));
        return result;
    }

    @NonNull
    private LiveData<Double> mapWalletBalance(@NonNull LiveData<WalletEntity> source) {
        MediatorLiveData<Double> result = new MediatorLiveData<>();
        result.addSource(source, wallet -> result.setValue(wallet != null ? wallet.getBalance() : 0d));
        return result;
    }

    @NonNull
    private LiveData<List<CategorySliceUiModel>> mapCategorySliceLiveData(@NonNull LiveData<List<CategorySumDTO>> source) {
        MediatorLiveData<List<CategorySliceUiModel>> result = new MediatorLiveData<>();
        result.addSource(source, value -> result.setValue(mapCategorySlices(value)));
        return result;
    }

    @Nullable
    private PeriodType parsePeriodType(@Nullable String periodTypeName) {
        if (periodTypeName == null || periodTypeName.trim().isEmpty()) {
            return null;
        }
        try {
            return PeriodType.valueOf(periodTypeName);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @NonNull
    private List<CategorySliceUiModel> mapCategorySlices(@Nullable List<CategorySumDTO> source) {
        List<CategorySliceUiModel> items = new ArrayList<>();
        if (source == null) {
            return items;
        }
        for (CategorySumDTO dto : source) {
            items.add(new CategorySliceUiModel(
                    dto.getCategoryId(),
                    dto.getCategoryName(),
                    dto.getIconName(),
                    dto.getTotalAmount(),
                    dto.getTransactionCount()
            ));
        }
        return items;
    }

    public static class Factory implements ViewModelProvider.Factory {

        private final TransactionRepository transactionRepository;
        private final WalletRepository walletRepository;
        private final String userId;

        public Factory(@NonNull TransactionRepository transactionRepository,
                       @NonNull WalletRepository walletRepository,
                       @NonNull String userId) {
            this.transactionRepository = transactionRepository;
            this.walletRepository = walletRepository;
            this.userId = userId;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (modelClass.isAssignableFrom(StatisticsViewModel.class)) {
                return (T) new StatisticsViewModel(transactionRepository, walletRepository, userId);
            }
            throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
        }
    }

    public static class CategorySliceUiModel {
        private final String categoryId;
        private final String categoryName;
        private final String iconName;
        private final double totalAmount;
        private final int transactionCount;

        public CategorySliceUiModel(@Nullable String categoryId,
                                    @Nullable String categoryName,
                                    @Nullable String iconName,
                                    double totalAmount,
                                    int transactionCount) {
            this.categoryId = categoryId;
            this.categoryName = categoryName != null ? categoryName : "Chưa phân loại";
            this.iconName = iconName;
            this.totalAmount = totalAmount;
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

        @Nullable
        public String getIconName() {
            return iconName;
        }

        public double getTotalAmount() {
            return totalAmount;
        }

        public int getTransactionCount() {
            return transactionCount;
        }
    }

    public enum PeriodType {
        DAY,
        WEEK,
        MONTH,
        QUARTER,
        YEAR,
        ALL,
        CUSTOM
    }

    public static class FilterState {
        @Nullable
        private final String walletId;
        private final long startDate;
        private final long endDate;
        @NonNull
        private final String displayLabel;
        @NonNull
        private final PeriodType periodType;

        public FilterState(@Nullable String walletId,
                           long startDate,
                           long endDate,
                           @NonNull String displayLabel,
                           @NonNull PeriodType periodType) {
            this.walletId = walletId;
            this.startDate = startDate;
            this.endDate = endDate;
            this.displayLabel = displayLabel;
            this.periodType = periodType;
        }

        @NonNull
        public static FilterState createCurrentMonth(@Nullable String walletId) {
            return createMonth(walletId, LocalDate.now());
        }

        @Nullable
        public String getWalletId() {
            return walletId;
        }

        public long getStartDate() {
            return startDate;
        }

        public long getEndDate() {
            return endDate;
        }

        @NonNull
        public String getDisplayLabel() {
            return displayLabel;
        }

        @NonNull
        public PeriodType getPeriodType() {
            return periodType;
        }

        @NonNull
        public String getStartDatePreview() {
            LocalDate date = Instant.ofEpochMilli(startDate)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            int month = date.getMonthValue();
            int year = date.getYear();
            return String.format("%02d/%d", month, year);
        }

        @NonNull
        public static FilterState createRange(@Nullable String walletId,
                                              long startDate,
                                              long endDate) {
            LocalDate start = toLocalDate(startDate);
            LocalDate end = toLocalDate(endDate);
            if (start.getDayOfMonth() == 1 && end.getDayOfMonth() == end.lengthOfMonth()
                    && start.getMonth() == end.getMonth()
                    && start.getYear() == end.getYear()) {
                return createMonth(walletId, start);
            }
            String label = String.format(
                    Locale.getDefault(),
                    "%02d/%02d - %02d/%02d",
                    start.getDayOfMonth(),
                    start.getMonthValue(),
                    end.getDayOfMonth(),
                    end.getMonthValue()
            );
            return new FilterState(walletId, startDate, endDate, label, PeriodType.CUSTOM);
        }

        @NonNull
        public static FilterState fromExplicitRange(@Nullable String walletId,
                                                    long startDate,
                                                    long endDate,
                                                    @NonNull PeriodType periodType) {
            LocalDate start = toLocalDate(startDate);
            LocalDate end = toLocalDate(endDate);
            String label;
            switch (periodType) {
                case DAY:
                    label = formatDayLabel(start);
                    break;
                case WEEK:
                    label = formatWeekLabel(start, end);
                    break;
                case MONTH:
                    label = formatMonthLabel(start);
                    break;
                case QUARTER:
                    label = formatQuarterLabel(start);
                    break;
                case YEAR:
                    label = formatYearLabel(start);
                    break;
                case ALL:
                    return createAll(walletId);
                case CUSTOM:
                default:
                    return createRange(walletId, startDate, endDate);
            }
            return new FilterState(walletId, startDate, endDate, label, periodType);
        }

        @NonNull
        public static FilterState createForPeriodType(@NonNull PeriodType periodType,
                                                      @Nullable String walletId) {
            LocalDate today = LocalDate.now();
            switch (periodType) {
                case DAY:
                    return createDay(walletId, today);
                case WEEK:
                    return createWeek(walletId, today);
                case QUARTER:
                    return createQuarter(walletId, today);
                case YEAR:
                    return createYear(walletId, today);
                case ALL:
                    return createAll(walletId);
                case CUSTOM:
                    return createRange(walletId, toStartMillis(today), toEndMillis(today));
                case MONTH:
                default:
                    return createMonth(walletId, today);
            }
        }

        @NonNull
        public static FilterState createDay(@Nullable String walletId, @NonNull LocalDate day) {
            return new FilterState(
                    walletId,
                    toStartMillis(day),
                    toEndMillis(day),
                    formatDayLabel(day),
                    PeriodType.DAY
            );
        }

        @NonNull
        public static FilterState createWeek(@Nullable String walletId, @NonNull LocalDate date) {
            LocalDate start = date.minusDays(date.getDayOfWeek().getValue() - 1L);
            LocalDate end = start.plusDays(6L);
            return new FilterState(
                    walletId,
                    toStartMillis(start),
                    toEndMillis(end),
                    formatWeekLabel(start, end),
                    PeriodType.WEEK
            );
        }

        @NonNull
        public static FilterState createMonth(@Nullable String walletId, @NonNull LocalDate monthDate) {
            LocalDate start = monthDate.withDayOfMonth(1);
            LocalDate end = monthDate.withDayOfMonth(monthDate.lengthOfMonth());
            ZoneId zoneId = ZoneId.systemDefault();
            long startMillis = start.atStartOfDay(zoneId).toInstant().toEpochMilli();
            long endMillis = end.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli() - 1L;
            String label = formatMonthLabel(start);
            return new FilterState(walletId, startMillis, endMillis, label, PeriodType.MONTH);
        }

        @NonNull
        public static FilterState createQuarter(@Nullable String walletId, @NonNull LocalDate date) {
            int startMonth = (((date.getMonthValue() - 1) / 3) * 3) + 1;
            LocalDate start = LocalDate.of(date.getYear(), startMonth, 1);
            LocalDate end = start.plusMonths(2).withDayOfMonth(start.plusMonths(2).lengthOfMonth());
            String label = formatQuarterLabel(start);
            return new FilterState(walletId, toStartMillis(start), toEndMillis(end), label, PeriodType.QUARTER);
        }

        @NonNull
        public static FilterState createYear(@Nullable String walletId, @NonNull LocalDate date) {
            LocalDate start = LocalDate.of(date.getYear(), 1, 1);
            LocalDate end = LocalDate.of(date.getYear(), 12, 31);
            return new FilterState(walletId, toStartMillis(start), toEndMillis(end), formatYearLabel(start), PeriodType.YEAR);
        }

        @NonNull
        public static FilterState createAll(@Nullable String walletId) {
            return new FilterState(walletId, 0L, Long.MAX_VALUE, "TẤT CẢ", PeriodType.ALL);
        }

        @NonNull
        public FilterState shift(int direction) {
            LocalDate start = toLocalDate(startDate);
            switch (periodType) {
                case DAY:
                    return createDay(walletId, start.plusDays(direction));
                case WEEK:
                    return createWeek(walletId, start.plusWeeks(direction));
                case MONTH:
                    return createMonth(walletId, start.plusMonths(direction));
                case QUARTER:
                    return createQuarter(walletId, start.plusMonths(direction * 3L));
                case YEAR:
                    return createYear(walletId, start.plusYears(direction));
                case ALL:
                    return this;
                case CUSTOM:
                default:
                    LocalDate end = toLocalDate(endDate);
                    long daySpan = ChronoUnit.DAYS.between(start, end) + 1L;
                    LocalDate shiftedStart = start.plusDays(daySpan * direction);
                    LocalDate shiftedEnd = end.plusDays(daySpan * direction);
                    return createRange(walletId, toStartMillis(shiftedStart), toEndMillis(shiftedEnd));
            }
        }

        @NonNull
        public FilterState withWalletId(@Nullable String updatedWalletId) {
            return new FilterState(updatedWalletId, startDate, endDate, displayLabel, periodType);
        }

        @NonNull
        public String getPreviousHeaderLabel() {
            return shift(-1).getDisplayLabel();
        }

        @NonNull
        public String getNextHeaderLabel() {
            return shift(1).getDisplayLabel();
        }

        @NonNull
        private static LocalDate toLocalDate(long epochMillis) {
            if (epochMillis <= 0L || epochMillis == Long.MAX_VALUE) {
                return LocalDate.now();
            }
            return Instant.ofEpochMilli(epochMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
        }

        private static long toStartMillis(@NonNull LocalDate date) {
            return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        }

        private static long toEndMillis(@NonNull LocalDate date) {
            return date.plusDays(1)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli() - 1L;
        }

        @NonNull
        private static String formatDayLabel(@NonNull LocalDate date) {
            if (date.equals(LocalDate.now())) {
                return "HÔM NAY";
            }
            return String.format(Locale.getDefault(), "%02d/%02d/%d",
                    date.getDayOfMonth(),
                    date.getMonthValue(),
                    date.getYear());
        }

        @NonNull
        private static String formatWeekLabel(@NonNull LocalDate start, @NonNull LocalDate end) {
            LocalDate today = LocalDate.now();
            LocalDate currentWeekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L);
            if (start.equals(currentWeekStart)) {
                return "TUẦN NÀY";
            }
            return String.format(Locale.getDefault(), "%02d/%02d - %02d/%02d",
                    start.getDayOfMonth(),
                    start.getMonthValue(),
                    end.getDayOfMonth(),
                    end.getMonthValue());
        }

        @NonNull
        private static String formatMonthLabel(@NonNull LocalDate start) {
            LocalDate today = LocalDate.now();
            if (start.getMonthValue() == today.getMonthValue() && start.getYear() == today.getYear()) {
                return "THÁNG NÀY";
            }
            return String.format(Locale.getDefault(), "%02d/%d", start.getMonthValue(), start.getYear());
        }

        @NonNull
        private static String formatQuarterLabel(@NonNull LocalDate start) {
            LocalDate today = LocalDate.now();
            int quarter = ((start.getMonthValue() - 1) / 3) + 1;
            int currentQuarter = ((today.getMonthValue() - 1) / 3) + 1;
            if (quarter == currentQuarter && start.getYear() == today.getYear()) {
                return "QUÝ NÀY";
            }
            return "Q" + quarter + "/" + start.getYear();
        }

        @NonNull
        private static String formatYearLabel(@NonNull LocalDate start) {
            if (start.getYear() == LocalDate.now().getYear()) {
                return "NĂM NÀY";
            }
            return String.valueOf(start.getYear());
        }
    }
}
