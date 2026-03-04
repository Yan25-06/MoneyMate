package com.example.moneymate.data.repository;

import androidx.lifecycle.LiveData;

import com.example.moneymate.data.local.dao.UserDao;
import com.example.moneymate.data.local.entity.UserEntity;

/**
 * Repository for user profile data.
 */
public class UserRepository {
    private final UserDao userDao;

    public UserRepository(UserDao userDao) {
        this.userDao = userDao;
    }

    public LiveData<UserEntity> getUser(String uid) {
        return userDao.getUserById(uid);
    }

    public void insertUser(UserEntity user) {
        userDao.insertUser(user);
    }

    public void updateUser(UserEntity user) {
        userDao.updateUser(user);
    }

    public void deleteUser(String uid) {
        userDao.deleteUser(uid);
    }
}
