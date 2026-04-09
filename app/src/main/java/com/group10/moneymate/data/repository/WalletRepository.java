package com.group10.moneymate.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
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

    public interface WriteCallback {
        void onSuccess();
        void onError(@NonNull Throwable throwable);
    }

    private final WalletDao walletDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

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
        insertWalletInternal(wallet, null);
    }

    public void insert(WalletEntity wallet, @Nullable WriteCallback callback) {
        insertWalletInternal(wallet, callback);
    }

    public void update(WalletEntity wallet) {
        insertWalletInternal(wallet, null);
    }

    public void update(WalletEntity wallet, @Nullable WriteCallback callback) {
        insertWalletInternal(wallet, callback);
    }

    private void insertWalletInternal(WalletEntity wallet, @Nullable WriteCallback callback) {
        WalletEntity writeWallet = copyWallet(wallet);
        if (writeWallet.getIconName().trim().isEmpty()) {
            writeWallet.setIconName("ic_wallet_default");
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                if (writeWallet.getId() == null || writeWallet.getId().trim().isEmpty()) {
                    writeWallet.setId(UUID.randomUUID().toString());
                }
                if (writeWallet.getCreatedAt() <= 0L) {
                    writeWallet.setCreatedAt(now);
                }
                writeWallet.setUpdatedAt(now);
                writeWallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                walletDao.upsertLocal(writeWallet);
                notifySuccess(callback);
            } catch (Exception exception) {
                notifyError(callback, exception);
            }
        });
    }

    @NonNull
    private WalletEntity copyWallet(@NonNull WalletEntity source) {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(source.getId());
        wallet.setUserId(source.getUserId());
        wallet.setName(source.getName());
        wallet.setBalance(source.getBalance());
        wallet.setType(source.getType());
        wallet.setIconName(source.getIconName());
        wallet.setArchived(source.isArchived());
        wallet.setExcluded(source.isExcluded());
        wallet.setUpdatedAt(source.getUpdatedAt());
        wallet.setSyncStatus(source.getSyncStatus());
        wallet.setDeleted(source.isDeleted());
        wallet.setCreatedAt(source.getCreatedAt());
        return wallet;
    }

    private void notifySuccess(@Nullable WriteCallback callback) {
        if (callback == null) {
            return;
        }
        mainHandler.post(callback::onSuccess);
    }

    private void notifyError(@Nullable WriteCallback callback, @NonNull Throwable throwable) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onError(throwable));
    }

    public void softDelete(WalletEntity wallet) {
        long updatedAt = System.currentTimeMillis();
        AppDatabase.databaseWriteExecutor.execute(() ->
                walletDao.softDelete(wallet.getId(), updatedAt));
    }

    public void archive(WalletEntity wallet) {
        long updatedAt = System.currentTimeMillis();
        AppDatabase.databaseWriteExecutor.execute(() -> walletDao.archive(wallet.getId(), updatedAt));
    }

    public void restore(WalletEntity wallet) {
        long updatedAt = System.currentTimeMillis();
        AppDatabase.databaseWriteExecutor.execute(() -> walletDao.restore(wallet.getId(), updatedAt));
    }

    public List<WalletEntity> getPendingSyncPagedSince(@NonNull String userId,
                                                       long lastSyncedAt,
                                                       @NonNull String lastSyncedId,
                                                       int limit,
                                                       int offset) {
        return walletDao.getPendingSyncWalletsPagedSince(userId, lastSyncedAt, lastSyncedId, limit, offset);
    }

    public void markSynced(@NonNull String id) {
        walletDao.markSynced(id);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void hardDeleteById(@NonNull String id) {
        walletDao.hardDeleteById(id);
    }

    public WalletEntity getByIdSync(String id) {
        return walletDao.getByIdSync(id);
    }
}
