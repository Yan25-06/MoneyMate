package com.group10.moneymate.data.repository;

import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.UserDao;
import com.group10.moneymate.data.local.entity.UserEntity;

/**
 * Repository for user profile data.
 * Write operations chạy trên {@link AppDatabase#databaseWriteExecutor}.
 */
public class UserRepository {

    private final UserDao userDao;

    public UserRepository(UserDao userDao) {
        this.userDao = userDao;
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    public LiveData<UserEntity> getUser(String uid) {
        return userDao.getUserById(uid);
    }

    // ─── Write (databaseWriteExecutor) ────────────────────────────────────────

    public void insertUser(UserEntity user) {
        AppDatabase.databaseWriteExecutor.execute(() -> userDao.insertUser(user));
    }

    public void updateUser(UserEntity user) {
        AppDatabase.databaseWriteExecutor.execute(() -> userDao.updateUser(user));
    }

    public void deleteUser(String uid) {
        AppDatabase.databaseWriteExecutor.execute(() -> userDao.deleteUser(uid));
    }
}