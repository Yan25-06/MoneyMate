package com.group10.moneymate.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.TransactionDao;
import com.group10.moneymate.data.local.dao.WalletDao;
import com.group10.moneymate.data.local.dto.CategorySumDTO;
import com.group10.moneymate.data.local.dto.DailyTrendDTO;
import com.group10.moneymate.data.local.dto.NetIncomeDTO;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.workers.SyncScheduler;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository for transaction data.
 * Mọi write operation chạy trên {@link AppDatabase#databaseWriteExecutor}.
 */
public class TransactionRepository {

    public interface PageCallback<T> {
        void onSuccess(T data);
        void onError(Exception exception);
    }

    public interface WriteCallback {
        void onSuccess();
        void onError(@NonNull Throwable throwable);
    }

    private final TransactionDao transactionDao;
    private final WalletDao walletDao;
    @Nullable
    private final SyncScheduler syncScheduler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // Keep updated_at strictly increasing so MAX(updated_at)-based invalidation
    // cannot miss rapid consecutive writes that land in the same millisecond.
    private static final AtomicLong LAST_WRITE_TIMESTAMP = new AtomicLong(0L);

    public TransactionRepository(TransactionDao transactionDao, WalletDao walletDao) {
        this(transactionDao, walletDao, null);
    }

    public TransactionRepository(TransactionDao transactionDao,
                                 WalletDao walletDao,
                                 @Nullable SyncScheduler syncScheduler) {
        this.transactionDao = transactionDao;
        this.walletDao = walletDao;
        this.syncScheduler = syncScheduler;
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    public LiveData<List<TransactionEntity>> getAllTransactions(String userId) {
        return transactionDao.getAllTransactions(userId);
    }

    public LiveData<Long> getTransactionInvalidationKey(String userId) {
        return transactionDao.getTransactionInvalidationKey(userId);
    }

    public LiveData<List<TransactionEntity>> getRecentTransactions(String userId, int limit) {
        return transactionDao.getRecentTransactions(userId, limit);
    }

    public LiveData<TransactionEntity> getTransactionById(String id) {
        return transactionDao.getTransactionById(id);
    }

    public LiveData<List<TransactionEntity>> getTransactionsByDateRange(String userId, long startDate, long endDate) {
        return transactionDao.getTransactionsByDateRange(userId, startDate, endDate);
    }

    public LiveData<List<TransactionEntity>> getTransactionsByType(String userId, String type) {
        return transactionDao.getTransactionsByType(userId, type);
    }

    public LiveData<List<TransactionEntity>> getTransactionsByCategory(String userId, String categoryId) {
        return transactionDao.getTransactionsByCategory(userId, categoryId);
    }

    public LiveData<List<TransactionEntity>> getTransactionsForBudget(String userId,
                                                                      String categoryId,
                                                                      String walletId,
                                                                      long startDate,
                                                                      long endDate) {
        if (Constants.isOtherCategoryId(categoryId)) {
            return transactionDao.getTransactionsForOtherCategories(
                    userId,
                    startDate,
                    endDate,
                    walletId,
                    Constants.CATEGORY_ID_OTHER,
                    Constants.CATEGORY_ID_OTHER_LEGACY
            );
        }
        return transactionDao.getTransactionsForBudget(userId, categoryId, walletId, startDate, endDate);
    }

    public LiveData<List<TransactionEntity>> getExpenseTransactionsByRange(String userId,
                                                                           long startDate,
                                                                           long endDate) {
        return transactionDao.getExpenseTransactionsByRange(userId, startDate, endDate);
    }

    public LiveData<List<TransactionEntity>> getTransactionsByWallet(String userId, String walletId) {
        return transactionDao.getTransactionsByWallet(userId, walletId);
    }

    public LiveData<List<TransactionEntity>> searchTransactions(String userId, String keyword) {
        return transactionDao.searchTransactions(userId, keyword);
    }

    public void getFirstTransactionsPage(String userId,
                                         int limit,
                                         @NonNull PageCallback<List<TransactionEntity>> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<TransactionEntity> page = transactionDao.getFirstTransactionsPageSync(userId, limit);
                mainHandler.post(() -> callback.onSuccess(page));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void getTransactionsPageByCursor(String userId,
                                            int limit,
                                            long lastTimestamp,
                                            @NonNull String lastId,
                                            @NonNull PageCallback<List<TransactionEntity>> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<TransactionEntity> page = transactionDao.getTransactionsPagedByCursorSync(
                        userId,
                        lastTimestamp,
                        lastId,
                        limit
                );
                mainHandler.post(() -> callback.onSuccess(page));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public LiveData<Double> getTotalIncome(String userId, long startDate, long endDate) {
        return transactionDao.getTotalIncome(userId, startDate, endDate);
    }

    public LiveData<Double> getTotalExpense(String userId, long startDate, long endDate) {
        return transactionDao.getTotalExpense(userId, startDate, endDate);
    }

    public LiveData<Double> getTotalIncomeFiltered(String userId,
                                                   long startDate,
                                                   long endDate,
                                                   @Nullable String walletId) {
        return transactionDao.getTotalIncomeFiltered(userId, startDate, endDate, walletId);
    }

    public LiveData<Double> getTotalExpenseFiltered(String userId,
                                                    long startDate,
                                                    long endDate,
                                                    @Nullable String walletId) {
        return transactionDao.getTotalExpenseFiltered(userId, startDate, endDate, walletId);
    }

    public LiveData<Double> getTotalAmountByCategoryFiltered(String userId,
                                                             String type,
                                                             String categoryId,
                                                             long startDate,
                                                             long endDate,
                                                             @Nullable String walletId) {
        return transactionDao.getTotalAmountByCategoryFiltered(
                userId,
                type,
                categoryId,
                startDate,
                endDate,
                walletId
        );
    }

    public LiveData<NetIncomeDTO> getNetIncomeSummary(String userId,
                                                      long startDate,
                                                      long endDate,
                                                      @Nullable String walletId,
                                                      String periodLabel) {
        return transactionDao.getNetIncomeSummary(userId, startDate, endDate, walletId, periodLabel);
    }

    public LiveData<List<NetIncomeDTO>> getNetIncomeTrend(String userId,
                                                          long startDate,
                                                          long endDate,
                                                          @Nullable String walletId,
                                                          String periodFormat) {
        return transactionDao.getNetIncomeTrend(userId, startDate, endDate, walletId, periodFormat);
    }

    public LiveData<List<CategorySumDTO>> getCategorySums(String userId,
                                                          String type,
                                                          long startDate,
                                                          long endDate,
                                                          @Nullable String walletId) {
        return transactionDao.getCategorySums(userId, type, startDate, endDate, walletId);
    }

    public LiveData<List<CategorySumDTO>> getRootCategorySums(String userId,
                                                              String type,
                                                              long startDate,
                                                              long endDate,
                                                              @Nullable String walletId) {
        return transactionDao.getRootCategorySums(userId, type, startDate, endDate, walletId);
    }

    public LiveData<List<CategorySumDTO>> getChildCategorySums(String userId,
                                                               String type,
                                                               long startDate,
                                                               long endDate,
                                                               @Nullable String walletId,
                                                               @NonNull String parentCategoryId) {
        return transactionDao.getChildCategorySums(
                userId,
                type,
                startDate,
                endDate,
                walletId,
                parentCategoryId
        );
    }

    public LiveData<List<CategorySumDTO>> getCategoryBranchSums(String userId,
                                                                String type,
                                                                long startDate,
                                                                long endDate,
                                                                @Nullable String walletId,
                                                                @NonNull String parentCategoryId) {
        return transactionDao.getCategoryBranchSums(
                userId,
                type,
                startDate,
                endDate,
                walletId,
                parentCategoryId
        );
    }

    public LiveData<Double> getParentCategoryBranchTotalAmount(String userId,
                                                               String type,
                                                               @NonNull String parentCategoryId,
                                                               long startDate,
                                                               long endDate,
                                                               @Nullable String walletId) {
        return transactionDao.getParentCategoryBranchTotalAmount(
                userId,
                type,
                parentCategoryId,
                startDate,
                endDate,
                walletId
        );
    }

    public LiveData<List<TransactionEntity>> getTransactionsForStatisticsDrillDown(String userId,
                                                                                   String type,
                                                                                   long startDate,
                                                                                   long endDate,
                                                                                   @Nullable String walletId,
                                                                                   @NonNull String categoryId) {
        return transactionDao.getTransactionsForStatisticsDrillDown(
                userId,
                type,
                startDate,
                endDate,
                walletId,
                categoryId
        );
    }

    public LiveData<List<DailyTrendDTO>> getAmountTrend(String userId,
                                                        String type,
                                                        long startDate,
                                                        long endDate,
                                                        @Nullable String walletId,
                                                        String periodFormat) {
        return transactionDao.getAmountTrend(userId, type, startDate, endDate, walletId, periodFormat);
    }

    public LiveData<List<DailyTrendDTO>> getCategoryAmountTrend(String userId,
                                                                String type,
                                                                String categoryId,
                                                                long startDate,
                                                                long endDate,
                                                                @Nullable String walletId,
                                                                String periodFormat) {
        return transactionDao.getCategoryAmountTrend(
                userId,
                type,
                categoryId,
                startDate,
                endDate,
                walletId,
                periodFormat
        );
    }

    public LiveData<List<DailyTrendDTO>> getParentCategoryBranchAmountTrend(String userId,
                                                                            String type,
                                                                            @NonNull String parentCategoryId,
                                                                            long startDate,
                                                                            long endDate,
                                                                            @Nullable String walletId,
                                                                            String periodFormat) {
        return transactionDao.getParentCategoryBranchAmountTrend(
                userId,
                type,
                parentCategoryId,
                startDate,
                endDate,
                walletId,
                periodFormat
        );
    }

    public LiveData<Double> getTotalExpenseByCategory(String userId,
                                                      @Nullable String categoryId,
                                                      @Nullable String walletId,
                                                      long startDate,
                                                      long endDate) {
        if (Constants.isOtherCategoryId(categoryId)) {
            return transactionDao.getSpentForOtherCategories(
                    userId,
                    startDate,
                    endDate,
                    walletId,
                    Constants.CATEGORY_ID_OTHER,
                    Constants.CATEGORY_ID_OTHER_LEGACY
            );
        }
        return transactionDao.getTotalExpenseByCategory(userId, categoryId, walletId, startDate, endDate);
    }

    public List<TransactionEntity> getPendingSyncSince(@NonNull String userId,
                                                       long lastSyncedAt,
                                                       @NonNull String lastSyncedId,
                                                       int limit) {
        return transactionDao.getPendingSyncSince(userId, lastSyncedAt, lastSyncedId, limit);
    }

    public List<TransactionEntity> getPendingSyncPagedSince(@NonNull String userId,
                                                            long lastSyncedAt,
                                                            @NonNull String lastSyncedId,
                                                            int limit,
                                                            int offset) {
        return transactionDao.getPendingSyncTransactionsPagedSince(
                userId,
                lastSyncedAt,
                lastSyncedId,
                limit,
                offset
        );
    }

    public void markSynced(@NonNull String id) {
        transactionDao.markSynced(id);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void hardDeleteById(@NonNull String id) {
        transactionDao.hardDeleteById(id);
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    public void insertTransaction(TransactionEntity transaction) {
        upsertTransactionInternal(transaction, null);
    }

    public void insertTransaction(TransactionEntity transaction, @Nullable WriteCallback callback) {
        upsertTransactionInternal(transaction, callback);
    }

    public void updateTransaction(TransactionEntity newTransaction) {
        upsertTransactionInternal(newTransaction, null);
    }

    public void updateTransaction(TransactionEntity newTransaction, @Nullable WriteCallback callback) {
        upsertTransactionInternal(newTransaction, callback);
    }

    private void upsertTransactionInternal(TransactionEntity transaction, @Nullable WriteCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                long now = nextWriteTimestamp();
                if (transaction.getId() == null || transaction.getId().trim().isEmpty()) {
                    transaction.setId(UUID.randomUUID().toString());
                }
                if (transaction.getCreatedAt() <= 0L) {
                    transaction.setCreatedAt(now);
                }
                transaction.setUpdatedAt(now);
                transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                transactionDao.upsertLocal(transaction);
                scheduleSyncIfEnabled();
                notifyWriteSuccess(callback);
            } catch (Exception exception) {
                notifyWriteError(callback, exception);
            }
        });
    }

    public void softDeleteTransaction(TransactionEntity transaction) {
        softDeleteTransaction(transaction, null);
    }

    public void softDeleteTransaction(TransactionEntity transaction, @Nullable WriteCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                transactionDao.softDelete(transaction.getId(), nextWriteTimestamp());
                scheduleSyncIfEnabled();
                notifyWriteSuccess(callback);
            } catch (Exception exception) {
                notifyWriteError(callback, exception);
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

    private void notifyWriteSuccess(@Nullable WriteCallback callback) {
        if (callback == null) {
            return;
        }
        mainHandler.post(callback::onSuccess);
    }

    private void notifyWriteError(@Nullable WriteCallback callback, @NonNull Throwable throwable) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onError(throwable));
    }

    private void scheduleSyncIfEnabled() {
        if (syncScheduler != null) {
            syncScheduler.scheduleOneTimeSyncDebounced();
        }
    }
}
