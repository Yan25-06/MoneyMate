package com.group10.moneymate.ui.transaction;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.DistinctLiveData;
import com.group10.moneymate.utils.IconProvider;
import com.group10.moneymate.utils.TimeWindowUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TransactionSearchViewModel extends AndroidViewModel {

    public enum SearchMode {
        QUICK, ADVANCED, RESULTS
    }

    public static class SearchSummary {
        public int count;
        public double totalIncome;
        public double totalExpense;
        public double net;

        public SearchSummary(int count, double totalIncome, double totalExpense, double net) {
            this.count = count;
            this.totalIncome = totalIncome;
            this.totalExpense = totalExpense;
            this.net = net;
        }
    }

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final String userId;

    private final MutableLiveData<SearchMode> currentMode = new MutableLiveData<>(SearchMode.QUICK);
    private final MutableLiveData<TransactionSearchFilter> filter = new MutableLiveData<>(new TransactionSearchFilter());

    private final LiveData<List<TransactionEntity>> rawResults;
    private final MediatorLiveData<List<TransactionTimeGroupAdapter.GroupItem>> groupedResults = new MediatorLiveData<>();
    private final MutableLiveData<SearchSummary> summary = new MutableLiveData<>(new SearchSummary(0, 0, 0, 0));

    private final LiveData<List<CategoryEntity>> expenseCategories;
    private final LiveData<List<CategoryEntity>> incomeCategories;
    private final LiveData<List<CategoryEntity>> debtCategories;
    private final Map<String, CategoryEntity> categoryMap = new HashMap<>();
    private final MutableLiveData<String> resolvedCategoryLabel = new MutableLiveData<>(null);

    public TransactionSearchViewModel(@NonNull Application application) {
        super(application);

        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        userId = container.authRepository.getCurrentUserId();
        transactionRepository = container.transactionRepository;
        categoryRepository = container.categoryRepository;

        expenseCategories = DistinctLiveData.distinctUntilChanged(
                categoryRepository.getCategoriesByTypeIncludingDeleted(userId, Constants.TYPE_EXPENSE)
        );
        incomeCategories = DistinctLiveData.distinctUntilChanged(
                categoryRepository.getCategoriesByTypeIncludingDeleted(userId, Constants.TYPE_INCOME)
        );
        debtCategories = DistinctLiveData.distinctUntilChanged(
                categoryRepository.getCategoriesByTypeIncludingDeleted(userId, Constants.TYPE_DEBT)
        );

        rawResults = DistinctLiveData.distinctUntilChanged(
                Transformations.switchMap(filter, currentFilter -> {
                    Double amountValue = currentFilter.amountMode != TransactionSearchFilter.AmountMode.ALL ? currentFilter.amountValue : null;
                    Double amountMin = currentFilter.amountMode == TransactionSearchFilter.AmountMode.BETWEEN ? currentFilter.amountMin : null;
                    Double amountMax = currentFilter.amountMode == TransactionSearchFilter.AmountMode.BETWEEN ? currentFilter.amountMax : null;

                    Long timeValue = currentFilter.timeMode != TransactionSearchFilter.TimeMode.ALL ? currentFilter.timeValue : null;
                    Long timeStart = currentFilter.timeMode == TransactionSearchFilter.TimeMode.BETWEEN ? currentFilter.timeStart : null;
                    Long timeEnd = currentFilter.timeMode == TransactionSearchFilter.TimeMode.BETWEEN ? currentFilter.timeEnd : null;

                    return transactionRepository.searchTransactionsAdvanced(
                            userId,
                            currentFilter.keyword,
                            currentFilter.amountMode.name(),
                            amountValue,
                            amountMin,
                            amountMax,
                            currentFilter.timeMode.name(),
                            timeValue,
                            timeStart,
                            timeEnd,
                            currentFilter.walletId,
                            currentFilter.categoryId
                    );
                })
        );

        groupedResults.addSource(expenseCategories, categories -> {
            mergeCategories(categories);
            rebuildGroupedResults(rawResults.getValue());
            refreshResolvedCategoryLabel();
        });
        groupedResults.addSource(incomeCategories, categories -> {
            mergeCategories(categories);
            rebuildGroupedResults(rawResults.getValue());
            refreshResolvedCategoryLabel();
        });
        groupedResults.addSource(debtCategories, categories -> {
            mergeCategories(categories);
            rebuildGroupedResults(rawResults.getValue());
            refreshResolvedCategoryLabel();
        });
        groupedResults.addSource(rawResults, this::rebuildGroupedResults);
    }

    private void mergeCategories(List<CategoryEntity> categories) {
        if (categories != null) {
            for (CategoryEntity category : categories) {
                categoryMap.put(category.getId(), category);
            }
        }
    }

    public LiveData<SearchMode> getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(SearchMode mode) {
        currentMode.setValue(mode);
    }

    public LiveData<TransactionSearchFilter> getFilter() {
        return filter;
    }

    public void updateFilter(TransactionSearchFilter newFilter) {
        filter.setValue(newFilter);
        refreshResolvedCategoryLabel();
    }

    /**
     * Set a selected category by ID. Immediately resolves its display name from
     * the already-loaded categoryMap and updates both the filter and the label LiveData.
     */
    public void setCategoryId(@NonNull String categoryId) {
        TransactionSearchFilter f = filter.getValue();
        if (f == null) f = new TransactionSearchFilter();

        f.categoryId = categoryId;

        // Resolve name synchronously from map (already populated by DB observers)
        CategoryEntity cat = categoryMap.get(categoryId);
        if (cat != null) {
            f.categoryLabel = cat.getName();
            resolvedCategoryLabel.setValue(cat.getName());
        } else {
            // Map not yet populated — clear label for now; refreshResolvedCategoryLabel will retry
            f.categoryLabel = null;
            resolvedCategoryLabel.setValue(null);
        }

        filter.setValue(f);
    }

    public LiveData<List<TransactionTimeGroupAdapter.GroupItem>> getGroupedResults() {
        return groupedResults;
    }

    public LiveData<SearchSummary> getSummary() {
        return summary;
    }

    /** Emits the resolved display name of the currently selected categoryId, or null. */
    public LiveData<String> getResolvedCategoryLabel() {
        return resolvedCategoryLabel;
    }

    private void refreshResolvedCategoryLabel() {
        TransactionSearchFilter f = filter.getValue();
        if (f == null || f.categoryId == null) {
            resolvedCategoryLabel.setValue(null);
            return;
        }
        CategoryEntity cat = categoryMap.get(f.categoryId);
        if (cat != null) {
            f.categoryLabel = cat.getName();
            resolvedCategoryLabel.setValue(cat.getName());
        }
    }

    private void rebuildGroupedResults(List<TransactionEntity> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            groupedResults.setValue(new ArrayList<>());
            summary.setValue(new SearchSummary(0, 0, 0, 0));
            return;
        }

        double totalIncome = 0d;
        double totalExpense = 0d;
        Map<String, TransactionTimeBucket> grouped = new LinkedHashMap<>();

        for (TransactionEntity transaction : transactions) {
            if (Constants.TYPE_INCOME.equals(transaction.getType())) {
                totalIncome += transaction.getAmount();
            } else if (Constants.TYPE_EXPENSE.equals(transaction.getType())) {
                totalExpense += transaction.getAmount();
            }

            LocalDate date = TimeWindowUtils.toDeviceLocalDate(transaction.getTimestamp());
            String key = date.toString();
            TransactionTimeBucket bucket = grouped.get(key);
            if (bucket == null) {
                bucket = new TransactionTimeBucket(key, date);
                grouped.put(key, bucket);
            }
            bucket.add(transaction);
        }

        summary.setValue(new SearchSummary(transactions.size(), totalIncome, totalExpense, totalIncome - totalExpense));

        List<TransactionTimeBucket> buckets = new ArrayList<>(grouped.values());
        buckets.sort((left, right) -> Long.compare(right.getSortMillis(), left.getSortMillis()));

        List<TransactionTimeGroupAdapter.GroupItem> items = new ArrayList<>();
        for (TransactionTimeBucket bucket : buckets) {
            bucket.sortTransactions();
            items.add(bucket.toGroupItem());
        }

        groupedResults.setValue(items);
    }

    @NonNull
    private String formatNetAmount(double amount) {
        if (amount < 0d) {
            return "-" + CurrencyFormatter.format(Math.abs(amount), "VND");
        }
        return CurrencyFormatter.format(amount, "VND");
    }

    @NonNull
    private String capitalize(@NonNull String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(new Locale("vi", "VN")) + value.substring(1);
    }

    private class TransactionTimeBucket {
        @NonNull private final String key;
        @NonNull private final LocalDate anchorDate;
        @NonNull private final List<TransactionEntity> transactions = new ArrayList<>();
        private double totalIncome;
        private double totalExpense;

        private TransactionTimeBucket(@NonNull String key, @NonNull LocalDate anchorDate) {
            this.key = key;
            this.anchorDate = anchorDate;
        }

        private void add(@NonNull TransactionEntity transaction) {
            transactions.add(transaction);
            if (Constants.TYPE_INCOME.equals(transaction.getType())) {
                totalIncome += transaction.getAmount();
            } else if (Constants.TYPE_EXPENSE.equals(transaction.getType())) {
                totalExpense += transaction.getAmount();
            }
        }

        private long getSortMillis() {
            return TimeWindowUtils.startOfDayUtc(anchorDate);
        }

        private void sortTransactions() {
            transactions.sort((left, right) -> Long.compare(right.getTimestamp(), left.getTimestamp()));
        }

        @NonNull
        private TransactionTimeGroupAdapter.RowItem toRowItem(@NonNull TransactionEntity transaction) {
            CategoryEntity category = transaction.getCategoryId() != null
                    ? categoryMap.get(transaction.getCategoryId())
                    : null;
            String type = transaction.getType();
            return new TransactionTimeGroupAdapter.RowItem(
                    transaction,
                    IconProvider.resolveCategoryIconByType(
                            getApplication(),
                            category != null ? category.getIconName() : null,
                            type
                    ),
                    category != null ? category.getName() : getApplication().getString(R.string.ledger_section_unknown),
                    resolveSubtitle(transaction),
                    resolveAmountLabel(transaction.getAmount(), type),
                    getApplication().getColor(resolveAmountColor(type))
            );
        }

        @NonNull
        private TransactionTimeGroupAdapter.GroupItem toGroupItem() {
            List<TransactionTimeGroupAdapter.RowItem> rowItems = new ArrayList<>();
            for (TransactionEntity transaction : transactions) {
                rowItems.add(toRowItem(transaction));
            }

            LocalDate today = LocalDate.now(java.time.ZoneId.systemDefault());
            String title;
            if (anchorDate.equals(today)) {
                title = getApplication().getString(R.string.statistics_today);
            } else if (anchorDate.equals(today.minusDays(1))) {
                title = "Hôm qua";
            } else {
                title = capitalize(anchorDate.getDayOfWeek().getDisplayName(java.time.format.TextStyle.FULL, new Locale("vi", "VN")));
            }
            String subtitle = String.format(Locale.getDefault(), "tháng %d %d",
                    anchorDate.getMonthValue(),
                    anchorDate.getYear());
            return new TransactionTimeGroupAdapter.GroupItem(
                    key,
                    String.valueOf(anchorDate.getDayOfMonth()),
                    title,
                    subtitle,
                    formatNetAmount(totalIncome - totalExpense),
                    rowItems
            );
        }

        @NonNull
        private String resolveSubtitle(@NonNull TransactionEntity transaction) {
            return transaction.getNote() != null && !transaction.getNote().trim().isEmpty()
                    ? transaction.getNote()
                    : getApplication().getString(R.string.transaction_detail_no_note);
        }

        @NonNull
        private String resolveAmountLabel(double amount, @Nullable String type) {
            if (Constants.TYPE_INCOME.equals(type)) {
                return "+" + CurrencyFormatter.format(amount, "VND");
            }
            if (Constants.TYPE_EXPENSE.equals(type)) {
                return "-" + CurrencyFormatter.format(amount, "VND");
            }
            return CurrencyFormatter.format(amount, "VND");
        }

        private int resolveAmountColor(@Nullable String type) {
            if (Constants.TYPE_INCOME.equals(type)) {
                return R.color.transfer_blue;
            }
            if (Constants.TYPE_EXPENSE.equals(type)) {
                return R.color.expense_red;
            }
            return R.color.statistics_text_primary;
        }
    }
}
