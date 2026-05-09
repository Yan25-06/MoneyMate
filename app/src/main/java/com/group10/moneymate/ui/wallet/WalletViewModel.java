package com.group10.moneymate.ui.wallet;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.WalletRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.models.WalletType;
import com.group10.moneymate.ui.common.DebounceableAndroidViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class WalletViewModel extends DebounceableAndroidViewModel {

    private final AppContainer container;
    private final String userId;
    private final MediatorLiveData<List<WalletWithBalance>> wallets = new MediatorLiveData<>();
    private final MediatorLiveData<Double> totalBalance = new MediatorLiveData<>();

    public WalletViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        container = app.getAppContainer();

        userId = container.authRepository.getCurrentUserId();

        LiveData<List<WalletWithBalance>> walletSource =
                container.walletRepository.getAllByUserWithBalance(userId);
        LiveData<Double> totalBalanceSource = container.walletRepository.getTotalBalance(userId);
        wallets.setValue(new ArrayList<>());
        totalBalance.setValue(0d);
        wallets.addSource(walletSource, value ->
                wallets.setValue(value != null ? new ArrayList<>(value) : new ArrayList<>()));
        totalBalance.addSource(totalBalanceSource, value ->
                totalBalance.setValue(value != null ? value : 0d));
        wallets.addSource(container.walletRepository.getLocalWriteEvents(), this::onWalletWritten);
    }

    public LiveData<List<WalletWithBalance>> getWallets() {
        return wallets;
    }

    public LiveData<Double> getTotalBalance() {
        return totalBalance;
    }

    private void onWalletWritten(@Nullable WalletRepository.LocalWriteEvent event) {
        if (event == null || !userId.equals(event.getUserId())) {
            return;
        }
        container.walletRepository.loadOverviewSnapshot(
                userId,
                new WalletRepository.OverviewSnapshotCallback() {
                    @Override
                    public void onSuccess(@NonNull List<WalletWithBalance> walletSnapshot,
                                          double totalBalanceSnapshot) {
                        wallets.setValue(new ArrayList<>(walletSnapshot));
                        totalBalance.setValue(totalBalanceSnapshot);
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        // Room LiveData remains the fallback source if the eager snapshot fails.
                    }
                }
        );
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
        WalletEntity updatedWallet = copyWallet(wallet);
        updatedWallet.setName(name);
        updatedWallet.setType(type.name());
        updatedWallet.setBalance(balance);
        updatedWallet.setIconName(iconName);
        updatedWallet.setUpdatedAt(System.currentTimeMillis());
        updatedWallet.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        container.walletRepository.update(updatedWallet, callback);
    }

    @NonNull
    private WalletEntity copyWallet(@NonNull WalletEntity source) {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(source.getId());
        wallet.setUserId(source.getUserId());
        wallet.setName(source.getName());
        wallet.setBalance(source.getBalance());
        wallet.setType(source.getType());
        wallet.setIconName(source.getIconName());
        wallet.setArchived(source.isArchived());
        wallet.setExcluded(source.isExcluded());
        wallet.setUpdatedAt(source.getUpdatedAt());
        wallet.setSyncStatus(source.getSyncStatus());
        wallet.setDeleted(source.isDeleted());
        wallet.setCreatedAt(source.getCreatedAt());
        return wallet;
    }

    public void deleteWallet(WalletEntity wallet) {
        container.walletRepository.softDelete(wallet);
    }

    public void deleteWallet(WalletEntity wallet,
                             com.group10.moneymate.data.repository.WalletRepository.WriteCallback callback) {
        container.walletRepository.softDelete(wallet, callback);
    }

    public void archiveWallet(WalletEntity wallet) {
        container.walletRepository.archive(wallet);
    }

    public void archiveWallet(WalletEntity wallet,
                              com.group10.moneymate.data.repository.WalletRepository.WriteCallback callback) {
        container.walletRepository.archive(wallet, callback);
    }

    public void restoreWallet(WalletEntity wallet) {
        container.walletRepository.restore(wallet);
    }

    public void restoreWallet(WalletEntity wallet,
                              com.group10.moneymate.data.repository.WalletRepository.WriteCallback callback) {
        container.walletRepository.restore(wallet, callback);
    }
}
