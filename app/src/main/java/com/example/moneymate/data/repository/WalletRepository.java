package com.example.moneymate.data.repository;

import androidx.lifecycle.LiveData;

import com.example.moneymate.data.local.dao.WalletDao;
import com.example.moneymate.data.local.entity.WalletEntity;

import java.util.List;

/**
 * Repository for wallet data.
 */
public class WalletRepository {
    private final WalletDao walletDao;

    public WalletRepository(WalletDao walletDao) {
        this.walletDao = walletDao;
    }

    public LiveData<List<WalletEntity>> getAllWallets(String userId) {
        return walletDao.getAllWallets(userId);
    }

    public LiveData<WalletEntity> getWalletById(String id) {
        return walletDao.getWalletById(id);
    }

    public LiveData<Double> getTotalBalance(String userId) {
        return walletDao.getTotalBalance(userId);
    }

    public void insertWallet(WalletEntity wallet) {
        walletDao.insertWallet(wallet);
    }

    public void updateWallet(WalletEntity wallet) {
        walletDao.updateWallet(wallet);
    }

    public void deleteWallet(WalletEntity wallet) {
        walletDao.deleteWallet(wallet);
    }
}
