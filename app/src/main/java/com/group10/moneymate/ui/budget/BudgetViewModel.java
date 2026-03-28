package com.group10.moneymate.ui.budget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.repository.BudgetRepository;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.TransactionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BudgetViewModel extends ViewModel {

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionRepository transactionRepository;
    private final String userId;

    private final LiveData<List<BudgetEntity>> budgetSource;
    private final LiveData<List<CategoryEntity>> expenseCategories;
    private final MediatorLiveData<List<BudgetUIModel>> activeBudgets = new MediatorLiveData<>();
    private final MediatorLiveData<List<BudgetUIModel>> finishedBudgets = new MediatorLiveData<>();
    private final MediatorLiveData<BudgetSummaryUIModel> summary = new MediatorLiveData<>();

    private final Map<String, LiveData<CategoryEntity>> categorySources = new HashMap<>();
    private final Map<String, LiveData<Double>> spentSources = new HashMap<>();
    private final Map<String, CategoryEntity> categoryValues = new HashMap<>();
    private final Map<String, Double> spentValues = new HashMap<>();
    private List<BudgetEntity> currentBudgets = new ArrayList<>();

    public BudgetViewModel(@NonNull BudgetRepository budgetRepository,
                           @NonNull CategoryRepository categoryRepository,
                           @NonNull TransactionRepository transactionRepository,
                           @NonNull String userId) {
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.transactionRepository = transactionRepository;
        this.userId = userId;
        this.budgetSource = budgetRepository.getAllBudgets();
        this.expenseCategories = categoryRepository.getCategoriesByType(userId, "EXPENSE");

        activeBudgets.addSource(budgetSource, this::onBudgetsChanged);
    }

    public LiveData<List<BudgetUIModel>> getActiveBudgets() {
        return activeBudgets;
    }

    public LiveData<List<BudgetUIModel>> getFinishedBudgets() {
        return finishedBudgets;
    }

    public LiveData<BudgetSummaryUIModel> getSummary() {
        return summary;
    }

    public LiveData<List<CategoryEntity>> getExpenseCategories() {
        return expenseCategories;
    }

    public LiveData<BudgetEntity> getBudgetById(@NonNull String budgetId) {
        return budgetRepository.getBudgetById(budgetId);
    }

    public LiveData<BudgetUIModel> getBudgetUiModel(@NonNull String budgetId) {
        MediatorLiveData<BudgetUIModel> result = new MediatorLiveData<>();
        LiveData<BudgetEntity> budgetLiveData = budgetRepository.getBudgetById(budgetId);
        final LiveData<CategoryEntity>[] categorySource = new LiveData[]{null};
        final LiveData<Double>[] spentSource = new LiveData[]{null};
        final BudgetEntity[] budgetHolder = new BudgetEntity[]{null};
        final CategoryEntity[] categoryHolder = new CategoryEntity[]{null};
        final double[] spentHolder = new double[]{0d};

        Runnable publish = () -> {
            if (budgetHolder[0] == null) {
                result.setValue(null);
                return;
            }
            CategoryEntity category = categoryHolder[0];
            result.setValue(new BudgetUIModel(
                    budgetHolder[0],
                    category != null ? category.getName() : "Category",
                    category != null ? category.getIconResId() : "",
                    spentHolder[0],
                    category != null ? category.getColorHex() : "",
                    BudgetUiUtils.isActiveToday(budgetHolder[0])
            ));
        };

        result.addSource(budgetLiveData, budget -> {
            budgetHolder[0] = budget;
            categoryHolder[0] = null;
            spentHolder[0] = 0d;

            if (categorySource[0] != null) {
                result.removeSource(categorySource[0]);
                categorySource[0] = null;
            }
            if (spentSource[0] != null) {
                result.removeSource(spentSource[0]);
                spentSource[0] = null;
            }

            if (budget == null) {
                publish.run();
                return;
            }

            categorySource[0] = categoryRepository.getCategoryById(budget.getCategoryId());
            result.addSource(categorySource[0], category -> {
                categoryHolder[0] = category;
                publish.run();
            });

            spentSource[0] = transactionRepository.getTotalExpenseByCategory(
                    userId,
                    budget.getCategoryId(),
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

    public void addBudget(@NonNull String categoryId,
                          double amount,
                          long startDate,
                          long endDate) {
        addBudget(categoryId, amount, startDate, endDate, null);
    }

    public void addBudget(@NonNull String categoryId,
                          double amount,
                          long startDate,
                          long endDate,
                          @Nullable BudgetRepository.WriteCallback callback) {
        BudgetEntity budgetEntity = new BudgetEntity();
        budgetEntity.setId(UUID.randomUUID().toString());
        budgetEntity.setCategoryId(categoryId);
        budgetEntity.setAmount(amount);
        budgetEntity.setStartDate(startDate);
        budgetEntity.setEndDate(endDate);
        budgetRepository.addBudget(budgetEntity, callback);
    }

    public void updateBudget(@NonNull BudgetEntity budgetEntity,
                             @NonNull String categoryId,
                             double amount,
                             long startDate,
                             long endDate) {
        updateBudget(budgetEntity, categoryId, amount, startDate, endDate, null);
    }

    public void updateBudget(@NonNull BudgetEntity budgetEntity,
                             @NonNull String categoryId,
                             double amount,
                             long startDate,
                             long endDate,
                             @Nullable BudgetRepository.WriteCallback callback) {
        budgetEntity.setCategoryId(categoryId);
        budgetEntity.setAmount(amount);
        budgetEntity.setStartDate(startDate);
        budgetEntity.setEndDate(endDate);
        budgetRepository.updateBudget(budgetEntity, callback);
    }

    public void deleteBudget(@NonNull BudgetEntity budgetEntity) {
        budgetRepository.softDeleteBudget(budgetEntity.getId());
    }

    private void onBudgetsChanged(@Nullable List<BudgetEntity> budgets) {
        currentBudgets = budgets != null ? new ArrayList<>(budgets) : new ArrayList<>();
        syncChildSources();
        rebuildUiModels();
    }

    private void syncChildSources() {
        Set<String> activeIds = new HashSet<>();
        for (BudgetEntity budgetEntity : currentBudgets) {
            String budgetId = budgetEntity.getId();
            activeIds.add(budgetId);

            if (!categorySources.containsKey(budgetId)) {
                final String sourceBudgetId = budgetId;
                LiveData<CategoryEntity> categoryLiveData =
                        categoryRepository.getCategoryById(budgetEntity.getCategoryId());
                categorySources.put(sourceBudgetId, categoryLiveData);
                activeBudgets.addSource(categoryLiveData, categoryEntity -> {
                    categoryValues.put(sourceBudgetId, categoryEntity);
                    rebuildUiModels();
                });
            }

            if (!spentSources.containsKey(budgetId)) {
                final String sourceBudgetId = budgetId;
                LiveData<Double> spentLiveData = transactionRepository.getTotalExpenseByCategory(
                        userId,
                        budgetEntity.getCategoryId(),
                        budgetEntity.getStartDate(),
                        budgetEntity.getEndDate()
                );
                spentSources.put(sourceBudgetId, spentLiveData);
                activeBudgets.addSource(spentLiveData, spentAmount -> {
                    spentValues.put(sourceBudgetId, spentAmount != null ? spentAmount : 0d);
                    rebuildUiModels();
                });
            }
        }

        removeStaleSources(categorySources, activeIds, categoryValues);
        removeStaleSources(spentSources, activeIds, spentValues);
    }

    private <T> void removeStaleSources(@NonNull Map<String, LiveData<T>> sourceMap,
                                        @NonNull Set<String> activeIds,
                                        @NonNull Map<String, ?> valueMap) {
        List<String> staleIds = new ArrayList<>();
        for (String id : sourceMap.keySet()) {
            if (!activeIds.contains(id)) {
                staleIds.add(id);
            }
        }

        for (String staleId : staleIds) {
            LiveData<T> liveData = sourceMap.remove(staleId);
            if (liveData != null) {
                activeBudgets.removeSource(liveData);
            }
            valueMap.remove(staleId);
        }
    }

    private void rebuildUiModels() {
        List<BudgetUIModel> allItems = new ArrayList<>();
        List<BudgetUIModel> activeItems = new ArrayList<>();
        List<BudgetUIModel> finishedItems = new ArrayList<>();

        for (BudgetEntity budgetEntity : currentBudgets) {
            String budgetId = budgetEntity.getId();
            CategoryEntity categoryEntity = categoryValues.get(budgetId);
            double spentAmount = spentValues.containsKey(budgetId)
                    ? spentValues.get(budgetId)
                    : 0d;
            boolean isActive = BudgetUiUtils.isActiveToday(budgetEntity);

            BudgetUIModel item = new BudgetUIModel(
                    budgetEntity,
                    categoryEntity != null ? categoryEntity.getName() : "Danh mục",
                    categoryEntity != null ? categoryEntity.getIconResId() : "",
                    spentAmount,
                    categoryEntity != null ? categoryEntity.getColorHex() : "",
                    isActive
            );

            allItems.add(item);
            if (isActive) {
                activeItems.add(item);
            } else {
                finishedItems.add(item);
            }
        }

        Collections.sort(activeItems, Comparator
                .comparingDouble(BudgetUIModel::getPercent).reversed()
                .thenComparing(item -> item.getBudgetEntity().getEndDate()));
        Collections.sort(finishedItems, Comparator
                .comparingLong((BudgetUIModel item) -> item.getBudgetEntity().getEndDate())
                .reversed());

        activeBudgets.setValue(activeItems);
        finishedBudgets.setValue(finishedItems);
        summary.setValue(buildSummary(activeItems));
    }

    @NonNull
    private BudgetSummaryUIModel buildSummary(@NonNull List<BudgetUIModel> activeItems) {
        if (activeItems.isEmpty()) {
            return new BudgetSummaryUIModel(0d, 0d, 0d, 0, false);
        }

        double totalBudget = 0d;
        double totalSpent = 0d;
        long maxEndDate = 0L;

        for (BudgetUIModel item : activeItems) {
            totalBudget += item.getBudgetEntity().getAmount();
            totalSpent += item.getSpentAmount();
            maxEndDate = Math.max(maxEndDate, item.getBudgetEntity().getEndDate());
        }

        return new BudgetSummaryUIModel(
                totalBudget,
                totalSpent,
                totalBudget - totalSpent,
                BudgetUiUtils.getDaysLeftInclusive(maxEndDate),
                true
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        activeBudgets.removeSource(budgetSource);
        for (LiveData<CategoryEntity> source : categorySources.values()) {
            activeBudgets.removeSource(source);
        }
        for (LiveData<Double> source : spentSources.values()) {
            activeBudgets.removeSource(source);
        }
        categorySources.clear();
        spentSources.clear();
        categoryValues.clear();
        spentValues.clear();
    }

    public static class BudgetSummaryUIModel {
        private final double totalBudget;
        private final double totalSpent;
        private final double remainingAmount;
        private final int daysLeft;
        private final boolean hasActiveBudgets;

        public BudgetSummaryUIModel(double totalBudget,
                                    double totalSpent,
                                    double remainingAmount,
                                    int daysLeft,
                                    boolean hasActiveBudgets) {
            this.totalBudget = totalBudget;
            this.totalSpent = totalSpent;
            this.remainingAmount = remainingAmount;
            this.daysLeft = daysLeft;
            this.hasActiveBudgets = hasActiveBudgets;
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
        private final String userId;

        public Factory(@NonNull BudgetRepository budgetRepository,
                       @NonNull CategoryRepository categoryRepository,
                       @NonNull TransactionRepository transactionRepository,
                       @NonNull String userId) {
            this.budgetRepository = budgetRepository;
            this.categoryRepository = categoryRepository;
            this.transactionRepository = transactionRepository;
            this.userId = userId;
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
                    userId
            );
        }
    }
}
