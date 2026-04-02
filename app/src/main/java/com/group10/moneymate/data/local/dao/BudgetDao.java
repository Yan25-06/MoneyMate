package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.annotation.RestrictTo;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.group10.moneymate.data.local.entity.BudgetEntity;

import java.util.List;

@Dao
public interface BudgetDao {
    @RawQuery
    int upsertLocalRaw(SupportSQLiteQuery query);

    default void upsertLocal(BudgetEntity budget) {
        String sql = "INSERT INTO budgets ("
                + "id, category_id, user_id, amount, start_date, end_date, wallet_id, "
                + "created_at, updated_at, is_deleted, sync_status"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(user_id, wallet_id, start_date, end_date, category_id) DO UPDATE SET "
                + "amount = excluded.amount, "
                + "updated_at = excluded.updated_at, "
                + "sync_status = CASE WHEN budgets.sync_status = 2 THEN 2 ELSE excluded.sync_status END, "
                + "is_deleted = CASE WHEN budgets.is_deleted = 1 THEN 1 ELSE excluded.is_deleted END, "
                + "created_at = CASE WHEN budgets.created_at IS NULL OR budgets.created_at <= 0 THEN excluded.created_at ELSE budgets.created_at END";
        upsertLocalRaw(new SimpleSQLiteQuery(sql, new Object[] {
                budget.getId(),
                budget.getCategoryId(),
                budget.getUserId(),
                budget.getAmount(),
                budget.getStartDate(),
                budget.getEndDate(),
                budget.getWalletId(),
                budget.getCreatedAt(),
                budget.getUpdatedAt(),
                budget.isDeleted() ? 1 : 0,
                budget.getSyncStatus()
        }));
    }

    default void insert(BudgetEntity budget) {
        upsertLocal(budget);
    }


    @Query("SELECT * FROM budgets WHERE user_id = :userId AND is_deleted = 0 ORDER BY start_date ASC, updated_at DESC")
    LiveData<List<BudgetEntity>> getAllBudgets(String userId);

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND id = :id AND is_deleted = 0")
    LiveData<BudgetEntity> getBudgetById(String userId, String id);

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND id = :id AND is_deleted = 0")
    BudgetEntity getBudgetByIdSync(String userId, String id);

    @Query("SELECT COUNT(*) FROM budgets "
            + "WHERE user_id = :userId AND is_deleted = 0 AND category_id IS NULL "
            + "AND ((:walletId IS NULL AND wallet_id IS NULL) OR wallet_id = :walletId) "
            + "AND start_date = :startDate AND end_date = :endDate")
    int countAllCategoriesBudgets(String userId, String walletId, long startDate, long endDate);

    @Query("SELECT COUNT(*) FROM budgets "
            + "WHERE user_id = :userId AND is_deleted = 0 AND category_id IS NULL AND id != :excludeId "
            + "AND ((:walletId IS NULL AND wallet_id IS NULL) OR wallet_id = :walletId) "
            + "AND start_date = :startDate AND end_date = :endDate")
    int countAllCategoriesBudgetsExcluding(String userId, String walletId, long startDate, long endDate, String excludeId);

    @Query("SELECT * FROM budgets "
            + "WHERE user_id = :userId AND is_deleted = 0 AND category_id IS NULL "
            + "AND ((:walletId IS NULL AND wallet_id IS NULL) OR wallet_id = :walletId) "
            + "AND start_date = :startDate AND end_date = :endDate "
            + "LIMIT 1")
    BudgetEntity getAllCategoriesBudgetSync(String userId, String walletId, long startDate, long endDate);

    @Query("SELECT COUNT(*) FROM budgets "
            + "WHERE user_id = :userId AND is_deleted = 0 AND category_id IS NOT NULL "
            + "AND category_id NOT IN ('VIRTUAL_OTHER', 'VIRTUAL_OTHER_CATEGORIES') "
            + "AND ((:walletId IS NULL AND wallet_id IS NULL) OR wallet_id = :walletId) "
            + "AND start_date = :startDate AND end_date = :endDate")
    int countSpecificCategoryBudgets(String userId, String walletId, long startDate, long endDate);

    @Query("SELECT SUM(amount) FROM budgets "
            + "WHERE user_id = :userId AND is_deleted = 0 AND category_id IS NOT NULL "
            + "AND category_id NOT IN ('VIRTUAL_OTHER', 'VIRTUAL_OTHER_CATEGORIES') "
            + "AND ((:walletId IS NULL AND wallet_id IS NULL) OR wallet_id = :walletId) "
            + "AND start_date = :startDate AND end_date = :endDate")
    Double sumSpecificCategoryBudgets(String userId, String walletId, long startDate, long endDate);

    @Query("SELECT COUNT(*) FROM budgets "
            + "WHERE user_id = :userId AND is_deleted = 0 "
            + "AND category_id IN ('VIRTUAL_OTHER', 'VIRTUAL_OTHER_CATEGORIES') "
            + "AND ((:walletId IS NULL AND wallet_id IS NULL) OR wallet_id = :walletId) "
            + "AND start_date = :startDate AND end_date = :endDate")
    int countOtherCategoryBudgets(String userId, String walletId, long startDate, long endDate);

    @Query("SELECT * FROM budgets "
            + "WHERE user_id = :userId AND is_deleted = 0 "
            + "AND category_id IN ('VIRTUAL_OTHER', 'VIRTUAL_OTHER_CATEGORIES') "
            + "AND ((:walletId IS NULL AND wallet_id IS NULL) OR wallet_id = :walletId) "
            + "AND start_date = :startDate AND end_date = :endDate "
            + "LIMIT 1")
    BudgetEntity getOtherCategoryBudgetSync(String userId, String walletId, long startDate, long endDate);

    @Query("UPDATE budgets SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE user_id = :userId AND id = :id")
    void softDelete(String userId, String id, long updatedAt);

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND sync_status IN (1, 2) " +
            "AND (updated_at > :lastSyncedAt OR (updated_at = :lastSyncedAt AND id > :lastSyncedId)) " +
            "ORDER BY updated_at ASC, id ASC LIMIT :limit")
    List<BudgetEntity> getPendingSyncSince(String userId,
                                           long lastSyncedAt,
                                           String lastSyncedId,
                                           int limit);

    @Query("UPDATE budgets SET sync_status = 0 WHERE id = :id")
    void markSynced(String id);

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Query("DELETE FROM budgets WHERE id = :id")
    void hardDeleteById(String id);
}
