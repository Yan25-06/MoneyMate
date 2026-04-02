package com.group10.moneymate.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

import java.util.List;

/**
 * Repository for transaction data.
 * Mọi write operation chạy trên {@link AppDatabase#databaseWriteExecutor}.
 */
public class TransactionRepository {

    private final TransactionDao transactionDao;
    private final WalletDao walletDao;

    public TransactionRepository(TransactionDao transactionDao, WalletDao walletDao) {
        this.transactionDao = transactionDao;
        this.walletDao = walletDao;
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    public LiveData<List<TransactionEntity>> getAllTransactions(String userId) {
        return transactionDao.getAllTransactions(userId);
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

    // ─── Write ────────────────────────────────────────────────────────────────

    public void insertTransaction(TransactionEntity transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
            transaction.setUpdatedAt(System.currentTimeMillis());
            transactionDao.insertTransaction(transaction);
        });
    }

    public void updateTransaction(TransactionEntity newTransaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            newTransaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
            newTransaction.setUpdatedAt(System.currentTimeMillis());
            transactionDao.updateTransaction(newTransaction);
        });
    }

    public void softDeleteTransaction(TransactionEntity transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transactionDao.softDelete(transaction.getId(), System.currentTimeMillis());
        });
    }
}
