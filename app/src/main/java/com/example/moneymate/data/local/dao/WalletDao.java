package com.example.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymate.data.local.entity.WalletEntity;

import java.util.List;

@Dao
public interface WalletDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertWallet(WalletEntity wallet);

    @Update
    void updateWallet(WalletEntity wallet);

    @Delete
    void deleteWallet(WalletEntity wallet);

    @Query("SELECT * FROM wallets WHERE userId = :userId ORDER BY createdAt ASC")
    LiveData<List<WalletEntity>> getAllWallets(String userId);

    @Query("SELECT * FROM wallets WHERE id = :id")
    LiveData<WalletEntity> getWalletById(String id);

    @Query("SELECT * FROM wallets WHERE id = :id")
    WalletEntity getWalletByIdSync(String id);

    @Query("SELECT SUM(balance) FROM wallets WHERE userId = :userId")
    LiveData<Double> getTotalBalance(String userId);


    @Query("DELETE FROM wallets WHERE userId = :userId")
    void deleteAllByUser(String userId);
}
