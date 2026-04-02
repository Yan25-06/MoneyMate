package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.annotation.RestrictTo;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.group10.moneymate.data.local.dto.CategorySumDTO;
import com.group10.moneymate.data.local.dto.DailyTrendDTO;
import com.group10.moneymate.data.local.dto.NetIncomeDTO;
import com.group10.moneymate.data.local.entity.TransactionEntity;

import java.util.List;

@Dao
public interface TransactionDao {
    @RawQuery
    int upsertLocalRaw(SupportSQLiteQuery query);

    default void upsertLocal(TransactionEntity transaction) {
        String sql = "INSERT INTO transactions ("
                + "id, wallet_id, category_id, debt_id, event_id, amount, type, to_wallet_id, note, "
                + "timestamp, image_path, created_at, updated_at, sync_status, is_deleted, user_id"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET "
                + "wallet_id = excluded.wallet_id, "
                + "category_id = excluded.category_id, "
                + "debt_id = excluded.debt_id, "
                + "event_id = excluded.event_id, "
                + "amount = excluded.amount, "
                + "type = excluded.type, "
                + "to_wallet_id = excluded.to_wallet_id, "
                + "note = excluded.note, "
                + "timestamp = excluded.timestamp, "
                + "image_path = excluded.image_path, "
                + "updated_at = excluded.updated_at, "
                + "sync_status = CASE WHEN transactions.sync_status = 2 THEN 2 ELSE excluded.sync_status END, "
                + "is_deleted = CASE WHEN transactions.is_deleted = 1 THEN 1 ELSE excluded.is_deleted END, "
                + "created_at = CASE WHEN transactions.created_at IS NULL OR transactions.created_at <= 0 THEN excluded.created_at ELSE transactions.created_at END, "
                + "user_id = excluded.user_id";
        upsertLocalRaw(new SimpleSQLiteQuery(sql, new Object[] {
                transaction.getId(),
                transaction.getWalletId(),
                transaction.getCategoryId(),
                transaction.getDebtId(),
                transaction.getEventId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getToWalletId(),
                transaction.getNote(),
                transaction.getTimestamp(),
                transaction.getImagePath(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt(),
                transaction.getSyncStatus(),
                transaction.isDeleted() ? 1 : 0,
                transaction.getUserId()
        }));
    }

    default void insertTransaction(TransactionEntity transaction) {
        upsertLocal(transaction);
    }

    default void updateTransaction(TransactionEntity transaction) {
        upsertLocal(transaction);
    }

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

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "LEFT JOIN wallets tw ON tw.id = t.to_wallet_id " +
            "WHERE t.user_id = :userId AND t.is_deleted = 0 " +
            "AND (t.to_wallet_id IS NULL OR tw.is_deleted = 0) " +
            "ORDER BY t.timestamp DESC, t.id DESC LIMIT :limit")
    List<TransactionEntity> getFirstTransactionsPageSync(String userId, int limit);

    @Query("SELECT t.* FROM transactions t " +
            "INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 " +
            "LEFT JOIN wallets tw ON tw.id = t.to_wallet_id " +
            "WHERE t.user_id = :userId AND t.is_deleted = 0 " +
            "AND (t.to_wallet_id IS NULL OR tw.is_deleted = 0) " +
            "AND (t.timestamp < :lastTimestamp OR (t.timestamp = :lastTimestamp AND t.id < :lastId)) " +
            "ORDER BY t.timestamp DESC, t.id DESC LIMIT :limit")
    List<TransactionEntity> getTransactionsPagedByCursorSync(String userId,
                                                             long lastTimestamp,
                                                             String lastId,
                                                             int limit);

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
            "CAST((CASE WHEN :periodFormat = '%Y-%m' THEN (t.timestamp / 2592000000) ELSE (t.timestamp / 86400000) END) AS TEXT) AS periodLabel, " +
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
            "GROUP BY (CASE WHEN :periodFormat = '%Y-%m' THEN (t.timestamp / 2592000000) ELSE (t.timestamp / 86400000) END) " +
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
            "CAST((CASE WHEN :periodFormat = '%Y-%m' THEN (t.timestamp / 2592000000) ELSE (t.timestamp / 86400000) END) AS TEXT) AS periodLabel, " +
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
            "GROUP BY (CASE WHEN :periodFormat = '%Y-%m' THEN (t.timestamp / 2592000000) ELSE (t.timestamp / 86400000) END) " +
            "ORDER BY MIN(t.timestamp) ASC")
    LiveData<List<DailyTrendDTO>> getAmountTrend(String userId,
                                                 String type,
                                                 long startDate,
                                                 long endDate,
                                                 String walletId,
                                                 String periodFormat);

    @Query("SELECT MIN(t.timestamp) AS periodStart, " +
            "CAST((CASE WHEN :periodFormat = '%Y-%m' THEN (t.timestamp / 2592000000) ELSE (t.timestamp / 86400000) END) AS TEXT) AS periodLabel, " +
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
            "GROUP BY (CASE WHEN :periodFormat = '%Y-%m' THEN (t.timestamp / 2592000000) ELSE (t.timestamp / 86400000) END) " +
            "ORDER BY MIN(t.timestamp) ASC")
    LiveData<List<DailyTrendDTO>> getCategoryAmountTrend(String userId,
                                                         String type,
                                                         String categoryId,
                                                         long startDate,
                                                         long endDate,
                                                         String walletId,
                                                         String periodFormat);

    @Query("SELECT MIN(t.timestamp) AS periodStart, " +
            "CAST((CASE WHEN :periodFormat = '%Y-%m' THEN (t.timestamp / 2592000000) ELSE (t.timestamp / 86400000) END) AS TEXT) AS periodLabel, " +
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
            "GROUP BY (CASE WHEN :periodFormat = '%Y-%m' THEN (t.timestamp / 2592000000) ELSE (t.timestamp / 86400000) END) " +
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

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND sync_status IN (1, 2) " +
            "AND (updated_at > :lastSyncedAt OR (updated_at = :lastSyncedAt AND id > :lastSyncedId)) " +
            "ORDER BY updated_at ASC, id ASC LIMIT :limit")
    List<TransactionEntity> getPendingSyncSince(String userId,
                                                long lastSyncedAt,
                                                String lastSyncedId,
                                                int limit);

    @Query("UPDATE transactions SET sync_status = 0 WHERE id = :id")
    void markSynced(String id);

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Query("DELETE FROM transactions WHERE id = :id")
    void hardDeleteById(String id);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND sync_status IN (1, 2)")
    @Deprecated
    List<TransactionEntity> getPendingSyncTransactions(String userId);

    @Query("SELECT * FROM transactions WHERE user_id = :userId AND sync_status IN (1, 2) " +
            "AND (updated_at > :lastSyncedAt OR (updated_at = :lastSyncedAt AND id > :lastSyncedId)) " +
            "ORDER BY updated_at ASC, id ASC LIMIT :limit OFFSET :offset")
    List<TransactionEntity> getPendingSyncTransactionsPagedSince(String userId,
                                                                long lastSyncedAt,
                                                                String lastSyncedId,
                                                                int limit,
                                                                int offset);

    @Query("UPDATE transactions SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt " +
            "WHERE user_id = :userId AND is_deleted = 0")
    void softDeleteAllByUser(String userId, long updatedAt);
}
