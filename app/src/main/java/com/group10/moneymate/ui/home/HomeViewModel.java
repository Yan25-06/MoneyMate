package com.group10.moneymate.ui.home;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;

import java.util.Calendar;
import java.util.List;

/**
 * ViewModel for the home/dashboard screen.
 */
public class HomeViewModel extends AndroidViewModel {

    private final LiveData<List<WalletEntity>> wallets;
    private final LiveData<Double> totalBalance;
    private final LiveData<List<TransactionEntity>> recentTransactions;
    private final LiveData<Double> monthlyIncome;
    private final LiveData<Double> monthlyExpense;

    public HomeViewModel(@NonNull Application application) {
        super(application);

        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        String userId = container.authRepository.getCurrentUserId();

        // 1. Lấy danh sách ví
        wallets = container.walletRepository.getAllByUser(userId);

        // 2. Tính tổng số dư ví (loại trừ ví bị excluded)
        totalBalance = Transformations.map(wallets, walletList -> {
            double total = 0.0;
            if (walletList != null) {
                for (WalletEntity w : walletList) {
                    if (!w.isExcluded()) total += w.getBalance();
                }
            }
            return total;
        });

        // 3. Lấy 5 giao dịch gần nhất
        recentTransactions = container.transactionRepository.getRecentTransactions(userId, 5);

        // 4. Lấy thu chi tháng này (tính Start Date và End Date của tháng hiện tại)
        long[] monthBounds = getCurrentMonthBounds();
        long startDate = monthBounds[0];
        long endDate = monthBounds[1];

        monthlyIncome = container.transactionRepository.getTotalIncome(userId, startDate, endDate);
        monthlyExpense = container.transactionRepository.getTotalExpense(userId, startDate, endDate);
    }

    /**
     * Hàm helper để lấy millisecond đầu tháng và cuối tháng hiện tại.
     * Trả về mảng 2 phần tử: [0] là startDate, [1] là endDate.
     */
    private long[] getCurrentMonthBounds() {
        Calendar calendar = Calendar.getInstance();

        // Xét ngày đầu tháng, 00:00:00
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startOfMonth = calendar.getTimeInMillis();

        // Xét ngày cuối tháng, 23:59:59
        calendar.add(Calendar.MONTH, 1);
        calendar.add(Calendar.MILLISECOND, -1);
        long endOfMonth = calendar.getTimeInMillis();

        return new long[]{startOfMonth, endOfMonth};
    }

    public LiveData<List<WalletEntity>> getWallets() { return wallets; }
    public LiveData<Double> getTotalBalance() { return totalBalance; }
    public LiveData<List<TransactionEntity>> getRecentTransactions() { return recentTransactions; }
    public LiveData<Double> getMonthlyIncome() { return monthlyIncome; }
    public LiveData<Double> getMonthlyExpense() { return monthlyExpense; }
}