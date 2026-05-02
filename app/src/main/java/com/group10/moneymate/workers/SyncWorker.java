package com.group10.moneymate.workers;

import android.app.ActivityManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.local.entity.EventEntity;
import com.group10.moneymate.data.local.entity.SyncMetadataEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.remote.EntityToSupabaseMapper;
// BUG FIX: xóa import SupabaseAuthHelper không dùng đến
// import com.group10.moneymate.data.remote.SupabaseAuthHelper;
import com.group10.moneymate.data.remote.SupabaseSyncClient;
import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.data.repository.BudgetRepository;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.DebtRepository;
import com.group10.moneymate.data.repository.EventRepository;
import com.group10.moneymate.data.repository.SyncMetadataRepository;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.data.repository.WalletRepository;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.ForegroundUiNotifier;
import com.group10.moneymate.utils.NotificationHelper;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;

public class SyncWorker extends Worker {

    private static final String TAG = "SyncWorker";

    public static final String DOMAIN_WALLETS = "wallets";
    public static final String DOMAIN_CATEGORIES = "categories";
    public static final String DOMAIN_DEBTS = "debts";
    public static final String DOMAIN_EVENTS = "events";
    public static final String DOMAIN_BUDGETS = "budgets";
    public static final String DOMAIN_TRANSACTIONS = "transactions";

    private static final int MAX_ATTEMPTS = 3;
    private static final int SYNC_BATCH_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final WalletRepository walletRepository;
    private final DebtRepository debtRepository;
    private final EventRepository eventRepository;
    private final SyncMetadataRepository syncMetadataRepository;
    private final AuthRepository authRepository;
    private final SupabaseSyncClient syncClient;

    public SyncWorker(@NonNull Context context,
            @NonNull WorkerParameters workerParameters,
            @NonNull TransactionRepository transactionRepository,
            @NonNull BudgetRepository budgetRepository,
            @NonNull CategoryRepository categoryRepository,
            @NonNull WalletRepository walletRepository,
            @NonNull DebtRepository debtRepository,
            @NonNull EventRepository eventRepository,
            @NonNull SyncMetadataRepository syncMetadataRepository,
            @NonNull AuthRepository authRepository,
            @NonNull SupabaseSyncClient syncClient) {
        super(context, workerParameters);
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.walletRepository = walletRepository;
        this.debtRepository = debtRepository;
        this.eventRepository = eventRepository;
        this.syncMetadataRepository = syncMetadataRepository;
        this.authRepository = authRepository;
        this.syncClient = syncClient;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "doWork: Starting sync worker...");

        String userId = authRepository.getCurrentUserId();
        if (userId == null || userId.trim().isEmpty()) {
            Log.w(TAG, "doWork: userId is null or empty, returning success");
            return Result.success();
        }
        Log.d(TAG, "doWork: userId = " + userId);

        String token = authRepository.getCurrentAccessToken();
        if (token == null || token.trim().isEmpty()) {
            Log.e(TAG, "doWork: token is null or empty, returning failure");
            return Result.failure();
        }
        Log.d(TAG, "doWork: token retrieved successfully");

        try {
            Log.d(TAG, "doWork: Starting sync for all domains...");
            syncWallets(userId, token);
            Log.d(TAG, "doWork: Wallets sync completed");

            syncCategories(userId, token);
            Log.d(TAG, "doWork: Categories sync completed");

            syncDebts(userId, token);
            Log.d(TAG, "doWork: Debts sync completed");

            syncEvents(userId, token);
            Log.d(TAG, "doWork: Events sync completed");

            syncBudgets(userId, token);
            Log.d(TAG, "doWork: Budgets sync completed");

            syncTransactions(userId, token);
            Log.d(TAG, "doWork: Transactions sync completed");

            Log.i(TAG, "doWork: All sync operations completed successfully");
            return Result.success();

        } catch (SupabaseSyncClient.SyncException e) {
            Log.e(TAG, "doWork: SyncException caught", e);
            if (e.isAuthError()) {
                Log.e(TAG, "doWork: Auth error detected, notifying and returning failure");
                notifySyncFailure();
                return Result.failure();
            }
            if (getRunAttemptCount() >= MAX_ATTEMPTS - 1) {
                Log.e(TAG, "doWork: Max attempts reached, notifying and returning failure");
                notifySyncFailure();
                return Result.failure();
            }
            Log.w(TAG, "doWork: Retrying sync (attempt " + (getRunAttemptCount() + 1) + "/" + MAX_ATTEMPTS + ")");
            return Result.retry();

        } catch (Exception e) {
            Log.e(TAG, "doWork: Unexpected exception caught", e);
            if (getRunAttemptCount() >= MAX_ATTEMPTS - 1) {
                Log.e(TAG, "doWork: Max attempts reached for unexpected exception, notifying and returning failure");
                notifySyncFailure();
                return Result.failure();
            }
            Log.w(TAG, "doWork: Retrying sync after unexpected exception (attempt " + (getRunAttemptCount() + 1) + "/"
                    + MAX_ATTEMPTS + ")");
            return Result.retry();
        }
    }

    private void syncWallets(@NonNull String userId,
            @NonNull String token) throws SupabaseSyncClient.SyncException {
        Log.d(TAG, "syncWallets: Starting wallet sync for userId=" + userId);

        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_WALLETS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        Log.d(TAG, "syncWallets: Checkpoint - lastSyncedAt=" + lastSyncedAt + ", lastSyncedId=" + lastSyncedId);

        int batchCount = 0;
        while (true) {
            List<WalletEntity> pending = walletRepository.getPendingSyncPagedSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE, 0);
            if (pending == null || pending.isEmpty()) {
                Log.d(TAG, "syncWallets: No more pending wallets to sync");
                return;
            }

            Log.d(TAG, "syncWallets: Batch " + (++batchCount) + " - Processing " + pending.size() + " wallets");

            for (WalletEntity wallet : pending) {
                try {
                    Log.d(TAG, "syncWallets: Syncing wallet id=" + wallet.getId() + ", syncStatus="
                            + wallet.getSyncStatus());
                    JSONArray arr = new JSONArray();
                    arr.put(EntityToSupabaseMapper.fromWallet(wallet));
                    syncClient.upsert(DOMAIN_WALLETS, arr, token);
                    Log.d(TAG, "syncWallets: Successfully upserted wallet id=" + wallet.getId());
                } catch (JSONException e) {
                    Log.e(TAG, "syncWallets: JSON mapping failed for wallet " + wallet.getId(), e);
                    throw new SupabaseSyncClient.SyncException(
                            "JSON mapping failed for wallet " + wallet.getId(), 0);
                }

                if (wallet.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    walletRepository.hardDeleteById(wallet.getId());
                    Log.d(TAG, "syncWallets: Hard deleted wallet id=" + wallet.getId());
                } else {
                    walletRepository.markSynced(wallet.getId());
                    Log.d(TAG, "syncWallets: Marked wallet id=" + wallet.getId() + " as synced");
                }

                lastSyncedAt = wallet.getUpdatedAt();
                lastSyncedId = wallet.getId();
                syncMetadataRepository.updateCheckpoint(
                        userId, DOMAIN_WALLETS, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void syncCategories(@NonNull String userId,
            @NonNull String token) throws SupabaseSyncClient.SyncException {
        Log.d(TAG, "syncCategories: Starting category sync for userId=" + userId);

        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_CATEGORIES);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        Log.d(TAG, "syncCategories: Checkpoint - lastSyncedAt=" + lastSyncedAt + ", lastSyncedId=" + lastSyncedId);

        int batchCount = 0;
        while (true) {
            List<CategoryEntity> pending = categoryRepository.getPendingSyncPagedSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE, 0);
            if (pending == null || pending.isEmpty()) {
                Log.d(TAG, "syncCategories: No more pending categories to sync");
                return;
            }

            Log.d(TAG, "syncCategories: Batch " + (++batchCount) + " - Processing " + pending.size() + " categories");

            for (CategoryEntity category : pending) {
                try {
                    Log.d(TAG, "syncCategories: Syncing category id=" + category.getId() + ", syncStatus="
                            + category.getSyncStatus());
                    JSONArray arr = new JSONArray();
                    arr.put(EntityToSupabaseMapper.fromCategory(category));
                    syncClient.upsert(DOMAIN_CATEGORIES, arr, token);
                    Log.d(TAG, "syncCategories: Successfully upserted category id=" + category.getId());
                } catch (JSONException e) {
                    Log.e(TAG, "syncCategories: JSON mapping failed for category " + category.getId(), e);
                    throw new SupabaseSyncClient.SyncException(
                            "JSON mapping failed for category " + category.getId(), 0);
                }

                if (category.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    categoryRepository.hardDeleteById(category.getId());
                    Log.d(TAG, "syncCategories: Hard deleted category id=" + category.getId());
                } else {
                    categoryRepository.markSynced(category.getId());
                    Log.d(TAG, "syncCategories: Marked category id=" + category.getId() + " as synced");
                }

                lastSyncedAt = category.getUpdatedAt();
                lastSyncedId = category.getId();
                syncMetadataRepository.updateCheckpoint(
                        userId, DOMAIN_CATEGORIES, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void syncDebts(@NonNull String userId,
            @NonNull String token) throws SupabaseSyncClient.SyncException {
        Log.d(TAG, "syncDebts: Starting debt sync for userId=" + userId);

        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_DEBTS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        Log.d(TAG, "syncDebts: Checkpoint - lastSyncedAt=" + lastSyncedAt + ", lastSyncedId=" + lastSyncedId);

        int batchCount = 0;
        while (true) {
            List<DebtEntity> pending = debtRepository.getPendingSyncPagedSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE, 0);
            if (pending == null || pending.isEmpty()) {
                Log.d(TAG, "syncDebts: No more pending debts to sync");
                return;
            }

            Log.d(TAG, "syncDebts: Batch " + (++batchCount) + " - Processing " + pending.size() + " debts");

            for (DebtEntity debt : pending) {
                try {
                    Log.d(TAG, "syncDebts: Syncing debt id=" + debt.getId() + ", syncStatus=" + debt.getSyncStatus());
                    JSONArray arr = new JSONArray();
                    arr.put(EntityToSupabaseMapper.fromDebt(debt));
                    syncClient.upsert(DOMAIN_DEBTS, arr, token);
                    Log.d(TAG, "syncDebts: Successfully upserted debt id=" + debt.getId());
                } catch (JSONException e) {
                    Log.e(TAG, "syncDebts: JSON mapping failed for debt " + debt.getId(), e);
                    throw new SupabaseSyncClient.SyncException(
                            "JSON mapping failed for debt " + debt.getId(), 0);
                }

                if (debt.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    debtRepository.hardDeleteById(debt.getId());
                    Log.d(TAG, "syncDebts: Hard deleted debt id=" + debt.getId());
                } else {
                    debtRepository.markSynced(debt.getId());
                    Log.d(TAG, "syncDebts: Marked debt id=" + debt.getId() + " as synced");
                }

                lastSyncedAt = debt.getUpdatedAt();
                lastSyncedId = debt.getId();
                syncMetadataRepository.updateCheckpoint(
                        userId, DOMAIN_DEBTS, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void syncEvents(@NonNull String userId,
            @NonNull String token) throws SupabaseSyncClient.SyncException {
        Log.d(TAG, "syncEvents: Starting event sync for userId=" + userId);

        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_EVENTS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        Log.d(TAG, "syncEvents: Checkpoint - lastSyncedAt=" + lastSyncedAt + ", lastSyncedId=" + lastSyncedId);

        int batchCount = 0;
        while (true) {
            List<EventEntity> pending = eventRepository.getPendingSyncPagedSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE, 0);
            if (pending == null || pending.isEmpty()) {
                Log.d(TAG, "syncEvents: No more pending events to sync");
                return;
            }

            Log.d(TAG, "syncEvents: Batch " + (++batchCount) + " - Processing " + pending.size() + " events");

            for (EventEntity event : pending) {
                try {
                    Log.d(TAG,
                            "syncEvents: Syncing event id=" + event.getId() + ", syncStatus=" + event.getSyncStatus());
                    JSONArray arr = new JSONArray();
                    arr.put(EntityToSupabaseMapper.fromEvent(event));
                    syncClient.upsert(DOMAIN_EVENTS, arr, token);
                    Log.d(TAG, "syncEvents: Successfully upserted event id=" + event.getId());
                } catch (JSONException e) {
                    Log.e(TAG, "syncEvents: JSON mapping failed for event " + event.getId(), e);
                    throw new SupabaseSyncClient.SyncException(
                            "JSON mapping failed for event " + event.getId(), 0);
                }

                if (event.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    eventRepository.hardDeleteById(event.getId());
                    Log.d(TAG, "syncEvents: Hard deleted event id=" + event.getId());
                } else {
                    eventRepository.markSynced(event.getId());
                    Log.d(TAG, "syncEvents: Marked event id=" + event.getId() + " as synced");
                }

                lastSyncedAt = event.getUpdatedAt();
                lastSyncedId = event.getId();
                syncMetadataRepository.updateCheckpoint(
                        userId, DOMAIN_EVENTS, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void syncBudgets(@NonNull String userId,
            @NonNull String token) throws SupabaseSyncClient.SyncException {
        Log.d(TAG, "syncBudgets: Starting budget sync for userId=" + userId);

        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_BUDGETS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        Log.d(TAG, "syncBudgets: Checkpoint - lastSyncedAt=" + lastSyncedAt + ", lastSyncedId=" + lastSyncedId);

        int batchCount = 0;
        while (true) {
            List<BudgetEntity> pending = budgetRepository.getPendingSyncSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE);
            if (pending == null || pending.isEmpty()) {
                Log.d(TAG, "syncBudgets: No more pending budgets to sync");
                return;
            }

            Log.d(TAG, "syncBudgets: Batch " + (++batchCount) + " - Processing " + pending.size() + " budgets");

            for (BudgetEntity budget : pending) {
                try {
                    Log.d(TAG, "syncBudgets: Syncing budget id=" + budget.getId() + ", syncStatus="
                            + budget.getSyncStatus());
                    JSONArray arr = new JSONArray();
                    arr.put(EntityToSupabaseMapper.fromBudget(budget));
                    syncClient.upsert(DOMAIN_BUDGETS, arr, token);
                    Log.d(TAG, "syncBudgets: Successfully upserted budget id=" + budget.getId());
                } catch (JSONException e) {
                    Log.e(TAG, "syncBudgets: JSON mapping failed for budget " + budget.getId(), e);
                    throw new SupabaseSyncClient.SyncException(
                            "JSON mapping failed for budget " + budget.getId(), 0);
                }

                if (budget.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    budgetRepository.hardDeleteById(budget.getId());
                    Log.d(TAG, "syncBudgets: Hard deleted budget id=" + budget.getId());
                } else {
                    budgetRepository.markSynced(budget.getId());
                    Log.d(TAG, "syncBudgets: Marked budget id=" + budget.getId() + " as synced");
                }

                lastSyncedAt = budget.getUpdatedAt();
                lastSyncedId = budget.getId();
                syncMetadataRepository.updateCheckpoint(
                        userId, DOMAIN_BUDGETS, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void syncTransactions(@NonNull String userId,
            @NonNull String token) throws SupabaseSyncClient.SyncException {
        Log.d(TAG, "syncTransactions: Starting transaction sync for userId=" + userId);

        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_TRANSACTIONS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        Log.d(TAG, "syncTransactions: Checkpoint - lastSyncedAt=" + lastSyncedAt + ", lastSyncedId=" + lastSyncedId);

        int batchCount = 0;
        while (true) {
            List<TransactionEntity> pending = transactionRepository.getPendingSyncPagedSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE, 0);
            if (pending == null || pending.isEmpty()) {
                Log.d(TAG, "syncTransactions: No more pending transactions to sync");
                return;
            }

            Log.d(TAG,
                    "syncTransactions: Batch " + (++batchCount) + " - Processing " + pending.size() + " transactions");

            for (TransactionEntity tx : pending) {
                try {
                    Log.d(TAG, "syncTransactions: Syncing transaction id=" + tx.getId() + ", syncStatus="
                            + tx.getSyncStatus());
                    JSONArray arr = new JSONArray();
                    arr.put(EntityToSupabaseMapper.fromTransaction(tx));
                    syncClient.upsert(DOMAIN_TRANSACTIONS, arr, token);
                    Log.d(TAG, "syncTransactions: Successfully upserted transaction id=" + tx.getId());
                } catch (JSONException e) {
                    Log.e(TAG, "syncTransactions: JSON mapping failed for transaction " + tx.getId(), e);
                    throw new SupabaseSyncClient.SyncException(
                            "JSON mapping failed for transaction " + tx.getId(), 0);
                }

                if (tx.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    transactionRepository.hardDeleteById(tx.getId());
                    Log.d(TAG, "syncTransactions: Hard deleted transaction id=" + tx.getId());
                } else {
                    transactionRepository.markSynced(tx.getId());
                    Log.d(TAG, "syncTransactions: Marked transaction id=" + tx.getId() + " as synced");
                }

                lastSyncedAt = tx.getUpdatedAt();
                lastSyncedId = tx.getId();
                syncMetadataRepository.updateCheckpoint(
                        userId, DOMAIN_TRANSACTIONS, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void notifySyncFailure() {
        Context context = getApplicationContext();
        boolean isForeground = isAppInForeground(context) && ForegroundUiNotifier.isAppForeground();
        if (isForeground) {
            ForegroundUiNotifier.showSyncFailedSnackbar();
            return;
        }
        NotificationHelper.showSyncFailedNotification(context);
    }

    private boolean isAppInForeground(@NonNull Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null)
            return false;
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(info);
        return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                || info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }
}