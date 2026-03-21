package com.group10.moneymate.ui.wallet;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.models.WalletType;

import java.util.List;
import java.util.UUID;

public class WalletViewModel extends AndroidViewModel {

    private final AppContainer container;
    private final String userId;
    private final LiveData<List<WalletEntity>> wallets;
    private final LiveData<Double> totalBalance;

    public WalletViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        container = app.appContainer;

        userId = container.authRepository.getCurrentUserId();

        wallets = container.walletRepository.getAllByUser(userId);
        totalBalance = container.walletRepository.getTotalBalance(userId);
    }

    public LiveData<List<WalletEntity>> getWallets() {
        return wallets;
    }

    public LiveData<Double> getTotalBalance() {
        return totalBalance;
    }

    public LiveData<WalletEntity> getWalletById(String walletId) {
        return container.walletRepository.getById(walletId);
    }

    public void addWallet(String name, WalletType type, double balance) {
        long now = System.currentTimeMillis();
        WalletEntity wallet = new WalletEntity();
        wallet.setId(UUID.randomUUID().toString());
        wallet.setUserId(userId);
        wallet.setName(name);
        wallet.setType(type.name());
        wallet.setBalance(balance);
        wallet.setColorHex("#4CAF50");
        wallet.setExcluded(false);
        wallet.setCreatedAt(now);
        wallet.setUpdatedAt(now);
        wallet.setDeleted(false);
        wallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        container.walletRepository.insert(wallet);
    }

    public void updateWallet(WalletEntity wallet, String name, WalletType type, double balance) {
        wallet.setName(name);
        wallet.setType(type.name());
        wallet.setBalance(balance);
        wallet.setUpdatedAt(System.currentTimeMillis());
        wallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        container.walletRepository.update(wallet);
    }

    public void deleteWallet(WalletEntity wallet) {
        container.walletRepository.softDelete(wallet);
    }
}
