package com.group10.moneymate.ui.debt;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.repository.DebtRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.models.DebtType;

import java.util.List;

public class DebtViewModel extends AndroidViewModel {

    private final DebtRepository debtRepository;
    private final String userId;

    private final MutableLiveData<String> selectedDebtId = new MutableLiveData<>();
    private final LiveData<DebtEntity> selectedDebt;

    public DebtViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        debtRepository = container.debtRepository;
        userId = container.prefsManager.getUid();

        selectedDebt = Transformations.switchMap(selectedDebtId, id -> {
            if (id == null || id.isEmpty()) {
                return new MutableLiveData<>(null);
            }
            return debtRepository.getDebtById(id);
        });
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    public LiveData<List<DebtEntity>> getAllDebts() {
        return debtRepository.getAllDebts(userId);
    }

    public LiveData<List<DebtEntity>> getDebtsByType(String type) {
        return debtRepository.getDebtsByType(userId, type);
    }

    public LiveData<List<DebtEntity>> getDebtsByTypeAndStatus(String type, String status) {
        return debtRepository.getDebtsByTypeAndStatus(userId, type, status);
    }

    public LiveData<List<DebtEntity>> getOngoingDebtsByType(String type) {
        return debtRepository.getOngoingDebtsByType(userId, type);
    }

    public LiveData<DebtEntity> getDebtById(String id) {
        return debtRepository.getDebtById(id);
    }

    public void selectDebt(@Nullable String debtId) {
        selectedDebtId.setValue(debtId);
    }

    public LiveData<DebtEntity> getSelectedDebt() {
        return selectedDebt;
    }

    public LiveData<List<TransactionEntity>> getTransactionsByDebtId(String debtId) {
        return debtRepository.getTransactionsByDebtId(debtId);
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    public void createDebtWithTransaction(@NonNull DebtEntity debt,
                                           @NonNull TransactionEntity transaction,
                                           @Nullable DebtRepository.WriteCallback callback) {
        debt.setUserId(userId);
        transaction.setUserId(userId);
        debtRepository.createDebtWithTransaction(debt, transaction, callback);
    }

    public void createCashbackTransaction(@NonNull String debtId,
                                           double amount,
                                           @NonNull TransactionEntity transaction,
                                           @Nullable DebtRepository.WriteCallback callback) {
        transaction.setUserId(userId);
        debtRepository.createCashbackTransaction(debtId, amount, transaction, callback);
    }

    public void insert(DebtEntity debt) {
        debt.setUserId(userId);
        debtRepository.insert(debt);
    }

    public void update(DebtEntity debt) {
        debtRepository.update(debt);
    }

    public void softDelete(DebtEntity debt) {
        debtRepository.softDelete(debt);
    }

    public void deleteDebtWithTransactions(@NonNull DebtEntity debt,
                                            @Nullable DebtRepository.WriteCallback callback) {
        debtRepository.deleteDebtWithTransactions(debt, callback);
    }

    public void updateDebtDetails(@NonNull DebtEntity debt,
                                   @Nullable DebtRepository.WriteCallback callback) {
        debtRepository.updateDebtDetails(debt, callback);
    }

    public void handleDebtTransactionDelete(@NonNull TransactionEntity transaction,
                                             @Nullable DebtRepository.HandleDebtTransactionDeleteCallback callback) {
        debtRepository.handleDebtTransactionDelete(transaction, callback);
    }

    public void recalcDebtAfterTransactionDelete(@NonNull String debtId,
                                                  @Nullable DebtRepository.WriteCallback callback) {
        debtRepository.recalcDebtAfterTransactionDelete(debtId, callback);
    }

    public String getUserId() {
        return userId;
    }
}