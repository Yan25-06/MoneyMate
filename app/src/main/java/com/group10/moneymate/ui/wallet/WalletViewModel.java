package com.group10.moneymate.ui.wallet;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.models.WalletType;
import com.group10.moneymate.ui.common.DebounceableAndroidViewModel;
import com.group10.moneymate.utils.DistinctLiveData;

import java.util.List;
import java.util.UUID;

public class WalletViewModel extends DebounceableAndroidViewModel {

    private final AppContainer container;
    private final String userId;
    private final LiveData<List<WalletWithBalance>> wallets;
    private final LiveData<Double> totalBalance;

    public WalletViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        container = app.getAppContainer();

        userId = container.authRepository.getCurrentUserId();

        wallets = DistinctLiveData.distinctUntilChanged(container.walletRepository.getAllByUserWithBalance(userId));
        totalBalance = container.walletRepository.getTotalBalance(userId);
    }

    public LiveData<List<WalletWithBalance>> getWallets() {
        return wallets;
    }

    public LiveData<Double> getTotalBalance() {
        return totalBalance;
    }

    public LiveData<WalletEntity> getWalletById(String walletId) {
        return container.walletRepository.getById(walletId);
    }

    public LiveData<WalletWithBalance> getWalletWithBalanceById(String walletId) {
        return container.walletRepository.getByIdWithBalance(walletId);
    }

    public void addWallet(String name, WalletType type, double balance, @NonNull String iconName) {
        addWallet(name, type, balance, iconName, null);
    }

    public void addWallet(String name,
                          WalletType type,
                          double balance,
                          @NonNull String iconName,
                          com.group10.moneymate.data.repository.WalletRepository.WriteCallback callback) {
        long now = System.currentTimeMillis();
        WalletEntity wallet = new WalletEntity();
        wallet.setId(UUID.randomUUID().toString());
        wallet.setUserId(userId);
        wallet.setName(name);
        wallet.setType(type.name());
        wallet.setBalance(balance);
        wallet.setIconName(iconName);
        wallet.setExcluded(false);
        wallet.setCreatedAt(now);
        wallet.setUpdatedAt(now);
        wallet.setDeleted(false);
        wallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        container.walletRepository.insert(wallet, callback);
    }

    public void updateWallet(WalletEntity wallet,
                             String name,
                             WalletType type,
                             double balance,
                             @NonNull String iconName) {
        updateWallet(wallet, name, type, balance, iconName, null);
    }

    public void updateWallet(WalletEntity wallet,
                             String name,
                             WalletType type,
                             double balance,
                             @NonNull String iconName,
                             com.group10.moneymate.data.repository.WalletRepository.WriteCallback callback) {
        wallet.setName(name);
        wallet.setType(type.name());
        wallet.setBalance(balance);
        wallet.setIconName(iconName);
        wallet.setUpdatedAt(System.currentTimeMillis());
        wallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        container.walletRepository.update(wallet, callback);
    }

    public void deleteWallet(WalletEntity wallet) {
        container.walletRepository.softDelete(wallet);
    }

    public void archiveWallet(WalletEntity wallet) {
        container.walletRepository.archive(wallet);
    }

    public void restoreWallet(WalletEntity wallet) {
        container.walletRepository.restore(wallet);
    }
}
