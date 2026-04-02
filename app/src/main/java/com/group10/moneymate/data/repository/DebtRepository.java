package com.group10.moneymate.data.repository;

import androidx.lifecycle.LiveData;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.DebtDao;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.models.SyncStatus;

import java.util.List;
import java.util.UUID;

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
        upsertDebtInternal(debt);
    }

    public void update(DebtEntity debt) {
        upsertDebtInternal(debt);
    }

    private void upsertDebtInternal(DebtEntity debt) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            if (debt.getId() == null || debt.getId().trim().isEmpty()) {
                debt.setId(UUID.randomUUID().toString());
            }
            if (debt.getCreatedAt() <= 0L) {
                debt.setCreatedAt(now);
            }
            debt.setUpdatedAt(now);
            debt.setSyncStatus(SyncStatus.PENDING_UPLOAD);
            debtDao.upsertLocal(debt);
        });
    }

    public void softDelete(String id) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                debtDao.softDelete(id, System.currentTimeMillis()));
    }

    public List<DebtEntity> getPendingSyncPagedSince(@NonNull String userId,
                                                     long lastSyncedAt,
                                                     @NonNull String lastSyncedId,
                                                     int limit,
                                                     int offset) {
        return debtDao.getPendingSyncDebtsPagedSince(userId, lastSyncedAt, lastSyncedId, limit, offset);
    }

    public void markSynced(@NonNull String id) {
        debtDao.markSynced(id);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void hardDeleteById(@NonNull String id) {
        debtDao.hardDeleteById(id);
    }
}