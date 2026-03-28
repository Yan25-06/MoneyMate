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
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(BudgetEntity budget);

    @Update
    void update(BudgetEntity budget);

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND is_deleted = 0 ORDER BY start_date ASC, updated_at DESC")
    LiveData<List<BudgetEntity>> getAllBudgets(String userId);

    @Query("SELECT * FROM budgets WHERE user_id = :userId AND id = :id AND is_deleted = 0")
    LiveData<BudgetEntity> getBudgetById(String userId, String id);

    @Query("UPDATE budgets SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE user_id = :userId AND id = :id")
    void softDelete(String userId, String id, long updatedAt);
}
