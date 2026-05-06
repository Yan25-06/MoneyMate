package com.group10.moneymate.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.WalletDao;
import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.models.SyncStatus;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository for wallet data.
 */
public class WalletRepository {

    public interface WriteCallback {
        void onSuccess();
        void onError(@NonNull Throwable throwable);
    }

    public interface OverviewSnapshotCallback {
        void onSuccess(@NonNull List<WalletWithBalance> wallets, double totalBalance);
        void onError(@NonNull Throwable throwable);
    }

    public static final class LocalWriteEvent {
        @NonNull
        private final String walletId;
        @NonNull
        private final String userId;

        private LocalWriteEvent(@NonNull String walletId, @NonNull String userId) {
            this.walletId = walletId;
            this.userId = userId;
        }

        @NonNull
        public String getWalletId() {
            return walletId;
        }

        @NonNull
        public String getUserId() {
            return userId;
        }
    }

    private final WalletDao walletDao;
    @Nullable
    private final AppDatabase appDatabase;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<LocalWriteEvent> localWriteEvents = new MutableLiveData<>();
    private static final AtomicLong LAST_WRITE_TIMESTAMP = new AtomicLong(0L);

    public WalletRepository(WalletDao walletDao) {
        this(null, walletDao);
    }

    public WalletRepository(@Nullable AppDatabase appDatabase, @NonNull WalletDao walletDao) {
        this.appDatabase = appDatabase;
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

    public LiveData<LocalWriteEvent> getLocalWriteEvents() {
        return localWriteEvents;
    }

    public void loadOverviewSnapshot(@NonNull String userId,
                                     @NonNull OverviewSnapshotCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<WalletWithBalance> snapshot = walletDao.getAllByUserWithBalanceSync(userId);
                double total = walletDao.getTotalBalanceSync(userId);
                mainHandler.post(() -> callback.onSuccess(snapshot, total));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
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
                long now = nextWriteTimestamp();
                if (writeWallet.getId() == null || writeWallet.getId().trim().isEmpty()) {
                    writeWallet.setId(UUID.randomUUID().toString());
                }
                if (writeWallet.getCreatedAt() <= 0L) {
                    writeWallet.setCreatedAt(now);
                }
                writeWallet.setUpdatedAt(now);
                writeWallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                walletDao.upsertLocal(writeWallet);
                refreshLocalObservers();
                notifyLocalWrite(writeWallet.getId(), writeWallet.getUserId());
                notifySuccess(callback);
            } catch (Exception exception) {
                notifyError(callback, exception);
            }
        });
    }

    private long nextWriteTimestamp() {
        while (true) {
            long previous = LAST_WRITE_TIMESTAMP.get();
            long candidate = Math.max(System.currentTimeMillis(), previous + 1L);
            if (LAST_WRITE_TIMESTAMP.compareAndSet(previous, candidate)) {
                return candidate;
            }
        }
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
        softDelete(wallet, null);
    }

    public void softDelete(WalletEntity wallet, @Nullable WriteCallback callback) {
        long updatedAt = nextWriteTimestamp();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                walletDao.softDelete(wallet.getId(), updatedAt);
                refreshLocalObservers();
                notifyLocalWrite(wallet.getId(), wallet.getUserId());
                notifySuccess(callback);
            } catch (Exception exception) {
                notifyError(callback, exception);
            }
        });
    }

    public void archive(WalletEntity wallet) {
        archive(wallet, null);
    }

    public void archive(WalletEntity wallet, @Nullable WriteCallback callback) {
        long updatedAt = nextWriteTimestamp();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                walletDao.archive(wallet.getId(), updatedAt);
                refreshLocalObservers();
                notifyLocalWrite(wallet.getId(), wallet.getUserId());
                notifySuccess(callback);
            } catch (Exception exception) {
                notifyError(callback, exception);
            }
        });
    }

    public void restore(WalletEntity wallet) {
        restore(wallet, null);
    }

    public void restore(WalletEntity wallet, @Nullable WriteCallback callback) {
        long updatedAt = nextWriteTimestamp();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                walletDao.restore(wallet.getId(), updatedAt);
                refreshLocalObservers();
                notifyLocalWrite(wallet.getId(), wallet.getUserId());
                notifySuccess(callback);
            } catch (Exception exception) {
                notifyError(callback, exception);
            }
        });
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

    @SuppressWarnings("deprecation")
    private void refreshLocalObservers() {
        if (appDatabase != null) {
            appDatabase.getInvalidationTracker().refreshVersionsSync();
        }
    }

    private void notifyLocalWrite(@NonNull String walletId, @Nullable String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }
        mainHandler.post(() -> localWriteEvents.setValue(new LocalWriteEvent(walletId, userId)));
    }
}
