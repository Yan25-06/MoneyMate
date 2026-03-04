package com.example.moneymate.data.repository;

import androidx.lifecycle.LiveData;

import com.example.moneymate.data.local.dao.TransactionDao;
import com.example.moneymate.data.local.entity.TransactionEntity;

import java.util.List;

/**
 * Repository for transaction data.
 */
public class TransactionRepository {
    private final TransactionDao transactionDao;

    public TransactionRepository(TransactionDao transactionDao) {
        this.transactionDao = transactionDao;
    }

    public LiveData<List<TransactionEntity>> getAllTransactions(String userId) {
        return transactionDao.getAllTransactions(userId);
    }

    public LiveData<List<TransactionEntity>> getRecentTransactions(String userId, int limit) {
        return transactionDao.getRecentTransactions(userId, limit);
    }

    public LiveData<TransactionEntity> getTransactionById(String id) {
        return transactionDao.getTransactionById(id);
    }

    public void insertTransaction(TransactionEntity transaction) {
        transactionDao.insertTransaction(transaction);
    }

    public void updateTransaction(TransactionEntity transaction) {
        transactionDao.updateTransaction(transaction);
    }

    public void deleteTransaction(TransactionEntity transaction) {
        transactionDao.deleteTransaction(transaction);
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

    public LiveData<List<TransactionEntity>> getTransactionsByWallet(String userId, String walletId) {
        return transactionDao.getTransactionsByWallet(userId, walletId);
    }

    public LiveData<List<TransactionEntity>> searchTransactions(String userId, String keyword) {
        return transactionDao.searchTransactions(userId, keyword);
    }

    public LiveData<Double> getTotalIncome(String userId, long startDate, long endDate) {
        return transactionDao.getTotalIncome(userId, startDate, endDate);
    }

    public LiveData<Double> getTotalExpense(String userId, long startDate, long endDate) {
        return transactionDao.getTotalExpense(userId, startDate, endDate);
    }
}
