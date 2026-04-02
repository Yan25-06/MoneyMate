package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.group10.moneymate.data.local.entity.DebtEntity;

import java.util.List;

@Dao
public interface DebtDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertDebt(DebtEntity debt);

    @Update
    void updateDebt(DebtEntity debt);

    @Delete
    void deleteDebt(DebtEntity debt);

    @Query("SELECT * FROM debts WHERE user_id = :userId AND is_deleted = 0 ORDER BY updated_at DESC")
    LiveData<List<DebtEntity>> getAllDebts(String userId);

    @Query("SELECT * FROM debts WHERE user_id = :userId AND type = :type AND is_deleted = 0 ORDER BY updated_at DESC")
    LiveData<List<DebtEntity>> getDebtsByType(String userId, String type);

    @Query("SELECT * FROM debts WHERE user_id = :userId AND status = :status AND is_deleted = 0 ORDER BY updated_at DESC")
    LiveData<List<DebtEntity>> getDebtsByStatus(String userId, String status);

    @Query("SELECT * FROM debts WHERE id = :id AND is_deleted = 0")
    LiveData<DebtEntity> getDebtById(String id);

    @Query("UPDATE debts SET is_deleted = 1, sync_status = 1, updated_at = :updatedAt WHERE id = :id")
    void softDelete(String id, long updatedAt);

    @Query("SELECT * FROM debts WHERE user_id = :userId AND sync_status != 0")
    List<DebtEntity> getPendingSyncDebts(String userId);

    @Query("DELETE FROM debts WHERE user_id = :userId")
    void deleteAllByUser(String userId);
}