package com.group10.moneymate.ui.settings;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.PrefsManager;
import com.group10.moneymate.workers.SyncScheduler;

public class SettingsViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final PrefsManager prefsManager;
    private final MutableLiveData<Boolean> logoutSuccess = new MutableLiveData<>(false);

    public SettingsViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        authRepository = container.authRepository;
        prefsManager = container.prefsManager;
    }

    public LiveData<Boolean> getLogoutSuccess() {
        return logoutSuccess;
    }

    public void signOut() {
        authRepository.signOut();
        logoutSuccess.setValue(true);
    }

    /**
     * Trigger sync thủ công. SyncViewModel ở Activity scope sẽ tự observe WorkManager.
     */
    public void triggerManualSync() {
        SyncScheduler.enqueueManualRetryNow(getApplication());
    }
}