package com.group10.moneymate.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.sqlite.db.SimpleSQLiteQuery;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.TransactionDao;
import com.group10.moneymate.data.local.dao.WalletDao;
import com.group10.moneymate.data.local.dto.CategorySumDTO;
import com.group10.moneymate.data.local.dto.DailyTrendDTO;
import com.group10.moneymate.data.local.dto.NetIncomeDTO;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.utils.ReceiptImageHashUtils;
import com.group10.moneymate.workers.SyncScheduler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Repository for transaction data.
 * Mọi write operation chạy trên {@link AppDatabase#databaseWriteExecutor}.
 */
public class TransactionRepository {

    public interface PageCallback<T> {
        void onSuccess(T data);

        void onError(Exception exception);
    }

    public interface WriteCallback {
        void onSuccess();

        void onError(@NonNull Throwable throwable);
    }

    public interface DuplicateCheckCallback {
        void onCompleted(@NonNull DuplicateCheckResult result);

        void onError(@NonNull Throwable throwable);
    }

    public static final class LocalWriteEvent {
        public static final String TYPE_UPSERT = "upsert";
        public static final String TYPE_DELETE = "delete";

        @NonNull
        private final String type;
        @Nullable
        private final TransactionEntity transaction;
        @Nullable
        private final String transactionId;

        private LocalWriteEvent(@NonNull String type,
                @Nullable TransactionEntity transaction,
                @Nullable String transactionId) {
            this.type = type;
            this.transaction = transaction;
            this.transactionId = transactionId;
        }

        @NonNull
        public static LocalWriteEvent upsert(@NonNull TransactionEntity transaction) {
            return new LocalWriteEvent(TYPE_UPSERT, transaction, transaction.getId());
        }

        @NonNull
        public static LocalWriteEvent delete(@NonNull String transactionId) {
            return new LocalWriteEvent(TYPE_DELETE, null, transactionId);
        }

        @NonNull
        public String getType() {
            return type;
        }

        @Nullable
        public TransactionEntity getTransaction() {
            return transaction;
        }

        @Nullable
        public String getTransactionId() {
            return transactionId;
        }
    }

    public static final class OcrDuplicateCandidate {
        @NonNull
        private final String candidateId;
        @Nullable
        private final String imagePath;
        private final double amount;
        private final long timestamp;
        @NonNull
        private final String note;

        public OcrDuplicateCandidate(@NonNull String candidateId,
                @Nullable String imagePath,
                double amount,
                long timestamp,
                @Nullable String note) {
            this.candidateId = candidateId;
            this.imagePath = imagePath;
            this.amount = amount;
            this.timestamp = timestamp;
            this.note = note == null ? "" : note.trim();
        }

        @NonNull
        public String getCandidateId() {
            return candidateId;
        }

        @Nullable
        public String getImagePath() {
            return imagePath;
        }

        public double getAmount() {
            return amount;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @NonNull
        public String getNote() {
            return note;
        }
    }

    public static final class SuspectedDuplicate {
        @NonNull
        private final String candidateId;
        @NonNull
        private final String existingTransactionId;
        private final double amount;
        private final long existingTimestamp;
        @NonNull
        private final String existingNote;

        public SuspectedDuplicate(@NonNull String candidateId,
                @NonNull String existingTransactionId,
                double amount,
                long existingTimestamp,
                @Nullable String existingNote) {
            this.candidateId = candidateId;
            this.existingTransactionId = existingTransactionId;
            this.amount = amount;
            this.existingTimestamp = existingTimestamp;
            this.existingNote = existingNote == null ? "" : existingNote.trim();
        }

        @NonNull
        public String getCandidateId() {
            return candidateId;
        }

        @NonNull
        public String getExistingTransactionId() {
            return existingTransactionId;
        }

        public double getAmount() {
            return amount;
        }

        public long getExistingTimestamp() {
            return existingTimestamp;
        }

        @NonNull
        public String getExistingNote() {
            return existingNote;
        }
    }

    public static final class DuplicateCheckResult {
        @NonNull
        private final List<SuspectedDuplicate> suspectedDuplicates;

        public DuplicateCheckResult(@NonNull List<SuspectedDuplicate> suspectedDuplicates) {
            this.suspectedDuplicates = suspectedDuplicates;
        }

        @NonNull
        public List<SuspectedDuplicate> getSuspectedDuplicates() {
            return suspectedDuplicates;
        }

        public boolean hasSuspectedDuplicates() {
            return !suspectedDuplicates.isEmpty();
        }
    }

    public static class TransactionValidationException extends IllegalArgumentException {
        public TransactionValidationException(@NonNull String message) {
            super(message);
        }
    }

    public static class DuplicateCheckException extends RuntimeException {
        public DuplicateCheckException(@NonNull String message, @NonNull Throwable cause) {
            super(message, cause);
        }
    }

    private static final long OCR_DUPLICATE_TIME_BUCKET_MS = 2L * 60L * 1000L;
    private static final double OCR_DUPLICATE_AMOUNT_TOLERANCE = 0.005d;

    private final TransactionDao transactionDao;
    private final WalletDao walletDao;
    private final AppDatabase appDatabase;
    @Nullable
    private final SyncScheduler syncScheduler;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<LocalWriteEvent> localWriteEvents = new MutableLiveData<>();
    // Keep updated_at strictly increasing so MAX(updated_at)-based invalidation
    // cannot miss rapid consecutive writes that land in the same millisecond.
    private static final AtomicLong LAST_WRITE_TIMESTAMP = new AtomicLong(0L);

    public TransactionRepository(@NonNull AppDatabase appDatabase,
            @NonNull TransactionDao transactionDao,
            @NonNull WalletDao walletDao) {
        this(appDatabase, transactionDao, walletDao, null);
    }

    public TransactionRepository(@NonNull AppDatabase appDatabase,
            @NonNull TransactionDao transactionDao,
            @NonNull WalletDao walletDao,
            @Nullable SyncScheduler syncScheduler) {
        this.appDatabase = appDatabase;
        this.transactionDao = transactionDao;
        this.walletDao = walletDao;
        this.syncScheduler = syncScheduler;
    }

    // ─── Read ─────────────────────────────────────────────────────────────────

    public LiveData<List<TransactionEntity>> getAllTransactions(String userId) {
        return transactionDao.getAllTransactions(userId);
    }

    public LiveData<List<TransactionEntity>> getTransactionsWindow(String userId, int limit) {
        return transactionDao.getTransactionsWindow(userId, limit);
    }

    public LiveData<Long> getTransactionInvalidationKey(String userId) {
        return transactionDao.getTransactionInvalidationKey(userId);
    }

    public LiveData<LocalWriteEvent> getLocalWriteEvents() {
        return localWriteEvents;
    }

    public LiveData<List<TransactionEntity>> getRecentTransactions(String userId, int limit) {
        return transactionDao.getRecentTransactions(userId, limit);
    }

    public LiveData<TransactionEntity> getTransactionById(String id) {
        return transactionDao.getTransactionById(id);
    }

    public LiveData<List<TransactionEntity>> getTransactionsByDateRange(String userId, long startDate, long endDate) {
        return transactionDao.getTransactionsByDateRange(userId, startDate, endDate);
    }

    public LiveData<List<TransactionEntity>> getTransactionsByType(String userId, String type) {
        return transactionDao.getTransactionsByType(userId, type);
    }

    public LiveData<List<TransactionEntity>> getTransactionsByCategory(String userId, String categoryId) {
        return transactionDao.getTransactionsByCategory(userId, categoryId);
    }

    public LiveData<List<TransactionEntity>> getTransactionsForBudget(String userId,
            String categoryId,
            String walletId,
            long startDate,
            long endDate) {
        if (Constants.isOtherCategoryId(categoryId)) {
            return transactionDao.getTransactionsForOtherCategories(
                    userId,
                    startDate,
                    endDate,
                    walletId,
                    Constants.CATEGORY_ID_OTHER,
                    Constants.CATEGORY_ID_OTHER_LEGACY);
        }
        return transactionDao.getTransactionsForBudget(userId, categoryId, walletId, startDate, endDate);
    }

    public LiveData<List<TransactionEntity>> getExpenseTransactionsByRange(String userId,
            long startDate,
            long endDate) {
        return transactionDao.getExpenseTransactionsByRange(userId, startDate, endDate);
    }

    public LiveData<List<TransactionEntity>> getTransactionsByWallet(String userId, String walletId) {
        return transactionDao.getTransactionsByWallet(userId, walletId);
    }

    public LiveData<List<TransactionEntity>> searchTransactions(String userId, String keyword) {
        return transactionDao.searchTransactions(userId, keyword);
    }

    public LiveData<List<TransactionEntity>> searchTransactionsAdvanced(
            String userId,
            @Nullable String keyword,
            @Nullable String amountMode,
            @Nullable Double amountValue,
            @Nullable Double amountMin,
            @Nullable Double amountMax,
            @Nullable String timeMode,
            @Nullable Long timeValue,
            @Nullable Long timeStart,
            @Nullable Long timeEnd,
            @Nullable String walletId,
            @Nullable String categoryId) {
        StringBuilder query = new StringBuilder();
        query.append("SELECT t.* FROM transactions t ");
        query.append("INNER JOIN wallets w ON w.id = t.wallet_id AND w.is_deleted = 0 ");
        query.append("LEFT JOIN wallets tw ON tw.id = t.to_wallet_id ");
        query.append("WHERE t.user_id = ? AND t.is_deleted = 0 ");
        query.append("AND (t.to_wallet_id IS NULL OR tw.is_deleted = 0) ");

        List<Object> args = new ArrayList<>();
        args.add(userId);

        if (keyword != null && !keyword.trim().isEmpty()) {
            query.append("AND (t.note LIKE ? OR t.category_id IN (SELECT id FROM categories WHERE name LIKE ?)) ");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }

        if (amountMode != null && !"ALL".equals(amountMode)) {
            switch (amountMode) {
                case "GT":
                    if (amountValue != null) {
                        query.append("AND t.amount > ? ");
                        args.add(amountValue);
                    }
                    break;
                case "LT":
                    if (amountValue != null) {
                        query.append("AND t.amount < ? ");
                        args.add(amountValue);
                    }
                    break;
                case "EQ":
                    if (amountValue != null) {
                        query.append("AND t.amount = ? ");
                        args.add(amountValue);
                    }
                    break;
                case "BETWEEN":
                    if (amountMin != null && amountMax != null) {
                        query.append("AND t.amount BETWEEN ? AND ? ");
                        args.add(amountMin);
                        args.add(amountMax);
                    }
                    break;
            }
        }

        if (timeMode != null && !"ALL".equals(timeMode)) {
            switch (timeMode) {
                case "AFTER":
                    if (timeValue != null) {
                        query.append("AND t.timestamp > ? ");
                        args.add(timeValue);
                    }
                    break;
                case "BEFORE":
                    if (timeValue != null) {
                        query.append("AND t.timestamp < ? ");
                        args.add(timeValue);
                    }
                    break;
                case "ON":
                    if (timeValue != null) {
                        long endOfDay = timeValue + 24L * 60 * 60 * 1000 - 1;
                        query.append("AND t.timestamp BETWEEN ? AND ? ");
                        args.add(timeValue);
                        args.add(endOfDay);
                    }
                    break;
                case "BETWEEN":
                    if (timeStart != null && timeEnd != null) {
                        query.append("AND t.timestamp BETWEEN ? AND ? ");
                        args.add(timeStart);
                        args.add(timeEnd);
                    }
                    break;
            }
        }

        if (walletId != null && !walletId.trim().isEmpty()) {
            query.append("AND t.wallet_id = ? ");
            args.add(walletId);
        }

        if (categoryId != null && !categoryId.trim().isEmpty()) {
            query.append("AND t.category_id = ? ");
            args.add(categoryId);
        }

        query.append("ORDER BY t.timestamp DESC");

        SimpleSQLiteQuery sqLiteQuery = new SimpleSQLiteQuery(query.toString(), args.toArray());
        return transactionDao.searchTransactionsAdvanced(sqLiteQuery);
    }

    public void getFirstTransactionsPage(String userId,
            int limit,
            @NonNull PageCallback<List<TransactionEntity>> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<TransactionEntity> page = transactionDao.getFirstTransactionsPageSync(userId, limit);
                mainHandler.post(() -> callback.onSuccess(page));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public void getTransactionsPageByCursor(String userId,
            int limit,
            long lastTimestamp,
            @NonNull String lastId,
            @NonNull PageCallback<List<TransactionEntity>> callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                List<TransactionEntity> page = transactionDao.getTransactionsPagedByCursorSync(
                        userId,
                        lastTimestamp,
                        lastId,
                        limit);
                mainHandler.post(() -> callback.onSuccess(page));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    public LiveData<Double> getTotalIncome(String userId, long startDate, long endDate) {
        return transactionDao.getTotalIncome(userId, startDate, endDate);
    }

    public LiveData<Double> getTotalExpense(String userId, long startDate, long endDate) {
        return transactionDao.getTotalExpense(userId, startDate, endDate);
    }

    public LiveData<Double> getTotalIncomeFiltered(String userId,
            long startDate,
            long endDate,
            @Nullable String walletId) {
        return transactionDao.getTotalIncomeFiltered(userId, startDate, endDate, walletId);
    }

    public LiveData<Double> getTotalExpenseFiltered(String userId,
            long startDate,
            long endDate,
            @Nullable String walletId) {
        return transactionDao.getTotalExpenseFiltered(userId, startDate, endDate, walletId);
    }

    public LiveData<Double> getTotalAmountByCategoryFiltered(String userId,
            String type,
            String categoryId,
            long startDate,
            long endDate,
            @Nullable String walletId) {
        return transactionDao.getTotalAmountByCategoryFiltered(
                userId,
                type,
                categoryId,
                startDate,
                endDate,
                walletId);
    }

    public LiveData<NetIncomeDTO> getNetIncomeSummary(String userId,
            long startDate,
            long endDate,
            @Nullable String walletId,
            String periodLabel) {
        return transactionDao.getNetIncomeSummary(userId, startDate, endDate, walletId, periodLabel);
    }

    public LiveData<List<NetIncomeDTO>> getNetIncomeTrend(String userId,
            long startDate,
            long endDate,
            @Nullable String walletId,
            String periodFormat) {
        return transactionDao.getNetIncomeTrend(userId, startDate, endDate, walletId, periodFormat);
    }

    public LiveData<List<CategorySumDTO>> getCategorySums(String userId,
            String type,
            long startDate,
            long endDate,
            @Nullable String walletId) {
        return transactionDao.getCategorySums(userId, type, startDate, endDate, walletId);
    }

    public LiveData<List<CategorySumDTO>> getRootCategorySums(String userId,
            String type,
            long startDate,
            long endDate,
            @Nullable String walletId) {
        return transactionDao.getRootCategorySums(userId, type, startDate, endDate, walletId);
    }

    public LiveData<List<CategorySumDTO>> getChildCategorySums(String userId,
            String type,
            long startDate,
            long endDate,
            @Nullable String walletId,
            @NonNull String parentCategoryId) {
        return transactionDao.getChildCategorySums(
                userId,
                type,
                startDate,
                endDate,
                walletId,
                parentCategoryId);
    }

    public LiveData<List<CategorySumDTO>> getCategoryBranchSums(String userId,
            String type,
            long startDate,
            long endDate,
            @Nullable String walletId,
            @NonNull String parentCategoryId) {
        return transactionDao.getCategoryBranchSums(
                userId,
                type,
                startDate,
                endDate,
                walletId,
                parentCategoryId);
    }

    public LiveData<Double> getParentCategoryBranchTotalAmount(String userId,
            String type,
            @NonNull String parentCategoryId,
            long startDate,
            long endDate,
            @Nullable String walletId) {
        return transactionDao.getParentCategoryBranchTotalAmount(
                userId,
                type,
                parentCategoryId,
                startDate,
                endDate,
                walletId);
    }

    public LiveData<List<TransactionEntity>> getTransactionsForStatisticsDrillDown(String userId,
            String type,
            long startDate,
            long endDate,
            @Nullable String walletId,
            @NonNull String categoryId) {
        return transactionDao.getTransactionsForStatisticsDrillDown(
                userId,
                type,
                startDate,
                endDate,
                walletId,
                categoryId);
    }

    public LiveData<List<DailyTrendDTO>> getAmountTrend(String userId,
            String type,
            long startDate,
            long endDate,
            @Nullable String walletId,
            String periodFormat) {
        return transactionDao.getAmountTrend(userId, type, startDate, endDate, walletId, periodFormat);
    }

    public LiveData<List<DailyTrendDTO>> getCategoryAmountTrend(String userId,
            String type,
            String categoryId,
            long startDate,
            long endDate,
            @Nullable String walletId,
            String periodFormat) {
        return transactionDao.getCategoryAmountTrend(
                userId,
                type,
                categoryId,
                startDate,
                endDate,
                walletId,
                periodFormat);
    }

    public LiveData<List<DailyTrendDTO>> getParentCategoryBranchAmountTrend(String userId,
            String type,
            @NonNull String parentCategoryId,
            long startDate,
            long endDate,
            @Nullable String walletId,
            String periodFormat) {
        return transactionDao.getParentCategoryBranchAmountTrend(
                userId,
                type,
                parentCategoryId,
                startDate,
                endDate,
                walletId,
                periodFormat);
    }

    public LiveData<Double> getTotalExpenseByCategory(String userId,
            @Nullable String categoryId,
            @Nullable String walletId,
            long startDate,
            long endDate) {
        if (Constants.isOtherCategoryId(categoryId)) {
            return transactionDao.getSpentForOtherCategories(
                    userId,
                    startDate,
                    endDate,
                    walletId,
                    Constants.CATEGORY_ID_OTHER,
                    Constants.CATEGORY_ID_OTHER_LEGACY);
        }
        return transactionDao.getTotalExpenseByCategory(userId, categoryId, walletId, startDate, endDate);
    }

    public List<TransactionEntity> getPendingSyncSince(@NonNull String userId,
            long lastSyncedAt,
            @NonNull String lastSyncedId,
            int limit) {
        return transactionDao.getPendingSyncSince(userId, lastSyncedAt, lastSyncedId, limit);
    }

    public List<TransactionEntity> getPendingSyncPagedSince(@NonNull String userId,
            long lastSyncedAt,
            @NonNull String lastSyncedId,
            int limit,
            int offset) {
        return transactionDao.getPendingSyncTransactionsPagedSince(
                userId,
                lastSyncedAt,
                lastSyncedId,
                limit,
                offset);
    }

    public void markSynced(@NonNull String id) {
        transactionDao.markSynced(id);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void hardDeleteById(@NonNull String id) {
        transactionDao.hardDeleteById(id);
    }

    /**
     * OCR duplicate gate before insert.
     * A transaction is considered a suspected duplicate only when all three signals
     * match:
     * 1) same internal receipt image hash (SHA-256), 2) same amount, 3) timestamp
     * within +/- 2 minutes.
     * The result is advisory and must always be confirmed by the user in UI.
     */
    public void checkOcrDuplicateCandidates(@NonNull String userId,
            @NonNull List<OcrDuplicateCandidate> candidates,
            @NonNull DuplicateCheckCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                DuplicateCheckResult result = new DuplicateCheckResult(
                        detectSuspectedDuplicates(userId, candidates));
                mainHandler.post(() -> callback.onCompleted(result));
            } catch (Exception exception) {
                mainHandler.post(() -> callback.onError(exception));
            }
        });
    }

    // ─── Write ────────────────────────────────────────────────────────────────

    public void insertTransaction(TransactionEntity transaction) {
        upsertTransactionInternal(transaction, null);
    }

    public void insertTransaction(TransactionEntity transaction, @Nullable WriteCallback callback) {
        upsertTransactionInternal(transaction, callback);
    }

    public void updateTransaction(TransactionEntity newTransaction) {
        upsertTransactionInternal(newTransaction, null);
    }

    public void updateTransaction(TransactionEntity newTransaction, @Nullable WriteCallback callback) {
        upsertTransactionInternal(newTransaction, callback);
    }

    public void insertTransactions(@NonNull List<TransactionEntity> transactions,
            @Nullable WriteCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                appDatabase.runInTransaction(() -> {
                    for (TransactionEntity transaction : transactions) {
                        TransactionEntity writeTransaction = copyTransaction(transaction);
                        prepareTransactionForWrite(writeTransaction);
                        transactionDao.upsertLocal(writeTransaction);
                    }
                });
                refreshLocalObservers();
                if (!transactions.isEmpty()) {
                    TransactionEntity latest = copyTransaction(transactions.get(transactions.size() - 1));
                    mainHandler.post(() -> localWriteEvents.setValue(LocalWriteEvent.upsert(latest)));
                }
                scheduleSyncIfEnabled();
                notifyWriteSuccess(callback);
            } catch (Exception exception) {
                notifyWriteError(callback, exception);
            }
        });
    }

    private void upsertTransactionInternal(TransactionEntity transaction, @Nullable WriteCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                prepareTransactionForWrite(transaction);
                transactionDao.upsertLocal(transaction);
                refreshLocalObservers();
                TransactionEntity writtenTransaction = copyTransaction(transaction);
                mainHandler.post(() -> localWriteEvents.setValue(LocalWriteEvent.upsert(writtenTransaction)));
                scheduleSyncIfEnabled();
                notifyWriteSuccess(callback);
            } catch (Exception exception) {
                notifyWriteError(callback, exception);
            }
        });
    }

    public void softDeleteTransaction(TransactionEntity transaction) {
        softDeleteTransaction(transaction, null);
    }

    public void softDeleteTransaction(TransactionEntity transaction, @Nullable WriteCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                transactionDao.softDelete(transaction.getId(), nextWriteTimestamp());
                refreshLocalObservers();
                String deletedTransactionId = transaction.getId();
                mainHandler.post(() -> localWriteEvents.setValue(LocalWriteEvent.delete(deletedTransactionId)));
                scheduleSyncIfEnabled();
                notifyWriteSuccess(callback);
            } catch (Exception exception) {
                notifyWriteError(callback, exception);
            }
        });
    }

    private long nextWriteTimestamp() {
        while (true) {
            long previous = LAST_WRITE_TIMESTAMP.get();
            long candidate = Math.max(System.currentTimeMillis(), previous + 1L);
            if (LAST_WRITE_TIMESTAMP.compareAndSet(previous, candidate)) {
                return candidate;
            }
        }
    }

    private void prepareTransactionForWrite(@NonNull TransactionEntity transaction) {
        validateTransaction(transaction);
        long now = nextWriteTimestamp();
        if (transaction.getId() == null || transaction.getId().trim().isEmpty()) {
            transaction.setId(UUID.randomUUID().toString());
        }
        if (transaction.getCreatedAt() <= 0L) {
            transaction.setCreatedAt(now);
        }
        transaction.setUpdatedAt(now);
        transaction.setDeleted(false);
        transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
    }

    private void validateTransaction(@NonNull TransactionEntity transaction) {
        if (isBlank(transaction.getUserId())) {
            throw new TransactionValidationException("transaction.validation.user_required");
        }
        if (isBlank(transaction.getWalletId())) {
            throw new TransactionValidationException("transaction.validation.wallet_required");
        }
        if (isBlank(transaction.getCategoryId())) {
            throw new TransactionValidationException("transaction.validation.category_required");
        }
        if (isBlank(transaction.getType())) {
            throw new TransactionValidationException("transaction.validation.type_required");
        }
        if (transaction.getAmount() <= 0d) {
            throw new TransactionValidationException("transaction.validation.amount_positive");
        }
        if (transaction.getTimestamp() <= 0L) {
            throw new TransactionValidationException("transaction.validation.timestamp_required");
        }
    }

    @NonNull
    private TransactionEntity copyTransaction(@NonNull TransactionEntity source) {
        TransactionEntity copy = new TransactionEntity();
        copy.setId(source.getId());
        copy.setWalletId(source.getWalletId());
        copy.setCategoryId(source.getCategoryId());
        copy.setDebtId(source.getDebtId());
        copy.setEventId(source.getEventId());
        copy.setAmount(source.getAmount());
        copy.setType(source.getType());
        copy.setToWalletId(source.getToWalletId());
        copy.setNote(source.getNote());
        copy.setTimestamp(source.getTimestamp());
        copy.setImagePath(source.getImagePath());
        copy.setCreatedAt(source.getCreatedAt());
        copy.setUpdatedAt(source.getUpdatedAt());
        copy.setSyncStatus(source.getSyncStatus());
        copy.setDeleted(source.isDeleted());
        copy.setUserId(source.getUserId());
        return copy;
    }

    private boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    @NonNull
    private List<SuspectedDuplicate> detectSuspectedDuplicates(@NonNull String userId,
            @NonNull List<OcrDuplicateCandidate> candidates) {
        List<SuspectedDuplicate> suspectedDuplicates = new ArrayList<>();
        Map<String, String> hashCache = new HashMap<>();
        for (OcrDuplicateCandidate candidate : candidates) {
            String candidateHash = computeImageHash(candidate.getImagePath(), hashCache);
            if (isBlank(candidateHash)) {
                continue;
            }
            long candidateTimestamp = candidate.getTimestamp();
            List<TransactionEntity> matchingTransactions = transactionDao.getTransactionsForDuplicateCheckSync(
                    userId,
                    candidate.getAmount() - OCR_DUPLICATE_AMOUNT_TOLERANCE,
                    candidate.getAmount() + OCR_DUPLICATE_AMOUNT_TOLERANCE,
                    candidateTimestamp - OCR_DUPLICATE_TIME_BUCKET_MS,
                    candidateTimestamp + OCR_DUPLICATE_TIME_BUCKET_MS);
            for (TransactionEntity existingTransaction : matchingTransactions) {
                String existingHash = computeImageHash(existingTransaction.getImagePath(), hashCache);
                if (isBlank(existingHash) || !existingHash.equals(candidateHash)) {
                    continue;
                }
                suspectedDuplicates.add(new SuspectedDuplicate(
                        candidate.getCandidateId(),
                        existingTransaction.getId(),
                        existingTransaction.getAmount(),
                        existingTransaction.getTimestamp(),
                        existingTransaction.getNote()));
                break;
            }
        }
        return suspectedDuplicates;
    }

    @Nullable
    private String computeImageHash(@Nullable String imagePath,
            @NonNull Map<String, String> hashCache) {
        if (isBlank(imagePath)) {
            return null;
        }
        if (hashCache.containsKey(imagePath)) {
            return hashCache.get(imagePath);
        }
        try {
            String hash = ReceiptImageHashUtils.computeSha256(imagePath);
            hashCache.put(imagePath, hash);
            return hash;
        } catch (IOException exception) {
            throw new DuplicateCheckException("transaction.duplicate_check.image_hash_failed", exception);
        }
    }

    private void notifyWriteSuccess(@Nullable WriteCallback callback) {
        if (callback == null) {
            return;
        }
        mainHandler.post(callback::onSuccess);
    }

    private void notifyWriteError(@Nullable WriteCallback callback, @NonNull Throwable throwable) {
        if (callback == null) {
            return;
        }
        mainHandler.post(() -> callback.onError(throwable));
    }

    private void scheduleSyncIfEnabled() {
        if (syncScheduler != null) {
            syncScheduler.scheduleOneTimeSyncDebounced();
        }
    }

    @SuppressWarnings("deprecation")
    private void refreshLocalObservers() {
        appDatabase.getInvalidationTracker().refreshVersionsSync();
    }
}
