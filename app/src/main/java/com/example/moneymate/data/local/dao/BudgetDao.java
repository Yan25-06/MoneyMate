package com.example.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymate.data.local.entity.BudgetEntity;

import java.util.List;

@Dao
public interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBudget(BudgetEntity budget);

    @Update
    void updateBudget(BudgetEntity budget);

    @Delete
    void deleteBudget(BudgetEntity budget);

    @Query("SELECT * FROM budgets WHERE userId = :userId AND monthYear = :monthYear")
    LiveData<List<BudgetEntity>> getBudgetsByMonth(String userId, String monthYear);

    @Query("SELECT * FROM budgets WHERE id = :id")
    LiveData<BudgetEntity> getBudgetById(String id);

    @Query("SELECT * FROM budgets WHERE userId = :userId AND categoryId = :categoryId AND monthYear = :monthYear")
    BudgetEntity getBudgetByCategoryAndMonthSync(String userId, String categoryId, String monthYear);


    @Query("DELETE FROM budgets WHERE userId = :userId")
    void deleteAllByUser(String userId);
}
