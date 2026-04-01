package com.group10.moneymate.ui.transaction;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.data.repository.WalletRepository;
import com.group10.moneymate.di.MoneyMateApplication;

import java.util.List;

public class TransactionViewModel extends AndroidViewModel {

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final String userId;

    // ─── Transactions list ────────────────────────────────────────────────────
    private final LiveData<List<TransactionEntity>> allTransactions;

    // ─── Filter state ─────────────────────────────────────────────────────────
    /** null = show all, "INCOME"/"EXPENSE" = filter by type */
    private final MutableLiveData<String> filterType = new MutableLiveData<>(null);

    private final LiveData<List<TransactionEntity>> filteredTransactions;

    // ─── Search ───────────────────────────────────────────────────────────────
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final LiveData<List<TransactionEntity>> searchResults;

    // ─── Wallets & Categories (cho picker) ───────────────────────────────────
    private final LiveData<List<WalletEntity>> wallets;
    private final LiveData<List<WalletWithBalance>> walletsWithBalance;
    private final LiveData<List<WalletEntity>> activeWallets;
    private final LiveData<List<CategoryEntity>> expenseCategories;
    private final LiveData<List<CategoryEntity>> incomeCategories;
    private final LiveData<List<CategoryEntity>> expenseCategoriesIncludingDeleted;
    private final LiveData<List<CategoryEntity>> incomeCategoriesIncludingDeleted;

    // ─── Giao dịch đang edit ─────────────────────────────────────────────────
    private final MutableLiveData<TransactionEntity> selectedTransaction = new MutableLiveData<>();

    public TransactionViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        transactionRepository = app.getAppContainer().transactionRepository;
        WalletRepository walletRepository = app.getAppContainer().walletRepository;
        categoryRepository = app.getAppContainer().categoryRepository;

        userId = app.getAppContainer().authRepository.getCurrentUserId();

        allTransactions = transactionRepository.getAllTransactions(userId);

        // Filter theo type (switchMap: khi filterType thay đổi → query lại)
        filteredTransactions = Transformations.switchMap(filterType, type -> {
            if (type == null || type.isEmpty()) {
                return transactionRepository.getAllTransactions(userId);
            } else {
                return transactionRepository.getTransactionsByType(userId, type);
            }
        });

        // Search
        searchResults = Transformations.switchMap(searchQuery, query -> {
            if (query == null || query.trim().isEmpty()) {
                return transactionRepository.getAllTransactions(userId);
            }
            return transactionRepository.searchTransactions(userId, query.trim());
        });

        wallets = walletRepository.getAllByUser(userId);
        walletsWithBalance = walletRepository.getAllByUserWithBalance(userId);
        activeWallets = walletRepository.getActiveByUser(userId);
        expenseCategories = categoryRepository.getCategoriesByType(userId, "EXPENSE");
        incomeCategories  = categoryRepository.getCategoriesByType(userId, "INCOME");
        expenseCategoriesIncludingDeleted =
                categoryRepository.getCategoriesByTypeIncludingDeleted(userId, "EXPENSE");
        incomeCategoriesIncludingDeleted =
                categoryRepository.getCategoriesByTypeIncludingDeleted(userId, "INCOME");
    }

    // ─── Expose LiveData ──────────────────────────────────────────────────────

    public LiveData<List<TransactionEntity>> getAllTransactions() {
        return allTransactions;
    }

    public LiveData<List<TransactionEntity>> getFilteredTransactions() {
        return filteredTransactions;
    }

    public LiveData<List<TransactionEntity>> getSearchResults() {
        return searchResults;
    }

    public LiveData<List<WalletEntity>> getWallets() {
        return wallets;
    }

    public LiveData<List<WalletWithBalance>> getWalletsWithBalance() {
        return walletsWithBalance;
    }

    public LiveData<List<WalletEntity>> getActiveWallets() {
        return activeWallets;
    }

    public LiveData<List<CategoryEntity>> getExpenseCategories() {
        return expenseCategories;
    }

    public LiveData<List<CategoryEntity>> getIncomeCategories() {
        return incomeCategories;
    }

    public LiveData<List<CategoryEntity>> getExpenseCategoriesIncludingDeleted() {
        return expenseCategoriesIncludingDeleted;
    }

    public LiveData<List<CategoryEntity>> getIncomeCategoriesIncludingDeleted() {
        return incomeCategoriesIncludingDeleted;
    }

    public LiveData<CategoryEntity> getCategoryById(String id) {
        return categoryRepository.getCategoryById(id);
    }

    public LiveData<CategoryEntity> getCategoryByIdIncludingDeleted(String id) {
        return categoryRepository.getCategoryByIdIncludingDeleted(id);
    }

    public LiveData<TransactionEntity> getSelectedTransaction() {
        return selectedTransaction;
    }

    // ─── Filter & Search ──────────────────────────────────────────────────────

    public void setFilterType(String type) {
        filterType.setValue(type);
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query);
    }

    // ─── Load transaction by id (cho Edit mode) ───────────────────────────────

    public LiveData<TransactionEntity> getTransactionById(String id) {
        return transactionRepository.getTransactionById(id);
    }

    public LiveData<List<TransactionEntity>> getTransactionsForBudget(@Nullable String categoryId,
                                                                      @Nullable String walletId,
                                                                      long startDate,
                                                                      long endDate) {
        return transactionRepository.getTransactionsForBudget(
                userId,
                categoryId,
                walletId,
                startDate,
                endDate
        );
    }

    public LiveData<List<TransactionEntity>> getExpenseTransactionsByRange(long startDate,
                                                                           long endDate) {
        return transactionRepository.getExpenseTransactionsByRange(userId, startDate, endDate);
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    public void insertTransaction(TransactionEntity transaction) {
        transactionRepository.insertTransaction(transaction);
    }

    public void updateTransaction(TransactionEntity oldTransaction, TransactionEntity newTransaction) {
        transactionRepository.updateTransaction(oldTransaction, newTransaction);
    }

    public void deleteTransaction(TransactionEntity transaction) {
        transactionRepository.softDeleteTransaction(transaction);
    }
}
