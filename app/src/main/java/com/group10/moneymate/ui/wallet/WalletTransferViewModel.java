package com.group10.moneymate.ui.wallet;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ViewModel cho màn hình chuyển tiền giữa 2 ví.
 * Lookup danh mục chuyển tiền (Tiền chuyển đi / Tiền chuyển đến) từ DB trên background thread.
 */
public class WalletTransferViewModel extends AndroidViewModel {

    public interface TransferCallback {
        void onSuccess();
        void onError(@NonNull String message);
    }

    private final AppContainer container;
    private final String userId;

    // Resolved transfer category IDs
    private String transferCategoryOutId;
    private String transferCategoryInId;

    private final MutableLiveData<Boolean> categoriesReady = new MutableLiveData<>();

    public WalletTransferViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        container = app.getAppContainer();
        userId = container.authRepository.getCurrentUserId();
        resolveTransferCategories();
    }

    /**
     * Resolve the transfer category IDs on a background thread.
     * Called once when the ViewModel is created.
     */
    private void resolveTransferCategories() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            CategoryEntity outCategory = container.database.categoryDao().getCategoryByIdSync(
                    Constants.CATEGORY_ID_EXP_TRANSFER_OUT);
            CategoryEntity inCategory = container.database.categoryDao().getCategoryByIdSync(
                    Constants.CATEGORY_ID_INC_TRANSFER_IN);
            if (outCategory != null) {
                transferCategoryOutId = outCategory.getId();
            }
            if (inCategory != null) {
                transferCategoryInId = inCategory.getId();
            }
            categoriesReady.postValue(outCategory != null && inCategory != null);
        });
    }

    @NonNull
    public LiveData<Boolean> getCategoriesReady() {
        return categoriesReady;
    }

    @Nullable
    public String getTransferCategoryOutId() {
        return transferCategoryOutId;
    }

    @Nullable
    public String getTransferCategoryInId() {
        return transferCategoryInId;
    }

    /**
     * Lấy danh sách ví chưa bị archive và chưa bị xóa.
     */
    @NonNull
    public LiveData<List<WalletEntity>> getActiveWallets() {
        return container.walletRepository.getActiveByUser(userId);
    }

    @NonNull
    public LiveData<WalletEntity> getWalletById(@NonNull String walletId) {
        return container.walletRepository.getById(walletId);
    }

    /**
     * Thực hiện chuyển tiền: tạo 2 (hoặc 3) transactions trong 1 DB transaction.
     *
     * @param amount    Số tiền chuyển
     * @param fromWalletId ID ví nguồn
     * @param toWalletId   ID ví đích
     * @param fromNote  Ghi chú cho giao dịch chi
     * @param toNote    Ghi chú cho giao dịch thu
     * @param timestamp Thời gian giao dịch
     * @param hasFee    Có phí hay không
     * @param feeAmount Số tiền phí (nếu hasFee = true)
     * @param feeNote   Ghi chú phí
     * @param callback  Callback để thông báo kết quả
     */
    public void executeTransfer(double amount,
                                @NonNull String fromWalletId,
                                @NonNull String toWalletId,
                                @NonNull String fromNote,
                                @NonNull String toNote,
                                long timestamp,
                                boolean hasFee,
                                double feeAmount,
                                @NonNull String feeNote,
                                @NonNull TransferCallback callback) {
        if (transferCategoryOutId == null || transferCategoryInId == null) {
            callback.onError("transfer_error_category_not_found");
            return;
        }

        List<TransactionEntity> transactions = new ArrayList<>();

        // Transaction 1: EXPENSE on FROM wallet (Tiền chuyển đi)
        TransactionEntity outTransaction = new TransactionEntity();
        outTransaction.setId(UUID.randomUUID().toString());
        outTransaction.setUserId(userId);
        outTransaction.setWalletId(fromWalletId);
        outTransaction.setToWalletId(toWalletId);
        outTransaction.setCategoryId(transferCategoryOutId);
        outTransaction.setType(Constants.TYPE_EXPENSE);
        outTransaction.setAmount(amount);
        outTransaction.setNote(fromNote);
        outTransaction.setTimestamp(timestamp);
        transactions.add(outTransaction);

        // Transaction 2: INCOME on TO wallet (Tiền chuyển đến)
        TransactionEntity inTransaction = new TransactionEntity();
        inTransaction.setId(UUID.randomUUID().toString());
        inTransaction.setUserId(userId);
        inTransaction.setWalletId(toWalletId);
        inTransaction.setToWalletId(fromWalletId);
        inTransaction.setCategoryId(transferCategoryInId);
        inTransaction.setType(Constants.TYPE_INCOME);
        inTransaction.setAmount(amount);
        inTransaction.setNote(toNote);
        inTransaction.setTimestamp(timestamp);
        transactions.add(inTransaction);

        // Transaction 3 (optional): EXPENSE for fee on FROM wallet
        if (hasFee && feeAmount > 0) {
            TransactionEntity feeTransaction = new TransactionEntity();
            feeTransaction.setId(UUID.randomUUID().toString());
            feeTransaction.setUserId(userId);
            feeTransaction.setWalletId(fromWalletId);
            feeTransaction.setCategoryId(transferCategoryOutId);
            feeTransaction.setType(Constants.TYPE_EXPENSE);
            feeTransaction.setAmount(feeAmount);
            feeTransaction.setNote(feeNote);
            feeTransaction.setTimestamp(timestamp);
            transactions.add(feeTransaction);
        }

        container.transactionRepository.insertTransactions(transactions, new TransactionRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                callback.onSuccess();
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                callback.onError(throwable.getMessage() != null ? throwable.getMessage() : "Unknown error");
            }
        });
    }
}
