package com.group10.moneymate.data.repository;

import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.DebtDao;
import com.group10.moneymate.data.local.entity.DebtEntity;

import java.util.List;

public class DebtRepository {
    private final DebtDao debtDao;

    public DebtRepository(DebtDao debtDao) {
        this.debtDao = debtDao;
    }

    public LiveData<List<DebtEntity>> getAllDebts(String userId) {
        return debtDao.getAllDebts(userId);
    }

    public LiveData<List<DebtEntity>> getDebtsByType(String userId, String type) {
        return debtDao.getDebtsByType(userId, type);
    }

    public LiveData<DebtEntity> getDebtById(String id) {
        return debtDao.getDebtById(id);
    }

    public void insert(DebtEntity debt) {
        AppDatabase.databaseWriteExecutor.execute(() -> debtDao.insertDebt(debt));
    }

    public void update(DebtEntity debt) {
        AppDatabase.databaseWriteExecutor.execute(() -> debtDao.updateDebt(debt));
    }

    public void softDelete(String id) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                debtDao.softDelete(id, System.currentTimeMillis()));
    }
}