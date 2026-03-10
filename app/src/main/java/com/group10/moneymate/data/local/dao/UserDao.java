package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.group10.moneymate.data.local.entity.UserEntity;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(UserEntity user);

    @Update
    void updateUser(UserEntity user);

    @Query("SELECT * FROM users WHERE id = :id")
    LiveData<UserEntity> getUserById(String id);

    @Query("SELECT * FROM users WHERE id = :id")
    UserEntity getUserByIdSync(String id);

    @Query("DELETE FROM users WHERE id = :id")
    void deleteUser(String id);
}