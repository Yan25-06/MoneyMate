package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.models.SyncStatus;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Dao
public abstract class WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insert(WalletEntity wallet);

    @Update
    public abstract void update(WalletEntity wallet);

    @Query("SELECT * FROM wallets WHERE user_id = :userId AND is_deleted = 0 ORDER BY created_at ASC")
    public abstract LiveData<List<WalletEntity>> getAllByUser(String userId);

    @Query("SELECT * FROM wallets WHERE user_id = :userId AND is_deleted = 0 AND is_archived = 0 ORDER BY created_at ASC")
    public abstract LiveData<List<WalletEntity>> getActiveByUser(String userId);

    @Query("SELECT * FROM wallets WHERE id = :id AND is_deleted = 0")
    public abstract LiveData<WalletEntity> getById(String id);

    @Query("SELECT * FROM wallets WHERE id = :id")
    public abstract WalletEntity getByIdSync(String id);

    @Query("SELECT SUM(balance) FROM wallets WHERE user_id = :userId AND is_deleted = 0 AND is_excluded = 0")
    public abstract LiveData<Double> getTotalBalance(String userId);

    @Query("UPDATE wallets SET is_archived = 1, sync_status = 1, updated_at = :updatedAt WHERE id = :id")
    public abstract void archive(String id, long updatedAt);

    @Query("UPDATE wallets SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
    protected abstract void softDeleteWalletById(String id, long updatedAt);

    @Query("SELECT * FROM transactions WHERE is_deleted = 0 " +
            "AND (wallet_id = :walletId OR to_wallet_id = :walletId) " +
            "AND (:userId IS NULL OR user_id = :userId)")
    protected abstract List<TransactionEntity> getRelatedActiveTransactionsByWalletSync(String userId, String walletId);

    @Query("UPDATE transactions SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt " +
            "WHERE is_deleted = 0 AND (wallet_id = :walletId OR to_wallet_id = :walletId) " +
            "AND (:userId IS NULL OR user_id = :userId)")
    protected abstract void softDeleteRelatedTransactionsByWalletId(String userId, String walletId, long updatedAt);

    @Transaction
    public void softDeleteWalletWithRelatedTransactions(String userId, String walletId, long updatedAt) {
        List<TransactionEntity> relatedTransactions = getRelatedActiveTransactionsByWalletSync(userId, walletId);
        Map<String, Double> otherWalletBalanceDelta = new HashMap<>();

        for (TransactionEntity transaction : relatedTransactions) {
            if (!"TRANSFER".equals(transaction.getType())) {
                continue;
            }

            String sourceWalletId = transaction.getWalletId();
            String destinationWalletId = transaction.getToWalletId();
            double amount = transaction.getAmount();

            if (walletId.equals(sourceWalletId)
                    && destinationWalletId != null
                    && !walletId.equals(destinationWalletId)) {
                otherWalletBalanceDelta.put(
                        destinationWalletId,
                        otherWalletBalanceDelta.getOrDefault(destinationWalletId, 0d) - amount
                );
            }

            if (walletId.equals(destinationWalletId)
                    && sourceWalletId != null
                    && !walletId.equals(sourceWalletId)) {
                otherWalletBalanceDelta.put(
                        sourceWalletId,
                        otherWalletBalanceDelta.getOrDefault(sourceWalletId, 0d) + amount
                );
            }
        }

        for (Map.Entry<String, Double> entry : otherWalletBalanceDelta.entrySet()) {
            WalletEntity affectedWallet = getByIdSync(entry.getKey());
            if (affectedWallet == null || affectedWallet.isDeleted()) {
                continue;
            }
            affectedWallet.setBalance(affectedWallet.getBalance() + entry.getValue());
            affectedWallet.setUpdatedAt(updatedAt);
            affectedWallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
            update(affectedWallet);
        }

        softDeleteRelatedTransactionsByWalletId(userId, walletId, updatedAt);
        softDeleteWalletById(walletId, updatedAt);
    }

    @Query("UPDATE wallets SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
    public abstract void softDelete(String id, long updatedAt);

    @Query("SELECT * FROM wallets WHERE user_id = :userId AND sync_status != 0")
    public abstract List<WalletEntity> getPendingSyncWallets(String userId);

    @Query("DELETE FROM wallets WHERE user_id = :userId")
    public abstract void deleteAllByUser(String userId);
}