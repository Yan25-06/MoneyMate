package com.group10.moneymate.ui.budget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.BudgetRepository;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.WalletRepository;

import java.util.List;
import java.util.UUID;

public class AddEditBudgetViewModel extends ViewModel {

    private final BudgetRepository budgetRepository;
    private final String userId;
    private final LiveData<List<CategoryEntity>> expenseCategories;
    private final LiveData<List<WalletEntity>> wallets;

    public AddEditBudgetViewModel(@NonNull BudgetRepository budgetRepository,
                                  @NonNull CategoryRepository categoryRepository,
                                  @NonNull WalletRepository walletRepository,
                                  @NonNull String userId) {
        this.budgetRepository = budgetRepository;
        this.userId = userId;
        this.expenseCategories = categoryRepository.getCategoriesByType(userId, "EXPENSE");
        this.wallets = walletRepository.getAllByUser(userId);
    }

    public LiveData<List<CategoryEntity>> getExpenseCategories() {
        return expenseCategories;
    }

    public LiveData<List<WalletEntity>> getWallets() {
        return wallets;
    }

    public LiveData<BudgetEntity> getBudgetById(@NonNull String budgetId) {
        return budgetRepository.getBudgetById(userId, budgetId);
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
                             long endDate,
                             @Nullable BudgetRepository.WriteCallback callback) {
        budgetEntity.setUserId(userId);
        budgetEntity.setCategoryId(categoryId);
        budgetEntity.setWalletId(walletId);
        budgetEntity.setAmount(amount);
        budgetEntity.setStartDate(startDate);
        budgetEntity.setEndDate(endDate);
        budgetRepository.updateBudget(budgetEntity, callback);
    }

    public static class Factory implements ViewModelProvider.Factory {
        private final BudgetRepository budgetRepository;
        private final CategoryRepository categoryRepository;
        private final WalletRepository walletRepository;
        private final String userId;

        public Factory(@NonNull BudgetRepository budgetRepository,
                       @NonNull CategoryRepository categoryRepository,
                       @NonNull WalletRepository walletRepository,
                       @NonNull String userId) {
            this.budgetRepository = budgetRepository;
            this.categoryRepository = categoryRepository;
            this.walletRepository = walletRepository;
            this.userId = userId;
        }

        @NonNull
        @Override
        @SuppressWarnings("unchecked")
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            if (!modelClass.isAssignableFrom(AddEditBudgetViewModel.class)) {
                throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass);
            }
            return (T) new AddEditBudgetViewModel(
                    budgetRepository,
                    categoryRepository,
                    walletRepository,
                    userId
            );
        }
    }
}
