package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.WalletEntity;
import java.util.List;

@Dao
public abstract class WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public abstract void insert(WalletEntity wallet);

    @Update
    public abstract void update(WalletEntity wallet);

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

    @Query("UPDATE wallets SET is_archived = 1, sync_status = 1, updated_at = :updatedAt WHERE id = :id")
    public abstract void archive(String id, long updatedAt);

    @Query("UPDATE wallets SET is_archived = 0, sync_status = 1, updated_at = :updatedAt WHERE id = :id")
    public abstract void restore(String id, long updatedAt);

    @Query("UPDATE wallets SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
    protected abstract void softDeleteWalletById(String id, long updatedAt);

    @Query("UPDATE wallets SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
    public abstract void softDelete(String id, long updatedAt);

    @Query("SELECT * FROM wallets WHERE user_id = :userId AND sync_status != 0")
    public abstract List<WalletEntity> getPendingSyncWallets(String userId);

    @Query("UPDATE wallets SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE user_id = :userId AND is_deleted = 0")
    public abstract void softDeleteAllByUser(String userId, long updatedAt);
}
