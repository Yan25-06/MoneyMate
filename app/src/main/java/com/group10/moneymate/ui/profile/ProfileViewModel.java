package com.group10.moneymate.ui.profile;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.group10.moneymate.data.local.dao.UserDao;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.repository.AuthRepository;

public class ProfileViewModel extends ViewModel {

    private final AuthRepository authRepository;
    private final UserDao userDao;
    private final LiveData<UserEntity> userProfileLiveData;

    public ProfileViewModel(AuthRepository authRepository, UserDao userDao) {
        this.authRepository = authRepository;
        this.userDao = userDao;

        String uid = authRepository.getCurrentUserId();
        if (uid != null && !uid.isEmpty()) {
            userProfileLiveData = userDao.getUserById(uid);
        } else {
            userProfileLiveData = new MutableLiveData<>();
        }
    }

    public LiveData<UserEntity> getUserProfile() {
        return userProfileLiveData;
    }
}
