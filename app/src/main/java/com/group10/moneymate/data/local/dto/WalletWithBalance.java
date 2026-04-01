package com.group10.moneymate.data.local.dto;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Embedded;

import com.group10.moneymate.data.local.entity.WalletEntity;

public class WalletWithBalance {

    @Embedded
    private WalletEntity wallet;

    @ColumnInfo(name = "current_balance")
    private double currentBalance;

    @NonNull
    public WalletEntity getWallet() {
        return wallet;
    }

    public void setWallet(@NonNull WalletEntity wallet) {
        this.wallet = wallet;
    }

    public double getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(double currentBalance) {
        this.currentBalance = currentBalance;
    }
}
