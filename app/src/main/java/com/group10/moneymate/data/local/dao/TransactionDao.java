package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.group10.moneymate.data.local.entity.TransactionEntity;

import java.util.List;

@Dao
public interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransaction(TransactionEntity transaction);

    @Update
    void updateTransaction(TransactionEntity transaction);

    @Delete
    void deleteTransaction(TransactionEntity transaction);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND is_deleted = 0 ORDER BY timestamp DESC")
    LiveData<List<TransactionEntity>> getAllTransactions(String userId);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND is_deleted = 0 ORDER BY timestamp DESC LIMIT :limit")
    LiveData<List<TransactionEntity>> getRecentTransactions(String userId, int limit);

    @Query("SELECT * FROM transactions WHERE id = :id AND is_deleted = 0")
    LiveData<TransactionEntity> getTransactionById(String id);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND timestamp BETWEEN :startDate AND :endDate AND is_deleted = 0 ORDER BY timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsByDateRange(String userId, long startDate, long endDate);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND type = :type AND is_deleted = 0 ORDER BY timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsByType(String userId, String type);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND category_id = :categoryId AND is_deleted = 0 ORDER BY timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsByCategory(String userId, String categoryId);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND wallet_id = :walletId AND is_deleted = 0 ORDER BY timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsByWallet(String userId, String walletId);

    @Query("SELECT * FROM transactions WHERE event_id = :eventId AND is_deleted = 0 ORDER BY timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsByEvent(String eventId);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND note LIKE '%' || :keyword || '%' AND is_deleted = 0 ORDER BY timestamp DESC")
    LiveData<List<TransactionEntity>> searchTransactions(String userId, String keyword);

    @Query("SELECT SUM(amount) FROM transactions WHERE user_id = :userId AND type = 'INCOME' AND timestamp BETWEEN :startDate AND :endDate AND is_deleted = 0")
    LiveData<Double> getTotalIncome(String userId, long startDate, long endDate);

    @Query("SELECT SUM(amount) FROM transactions WHERE user_id = :userId AND type = 'EXPENSE' AND timestamp BETWEEN :startDate AND :endDate AND is_deleted = 0")
    LiveData<Double> getTotalExpense(String userId, long startDate, long endDate);

    @Query("SELECT SUM(amount) FROM transactions WHERE user_id = :userId AND type = 'EXPENSE' AND category_id = :categoryId AND timestamp BETWEEN :startDate AND :endDate AND is_deleted = 0")
    double getTotalExpenseByCategorySync(String userId, String categoryId, long startDate, long endDate);

    @Query("UPDATE transactions SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
    void softDelete(String id, long updatedAt);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND sync_status != 0")
    List<TransactionEntity> getPendingSyncTransactions(String userId);

    @Query("DELETE FROM transactions WHERE user_id = :userId")
    void deleteAllByUser(String userId);
}