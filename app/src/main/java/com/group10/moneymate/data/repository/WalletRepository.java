package com.group10.moneymate.data.repository;

import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.WalletDao;
import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.models.SyncStatus;

import java.util.List;
import java.util.UUID;

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

    public LiveData<List<WalletWithBalance>> getAllByUserWithBalance(String userId) {
        return walletDao.getAllByUserWithBalance(userId);
    }

    public LiveData<List<WalletEntity>> getActiveByUser(String userId) {
        return walletDao.getActiveByUser(userId);
    }

    public LiveData<List<WalletWithBalance>> getActiveByUserWithBalance(String userId) {
        return walletDao.getActiveByUserWithBalance(userId);
    }

    public LiveData<WalletEntity> getById(String id) {
        return walletDao.getById(id);
    }

    public LiveData<WalletWithBalance> getByIdWithBalance(String id) {
        return walletDao.getByIdWithBalance(id);
    }

    public LiveData<Double> getTotalBalance(String userId) {
        return walletDao.getTotalBalance(userId);
    }

    public void insert(WalletEntity wallet) {
        upsertWalletInternal(wallet);
    }

    public void update(WalletEntity wallet) {
        upsertWalletInternal(wallet);
    }

    private void upsertWalletInternal(WalletEntity wallet) {
        if (wallet.getIconName().trim().isEmpty()) {
            wallet.setIconName("ic_wallet_default");
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            if (wallet.getId() == null || wallet.getId().trim().isEmpty()) {
                wallet.setId(UUID.randomUUID().toString());
            }
            if (wallet.getCreatedAt() <= 0L) {
                wallet.setCreatedAt(now);
            }
            wallet.setUpdatedAt(now);
            wallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
            walletDao.upsertLocal(wallet);
        });
    }

    public void softDelete(WalletEntity wallet) {
        long updatedAt = System.currentTimeMillis();
        wallet.setDeleted(true);
        wallet.setSyncStatus(SyncStatus.PENDING_DELETE);
        wallet.setUpdatedAt(updatedAt);
        AppDatabase.databaseWriteExecutor.execute(() ->
                walletDao.softDelete(wallet.getId(), updatedAt));
    }

    public void archive(WalletEntity wallet) {
        long updatedAt = System.currentTimeMillis();
        wallet.setArchived(true);
        wallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        wallet.setUpdatedAt(updatedAt);
        AppDatabase.databaseWriteExecutor.execute(() -> walletDao.archive(wallet.getId(), updatedAt));
    }

    public void restore(WalletEntity wallet) {
        long updatedAt = System.currentTimeMillis();
        wallet.setArchived(false);
        wallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        wallet.setUpdatedAt(updatedAt);
        AppDatabase.databaseWriteExecutor.execute(() -> walletDao.restore(wallet.getId(), updatedAt));
    }

    public WalletEntity getByIdSync(String id) {
        return walletDao.getByIdSync(id);
    }
}
