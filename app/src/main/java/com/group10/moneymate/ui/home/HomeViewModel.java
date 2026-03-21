package com.group10.moneymate.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;

import java.util.List;

/**
 * ViewModel for the home/dashboard screen.
 */
public class HomeViewModel extends AndroidViewModel {

    private final LiveData<List<WalletEntity>> wallets;

    public HomeViewModel(@NonNull Application application) {
        super(application);

        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.appContainer;
        String userId = container.authRepository.getCurrentUserId();

        wallets = container.walletRepository.getAllByUser(userId);
    }

    public LiveData<List<WalletEntity>> getWallets() {
        return wallets;
    }
}
