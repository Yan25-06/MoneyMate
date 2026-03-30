package com.group10.moneymate.ui.budget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.BudgetRepository;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.data.repository.WalletRepository;
import com.group10.moneymate.utils.Constants;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BudgetViewModel extends ViewModel {

    public enum BudgetTab {
        THIS_MONTH,
        FUTURE,
        CUSTOM
    }

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final String userId;
    private final Labels labels;

    private final LiveData<List<BudgetEntity>> budgetSource;
    private final LiveData<List<CategoryEntity>> expenseCategories;
    private final LiveData<List<WalletEntity>> wallets;
    private final MutableLiveData<String> selectedWalletFilterId = new MutableLiveData<>(null);
    private final MutableLiveData<BudgetTab> selectedTab = new MutableLiveData<>(BudgetTab.THIS_MONTH);
    private final MutableLiveData<Boolean> hasAnyBudgets = new MutableLiveData<>(false);
    private final MutableLiveData<List<BudgetUIModel>> activeBudgets = new MutableLiveData<>();
    private final MutableLiveData<List<BudgetUIModel>> finishedBudgets = new MutableLiveData<>();
    private final MutableLiveData<BudgetSummaryUIModel> summary = new MutableLiveData<>();

    private final Map<String, LiveData<CategoryEntity>> categorySources = new HashMap<>();
    private final Map<String, LiveData<WalletEntity>> walletSources = new HashMap<>();
    private final Map<String, LiveData<Double>> spentSources = new HashMap<>();
    private final Map<String, Observer<CategoryEntity>> categoryObservers = new HashMap<>();
    private final Map<String, Observer<WalletEntity>> walletObservers = new HashMap<>();
    private final Map<String, Observer<Double>> spentObservers = new HashMap<>();
    private final Map<String, CategoryEntity> categoryValues = new HashMap<>();
    private final Map<String, WalletEntity> walletValues = new HashMap<>();
    private final Map<String, Double> spentValues = new HashMap<>();
    private List<BudgetEntity> currentBudgets = new ArrayList<>();
    private final Observer<List<BudgetEntity>> budgetObserver = this::onBudgetsChanged;
    private final Observer<String> selectedWalletObserver = ignored -> rebuildUiModels();
    private final Observer<BudgetTab> selectedTabObserver = ignored -> rebuildUiModels();

    public BudgetViewModel(@NonNull BudgetRepository budgetRepository,
                           @NonNull CategoryRepository categoryRepository,
                           @NonNull TransactionRepository transactionRepository,
                           @NonNull WalletRepository walletRepository,
                           @NonNull String userId,
                           @NonNull Labels labels) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.userId = userId;
        this.labels = labels;
        this.budgetSource = budgetRepository.getAllBudgets(userId);
        this.expenseCategories = categoryRepository.getCategoriesByType(userId, "EXPENSE");
        this.wallets = walletRepository.getAllByUser(userId);

        budgetSource.observeForever(budgetObserver);
        selectedWalletFilterId.observeForever(selectedWalletObserver);
        selectedTab.observeForever(selectedTabObserver);
    }

    public LiveData<List<BudgetUIModel>> getActiveBudgets() {
        return activeBudgets;
    }

    public LiveData<List<BudgetUIModel>> getFinishedBudgets() {
        return finishedBudgets;
    }

    public LiveData<BudgetTab> getSelectedTabLiveData() {
        return selectedTab;
    }

    public LiveData<BudgetSummaryUIModel> getSummary() {
        return summary;
    }

    public LiveData<List<CategoryEntity>> getExpenseCategories() {
        return expenseCategories;
    }

    public LiveData<List<WalletEntity>> getWallets() {
        return wallets;
    }

    public LiveData<Boolean> getHasAnyBudgets() {
        return hasAnyBudgets;
    }

    public void setSelectedWalletFilter(@Nullable String walletId) {
        String current = selectedWalletFilterId.getValue();
        if (current == null ? walletId == null : current.equals(walletId)) {
            return;
        }
        selectedWalletFilterId.setValue(walletId);
    }

    @Nullable
    public String getSelectedWalletFilterId() {
        return selectedWalletFilterId.getValue();
    }

    public void setSelectedTab(@NonNull BudgetTab budgetTab) {
        BudgetTab current = selectedTab.getValue();
        if (current == budgetTab) {
            return;
        }
        selectedTab.setValue(budgetTab);
    }

    @NonNull
    public BudgetTab getSelectedTab() {
        BudgetTab current = selectedTab.getValue();
        return current != null ? current : BudgetTab.THIS_MONTH;
    }

    public LiveData<BudgetEntity> getBudgetById(@NonNull String budgetId) {
        return budgetRepository.getBudgetById(userId, budgetId);
    }

    public LiveData<List<TransactionEntity>> getBudgetTransactions(@NonNull BudgetEntity budgetEntity) {
        return transactionRepository.getTransactionsForBudget(
                userId,
                budgetEntity.getCategoryId(),
                budgetEntity.getWalletId(),
                budgetEntity.getStartDate(),
                budgetEntity.getEndDate()
        );
    }

    public LiveData<BudgetUIModel> getBudgetUiModel(@NonNull String budgetId) {
        MediatorLiveData<BudgetUIModel> result = new MediatorLiveData<>();
        LiveData<BudgetEntity> budgetLiveData = budgetRepository.getBudgetById(userId, budgetId);
        final LiveData<CategoryEntity>[] categorySource = new LiveData[]{null};
        final LiveData<WalletEntity>[] walletSource = new LiveData[]{null};
        final LiveData<Double>[] spentSource = new LiveData[]{null};
        final BudgetEntity[] budgetHolder = new BudgetEntity[]{null};
        final CategoryEntity[] categoryHolder = new CategoryEntity[]{null};
        final WalletEntity[] walletHolder = new WalletEntity[]{null};
        final double[] spentHolder = new double[]{0d};

        Runnable publish = () -> {
            if (budgetHolder[0] == null) {
                result.setValue(null);
                return;
            }
            result.setValue(new BudgetUIModel(
                    budgetHolder[0],
                    resolveCategoryName(budgetHolder[0], categoryHolder[0]),
                    resolveCategoryIcon(budgetHolder[0], categoryHolder[0]),
                    spentHolder[0],
                    resolveWalletName(budgetHolder[0], walletHolder[0]),
                    BudgetUiUtils.isActiveToday(budgetHolder[0])
            ));
        };

        result.addSource(budgetLiveData, budget -> {
            budgetHolder[0] = budget;
            categoryHolder[0] = null;
            walletHolder[0] = null;
            spentHolder[0] = 0d;

            if (categorySource[0] != null) {
                result.removeSource(categorySource[0]);
                categorySource[0] = null;
            }
            if (walletSource[0] != null) {
                result.removeSource(walletSource[0]);
                walletSource[0] = null;
            }
            if (spentSource[0] != null) {
                result.removeSource(spentSource[0]);
                spentSource[0] = null;
            }

            if (budget == null) {
                publish.run();
                return;
            }

            if (budget.getCategoryId() != null) {
                categorySource[0] = categoryRepository.getCategoryById(budget.getCategoryId());
                result.addSource(categorySource[0], category -> {
                    categoryHolder[0] = category;
                    publish.run();
                });
            }

            if (budget.getWalletId() != null) {
                walletSource[0] = walletRepository.getById(budget.getWalletId());
                result.addSource(walletSource[0], wallet -> {
                    walletHolder[0] = wallet;
                    publish.run();
                });
            }

            spentSource[0] = transactionRepository.getTotalExpenseByCategory(
                    userId,
                    budget.getCategoryId(),
                    budget.getWalletId(),
                    budget.getStartDate(),
                    budget.getEndDate()
            );
            result.addSource(spentSource[0], spentAmount -> {
                spentHolder[0] = spentAmount != null ? spentAmount : 0d;
                publish.run();
            });

            publish.run();
        });

        return result;
    }

    public void addBudget(@Nullable String categoryId,
                          @Nullable String walletId,
                          double amount,
                          long startDate,
                          long endDate) {
        addBudget(categoryId, walletId, amount, startDate, endDate, null);
    }

    public void addBudget(@Nullable String categoryId,
                          @Nullable String walletId,
                          double amount,
                          long startDate,
                          long endDate,
                          @Nullable BudgetRepository.WriteCallback callback) {
        BudgetEntity budgetEntity = new BudgetEntity();
        budgetEntity.setId(UUID.randomUUID().toString());
        budgetEntity.setUserId(userId);
        budgetEntity.setCategoryId(categoryId);
        budgetEntity.setWalletId(walletId);
        budgetEntity.setAmount(amount);
        budgetEntity.setStartDate(startDate);
        budgetEntity.setEndDate(endDate);
        budgetRepository.addBudget(budgetEntity, callback);
    }

    public void updateBudget(@NonNull BudgetEntity budgetEntity,
                             @Nullable String categoryId,
                             @Nullable String walletId,
                             double amount,
                             long startDate,
                             long endDate) {
        updateBudget(budgetEntity, categoryId, walletId, amount, startDate, endDate, null);
    }

    public void updateBudget(@NonNull BudgetEntity budgetEntity,
                             @Nullable String categoryId,
                             @Nullable String walletId,
                             double amount,
                             long startDate,
                             long endDate,
                             @Nullable BudgetRepository.WriteCallback callback) {
        budgetEntity.setCategoryId(categoryId);
        budgetEntity.setUserId(userId);
        budgetEntity.setWalletId(walletId);
        budgetEntity.setAmount(amount);
        budgetEntity.setStartDate(startDate);
        budgetEntity.setEndDate(endDate);
        budgetRepository.updateBudget(budgetEntity, callback);
    }

    public void deleteBudget(@NonNull BudgetEntity budgetEntity) {
        budgetRepository.softDeleteBudget(userId, budgetEntity.getId());
    }

    private void onBudgetsChanged(@Nullable List<BudgetEntity> budgets) {
        currentBudgets = budgets != null ? new ArrayList<>(budgets) : new ArrayList<>();
        hasAnyBudgets.setValue(!currentBudgets.isEmpty());
        syncChildSources();
        rebuildUiModels();
    }

    private void syncChildSources() {
        resetChildSources();

        for (BudgetEntity budgetEntity : currentBudgets) {
            String budgetId = budgetEntity.getId();

            if (budgetEntity.getCategoryId() != null) {
                LiveData<CategoryEntity> categoryLiveData =
                        categoryRepository.getCategoryById(budgetEntity.getCategoryId());
                Observer<CategoryEntity> categoryObserver = categoryEntity -> {
                    categoryValues.put(budgetId, categoryEntity);
                    rebuildUiModels();
                };
                categorySources.put(budgetId, categoryLiveData);
                categoryObservers.put(budgetId, categoryObserver);
                categoryLiveData.observeForever(categoryObserver);
            }

            if (budgetEntity.getWalletId() != null) {
                LiveData<WalletEntity> walletLiveData =
                        walletRepository.getById(budgetEntity.getWalletId());
                Observer<WalletEntity> walletObserver = walletEntity -> {
                    walletValues.put(budgetId, walletEntity);
                    rebuildUiModels();
                };
                walletSources.put(budgetId, walletLiveData);
                walletObservers.put(budgetId, walletObserver);
                walletLiveData.observeForever(walletObserver);
            }

            LiveData<Double> spentLiveData = transactionRepository.getTotalExpenseByCategory(
                    userId,
                    budgetEntity.getCategoryId(),
                    budgetEntity.getWalletId(),
                    budgetEntity.getStartDate(),
                    budgetEntity.getEndDate()
            );
            Observer<Double> spentObserver = spentAmount -> {
                spentValues.put(budgetId, spentAmount != null ? spentAmount : 0d);
                rebuildUiModels();
            };
            spentSources.put(budgetId, spentLiveData);
            spentObservers.put(budgetId, spentObserver);
            spentLiveData.observeForever(spentObserver);
        }
    }

    private void resetChildSources() {
        for (Map.Entry<String, LiveData<CategoryEntity>> entry : categorySources.entrySet()) {
            Observer<CategoryEntity> observer = categoryObservers.get(entry.getKey());
            if (observer != null) {
                entry.getValue().removeObserver(observer);
            }
        }
        for (Map.Entry<String, LiveData<WalletEntity>> entry : walletSources.entrySet()) {
            Observer<WalletEntity> observer = walletObservers.get(entry.getKey());
            if (observer != null) {
                entry.getValue().removeObserver(observer);
            }
        }
        for (Map.Entry<String, LiveData<Double>> entry : spentSources.entrySet()) {
            Observer<Double> observer = spentObservers.get(entry.getKey());
            if (observer != null) {
                entry.getValue().removeObserver(observer);
            }
        }
        categorySources.clear();
        walletSources.clear();
        spentSources.clear();
        categoryObservers.clear();
        walletObservers.clear();
        spentObservers.clear();
        categoryValues.clear();
        walletValues.clear();
        spentValues.clear();
    }

    private void rebuildUiModels() {
        BudgetPartition thisMonth = new BudgetPartition();
        BudgetPartition future = new BudgetPartition();
        BudgetPartition custom = new BudgetPartition();
        List<BudgetUIModel> finishedItems = new ArrayList<>();

        String selectedWalletId = selectedWalletFilterId.getValue();
        LocalDate today = LocalDate.now();
        LocalDate thisMonthStart = today.withDayOfMonth(1);
        LocalDate thisMonthEnd = today.withDayOfMonth(today.lengthOfMonth());

        for (BudgetEntity budgetEntity : currentBudgets) {
            if (matchesSelectedWallet(budgetEntity, selectedWalletId)) {
                BudgetUIModel item = buildBudgetUiModel(budgetEntity);
                assignToPartition(item, finishedItems, thisMonth, future, custom, thisMonthStart, thisMonthEnd);
            }
        }

        sortBudgetItems(thisMonth.items);
        sortBudgetItems(future.items);
        sortBudgetItems(custom.items);
        Collections.sort(finishedItems, Comparator
                .comparingLong((BudgetUIModel item) -> item.getBudgetEntity().getEndDate())
                .reversed());

        BudgetPartition selectedPartition = getSelectedPartition(thisMonth, future, custom);
        activeBudgets.setValue(selectedPartition.items);
        finishedBudgets.setValue(finishedItems);
        summary.setValue(buildSummary(selectedPartition.items, selectedPartition.allCategoriesBudget));
    }

    @NonNull
    private BudgetPartition getSelectedPartition(@NonNull BudgetPartition thisMonth,
                                                 @NonNull BudgetPartition future,
                                                 @NonNull BudgetPartition custom) {
        switch (getSelectedTab()) {
            case FUTURE:
                return future;
            case CUSTOM:
                return custom;
            case THIS_MONTH:
            default:
                return thisMonth;
        }
    }

    private void sortBudgetItems(@NonNull List<BudgetUIModel> items) {
        Collections.sort(items, (left, right) -> {
            boolean leftOther = isOtherCategoryBudget(left);
            boolean rightOther = isOtherCategoryBudget(right);
            if (leftOther != rightOther) {
                return leftOther ? 1 : -1;
            }
            int percentCompare = Double.compare(right.getPercent(), left.getPercent());
            if (percentCompare != 0) {
                return percentCompare;
            }
            return Long.compare(left.getBudgetEntity().getEndDate(), right.getBudgetEntity().getEndDate());
        });
    }

    private boolean isOtherCategoryBudget(@NonNull BudgetUIModel item) {
        return Constants.isOtherCategoryId(item.getBudgetEntity().getCategoryId());
    }

    @NonNull
    private String resolveWalletName(@NonNull BudgetEntity budgetEntity,
                                     @Nullable WalletEntity walletEntity) {
        if (budgetEntity.getWalletId() == null) {
            return labels.allWalletsLabel;
        }
        return walletEntity != null ? walletEntity.getName() : labels.unknownWalletLabel;
    }

    @NonNull
    private String resolveCategoryName(@NonNull BudgetEntity budgetEntity,
                                       @Nullable CategoryEntity categoryEntity) {
        if (budgetEntity.getCategoryId() == null) {
            return labels.allCategoriesLabel;
        }
        if (Constants.isOtherCategoryId(budgetEntity.getCategoryId())) {
            return labels.otherCategoriesLabel;
        }
        return categoryEntity != null ? categoryEntity.getName() : labels.unknownCategoryLabel;
    }

    @NonNull
    private String resolveCategoryIcon(@NonNull BudgetEntity budgetEntity,
                                       @Nullable CategoryEntity categoryEntity) {
        if (Constants.isOtherCategoryId(budgetEntity.getCategoryId())) {
            return "ic_category_other";
        }
        if (categoryEntity == null || categoryEntity.getIconName() == null) {
            return "";
        }
        return categoryEntity.getIconName();
    }

    private boolean isWithinMonth(@NonNull LocalDate date,
                                  @NonNull LocalDate monthStart,
                                  @NonNull LocalDate monthEnd) {
        return (!date.isBefore(monthStart)) && (!date.isAfter(monthEnd));
    }

    private boolean matchesSelectedWallet(@NonNull BudgetEntity budgetEntity,
                                          @Nullable String selectedWalletId) {
        return selectedWalletId == null || selectedWalletId.equals(budgetEntity.getWalletId());
    }

    @NonNull
    private BudgetUIModel buildBudgetUiModel(@NonNull BudgetEntity budgetEntity) {
        String budgetId = budgetEntity.getId();
        CategoryEntity categoryEntity = categoryValues.get(budgetId);
        WalletEntity walletEntity = walletValues.get(budgetId);
        double spentAmount = spentValues.getOrDefault(budgetId, 0d);
        return new BudgetUIModel(
                budgetEntity,
                resolveCategoryName(budgetEntity, categoryEntity),
                resolveCategoryIcon(budgetEntity, categoryEntity),
                spentAmount,
                resolveWalletName(budgetEntity, walletEntity),
                BudgetUiUtils.isActiveToday(budgetEntity)
        );
    }

    private void assignToPartition(@NonNull BudgetUIModel item,
                                   @NonNull List<BudgetUIModel> finishedItems,
                                   @NonNull BudgetPartition thisMonth,
                                   @NonNull BudgetPartition future,
                                   @NonNull BudgetPartition custom,
                                   @NonNull LocalDate thisMonthStart,
                                   @NonNull LocalDate thisMonthEnd) {
        LocalDate startDate = toLocalDate(item.getBudgetEntity().getStartDate());
        LocalDate endDate = toLocalDate(item.getBudgetEntity().getEndDate());

        if (endDate.isBefore(thisMonthStart)) {
            finishedItems.add(item);
        } else if (startDate.isAfter(thisMonthEnd)) {
            future.add(item);
        } else if (isWithinMonth(startDate, thisMonthStart, thisMonthEnd)
                && isWithinMonth(endDate, thisMonthStart, thisMonthEnd)) {
            thisMonth.add(item);
        } else {
            custom.add(item);
        }
    }

    @NonNull
    private BudgetSummaryUIModel buildSummary(@NonNull List<BudgetUIModel> visibleBudgets,
                                              @Nullable BudgetUIModel allCategoriesBudget) {
        if (allCategoriesBudget != null) {
            double specificBudgetsTotal = 0d;
            for (BudgetUIModel item : visibleBudgets) {
                specificBudgetsTotal += item.getBudgetEntity().getAmount();
            }
            double shortfall = Math.max(0d, specificBudgetsTotal - allCategoriesBudget.getBudgetEntity().getAmount());
            return new BudgetSummaryUIModel(
                    allCategoriesBudget.getBudgetEntity().getAmount(),
                    allCategoriesBudget.getSpentAmount(),
                    allCategoriesBudget.getRemainingAmount(),
                    BudgetUiUtils.getDaysLeftInclusive(allCategoriesBudget.getBudgetEntity().getEndDate()),
                    true,
                    allCategoriesBudget,
                    shortfall > 0d,
                    shortfall
            );
        }

        if (visibleBudgets.isEmpty()) {
            return new BudgetSummaryUIModel(0d, 0d, 0d, 0, false, null, false, 0d);
        }

        double totalBudget = 0d;
        double totalSpent = 0d;
        long maxEndDate = 0L;

        for (BudgetUIModel item : visibleBudgets) {
            totalBudget += item.getBudgetEntity().getAmount();
            totalSpent += item.getSpentAmount();
            maxEndDate = Math.max(maxEndDate, item.getBudgetEntity().getEndDate());
        }

        return new BudgetSummaryUIModel(
                totalBudget,
                totalSpent,
                totalBudget - totalSpent,
                BudgetUiUtils.getDaysLeftInclusive(maxEndDate),
                true,
                null,
                false,
                0d
        );
    }

    @NonNull
    private LocalDate toLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        budgetSource.removeObserver(budgetObserver);
        selectedWalletFilterId.removeObserver(selectedWalletObserver);
        selectedTab.removeObserver(selectedTabObserver);
        resetChildSources();
    }

    private static final class BudgetPartition {
        private final List<BudgetUIModel> items = new ArrayList<>();
        @Nullable
        private BudgetUIModel allCategoriesBudget;

        private void add(@NonNull BudgetUIModel item) {
            if (item.isAllCategories()) {
                if (allCategoriesBudget == null
                        || item.getBudgetEntity().getUpdatedAt() > allCategoriesBudget.getBudgetEntity().getUpdatedAt()) {
                    allCategoriesBudget = item;
                }
                return;
            }
            items.add(item);
        }
    }

    public static class BudgetSummaryUIModel {
        private final double totalBudget;
        private final double totalSpent;
        private final double remainingAmount;
        private final int daysLeft;
        private final boolean hasActiveBudgets;
        @Nullable
        private final BudgetUIModel allCategoriesBudget;
        private final boolean shouldShowGapWarning;
        private final double shortfallAmount;

        public BudgetSummaryUIModel(double totalBudget,
                                    double totalSpent,
                                    double remainingAmount,
                                    int daysLeft,
                                    boolean hasActiveBudgets,
                                    @Nullable BudgetUIModel allCategoriesBudget,
                                    boolean shouldShowGapWarning,
                                    double shortfallAmount) {
            this.totalBudget = totalBudget;
            this.totalSpent = totalSpent;
            this.remainingAmount = remainingAmount;
            this.daysLeft = daysLeft;
            this.hasActiveBudgets = hasActiveBudgets;
            this.allCategoriesBudget = allCategoriesBudget;
            this.shouldShowGapWarning = shouldShowGapWarning;
            this.shortfallAmount = shortfallAmount;
        }

        public double getTotalBudget() {
            return totalBudget;
        }

        public double getTotalSpent() {
            return totalSpent;
        }

        public double getRemainingAmount() {
            return remainingAmount;
        }

        public int getDaysLeft() {
            return daysLeft;
        }

        public boolean hasActiveBudgets() {
            return hasActiveBudgets;
        }

        @Nullable
        public BudgetUIModel getAllCategoriesBudget() {
            return allCategoriesBudget;
        }

        public boolean hasAllCategoriesBudget() {
            return allCategoriesBudget != null;
        }

        public boolean shouldShowGapWarning() {
            return shouldShowGapWarning;
        }

        public double getShortfallAmount() {
            return shortfallAmount;
        }

        public float getPercent() {
            if (totalBudget <= 0d) {
                return 0f;
            }
            return (float) ((totalSpent / totalBudget) * 100f);
        }
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final BudgetRepository budgetRepository;
        private final CategoryRepository categoryRepository;
        private final TransactionRepository transactionRepository;
        private final WalletRepository walletRepository;
        private final String userId;
        private final Labels labels;

        public Factory(@NonNull BudgetRepository budgetRepository,
                       @NonNull CategoryRepository categoryRepository,
                       @NonNull TransactionRepository transactionRepository,
                       @NonNull WalletRepository walletRepository,
                       @NonNull String userId,
                       @NonNull Labels labels) {
            this.budgetRepository = budgetRepository;
            this.categoryRepository = categoryRepository;
            this.transactionRepository = transactionRepository;
            this.walletRepository = walletRepository;
            this.userId = userId;
            this.labels = labels;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (!modelClass.isAssignableFrom(BudgetViewModel.class)) {
                throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass);
            }
            return (T) new BudgetViewModel(
                    budgetRepository,
                    categoryRepository,
                    transactionRepository,
                    walletRepository,
                    userId,
                    labels
            );
        }
    }

    public static final class Labels {
        private final String allCategoriesLabel;
        private final String otherCategoriesLabel;
        private final String allWalletsLabel;
        private final String unknownWalletLabel;
        private final String unknownCategoryLabel;

        public Labels(@NonNull String allCategoriesLabel,
                      @NonNull String otherCategoriesLabel,
                      @NonNull String allWalletsLabel,
                      @NonNull String unknownWalletLabel,
                      @NonNull String unknownCategoryLabel) {
            this.allCategoriesLabel = allCategoriesLabel;
            this.otherCategoriesLabel = otherCategoriesLabel;
            this.allWalletsLabel = allWalletsLabel;
            this.unknownWalletLabel = unknownWalletLabel;
            this.unknownCategoryLabel = unknownCategoryLabel;
        }
    }
}
