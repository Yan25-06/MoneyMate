package com.example.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymate.data.local.entity.TransactionEntity;

import java.util.List;

@Dao
public interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransaction(TransactionEntity transaction);

    @Update
    void updateTransaction(TransactionEntity transaction);

    @Delete
    void deleteTransaction(TransactionEntity transaction);

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC")
    LiveData<List<TransactionEntity>> getAllTransactions(String userId);

    @Query("SELECT * FROM transactions WHERE userId = :userId ORDER BY date DESC LIMIT :limit")
    LiveData<List<TransactionEntity>> getRecentTransactions(String userId, int limit);

    @Query("SELECT * FROM transactions WHERE id = :id")
    LiveData<TransactionEntity> getTransactionById(String id);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date DESC")
    LiveData<List<TransactionEntity>> getTransactionsByDateRange(String userId, long startDate, long endDate);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND type = :type ORDER BY date DESC")
    LiveData<List<TransactionEntity>> getTransactionsByType(String userId, String type);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND categoryId = :categoryId ORDER BY date DESC")
    LiveData<List<TransactionEntity>> getTransactionsByCategory(String userId, String categoryId);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND walletId = :walletId ORDER BY date DESC")
    LiveData<List<TransactionEntity>> getTransactionsByWallet(String userId, String walletId);

    @Query("SELECT * FROM transactions WHERE userId = :userId AND note LIKE '%' || :keyword || '%' ORDER BY date DESC")
    LiveData<List<TransactionEntity>> searchTransactions(String userId, String keyword);

    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND type = 'INCOME' AND date BETWEEN :startDate AND :endDate")
    LiveData<Double> getTotalIncome(String userId, long startDate, long endDate);

    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND type = 'EXPENSE' AND date BETWEEN :startDate AND :endDate")
    LiveData<Double> getTotalExpense(String userId, long startDate, long endDate);

    @Query("SELECT SUM(amount) FROM transactions WHERE userId = :userId AND type = 'EXPENSE' AND categoryId = :categoryId AND date BETWEEN :startDate AND :endDate")
    double getTotalExpenseByCategorySync(String userId, String categoryId, long startDate, long endDate);


    @Query("DELETE FROM transactions WHERE userId = :userId")
    void deleteAllByUser(String userId);
}
