package com.group10.moneymate.data.repository;

import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.dao.BudgetDao;
import com.group10.moneymate.data.local.entity.BudgetEntity;

import java.util.List;

/**
 * Repository for budget data.
 */
public class BudgetRepository {
    private final BudgetDao budgetDao;

    public BudgetRepository(BudgetDao budgetDao) {
        this.budgetDao = budgetDao;
    }

    public LiveData<List<BudgetEntity>> getBudgetsByMonth(String userId, String monthYear) {
        return budgetDao.getBudgetsByMonth(userId, monthYear);
    }

    public LiveData<BudgetEntity> getBudgetById(String id) {
        return budgetDao.getBudgetById(id);
    }

    public void insertBudget(BudgetEntity budget) {
        budgetDao.insertBudget(budget);
    }

    public void updateBudget(BudgetEntity budget) {
        budgetDao.updateBudget(budget);
    }

    public void deleteBudget(BudgetEntity budget) {
        budgetDao.deleteBudget(budget);
    }

    public BudgetEntity getBudgetByCategoryAndMonthSync(String userId, String categoryId, String monthYear) {
        return budgetDao.getBudgetByCategoryAndMonthSync(userId, categoryId, monthYear);
    }
}
