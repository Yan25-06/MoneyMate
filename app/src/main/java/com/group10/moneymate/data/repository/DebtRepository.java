package com.group10.moneymate.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.DebtDao;
import com.group10.moneymate.data.local.dao.TransactionDao;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.models.DebtStatus;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.workers.SyncScheduler;

import java.util.List;
import java.util.UUID;

/**
 * Repository for debt data.
 * Mọi write operation chạy trên {@link AppDatabase#databaseWriteExecutor}.
 */
public class DebtRepository {

    public interface WriteCallback {
        void onSuccess();
        void onError(@NonNull Throwable throwable);
    }

    private final DebtDao debtDao;
    private final TransactionDao transactionDao;
    private final AppDatabase appDatabase;
    @Nullable
    private final SyncScheduler syncScheduler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public DebtRepository(@NonNull DebtDao debtDao) {
        this(debtDao, null, null, null);
    }

    public DebtRepository(@NonNull DebtDao debtDao,
                          @Nullable TransactionDao transactionDao,
                          @Nullable AppDatabase appDatabase,
                          @Nullable SyncScheduler syncScheduler) {
        this.debtDao = debtDao;
        this.transactionDao = transactionDao;
        this.appDatabase = appDatabase;
        this.syncScheduler = syncScheduler;
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    public LiveData<List<DebtEntity>> getAllDebts(String userId) {
        return debtDao.getAllDebts(userId);
    }

    public LiveData<List<DebtEntity>> getDebtsByType(String userId, String type) {
        return debtDao.getDebtsByType(userId, type);
    }

    public LiveData<List<DebtEntity>> getDebtsByTypeAndStatus(String userId, String type, String status) {
        return debtDao.getDebtsByTypeAndStatus(userId, type, status);
    }

    public LiveData<List<DebtEntity>> getOngoingDebtsByType(String userId, String type) {
        return debtDao.getOngoingDebtsByType(userId, type);
    }

    public LiveData<List<DebtEntity>> getDebtsByStatus(String userId, String status) {
        return debtDao.getDebtsByStatus(userId, status);
    }

    public LiveData<DebtEntity> getDebtById(String id) {
        return debtDao.getDebtById(id);
    }

    public LiveData<List<TransactionEntity>> getTransactionsByDebtId(String debtId) {
        if (transactionDao == null) {
            throw new IllegalStateException("TransactionDao not initialized");
        }
        return transactionDao.getTransactionsByDebtId(debtId);
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    public void insert(DebtEntity debt) {
        debt.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        debt.setUpdatedAt(System.currentTimeMillis());
        AppDatabase.databaseWriteExecutor.execute(() -> debtDao.insertDebt(debt));
    }

    public void update(DebtEntity debt) {
        debt.setUpdatedAt(System.currentTimeMillis());
        debt.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        AppDatabase.databaseWriteExecutor.execute(() -> debtDao.updateDebt(debt));
    }

    public void softDelete(DebtEntity debt) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                debtDao.softDelete(debt.getId(), System.currentTimeMillis()));
    }

    /**
     * Atomic: tạo DebtEntity + TransactionEntity trong cùng một transaction.
     * Dùng khi user chọn "Cho vay" hoặc "Đi vay".
     */
    public void createDebtWithTransaction(@NonNull DebtEntity debt,
                                           @NonNull TransactionEntity transaction,
                                           @Nullable WriteCallback callback) {
        if (appDatabase == null || transactionDao == null) {
            notifyWriteError(callback, new IllegalStateException("Database not initialized for debt operations"));
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                long now = System.currentTimeMillis();

                if (debt.getId() == null || debt.getId().trim().isEmpty()) {
                    debt.setId(UUID.randomUUID().toString());
                }
                debt.setRemainingAmount(debt.getAmount());
                debt.setStatus(DebtStatus.ACTIVE.name());
                debt.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                debt.setUpdatedAt(now);
                if (debt.getCreatedAt() <= 0L) {
                    debt.setCreatedAt(now);
                }
                debt.setDeleted(false);

                if (transaction.getId() == null || transaction.getId().trim().isEmpty()) {
                    transaction.setId(UUID.randomUUID().toString());
                }
                
                if (transaction.getCategoryId() == null || transaction.getCategoryId().trim().isEmpty()) {
                    transaction.setCategoryId(resolveDebtCategoryId(debt.getType()));
                }
                
                transaction.setDebtId(debt.getId());
                transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                transaction.setUpdatedAt(now);
                if (transaction.getCreatedAt() <= 0L) {
                    transaction.setCreatedAt(now);
                }
                transaction.setDeleted(false);

                appDatabase.runInTransaction(() -> {
                    debtDao.upsertLocal(debt);
                    transactionDao.upsertLocal(transaction);
                });

                scheduleSyncIfEnabled();
                notifyWriteSuccess(callback);
            } catch (Exception exception) {
                notifyWriteError(callback, exception);
            }
        });
    }

    /**
     * Atomic: tạo transaction trả nợ/thu nợ + cập nhật remaining_amount trong debt.
     * Dùng khi user bấm "Trả lại" hoặc "Thu nợ" từ DebtDetail.
     *
     * @param debtId   ID của khoản nợ
     * @param amount   Số tiền trả/thu (phải > 0 và <= remaining_amount)
     * @param transaction TransactionEntity đã được prepare (userId, walletId, type, timestamp, etc.)
     */
    public void createCashbackTransaction(@NonNull String debtId,
                                           double amount,
                                           @NonNull TransactionEntity transaction,
                                           @Nullable WriteCallback callback) {
        if (appDatabase == null || transactionDao == null) {
            notifyWriteError(callback, new IllegalStateException("Database not initialized for debt operations"));
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                DebtEntity debt = debtDao.getByIdSync(debtId);
                if (debt == null) {
                    notifyWriteError(callback, new IllegalArgumentException("Debt not found: " + debtId));
                    return;
                }

                if (amount <= 0 || amount > debt.getRemainingAmount()) {
                    notifyWriteError(callback, new IllegalArgumentException(
                            "Invalid amount: " + amount + ", remaining: " + debt.getRemainingAmount()));
                    return;
                }

                long now = System.currentTimeMillis();
                double newRemaining = debt.getRemainingAmount() - amount;
                String newStatus = newRemaining <= 0
                        ? DebtStatus.SETTLED.name()
                        : DebtStatus.ACTIVE.name();

                if (transaction.getId() == null || transaction.getId().trim().isEmpty()) {
                    transaction.setId(UUID.randomUUID().toString());
                }
                
                if (transaction.getCategoryId() == null || transaction.getCategoryId().trim().isEmpty()) {
                    String actionType = "LEND".equals(debt.getType()) ? "DEBT_COLLECTION" : "REPAYMENT";
                    transaction.setCategoryId(resolveDebtCategoryId(actionType));
                }
                
                transaction.setDebtId(debtId);
                transaction.setAmount(amount);
                transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                transaction.setUpdatedAt(now);
                if (transaction.getCreatedAt() <= 0L) {
                    transaction.setCreatedAt(now);
                }
                transaction.setDeleted(false);

                appDatabase.runInTransaction(() -> {
                    transactionDao.upsertLocal(transaction);
                    debtDao.updateRemainingAmount(debtId, newRemaining, newStatus, now);
                });

                scheduleSyncIfEnabled();
                notifyWriteSuccess(callback);
            } catch (Exception exception) {
                notifyWriteError(callback, exception);
            }
        });
    }

    /**
     * Atomic: soft-delete tất cả transactions liên kết + soft-delete debt.
     */
    public void deleteDebtWithTransactions(@NonNull DebtEntity debt,
                                            @Nullable WriteCallback callback) {
        if (appDatabase == null || transactionDao == null) {
            notifyWriteError(callback, new IllegalStateException("Database not initialized for debt operations"));
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                long now = System.currentTimeMillis();
                appDatabase.runInTransaction(() -> {
                    transactionDao.softDeleteByDebtId(debt.getId(), now);
                    debtDao.softDelete(debt.getId(), now);
                });
                scheduleSyncIfEnabled();
                notifyWriteSuccess(callback);
            } catch (Exception exception) {
                notifyWriteError(callback, exception);
            }
        });
    }

    /**
     * Cập nhật thông tin khoản nợ (personName, amount, dueDate, note).
     * Nếu amount mới < số tiền đã trả → set remaining = 0, status = SETTLED.
     */
    public void updateDebtDetails(@NonNull DebtEntity debt,
                                   @Nullable WriteCallback callback) {
        if (appDatabase == null || transactionDao == null) {
            notifyWriteError(callback, new IllegalStateException("Database not initialized for debt operations"));
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                DebtEntity existing = debtDao.getByIdSync(debt.getId());
                if (existing == null) {
                    notifyWriteError(callback, new IllegalArgumentException("Debt not found: " + debt.getId()));
                    return;
                }

                // Recalc paid amount from active transactions
                List<TransactionEntity> transactions = transactionDao.getTransactionsByDebtIdSync(debt.getId());
                double paid = 0;
                if (transactions != null) {
                    // Skip the first creation transaction (the one with oldest timestamp)
                    // All other transactions are cashback transactions
                    TransactionEntity oldestTransaction = null;
                    for (TransactionEntity t : transactions) {
                        if (oldestTransaction == null || t.getTimestamp() < oldestTransaction.getTimestamp()) {
                            oldestTransaction = t;
                        }
                    }
                    for (TransactionEntity t : transactions) {
                        if (oldestTransaction != null && t.getId().equals(oldestTransaction.getId())) {
                            continue; // Skip creation transaction
                        }
                        paid += t.getAmount();
                    }
                }

                double newAmount = debt.getAmount();
                double newRemaining = Math.max(0, newAmount - paid);
                String newStatus = newRemaining <= 0
                        ? DebtStatus.SETTLED.name()
                        : DebtStatus.ACTIVE.name();

                long now = System.currentTimeMillis();
                debt.setRemainingAmount(newRemaining);
                debt.setStatus(newStatus);
                debt.setUpdatedAt(now);
                debt.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                // Preserve fields from existing entity
                debt.setType(existing.getType());
                debt.setUserId(existing.getUserId());
                debt.setCreatedAt(existing.getCreatedAt());
                debt.setDeleted(false);

                appDatabase.runInTransaction(() -> debtDao.upsertLocal(debt));

                scheduleSyncIfEnabled();
                notifyWriteSuccess(callback);
            } catch (Exception exception) {
                notifyWriteError(callback, exception);
            }
        });
    }

    /**
     * Recalc debt remaining_amount và status sau khi xóa 1 transaction thuộc debt.
     * Gọi SAU KHI transaction đã bị soft-delete.
     */
    public void recalcDebtAfterTransactionDelete(@NonNull String debtId,
                                                  @Nullable WriteCallback callback) {
        if (appDatabase == null || transactionDao == null) {
            notifyWriteError(callback, new IllegalStateException("Database not initialized for debt operations"));
            return;
        }
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                DebtEntity debt = debtDao.getByIdSync(debtId);
                if (debt == null) {
                    // Debt may have been deleted already — silently succeed
                    notifyWriteSuccess(callback);
                    return;
                }

                List<TransactionEntity> transactions = transactionDao.getTransactionsByDebtIdSync(debtId);
                double paid = 0;
                if (transactions != null) {
                    TransactionEntity oldestTransaction = null;
                    for (TransactionEntity t : transactions) {
                        if (oldestTransaction == null || t.getTimestamp() < oldestTransaction.getTimestamp()) {
                            oldestTransaction = t;
                        }
                    }
                    for (TransactionEntity t : transactions) {
                        if (oldestTransaction != null && t.getId().equals(oldestTransaction.getId())) {
                            continue;
                        }
                        paid += t.getAmount();
                    }
                }

                double newRemaining = Math.max(0, debt.getAmount() - paid);
                String newStatus = newRemaining <= 0
                        ? DebtStatus.SETTLED.name()
                        : DebtStatus.ACTIVE.name();

                long now = System.currentTimeMillis();
                debtDao.updateRemainingAmount(debtId, newRemaining, newStatus, now);

                scheduleSyncIfEnabled();
                notifyWriteSuccess(callback);
            } catch (Exception exception) {
                notifyWriteError(callback, exception);
            }
        });
    }

    /**
     * Xử lý xóa một transaction thuộc debt.
     * - Nếu transaction đó là "giao dịch gốc" (timestamp nhỏ nhất trong debt) → xóa cả debt + tất cả transactions.
     * - Nếu không → soft-delete transaction + recalc remaining/status của debt.
     *
     * Tất cả chạy atomic trên databaseWriteExecutor.
     *
     * @param isOriginalCallback Callback với boolean = true nếu là giao dịch gốc (để UI navigate back 2 cấp).
     */
    public void handleDebtTransactionDelete(@NonNull TransactionEntity transaction,
                                             @Nullable HandleDebtTransactionDeleteCallback callback) {
        if (appDatabase == null || transactionDao == null) {
            if (callback != null) {
                mainHandler.post(() -> callback.onError(new IllegalStateException("Database not initialized")));
            }
            return;
        }
        String debtId = transaction.getDebtId();
        String transactionId = transaction.getId();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                long now = System.currentTimeMillis();

                if (debtId == null || debtId.isEmpty()) {
                    // Không thuộc debt — xóa bình thường
                    transactionDao.softDelete(transactionId, now);
                    scheduleSyncIfEnabled();
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(false));
                    return;
                }

                // Lấy tất cả transactions còn active của debt để detect giao dịch gốc
                List<TransactionEntity> allDebtTxs = transactionDao.getTransactionsByDebtIdSync(debtId);
                boolean isOriginalTransaction = isCreationTransaction(transactionId, allDebtTxs);

                if (isOriginalTransaction) {
                    // Đây là giao dịch tạo nợ gốc → xóa cả debt + tất cả transactions liên quan
                    appDatabase.runInTransaction(() -> {
                        transactionDao.softDeleteByDebtId(debtId, now);
                        debtDao.softDelete(debtId, now);
                    });
                    scheduleSyncIfEnabled();
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(true));
                } else {
                    // Giao dịch trả nợ/thu nợ → xóa và recalc debt
                    appDatabase.runInTransaction(() -> {
                        transactionDao.softDelete(transactionId, now);

                        // Recalc sau khi đã soft-delete (query lại sẽ không thấy tx vừa xóa)
                        List<TransactionEntity> remaining = transactionDao.getTransactionsByDebtIdSync(debtId);
                        DebtEntity debt = debtDao.getByIdSync(debtId);
                        if (debt != null) {
                            double paid = calcPaidAmount(remaining);
                            double newRemaining = Math.max(0, debt.getAmount() - paid);
                            String newStatus = newRemaining <= 0
                                    ? DebtStatus.SETTLED.name()
                                    : DebtStatus.ACTIVE.name();
                            debtDao.updateRemainingAmount(debtId, newRemaining, newStatus, now);
                        }
                    });
                    scheduleSyncIfEnabled();
                    if (callback != null) mainHandler.post(() -> callback.onSuccess(false));
                }
            } catch (Exception exception) {
                if (callback != null) mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public interface HandleDebtTransactionDeleteCallback {
        /** @param wasOriginalTransaction true nếu vừa xóa giao dịch gốc và debt đã bị xóa cùng */
        void onSuccess(boolean wasOriginalTransaction);
        void onError(@NonNull Throwable throwable);
    }

    /**
     * Kiểm tra xem transactionId có phải là "giao dịch gốc" (creation transaction) của debt không.
     * Giao dịch gốc = transaction có timestamp nhỏ nhất trong danh sách.
     */
    private boolean isCreationTransaction(@NonNull String transactionId,
                                           @Nullable List<TransactionEntity> allDebtTxs) {
        if (allDebtTxs == null || allDebtTxs.isEmpty()) return false;
        TransactionEntity oldest = null;
        for (TransactionEntity t : allDebtTxs) {
            if (oldest == null || t.getTimestamp() < oldest.getTimestamp()) {
                oldest = t;
            }
        }
        return oldest != null && oldest.getId().equals(transactionId);
    }

    /**
     * Tính tổng số tiền đã trả/thu (bỏ qua giao dịch gốc — có timestamp nhỏ nhất).
     */
    private double calcPaidAmount(@Nullable List<TransactionEntity> transactions) {
        if (transactions == null || transactions.isEmpty()) return 0;
        TransactionEntity oldest = null;
        for (TransactionEntity t : transactions) {
            if (oldest == null || t.getTimestamp() < oldest.getTimestamp()) {
                oldest = t;
            }
        }
        double paid = 0;
        for (TransactionEntity t : transactions) {
            if (oldest != null && t.getId().equals(oldest.getId())) continue;
            paid += t.getAmount();
        }
        return paid;
    }

    // ─── Sync ─────────────────────────────────────────────────────────────────

    public List<DebtEntity> getPendingSyncPagedSince(String userId,
                                                          long lastSyncedAt,
                                                          String lastSyncedId,
                                                          int limit,
                                                          int offset) {
        return debtDao.getPendingSyncPagedSince(userId, lastSyncedAt, lastSyncedId, limit, offset);
    }

    public void markSynced(String id) {
        debtDao.markSynced(id);
    }

    public void hardDeleteById(String id) {
        AppDatabase.databaseWriteExecutor.execute(() -> debtDao.hardDeleteById(id));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

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

    @Nullable
    private String resolveDebtCategoryId(@NonNull String debtTypeStr) {
        if (appDatabase == null) return null;
        String name = null;
        String type = null;
        if ("LEND".equals(debtTypeStr)) {
            name = Constants.CATEGORY_NAME_LEND;
            type = Constants.TYPE_EXPENSE;
        } else if ("BORROW".equals(debtTypeStr)) {
            name = Constants.CATEGORY_NAME_BORROW;
            type = Constants.TYPE_INCOME;
        } else if ("DEBT_COLLECTION".equals(debtTypeStr)) {
            name = Constants.CATEGORY_NAME_COLLECTION;
            type = Constants.TYPE_INCOME;
        } else if ("REPAYMENT".equals(debtTypeStr)) {
            name = Constants.CATEGORY_NAME_REPAYMENT;
            type = Constants.TYPE_EXPENSE;
        }
        if (name != null && type != null) {
            CategoryEntity cat = appDatabase.categoryDao().getCategoryByNameAndTypeSync(name, type);
            if (cat != null) return cat.getId();
        }
        return null;
    }
}