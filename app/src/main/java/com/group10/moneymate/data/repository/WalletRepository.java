package com.group10.moneymate.data.repository;

import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.WalletDao;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.models.SyncStatus;

import java.util.List;

/**
 * Repository for wallet data.
 */
public class WalletRepository {
    private final WalletDao walletDao;

    public WalletRepository(WalletDao walletDao) {
        this.walletDao = walletDao;
    }

    public LiveData<List<WalletEntity>> getAllByUser(String userId) {
        return walletDao.getAllByUser(userId);
    }

    public LiveData<WalletEntity> getById(String id) {
        return walletDao.getById(id);
    }

    public LiveData<Double> getTotalBalance(String userId) {
        return walletDao.getTotalBalance(userId);
    }

    public void insert(WalletEntity wallet) {
        AppDatabase.databaseWriteExecutor.execute(() -> walletDao.insert(wallet));
    }

    public void update(WalletEntity wallet) {
        wallet.setUpdatedAt(System.currentTimeMillis());
        wallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        AppDatabase.databaseWriteExecutor.execute(() -> walletDao.update(wallet));
    }

    public void softDelete(WalletEntity wallet) {
        long updatedAt = System.currentTimeMillis();
        wallet.setDeleted(true);
        wallet.setSyncStatus(SyncStatus.PENDING_DELETE);
        wallet.setUpdatedAt(updatedAt);
        AppDatabase.databaseWriteExecutor.execute(() -> walletDao.softDelete(wallet.getId(), updatedAt));
    }

    public WalletEntity getByIdSync(String id) {
        return walletDao.getByIdSync(id);
    }
}
