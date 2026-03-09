package com.group10.moneymate.data.repository;

import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.BudgetDao;
import com.group10.moneymate.data.local.entity.BudgetEntity;

import java.util.List;

public class BudgetRepository {
    private final BudgetDao budgetDao;

    public BudgetRepository(BudgetDao budgetDao) {
        this.budgetDao = budgetDao;
    }

    public LiveData<List<BudgetEntity>> getBudgetsByMonth(String userId, int month, int year) {
        return budgetDao.getBudgetsByMonth(userId, month, year);
    }

    public LiveData<BudgetEntity> getBudgetById(String id) {
        return budgetDao.getBudgetById(id);
    }

    public BudgetEntity getBudgetByCategoryAndMonthSync(String userId, String categoryId, int month, int year) {
        return budgetDao.getBudgetByCategoryAndMonthSync(userId, categoryId, month, year);
    }

    public void insert(BudgetEntity budget) {
        AppDatabase.databaseWriteExecutor.execute(() -> budgetDao.insertBudget(budget));
    }

    public void update(BudgetEntity budget) {
        AppDatabase.databaseWriteExecutor.execute(() -> budgetDao.updateBudget(budget));
    }

    public void softDelete(String id) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                budgetDao.softDelete(id, System.currentTimeMillis()));
    }
}