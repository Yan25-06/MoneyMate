package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.group10.moneymate.data.local.entity.BudgetEntity;

import java.util.List;

@Dao
public interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insert(BudgetEntity budget);

    @Update
    void update(BudgetEntity budget);

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
}
