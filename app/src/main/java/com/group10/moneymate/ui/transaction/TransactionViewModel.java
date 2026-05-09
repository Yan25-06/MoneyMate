package com.group10.moneymate.ui.transaction;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
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
import com.group10.moneymate.ui.common.DebounceableAndroidViewModel;
import com.group10.moneymate.utils.DistinctLiveData;

import java.util.ArrayList;
import java.util.List;

public class TransactionViewModel extends DebounceableAndroidViewModel {

    public static class FilterParams {
        @Nullable
        private final String walletId;

        public FilterParams(@Nullable String walletId) {
            this.walletId = walletId;
        }

        @Nullable
        public String getWalletId() {
            return walletId;
        }
    }

    private final TransactionRepository transactionRepository;
    private final CategoryRepository categoryRepository;
    private final String userId;

    // ─── Transactions list ────────────────────────────────────────────────────
    private static final int PAGE_SIZE = 30;
    private final MediatorLiveData<List<TransactionEntity>> allTransactions = new MediatorLiveData<>();
    private final MutableLiveData<Integer> transactionWindowLimit = new MutableLiveData<>(PAGE_SIZE);
    private final MutableLiveData<Boolean> isLoadingMore = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> hasMore = new MutableLiveData<>(true);
    private int requestedTransactionWindowLimit = PAGE_SIZE;
    @Nullable
    private LiveData<List<TransactionEntity>> transactionWindowSource;

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

        allTransactions.setValue(new ArrayList<>());
        allTransactions.addSource(transactionWindowLimit, this::observeTransactionWindow);
        allTransactions.addSource(transactionRepository.getLocalWriteEvents(), this::applyLocalWriteEvent);
        resetPagination();

        // Filter theo type (switchMap: khi filterType thay đổi → query lại)
        filteredTransactions = DistinctLiveData.distinctUntilChanged(Transformations.switchMap(filterType, type -> {
            if (type == null || type.isEmpty()) {
                return allTransactions;
            } else {
                return transactionRepository.getTransactionsByType(userId, type);
            }
        }));

        // Search
        searchResults = DistinctLiveData.distinctUntilChanged(Transformations.switchMap(searchQuery, query -> {
            if (query == null || query.trim().isEmpty()) {
                return allTransactions;
            }
            return transactionRepository.searchTransactions(userId, query.trim());
        }));

        wallets = DistinctLiveData.distinctUntilChanged(walletRepository.getAllByUser(userId));
        walletsWithBalance = DistinctLiveData.distinctUntilChanged(walletRepository.getAllByUserWithBalance(userId));
        activeWallets = DistinctLiveData.distinctUntilChanged(walletRepository.getActiveByUser(userId));
        expenseCategories = DistinctLiveData.distinctUntilChanged(categoryRepository.getCategoriesByType(userId, "EXPENSE"));
        incomeCategories  = DistinctLiveData.distinctUntilChanged(categoryRepository.getCategoriesByType(userId, "INCOME"));
        expenseCategoriesIncludingDeleted =
                DistinctLiveData.distinctUntilChanged(
                        categoryRepository.getCategoriesByTypeIncludingDeleted(userId, "EXPENSE")
                );
        incomeCategoriesIncludingDeleted =
                DistinctLiveData.distinctUntilChanged(
                        categoryRepository.getCategoriesByTypeIncludingDeleted(userId, "INCOME")
                );
    }

    // ─── Expose LiveData ──────────────────────────────────────────────────────

    public LiveData<List<TransactionEntity>> getAllTransactions() {
        return allTransactions;
    }

    public LiveData<Boolean> getIsLoadingMore() {
        return isLoadingMore;
    }

    public LiveData<Boolean> getHasMore() {
        return hasMore;
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

    public LiveData<List<CategoryEntity>> getExpenseCategoriesForWallet(@Nullable String walletId) {
        return categoryRepository.getCategoriesByTypeAndWallet(userId, "EXPENSE", walletId);
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
        debounce(() -> filterType.setValue(type), 80L);
    }

    public void setSearchQuery(String query) {
        debounce(() -> searchQuery.setValue(query), 120L);
    }

    public void resetPagination() {
        requestedTransactionWindowLimit = PAGE_SIZE;
        allTransactions.setValue(new ArrayList<>());
        hasMore.setValue(true);
        isLoadingMore.setValue(false);
        Integer currentLimit = transactionWindowLimit.getValue();
        if (currentLimit == null || currentLimit != PAGE_SIZE) {
            transactionWindowLimit.setValue(PAGE_SIZE);
        } else {
            observeTransactionWindow(PAGE_SIZE);
        }
    }

    public void loadNextPage() {
        if (Boolean.TRUE.equals(isLoadingMore.getValue()) || Boolean.FALSE.equals(hasMore.getValue())) {
            return;
        }
        isLoadingMore.setValue(true);
        requestedTransactionWindowLimit += PAGE_SIZE;
        transactionWindowLimit.setValue(requestedTransactionWindowLimit);
    }

    public void applyFilter(@Nullable FilterParams filterParams) {
        // Placeholder for future filter-specific keyset sources.
        resetPagination();
    }

    public void resetPaginationForNewFilter() {
        resetPagination();
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

    public void insertTransaction(TransactionEntity transaction,
                                  @Nullable TransactionRepository.WriteCallback callback) {
        transactionRepository.insertTransaction(transaction, callback);
    }

    public void insertTransactions(@NonNull List<TransactionEntity> transactions,
                                   @Nullable TransactionRepository.WriteCallback callback) {
        transactionRepository.insertTransactions(transactions, callback);
    }

    public void checkOcrDuplicateCandidates(
            @NonNull List<TransactionRepository.OcrDuplicateCandidate> candidates,
            @NonNull TransactionRepository.DuplicateCheckCallback callback
    ) {
        transactionRepository.checkOcrDuplicateCandidates(userId, candidates, callback);
    }

    public void updateTransaction(TransactionEntity newTransaction) {
        transactionRepository.updateTransaction(newTransaction);
    }

    public void updateTransaction(TransactionEntity newTransaction,
                                  @Nullable TransactionRepository.WriteCallback callback) {
        transactionRepository.updateTransaction(newTransaction, callback);
    }

    public void deleteTransaction(TransactionEntity transaction) {
        transactionRepository.softDeleteTransaction(transaction);
    }

    public void deleteTransaction(TransactionEntity transaction,
                                  @Nullable TransactionRepository.WriteCallback callback) {
        transactionRepository.softDeleteTransaction(transaction, callback);
    }

    private void observeTransactionWindow(@Nullable Integer limitValue) {
        int limit = limitValue != null && limitValue > 0 ? limitValue : PAGE_SIZE;
        if (transactionWindowSource != null) {
            allTransactions.removeSource(transactionWindowSource);
        }
        transactionWindowSource = transactionRepository.getTransactionsWindow(userId, limit);
        allTransactions.addSource(transactionWindowSource, transactions -> {
            List<TransactionEntity> snapshot = transactions != null
                    ? new ArrayList<>(transactions)
                    : new ArrayList<>();
            allTransactions.setValue(snapshot);
            hasMore.setValue(snapshot.size() >= limit);
            isLoadingMore.setValue(false);
        });
    }

    private void applyLocalWriteEvent(@Nullable TransactionRepository.LocalWriteEvent event) {
        if (event == null) {
            return;
        }
        List<TransactionEntity> current = allTransactions.getValue();
        List<TransactionEntity> next = current != null ? new ArrayList<>(current) : new ArrayList<>();
        String eventTransactionId = event.getTransactionId();
        if (eventTransactionId != null) {
            for (int i = next.size() - 1; i >= 0; i--) {
                if (eventTransactionId.equals(next.get(i).getId())) {
                    next.remove(i);
                }
            }
        }
        if (TransactionRepository.LocalWriteEvent.TYPE_UPSERT.equals(event.getType())) {
            TransactionEntity transaction = event.getTransaction();
            if (transaction == null || transaction.isDeleted() || !userId.equals(transaction.getUserId())) {
                return;
            }
            next.add(transaction);
            next.sort((left, right) -> {
                int timestampCompare = Long.compare(right.getTimestamp(), left.getTimestamp());
                if (timestampCompare != 0) {
                    return timestampCompare;
                }
                return right.getId().compareTo(left.getId());
            });
            int maxWindow = Math.max(PAGE_SIZE, requestedTransactionWindowLimit);
            if (next.size() > maxWindow) {
                next = new ArrayList<>(next.subList(0, maxWindow));
            }
        }
        allTransactions.setValue(next);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }

    @Nullable
    public String getCurrentUserId() {
        return userId;
    }
}
