package com.example.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymate.data.local.entity.UserEntity;

@Dao
public interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(UserEntity user);

    @Update
    void updateUser(UserEntity user);

    @Query("SELECT * FROM users WHERE uid = :uid")
    LiveData<UserEntity> getUserById(String uid);

    @Query("SELECT * FROM users WHERE uid = :uid")
    UserEntity getUserByIdSync(String uid);

    @Query("DELETE FROM users WHERE uid = :uid")
    void deleteUser(String uid);
}
