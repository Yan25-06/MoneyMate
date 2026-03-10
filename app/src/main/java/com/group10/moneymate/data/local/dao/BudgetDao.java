package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.group10.moneymate.data.local.entity.BudgetEntity;

import java.util.List;

@Dao
public interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBudget(BudgetEntity budget);

    @Update
    void updateBudget(BudgetEntity budget);

    @Delete
    void deleteBudget(BudgetEntity budget);

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND month = :month AND year = :year AND is_deleted = 0")
    LiveData<List<BudgetEntity>> getBudgetsByMonth(String userId, int month, int year);

    @Query("SELECT * FROM budgets WHERE id = :id AND is_deleted = 0")
    LiveData<BudgetEntity> getBudgetById(String id);

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND category_id = :categoryId AND month = :month AND year = :year AND is_deleted = 0")
    BudgetEntity getBudgetByCategoryAndMonthSync(String userId, String categoryId, int month, int year);

    @Query("UPDATE budgets SET is_deleted = 1, sync_status = 1, updated_at = :updatedAt WHERE id = :id")
    void softDelete(String id, long updatedAt);

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND sync_status != 0")
    List<BudgetEntity> getPendingSyncBudgets(String userId);

    @Query("DELETE FROM budgets WHERE user_id = :userId")
    void deleteAllByUser(String userId);
}