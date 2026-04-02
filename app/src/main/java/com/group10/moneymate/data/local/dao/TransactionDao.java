package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.group10.moneymate.data.local.dto.CategorySumDTO;
import com.group10.moneymate.data.local.dto.DailyTrendDTO;
import com.group10.moneymate.data.local.dto.NetIncomeDTO;
import com.group10.moneymate.data.local.entity.TransactionEntity;

import java.util.List;

@Dao
public interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertTransaction(TransactionEntity transaction);

    @Update
    void updateTransaction(TransactionEntity transaction);

    @Delete
    void deleteTransaction(TransactionEntity transaction);

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "LEFT JOIN wallets tw ON tw.id = t.to_wallet_id " +
            "WHERE t.user_id = :userId AND t.is_deleted = 0 " +
            "AND (t.to_wallet_id IS NULL OR tw.is_deleted = 0) " +
            "ORDER BY t.timestamp DESC")
    LiveData<List<TransactionEntity>> getAllTransactions(String userId);

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "LEFT JOIN wallets tw ON tw.id = t.to_wallet_id " +
            "WHERE t.user_id = :userId AND t.is_deleted = 0 " +
            "AND (t.to_wallet_id IS NULL OR tw.is_deleted = 0) " +
            "ORDER BY t.timestamp DESC LIMIT :limit")
    LiveData<List<TransactionEntity>> getRecentTransactions(String userId, int limit);

    @Query("SELECT * FROM transactions WHERE id = :id AND is_deleted = 0")
    LiveData<TransactionEntity> getTransactionById(String id);

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "LEFT JOIN wallets tw ON tw.id = t.to_wallet_id " +
            "WHERE t.user_id = :userId AND t.timestamp BETWEEN :startDate AND :endDate AND t.is_deleted = 0 " +
            "AND (t.to_wallet_id IS NULL OR tw.is_deleted = 0) " +
            "ORDER BY t.timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsByDateRange(String userId, long startDate, long endDate);

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "LEFT JOIN wallets tw ON tw.id = t.to_wallet_id " +
            "WHERE t.user_id = :userId AND t.type = :type AND t.is_deleted = 0 " +
            "AND (t.to_wallet_id IS NULL OR tw.is_deleted = 0) " +
            "ORDER BY t.timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsByType(String userId, String type);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND category_id = :categoryId AND is_deleted = 0 ORDER BY timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsByCategory(String userId, String categoryId);

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND (:categoryId IS NULL OR t.category_id = :categoryId) " +
            "AND t.type = 'EXPENSE' " +
            "AND t.is_deleted = 0 " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "ORDER BY t.timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsForBudget(String userId,
                                                               String categoryId,
                                                               String walletId,
                                                               long startDate,
                                                               long endDate);

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND t.type = 'EXPENSE' " +
            "AND t.is_deleted = 0 " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "ORDER BY t.timestamp DESC")
    LiveData<List<TransactionEntity>> getExpenseTransactionsByRange(String userId,
                                                                    long startDate,
                                                                    long endDate);

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "LEFT JOIN wallets tw ON tw.id = t.to_wallet_id " +
            "WHERE t.user_id = :userId AND t.wallet_id = :walletId AND t.is_deleted = 0 " +
            "AND (t.to_wallet_id IS NULL OR tw.is_deleted = 0) " +
            "ORDER BY t.timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsByWallet(String userId, String walletId);

    @Query("SELECT * FROM transactions WHERE event_id = :eventId AND is_deleted = 0 ORDER BY timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsByEvent(String eventId);

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "LEFT JOIN wallets tw ON tw.id = t.to_wallet_id " +
            "WHERE t.user_id = :userId AND t.note LIKE '%' || :keyword || '%' AND t.is_deleted = 0 " +
            "AND (t.to_wallet_id IS NULL OR tw.is_deleted = 0) " +
            "ORDER BY t.timestamp DESC")
    LiveData<List<TransactionEntity>> searchTransactions(String userId, String keyword);

    @Query("SELECT SUM(t.amount) FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId AND t.type = 'INCOME' AND t.timestamp BETWEEN :startDate AND :endDate AND t.is_deleted = 0 AND t.sync_status != 2")
    LiveData<Double> getTotalIncome(String userId, long startDate, long endDate);

    @Query("SELECT SUM(t.amount) FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId AND t.type = 'EXPENSE' AND t.timestamp BETWEEN :startDate AND :endDate AND t.is_deleted = 0 AND t.sync_status != 2")
    LiveData<Double> getTotalExpense(String userId, long startDate, long endDate);

    @Query("SELECT SUM(t.amount) FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND t.type = 'INCOME' " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId)")
    LiveData<Double> getTotalIncomeFiltered(String userId, long startDate, long endDate, String walletId);

    @Query("SELECT SUM(t.amount) FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND t.type = 'EXPENSE' " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId)")
    LiveData<Double> getTotalExpenseFiltered(String userId, long startDate, long endDate, String walletId);

    @Query("SELECT SUM(t.amount) FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND t.type = :type " +
            "AND t.category_id = :categoryId " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId)")
    LiveData<Double> getTotalAmountByCategoryFiltered(String userId,
                                                      String type,
                                                      String categoryId,
                                                      long startDate,
                                                      long endDate,
                                                      String walletId);

    @Query("SELECT MIN(t.timestamp) AS periodStart, " +
            ":periodLabel AS periodLabel, " +
            "COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0.0) AS totalIncome, " +
            "COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0.0) AS totalExpense, " +
            "COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount WHEN t.type = 'EXPENSE' THEN -t.amount ELSE 0 END), 0.0) AS netAmount, " +
            "COUNT(t.id) AS transactionCount " +
            "FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND t.type != 'TRANSFER' " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId)")
    LiveData<NetIncomeDTO> getNetIncomeSummary(String userId,
                                               long startDate,
                                               long endDate,
                                               String walletId,
                                               String periodLabel);

    @Query("SELECT MIN(t.timestamp) AS periodStart, " +
            "STRFTIME(:periodFormat, t.timestamp / 1000, 'unixepoch') AS periodLabel, " +
            "COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount ELSE 0 END), 0.0) AS totalIncome, " +
            "COALESCE(SUM(CASE WHEN t.type = 'EXPENSE' THEN t.amount ELSE 0 END), 0.0) AS totalExpense, " +
            "COALESCE(SUM(CASE WHEN t.type = 'INCOME' THEN t.amount WHEN t.type = 'EXPENSE' THEN -t.amount ELSE 0 END), 0.0) AS netAmount, " +
            "COUNT(t.id) AS transactionCount " +
            "FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND t.type != 'TRANSFER' " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "GROUP BY STRFTIME(:periodFormat, t.timestamp / 1000, 'unixepoch') " +
            "ORDER BY MIN(t.timestamp) ASC")
    LiveData<List<NetIncomeDTO>> getNetIncomeTrend(String userId,
                                                   long startDate,
                                                   long endDate,
                                                   String walletId,
                                                   String periodFormat);

    @Query("SELECT t.category_id AS categoryId, " +
            "COALESCE(c.name, 'Chưa phân loại') AS categoryName, " +
            "COALESCE(c.icon_name, 'ic_category_default') AS iconName, " +
            "COALESCE(c.is_deleted, 0) AS categoryDeleted, " +
            "COALESCE(SUM(t.amount), 0.0) AS totalAmount, " +
            "COUNT(t.id) AS transactionCount " +
            "FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "LEFT JOIN categories c ON c.id = t.category_id " +
            "WHERE t.user_id = :userId " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND t.type = :type " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "GROUP BY t.category_id, c.name, c.icon_name, COALESCE(c.is_deleted, 0) " +
            "ORDER BY totalAmount DESC")
    LiveData<List<CategorySumDTO>> getCategorySums(String userId,
                                                   String type,
                                                   long startDate,
                                                   long endDate,
                                                   String walletId);

    @Query("SELECT " +
            "COALESCE(parent.id, c.id, t.category_id) AS categoryId, " +
            "COALESCE(parent.name, c.name, 'Chưa phân loại') AS categoryName, " +
            "COALESCE(parent.icon_name, c.icon_name, 'ic_category_default') AS iconName, " +
            "COALESCE(parent.is_deleted, c.is_deleted, 0) AS categoryDeleted, " +
            "COALESCE(SUM(t.amount), 0.0) AS totalAmount, " +
            "COUNT(t.id) AS transactionCount " +
            "FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "LEFT JOIN categories c ON c.id = t.category_id " +
            "LEFT JOIN categories parent ON parent.id = c.parent_id " +
            "WHERE t.user_id = :userId " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND t.type = :type " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "GROUP BY COALESCE(parent.id, c.id, t.category_id), " +
            "COALESCE(parent.name, c.name, 'Chưa phân loại'), " +
            "COALESCE(parent.icon_name, c.icon_name, 'ic_category_default'), " +
            "COALESCE(parent.is_deleted, c.is_deleted, 0) " +
            "ORDER BY totalAmount DESC")
    LiveData<List<CategorySumDTO>> getRootCategorySums(String userId,
                                                       String type,
                                                       long startDate,
                                                       long endDate,
                                                       String walletId);

    @Query("SELECT " +
            "c.id AS categoryId, " +
            "COALESCE(c.name, 'Chưa phân loại') AS categoryName, " +
            "COALESCE(c.icon_name, 'ic_category_default') AS iconName, " +
            "COALESCE(c.is_deleted, 0) AS categoryDeleted, " +
            "COALESCE(SUM(t.amount), 0.0) AS totalAmount, " +
            "COUNT(t.id) AS transactionCount " +
            "FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "INNER JOIN categories c ON c.id = t.category_id " +
            "WHERE t.user_id = :userId " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND t.type = :type " +
            "AND c.parent_id = :parentCategoryId " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "GROUP BY c.id, c.name, c.icon_name, COALESCE(c.is_deleted, 0) " +
            "ORDER BY totalAmount DESC")
    LiveData<List<CategorySumDTO>> getChildCategorySums(String userId,
                                                        String type,
                                                        long startDate,
                                                        long endDate,
                                                        String walletId,
                                                        String parentCategoryId);

    @Query("SELECT " +
            "c.id AS categoryId, " +
            "COALESCE(c.name, 'Chưa phân loại') AS categoryName, " +
            "COALESCE(c.icon_name, 'ic_category_default') AS iconName, " +
            "COALESCE(c.is_deleted, 0) AS categoryDeleted, " +
            "COALESCE(SUM(t.amount), 0.0) AS totalAmount, " +
            "COUNT(t.id) AS transactionCount " +
            "FROM categories c " +
            "LEFT JOIN transactions t ON t.category_id = c.id " +
            "AND t.user_id = :userId " +
            "AND EXISTS (SELECT 1 FROM wallets w WHERE w.id = t.wallet_id AND w.is_deleted = 0) " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND t.type = :type " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "WHERE c.id = :parentCategoryId OR c.parent_id = :parentCategoryId " +
            "GROUP BY c.id, c.name, c.icon_name, COALESCE(c.is_deleted, 0) " +
            "ORDER BY CASE WHEN c.id = :parentCategoryId THEN 0 ELSE 1 END, totalAmount DESC, c.name COLLATE NOCASE ASC")
    LiveData<List<CategorySumDTO>> getCategoryBranchSums(String userId,
                                                         String type,
                                                         long startDate,
                                                         long endDate,
                                                         String walletId,
                                                         String parentCategoryId);

    @Query("SELECT COALESCE(SUM(t.amount), 0.0) " +
            "FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "INNER JOIN categories c ON c.id = t.category_id " +
            "WHERE t.user_id = :userId " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND t.type = :type " +
            "AND (c.id = :parentCategoryId OR c.parent_id = :parentCategoryId) " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId)")
    LiveData<Double> getParentCategoryBranchTotalAmount(String userId,
                                                        String type,
                                                        String parentCategoryId,
                                                        long startDate,
                                                        long endDate,
                                                        String walletId);

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND t.type = :type " +
            "AND t.category_id = :categoryId " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "ORDER BY t.timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsForStatisticsDrillDown(String userId,
                                                                            String type,
                                                                            long startDate,
                                                                            long endDate,
                                                                            String walletId,
                                                                            String categoryId);

    @Query("SELECT MIN(t.timestamp) AS periodStart, " +
            "STRFTIME(:periodFormat, t.timestamp / 1000, 'unixepoch') AS periodLabel, " +
            "COALESCE(SUM(t.amount), 0.0) AS totalAmount, " +
            "COUNT(t.id) AS transactionCount " +
            "FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND t.type = :type " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "GROUP BY STRFTIME(:periodFormat, t.timestamp / 1000, 'unixepoch') " +
            "ORDER BY MIN(t.timestamp) ASC")
    LiveData<List<DailyTrendDTO>> getAmountTrend(String userId,
                                                 String type,
                                                 long startDate,
                                                 long endDate,
                                                 String walletId,
                                                 String periodFormat);

    @Query("SELECT MIN(t.timestamp) AS periodStart, " +
            "STRFTIME(:periodFormat, t.timestamp / 1000, 'unixepoch') AS periodLabel, " +
            "COALESCE(SUM(t.amount), 0.0) AS totalAmount, " +
            "COUNT(t.id) AS transactionCount " +
            "FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND t.type = :type " +
            "AND t.category_id = :categoryId " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "GROUP BY STRFTIME(:periodFormat, t.timestamp / 1000, 'unixepoch') " +
            "ORDER BY MIN(t.timestamp) ASC")
    LiveData<List<DailyTrendDTO>> getCategoryAmountTrend(String userId,
                                                         String type,
                                                         String categoryId,
                                                         long startDate,
                                                         long endDate,
                                                         String walletId,
                                                         String periodFormat);

    @Query("SELECT MIN(t.timestamp) AS periodStart, " +
            "STRFTIME(:periodFormat, t.timestamp / 1000, 'unixepoch') AS periodLabel, " +
            "COALESCE(SUM(t.amount), 0.0) AS totalAmount, " +
            "COUNT(t.id) AS transactionCount " +
            "FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "INNER JOIN categories c ON c.id = t.category_id " +
            "WHERE t.user_id = :userId " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND t.is_deleted = 0 " +
            "AND t.sync_status != 2 " +
            "AND t.type = :type " +
            "AND (c.id = :parentCategoryId OR c.parent_id = :parentCategoryId) " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "GROUP BY STRFTIME(:periodFormat, t.timestamp / 1000, 'unixepoch') " +
            "ORDER BY MIN(t.timestamp) ASC")
    LiveData<List<DailyTrendDTO>> getParentCategoryBranchAmountTrend(String userId,
                                                                     String type,
                                                                     String parentCategoryId,
                                                                     long startDate,
                                                                     long endDate,
                                                                     String walletId,
                                                                     String periodFormat);

    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND (:categoryId IS NULL OR t.category_id = :categoryId) " +
            "AND t.type = 'EXPENSE' " +
            "AND t.is_deleted = 0 " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "AND t.timestamp BETWEEN :startDate AND :endDate")
    LiveData<Double> getTotalExpenseByCategory(String userId,
                                               String categoryId,
                                               String walletId,
                                               long startDate,
                                               long endDate);

    @Query("SELECT COALESCE(SUM(t.amount), 0.0) FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND t.type = 'EXPENSE' " +
            "AND t.is_deleted = 0 " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "AND NOT EXISTS (" +
            "    SELECT 1 FROM budgets b " +
            "    WHERE b.user_id = :userId " +
            "    AND b.is_deleted = 0 " +
            "    AND b.category_id = t.category_id " +
            "    AND b.category_id IS NOT NULL " +
            "    AND b.category_id != :otherCategoryId " +
            "    AND b.category_id != :legacyOtherCategoryId " +
            "    AND t.timestamp BETWEEN b.start_date AND b.end_date " +
            "    AND (b.wallet_id IS NULL OR b.wallet_id = t.wallet_id)" +
            ")")
    LiveData<Double> getSpentForOtherCategories(String userId,
                                                long startDate,
                                                long endDate,
                                                String walletId,
                                                String otherCategoryId,
                                                String legacyOtherCategoryId);

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId " +
            "AND t.type = 'EXPENSE' " +
            "AND t.is_deleted = 0 " +
            "AND t.timestamp BETWEEN :startDate AND :endDate " +
            "AND (:walletId IS NULL OR t.wallet_id = :walletId) " +
            "AND NOT EXISTS (" +
            "    SELECT 1 FROM budgets b " +
            "    WHERE b.user_id = :userId " +
            "    AND b.is_deleted = 0 " +
            "    AND b.category_id = t.category_id " +
            "    AND b.category_id IS NOT NULL " +
            "    AND b.category_id != :otherCategoryId " +
            "    AND b.category_id != :legacyOtherCategoryId " +
            "    AND t.timestamp BETWEEN b.start_date AND b.end_date " +
            "    AND (b.wallet_id IS NULL OR b.wallet_id = t.wallet_id)" +
            ") " +
            "ORDER BY t.timestamp DESC")
    LiveData<List<TransactionEntity>> getTransactionsForOtherCategories(String userId,
                                                                        long startDate,
                                                                        long endDate,
                                                                        String walletId,
                                                                        String otherCategoryId,
                                                                        String legacyOtherCategoryId);

    @Query("SELECT SUM(t.amount) FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "WHERE t.user_id = :userId AND t.type = 'EXPENSE' AND t.category_id = :categoryId AND t.timestamp BETWEEN :startDate AND :endDate AND t.is_deleted = 0")
    double getTotalExpenseByCategorySync(String userId, String categoryId, long startDate, long endDate);

    @Query("UPDATE transactions SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
    void softDelete(String id, long updatedAt);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND sync_status != 0")
    List<TransactionEntity> getPendingSyncTransactions(String userId);

    @Query("DELETE FROM transactions WHERE user_id = :userId")
    void deleteAllByUser(String userId);
}
