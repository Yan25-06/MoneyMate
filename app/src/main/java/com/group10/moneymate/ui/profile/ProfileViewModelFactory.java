package com.group10.moneymate.ui.profile;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.group10.moneymate.data.local.dao.UserDao;
import com.group10.moneymate.data.repository.AuthRepository;

public class ProfileViewModelFactory implements ViewModelProvider.Factory {

    private final AuthRepository authRepository;
    private final UserDao userDao;

    public ProfileViewModelFactory(AuthRepository authRepository, UserDao userDao) {
        this.authRepository = authRepository;
        this.userDao = userDao;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(ProfileViewModel.class)) {
            return (T) new ProfileViewModel(authRepository, userDao);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}
