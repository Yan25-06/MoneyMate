package com.group10.moneymate.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.BudgetDao;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.models.SyncStatus;

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

    private final BudgetDao budgetDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public BudgetRepository(BudgetDao budgetDao) {
        this.budgetDao = budgetDao;
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
        // userId null = bug ở caller
        if (budget.getUserId() == null || budget.getUserId().trim().isEmpty()) {
            notifyError(callback, new IllegalArgumentException("Budget userId is null"));
            return;
        }
        long now = System.currentTimeMillis();
        if (budget.getId().trim().isEmpty()) {
            budget.setId(UUID.randomUUID().toString());
        }
        if (budget.getCreatedAt() <= 0L) {
            budget.setCreatedAt(now);
        }
        budget.setUpdatedAt(now);
        budget.setDeleted(false);
        budget.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                budgetDao.insert(budget);
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
        budget.setUpdatedAt(System.currentTimeMillis());
        budget.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                budgetDao.update(budget);
                notifySuccess(callback);
            } catch (Exception exception) {
                notifyError(callback, exception);
            }
        });
    }

    public void softDeleteBudget(String userId, String id) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                budgetDao.softDelete(userId, id, System.currentTimeMillis()));
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
}
