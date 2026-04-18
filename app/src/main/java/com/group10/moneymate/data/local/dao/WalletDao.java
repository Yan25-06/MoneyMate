package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.annotation.RestrictTo;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.Transaction;

import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.WalletEntity;
import java.util.List;

@Dao
public abstract class WalletDao {
    @Query("INSERT INTO wallets ("
            + "id, user_id, name, balance, type, icon_name, is_archived, is_excluded, "
            + "updated_at, sync_status, is_deleted, created_at"
            + ") VALUES (:id, :userId, :name, :balance, :type, :iconName, :isArchived, :isExcluded, :updatedAt, :syncStatus, :isDeleted, :createdAt) "
            + "ON CONFLICT(id) DO UPDATE SET "
            + "user_id = excluded.user_id, "
            + "name = excluded.name, "
            + "balance = excluded.balance, "
            + "type = excluded.type, "
            + "icon_name = excluded.icon_name, "
            + "is_archived = excluded.is_archived, "
            + "is_excluded = excluded.is_excluded, "
            + "updated_at = excluded.updated_at, "
            + "sync_status = CASE WHEN wallets.sync_status = 2 THEN 2 ELSE excluded.sync_status END, "
            + "is_deleted = CASE WHEN wallets.is_deleted = 1 THEN 1 ELSE excluded.is_deleted END, "
            + "created_at = CASE WHEN wallets.created_at IS NULL OR wallets.created_at <= 0 THEN excluded.created_at ELSE wallets.created_at END")
    protected abstract void upsertLocalInternal(String id,
                                                String userId,
                                                String name,
                                                double balance,
                                                String type,
                                                String iconName,
                                                boolean isArchived,
                                                boolean isExcluded,
                                                long updatedAt,
                                                int syncStatus,
                                                boolean isDeleted,
                                                long createdAt);

    public void upsertLocal(WalletEntity wallet) {
        upsertLocalInternal(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getName(),
                wallet.getBalance(),
                wallet.getType(),
                wallet.getIconName(),
                wallet.isArchived(),
                wallet.isExcluded(),
                wallet.getUpdatedAt(),
                wallet.getSyncStatus(),
                wallet.isDeleted(),
                wallet.getCreatedAt()
        );
    }

    public void insert(WalletEntity wallet) {
        upsertLocal(wallet);
    }

    @Query("UPDATE wallets SET " +
            "name = :name, " +
            "balance = :balance, " +
            "type = :type, " +
            "icon_name = :iconName, " +
            "is_excluded = :isExcluded, " +
            "updated_at = :updatedAt, " +
            "sync_status = :syncStatus " +
            "WHERE id = :id")
    public abstract void updateEditableFieldsById(String id,
                                                  String name,
                                                  double balance,
                                                  String type,
                                                  String iconName,
                                                  boolean isExcluded,
                                                  long updatedAt,
                                                  int syncStatus);

    @Query("SELECT * FROM wallets WHERE user_id = :userId AND is_deleted = 0 ORDER BY is_archived ASC, created_at ASC")
    public abstract LiveData<List<WalletEntity>> getAllByUser(String userId);

    @Query("SELECT w.*, " +
            "COALESCE(w.balance, 0) + " +
            "COALESCE((SELECT SUM(CASE " +
            "WHEN t.type = 'INCOME' THEN t.amount " +
            "WHEN t.type = 'EXPENSE' THEN -t.amount " +
            "WHEN t.type = 'TRANSFER' THEN -t.amount " +
            "ELSE 0 END) " +
            "FROM transactions t " +
            "WHERE t.wallet_id = w.id AND t.is_deleted = 0), 0) + " +
            "COALESCE((SELECT SUM(t.amount) " +
            "FROM transactions t " +
            "WHERE t.to_wallet_id = w.id AND t.type = 'TRANSFER' AND t.is_deleted = 0), 0) " +
            "AS current_balance " +
            "FROM wallets w " +
            "WHERE w.user_id = :userId AND w.is_deleted = 0 " +
            "ORDER BY w.is_archived ASC, w.created_at ASC")
    public abstract LiveData<List<WalletWithBalance>> getAllByUserWithBalance(String userId);

    @Query("SELECT * FROM wallets WHERE user_id = :userId AND is_deleted = 0 AND is_archived = 0 ORDER BY created_at ASC")
    public abstract LiveData<List<WalletEntity>> getActiveByUser(String userId);

    @Query("SELECT w.*, " +
            "COALESCE(w.balance, 0) + " +
            "COALESCE((SELECT SUM(CASE " +
            "WHEN t.type = 'INCOME' THEN t.amount " +
            "WHEN t.type = 'EXPENSE' THEN -t.amount " +
            "WHEN t.type = 'TRANSFER' THEN -t.amount " +
            "ELSE 0 END) " +
            "FROM transactions t " +
            "WHERE t.wallet_id = w.id AND t.is_deleted = 0), 0) + " +
            "COALESCE((SELECT SUM(t.amount) " +
            "FROM transactions t " +
            "WHERE t.to_wallet_id = w.id AND t.type = 'TRANSFER' AND t.is_deleted = 0), 0) " +
            "AS current_balance " +
            "FROM wallets w " +
            "WHERE w.user_id = :userId AND w.is_deleted = 0 AND w.is_archived = 0 " +
            "ORDER BY w.created_at ASC")
    public abstract LiveData<List<WalletWithBalance>> getActiveByUserWithBalance(String userId);

    @Query("SELECT * FROM wallets WHERE id = :id AND is_deleted = 0")
    public abstract LiveData<WalletEntity> getById(String id);

    @Query("SELECT w.*, " +
            "COALESCE(w.balance, 0) + " +
            "COALESCE((SELECT SUM(CASE " +
            "WHEN t.type = 'INCOME' THEN t.amount " +
            "WHEN t.type = 'EXPENSE' THEN -t.amount " +
            "WHEN t.type = 'TRANSFER' THEN -t.amount " +
            "ELSE 0 END) " +
            "FROM transactions t " +
            "WHERE t.wallet_id = w.id AND t.is_deleted = 0), 0) + " +
            "COALESCE((SELECT SUM(t.amount) " +
            "FROM transactions t " +
            "WHERE t.to_wallet_id = w.id AND t.type = 'TRANSFER' AND t.is_deleted = 0), 0) " +
            "AS current_balance " +
            "FROM wallets w " +
            "WHERE w.id = :id AND w.is_deleted = 0")
    public abstract LiveData<WalletWithBalance> getByIdWithBalance(String id);

    @Query("SELECT * FROM wallets WHERE id = :id")
    public abstract WalletEntity getByIdSync(String id);

    @Query("SELECT COALESCE(SUM(" +
            "COALESCE(w.balance, 0) + " +
            "COALESCE((SELECT SUM(CASE " +
            "WHEN t.type = 'INCOME' THEN t.amount " +
            "WHEN t.type = 'EXPENSE' THEN -t.amount " +
            "WHEN t.type = 'TRANSFER' THEN -t.amount " +
            "ELSE 0 END) " +
            "FROM transactions t " +
            "WHERE t.wallet_id = w.id AND t.is_deleted = 0), 0) + " +
            "COALESCE((SELECT SUM(t.amount) " +
            "FROM transactions t " +
            "WHERE t.to_wallet_id = w.id AND t.type = 'TRANSFER' AND t.is_deleted = 0), 0)" +
            "), 0) " +
            "FROM wallets w " +
            "WHERE w.user_id = :userId AND w.is_deleted = 0 AND w.is_excluded = 0")
    public abstract LiveData<Double> getTotalBalance(String userId);

    // BUG FIX: dùng SyncStatus.PENDING_UPLOAD (1) thay vì hardcode 1
    @Query("UPDATE wallets SET is_archived = :isArchived, sync_status = 1, updated_at = :updatedAt WHERE id = :id")
    protected abstract void updateArchiveStateById(String id, boolean isArchived, long updatedAt);

    @Transaction
    public void archive(String id, long updatedAt) {
        updateArchiveStateById(id, true, updatedAt);
    }

    @Transaction
    public void restore(String id, long updatedAt) {
        updateArchiveStateById(id, false, updatedAt);
    }

    // BUG FIX: thêm sync_status = 2 (PENDING_DELETE) vào markDeletedById
    // Trước đây query này thiếu sync_status nên wallet bị xóa không được đưa vào hàng chờ sync
    @Query("UPDATE wallets SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
    protected abstract void markDeletedById(String id, long updatedAt);

    @Query("UPDATE wallets SET " +
            "balance = balance + (" +
            "SELECT COALESCE(SUM(t.amount), 0) FROM transactions t " +
            "WHERE t.wallet_id = wallets.id " +
            "AND t.to_wallet_id = :walletId " +
            "AND t.type = 'TRANSFER' " +
            "AND t.is_deleted = 0" +
            "), " +
            "sync_status = 1, " +
            "updated_at = :updatedAt " +
            "WHERE id IN (" +
            "SELECT DISTINCT t.wallet_id FROM transactions t " +
            "WHERE t.to_wallet_id = :walletId " +
            "AND t.type = 'TRANSFER' " +
            "AND t.is_deleted = 0 " +
            "AND t.wallet_id IS NOT NULL " +
            "AND t.wallet_id != :walletId" +
            ")")
    protected abstract void restoreTransferSourceWalletBalances(String walletId, long updatedAt);

    @Query("UPDATE transactions SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt " +
            "WHERE is_deleted = 0 AND (wallet_id = :walletId OR to_wallet_id = :walletId)")
    protected abstract void softDeleteRelatedTransactions(String walletId, long updatedAt);

    @Transaction
    public void softDelete(String id, long updatedAt) {
        restoreTransferSourceWalletBalances(id, updatedAt);
        softDeleteRelatedTransactions(id, updatedAt);
        markDeletedById(id, updatedAt);
    }

    @Query("SELECT * FROM wallets WHERE user_id = :userId AND sync_status IN (1, 2)")
    @Deprecated
    public abstract List<WalletEntity> getPendingSyncWallets(String userId);

    @Query("SELECT * FROM wallets WHERE user_id = :userId AND sync_status IN (1, 2) " +
            "AND (updated_at > :lastSyncedAt OR (updated_at = :lastSyncedAt AND id > :lastSyncedId)) " +
            "ORDER BY updated_at ASC, id ASC LIMIT :limit OFFSET :offset")
    public abstract List<WalletEntity> getPendingSyncWalletsPagedSince(String userId,
                                                                       long lastSyncedAt,
                                                                       String lastSyncedId,
                                                                       int limit,
                                                                       int offset);

    @Query("UPDATE wallets SET sync_status = 0 WHERE id = :id")
    public abstract void markSynced(String id);

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Query("DELETE FROM wallets WHERE id = :id")
    public abstract void hardDeleteById(String id);

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Query("UPDATE wallets SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE user_id = :userId AND is_deleted = 0")
    public abstract void softDeleteAllByUser(String userId, long updatedAt);

    @Query("SELECT COUNT(*) FROM wallets WHERE user_id = :userId")
    public abstract int countWalletsByUser(String userId);
}