package com.group10.moneymate.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.BudgetDao;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.Constants;

import java.util.List;
import java.util.UUID;

/**
 * Repository for budget data.
 * Read methods trả về LiveData để UI observe trực tiếp từ Room.
 */
public class BudgetRepository {
    public interface WriteCallback {
        void onSuccess();
        void onError(Throwable throwable);
    }

    public static class BudgetRuleException extends RuntimeException {
        public enum Reason {
            ALL_CATEGORIES_ALREADY_EXISTS,
            OTHER_CATEGORY_MANUAL_NOT_ALLOWED
        }

        private final Reason reason;

        public BudgetRuleException(Reason reason) {
            super(reason.name());
            this.reason = reason;
        }

        public Reason getReason() {
            return reason;
        }
    }

    private final BudgetDao budgetDao;
    private final AppDatabase appDatabase;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public BudgetRepository(BudgetDao budgetDao, AppDatabase appDatabase) {
        this.budgetDao = budgetDao;
        this.appDatabase = appDatabase;
    }

    public LiveData<List<BudgetEntity>> getAllBudgets(String userId) {
        return budgetDao.getAllBudgets(userId);
    }

    public LiveData<BudgetEntity> getBudgetById(String userId, String id) {
        return budgetDao.getBudgetById(userId, id);
    }

    public void addBudget(BudgetEntity budget) {
        addBudget(budget, null);
    }

    public void addBudget(BudgetEntity budget, @Nullable WriteCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                appDatabase.runInTransaction(() -> {
                    validateManualInsert(budget);
                    insertBudgetInternal(budget);
                    syncOtherCategoriesBudget(
                            budget.getUserId(),
                            budget.getWalletId(),
                            budget.getStartDate(),
                            budget.getEndDate()
                    );
                });
                notifySuccess(callback);
            } catch (Exception exception) {
                notifyError(callback, exception);
            }
        });
    }

    public void updateBudget(BudgetEntity budget) {
        updateBudget(budget, null);
    }

    public void updateBudget(BudgetEntity budget, @Nullable WriteCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                appDatabase.runInTransaction(() -> {
                    BudgetEntity existing = budgetDao.getBudgetByIdSync(budget.getUserId(), budget.getId());
                    validateManualUpdate(budget);
                    updateBudgetInternal(budget);
                    syncOtherCategoriesBudget(
                            budget.getUserId(),
                            budget.getWalletId(),
                            budget.getStartDate(),
                            budget.getEndDate()
                    );
                    if (existing != null && !isSameScope(existing, budget)) {
                        syncOtherCategoriesBudget(
                                existing.getUserId(),
                                existing.getWalletId(),
                                existing.getStartDate(),
                                existing.getEndDate()
                        );
                    }
                });
                notifySuccess(callback);
            } catch (Exception exception) {
                notifyError(callback, exception);
            }
        });
    }

    public void softDeleteBudget(String userId, String id) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            appDatabase.runInTransaction(() -> {
                BudgetEntity existing = budgetDao.getBudgetByIdSync(userId, id);
                budgetDao.softDelete(userId, id, System.currentTimeMillis());
                if (existing != null) {
                    syncOtherCategoriesBudget(
                            existing.getUserId(),
                            existing.getWalletId(),
                            existing.getStartDate(),
                            existing.getEndDate()
                    );
                }
            });
        });
    }

    public void insert(BudgetEntity budget) {
        addBudget(budget);
    }

    public void update(BudgetEntity budget) {
        updateBudget(budget);
    }

    public void softDelete(String userId, String id) {
        softDeleteBudget(userId, id);
    }

    private void notifySuccess(@Nullable WriteCallback callback) {
        if (callback == null) {
            return;
        }
        mainHandler.post(callback::onSuccess);
    }

    private void notifyError(@Nullable WriteCallback callback, @Nullable Throwable throwable) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onError(throwable != null ? throwable : new RuntimeException("Budget write failed")));
    }

    private void validateManualInsert(@NonNull BudgetEntity budget) {
        if (Constants.isOtherCategoryId(budget.getCategoryId())) {
            throw new BudgetRuleException(BudgetRuleException.Reason.OTHER_CATEGORY_MANUAL_NOT_ALLOWED);
        }
        if (budget.getCategoryId() == null) {
            int count = budgetDao.countAllCategoriesBudgets(
                    budget.getUserId(),
                    budget.getWalletId(),
                    budget.getStartDate(),
                    budget.getEndDate()
            );
            if (count > 0) {
                throw new BudgetRuleException(BudgetRuleException.Reason.ALL_CATEGORIES_ALREADY_EXISTS);
            }
        }
    }

    private void validateManualUpdate(@NonNull BudgetEntity budget) {
        if (Constants.isOtherCategoryId(budget.getCategoryId())) {
            throw new BudgetRuleException(BudgetRuleException.Reason.OTHER_CATEGORY_MANUAL_NOT_ALLOWED);
        }
        if (budget.getCategoryId() == null) {
            int count = budgetDao.countAllCategoriesBudgetsExcluding(
                    budget.getUserId(),
                    budget.getWalletId(),
                    budget.getStartDate(),
                    budget.getEndDate(),
                    budget.getId()
            );
            if (count > 0) {
                throw new BudgetRuleException(BudgetRuleException.Reason.ALL_CATEGORIES_ALREADY_EXISTS);
            }
        }
    }

    private void insertBudgetInternal(@NonNull BudgetEntity budget) {
        long now = System.currentTimeMillis();
        if (budget.getId() == null || budget.getId().trim().isEmpty()) {
            budget.setId(UUID.randomUUID().toString());
        }
        if (budget.getCreatedAt() <= 0L) {
            budget.setCreatedAt(now);
        }
        budget.setUpdatedAt(now);
        budget.setDeleted(false);
        budget.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        budgetDao.upsertLocal(budget);
    }

    private void updateBudgetInternal(@NonNull BudgetEntity budget) {
        if (budget.getCreatedAt() <= 0L) {
            budget.setCreatedAt(System.currentTimeMillis());
        }
        budget.setUpdatedAt(System.currentTimeMillis());
        budget.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        budgetDao.upsertLocal(budget);
    }

    private void syncOtherCategoriesBudget(@NonNull String userId,
                                           @Nullable String walletId,
                                           long startDate,
                                           long endDate) {
        BudgetEntity allCategories = budgetDao.getAllCategoriesBudgetSync(userId, walletId, startDate, endDate);
        BudgetEntity otherBudget = budgetDao.getOtherCategoryBudgetSync(userId, walletId, startDate, endDate);
        int specificCount = budgetDao.countSpecificCategoryBudgets(userId, walletId, startDate, endDate);

        if (allCategories == null || specificCount <= 0) {
            if (otherBudget != null) {
                budgetDao.softDelete(userId, otherBudget.getId(), System.currentTimeMillis());
            }
            return;
        }

        Double specificTotalValue = budgetDao.sumSpecificCategoryBudgets(userId, walletId, startDate, endDate);
        double specificTotal = specificTotalValue != null ? specificTotalValue : 0d;
        double remaining = Math.max(0d, allCategories.getAmount() - specificTotal);

        if (otherBudget == null) {
            BudgetEntity created = new BudgetEntity();
            created.setUserId(userId);
            created.setCategoryId(Constants.CATEGORY_ID_OTHER);
            created.setWalletId(walletId);
            created.setAmount(remaining);
            created.setStartDate(startDate);
            created.setEndDate(endDate);
            insertBudgetInternal(created);
            return;
        }

        if (Math.abs(otherBudget.getAmount() - remaining) < 0.01d) {
            return;
        }
        otherBudget.setAmount(remaining);
        updateBudgetInternal(otherBudget);
    }

    private boolean isSameScope(@NonNull BudgetEntity left, @NonNull BudgetEntity right) {
        if (left.getStartDate() != right.getStartDate() || left.getEndDate() != right.getEndDate()) {
            return false;
        }
        String leftWallet = left.getWalletId();
        String rightWallet = right.getWalletId();
        return leftWallet == null ? rightWallet == null : leftWallet.equals(rightWallet);
    }
}
