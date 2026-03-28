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

    @Query("SELECT * FROM budgets WHERE is_deleted = 0 ORDER BY start_date ASC, updated_at DESC")
    LiveData<List<BudgetEntity>> getAllBudgets();

    @Query("SELECT * FROM budgets WHERE id = :id AND is_deleted = 0")
    LiveData<BudgetEntity> getBudgetById(String id);

    @Query("UPDATE budgets SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
    void softDelete(String id, long updatedAt);
}
