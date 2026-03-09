package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.group10.moneymate.data.local.entity.WalletEntity;

import java.util.List;

@Dao
public interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWallet(WalletEntity wallet);

    @Update
    void updateWallet(WalletEntity wallet);

    @Delete
    void deleteWallet(WalletEntity wallet);

    @Query("SELECT * FROM wallets WHERE user_id = :userId AND is_deleted = 0 ORDER BY created_at ASC")
    LiveData<List<WalletEntity>> getAllWallets(String userId);

    @Query("SELECT * FROM wallets WHERE id = :id AND is_deleted = 0")
    LiveData<WalletEntity> getWalletById(String id);

    @Query("SELECT * FROM wallets WHERE id = :id")
    WalletEntity getWalletByIdSync(String id);

    @Query("SELECT SUM(balance) FROM wallets WHERE user_id = :userId AND is_deleted = 0 AND is_excluded = 0")
    LiveData<Double> getTotalBalance(String userId);

    @Query("UPDATE wallets SET is_deleted = 1, sync_status = 1, updated_at = :updatedAt WHERE id = :id")
    void softDelete(String id, long updatedAt);

    @Query("SELECT * FROM wallets WHERE user_id = :userId AND sync_status != 0")
    List<WalletEntity> getPendingSyncWallets(String userId);

    @Query("DELETE FROM wallets WHERE user_id = :userId")
    void deleteAllByUser(String userId);
}