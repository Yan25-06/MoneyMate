package com.group10.moneymate.ui.settings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;

public class SettingsViewModel extends AndroidViewModel {
    private final AuthRepository authRepository;
    private final MutableLiveData<Boolean> logoutSuccess = new MutableLiveData<>();

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        authRepository = container.authRepository;
    }

    public MutableLiveData<Boolean> getLogoutSuccess() {
        return logoutSuccess;
    }

    public void signOut() {
        authRepository.signOut();
        logoutSuccess.setValue(true);
    }
}
