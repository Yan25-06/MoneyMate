package com.group10.moneymate.data.repository;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.TransactionDao;
import com.group10.moneymate.data.local.dao.WalletDao;
import com.group10.moneymate.data.local.dto.CategorySumDTO;
import com.group10.moneymate.data.local.dto.DailyTrendDTO;
import com.group10.moneymate.data.local.dto.NetIncomeDTO;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.Constants;

import java.util.List;

/**
 * Repository for transaction data.
 * Mọi write operation chạy trên {@link AppDatabase#databaseWriteExecutor}.
 * Tự động cập nhật số dư ví khi insert / update / softDelete.
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

    public LiveData<List<DailyTrendDTO>> getAmountTrend(String userId,
                                                        String type,
                                                        long startDate,
                                                        long endDate,
                                                        @Nullable String walletId,
                                                        String periodFormat) {
        return transactionDao.getAmountTrend(userId, type, startDate, endDate, walletId, periodFormat);
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

    /**
     * Thêm giao dịch mới và cập nhật số dư ví tương ứng.
     */
    public void insertTransaction(TransactionEntity transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
            transaction.setUpdatedAt(System.currentTimeMillis());
            transactionDao.insertTransaction(transaction);
            applyBalanceChange(transaction, false);
        });
    }

    /**
     * Cập nhật giao dịch: hoàn tác số dư cũ, áp dụng số dư mới.
     *
     * @param oldTransaction bản ghi cũ (để hoàn tác số dư)
     * @param newTransaction bản ghi mới
     */
    public void updateTransaction(TransactionEntity oldTransaction, TransactionEntity newTransaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            newTransaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
            newTransaction.setUpdatedAt(System.currentTimeMillis());
            // Hoàn tác số dư của giao dịch cũ
            applyBalanceChange(oldTransaction, true);
            // Áp dụng số dư của giao dịch mới
            applyBalanceChange(newTransaction, false);
            transactionDao.updateTransaction(newTransaction);
        });
    }

    /**
     * Soft delete giao dịch và hoàn tác số dư ví.
     */
    public void softDeleteTransaction(TransactionEntity transaction) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            applyBalanceChange(transaction, true);
            transactionDao.softDelete(transaction.getId(), System.currentTimeMillis());
        });
    }

    // ─── Balance helper ───────────────────────────────────────────────────────

    /**
     * Cập nhật balance của ví theo loại giao dịch.
     *
     * @param transaction giao dịch cần xử lý
     * @param reverse     true = hoàn tác (undo), false = áp dụng
     */
    private void applyBalanceChange(TransactionEntity transaction, boolean reverse) {
        if (transaction.getWalletId() == null) return;

        WalletEntity wallet = walletDao.getByIdSync(transaction.getWalletId());
        if (wallet == null) return;

        double amount = transaction.getAmount();
        String type = transaction.getType();

        double delta;
        if ("INCOME".equals(type)) {
            delta = reverse ? -amount : amount;
        } else if ("EXPENSE".equals(type)) {
            delta = reverse ? amount : -amount;
        } else {
            // TRANSFER: trừ ví nguồn
            delta = reverse ? amount : -amount;
        }

        wallet.setBalance(wallet.getBalance() + delta);
        wallet.setUpdatedAt(System.currentTimeMillis());
        wallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        walletDao.update(wallet);

        // TRANSFER: cộng ví đích
        if ("TRANSFER".equals(type) && transaction.getToWalletId() != null) {
            WalletEntity toWallet = walletDao.getByIdSync(transaction.getToWalletId());
            if (toWallet != null) {
                double toDelta = reverse ? -amount : amount;
                toWallet.setBalance(toWallet.getBalance() + toDelta);
                toWallet.setUpdatedAt(System.currentTimeMillis());
                toWallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                walletDao.update(toWallet);
            }
        }
    }
}
