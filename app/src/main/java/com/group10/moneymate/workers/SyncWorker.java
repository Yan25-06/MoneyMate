package com.group10.moneymate.workers;

import android.app.ActivityManager;
import android.content.Context;

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
import com.group10.moneymate.data.remote.SupabaseAuthHelper;
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

/**
 * SyncWorker — Phase 2: Push dữ liệu local lên Supabase.
 *
 * Luồng cho mỗi domain:
 *   1. Đọc checkpoint (last_synced_at, last_synced_id) từ sync_metadata local
 *   2. Lấy batch bản ghi pending (sync_status IN (1,2)) kể từ checkpoint
 *   3. Với PENDING_UPLOAD (1): map sang JSON → upsert lên Supabase → markSynced local
 *   4. Với PENDING_DELETE (2): gọi DELETE trên Supabase → hardDelete local
 *   5. Cập nhật checkpoint sau mỗi bản ghi thành công
 *   6. Lặp cho đến khi hết pending
 *
 * Thứ tự domain tuân theo dependency:
 *   wallets → categories → debts → events → budgets → transactions
 *
 * Retry strategy: WorkManager tự retry theo exponential backoff (cấu hình trong SyncScheduler).
 * Sau MAX_ATTEMPTS lần thất bại liên tiếp → Result.failure() + thông báo người dùng.
 *
 * Auth error (401/403): không retry, trả về failure ngay (token hết hạn).
 */
public class SyncWorker extends Worker {

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
        String userId = authRepository.getCurrentUserId();
        if (userId == null || userId.trim().isEmpty()) {
            return Result.success(); // user chưa đăng nhập, bỏ qua
        }

        String token = authRepository.getCurrentAccessToken();
        if (token == null || token.trim().isEmpty()) {
            return Result.failure(); // không có token, không thể sync
        }

        try {
            // Thứ tự theo dependency: wallet → category → debt → event → budget → transaction
            syncWallets(userId, token);
            syncCategories(userId, token);
            syncDebts(userId, token);
            syncEvents(userId, token);
            syncBudgets(userId, token);
            syncTransactions(userId, token);
            return Result.success();

        } catch (SupabaseSyncClient.SyncException e) {
            if (e.isAuthError()) {
                // Token hết hạn hoặc RLS từ chối → không retry, báo lỗi ngay
                notifySyncFailure();
                return Result.failure();
            }
            // Network/server error → để WorkManager retry theo exponential backoff
            if (getRunAttemptCount() >= MAX_ATTEMPTS - 1) {
                notifySyncFailure();
                return Result.failure();
            }
            return Result.retry();

        } catch (Exception e) {
            if (getRunAttemptCount() >= MAX_ATTEMPTS - 1) {
                notifySyncFailure();
                return Result.failure();
            }
            return Result.retry();
        }
    }

    // ─── Domain sync methods ──────────────────────────────────────────────────

    private void syncWallets(@NonNull String userId,
                             @NonNull String token) throws SupabaseSyncClient.SyncException {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_WALLETS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<WalletEntity> pending = walletRepository.getPendingSyncPagedSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE, 0);
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (WalletEntity wallet : pending) {
                if (wallet.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    // Xóa mềm: cũng cần push is_deleted=true lên Supabase trước khi hard delete local
                    // Vì thiết bị khác cần biết bản ghi đã bị xóa
                    try {
                        JSONArray arr = new JSONArray();
                        arr.put(EntityToSupabaseMapper.fromWallet(wallet));
                        syncClient.upsert(DOMAIN_WALLETS, arr, token);
                    } catch (JSONException e) {
                        throw new SupabaseSyncClient.SyncException("JSON mapping failed for wallet " + wallet.getId(), 0);
                    }
                    walletRepository.hardDeleteById(wallet.getId());
                } else {
                    try {
                        JSONArray arr = new JSONArray();
                        arr.put(EntityToSupabaseMapper.fromWallet(wallet));
                        syncClient.upsert(DOMAIN_WALLETS, arr, token);
                    } catch (JSONException e) {
                        throw new SupabaseSyncClient.SyncException("JSON mapping failed for wallet " + wallet.getId(), 0);
                    }
                    walletRepository.markSynced(wallet.getId());
                }

                lastSyncedAt = wallet.getUpdatedAt();
                lastSyncedId = wallet.getId();
                syncMetadataRepository.updateCheckpoint(userId, DOMAIN_WALLETS, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void syncCategories(@NonNull String userId,
                                @NonNull String token) throws SupabaseSyncClient.SyncException {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_CATEGORIES);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<CategoryEntity> pending = categoryRepository.getPendingSyncPagedSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE, 0);
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (CategoryEntity category : pending) {
                try {
                    JSONArray arr = new JSONArray();
                    arr.put(EntityToSupabaseMapper.fromCategory(category));
                    syncClient.upsert(DOMAIN_CATEGORIES, arr, token);
                } catch (JSONException e) {
                    throw new SupabaseSyncClient.SyncException("JSON mapping failed for category " + category.getId(), 0);
                }

                if (category.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    categoryRepository.hardDeleteById(category.getId());
                } else {
                    categoryRepository.markSynced(category.getId());
                }

                lastSyncedAt = category.getUpdatedAt();
                lastSyncedId = category.getId();
                syncMetadataRepository.updateCheckpoint(userId, DOMAIN_CATEGORIES, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void syncDebts(@NonNull String userId,
                           @NonNull String token) throws SupabaseSyncClient.SyncException {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_DEBTS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<DebtEntity> pending = debtRepository.getPendingSyncPagedSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE, 0);
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (DebtEntity debt : pending) {
                try {
                    JSONArray arr = new JSONArray();
                    arr.put(EntityToSupabaseMapper.fromDebt(debt));
                    syncClient.upsert(DOMAIN_DEBTS, arr, token);
                } catch (JSONException e) {
                    throw new SupabaseSyncClient.SyncException("JSON mapping failed for debt " + debt.getId(), 0);
                }

                if (debt.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    debtRepository.hardDeleteById(debt.getId());
                } else {
                    debtRepository.markSynced(debt.getId());
                }

                lastSyncedAt = debt.getUpdatedAt();
                lastSyncedId = debt.getId();
                syncMetadataRepository.updateCheckpoint(userId, DOMAIN_DEBTS, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void syncEvents(@NonNull String userId,
                            @NonNull String token) throws SupabaseSyncClient.SyncException {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_EVENTS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<EventEntity> pending = eventRepository.getPendingSyncPagedSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE, 0);
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (EventEntity event : pending) {
                try {
                    JSONArray arr = new JSONArray();
                    arr.put(EntityToSupabaseMapper.fromEvent(event));
                    syncClient.upsert(DOMAIN_EVENTS, arr, token);
                } catch (JSONException e) {
                    throw new SupabaseSyncClient.SyncException("JSON mapping failed for event " + event.getId(), 0);
                }

                if (event.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    eventRepository.hardDeleteById(event.getId());
                } else {
                    eventRepository.markSynced(event.getId());
                }

                lastSyncedAt = event.getUpdatedAt();
                lastSyncedId = event.getId();
                syncMetadataRepository.updateCheckpoint(userId, DOMAIN_EVENTS, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void syncBudgets(@NonNull String userId,
                             @NonNull String token) throws SupabaseSyncClient.SyncException {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_BUDGETS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<BudgetEntity> pending = budgetRepository.getPendingSyncSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE);
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (BudgetEntity budget : pending) {
                try {
                    JSONArray arr = new JSONArray();
                    arr.put(EntityToSupabaseMapper.fromBudget(budget));
                    syncClient.upsert(DOMAIN_BUDGETS, arr, token);
                } catch (JSONException e) {
                    throw new SupabaseSyncClient.SyncException("JSON mapping failed for budget " + budget.getId(), 0);
                }

                if (budget.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    budgetRepository.hardDeleteById(budget.getId());
                } else {
                    budgetRepository.markSynced(budget.getId());
                }

                lastSyncedAt = budget.getUpdatedAt();
                lastSyncedId = budget.getId();
                syncMetadataRepository.updateCheckpoint(userId, DOMAIN_BUDGETS, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void syncTransactions(@NonNull String userId,
                                  @NonNull String token) throws SupabaseSyncClient.SyncException {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId, DOMAIN_TRANSACTIONS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<TransactionEntity> pending = transactionRepository.getPendingSyncPagedSince(
                    userId, lastSyncedAt, lastSyncedId, SYNC_BATCH_SIZE, 0);
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (TransactionEntity tx : pending) {
                try {
                    JSONArray arr = new JSONArray();
                    arr.put(EntityToSupabaseMapper.fromTransaction(tx));
                    syncClient.upsert(DOMAIN_TRANSACTIONS, arr, token);
                } catch (JSONException e) {
                    throw new SupabaseSyncClient.SyncException("JSON mapping failed for transaction " + tx.getId(), 0);
                }

                if (tx.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    transactionRepository.hardDeleteById(tx.getId());
                } else {
                    transactionRepository.markSynced(tx.getId());
                }

                lastSyncedAt = tx.getUpdatedAt();
                lastSyncedId = tx.getId();
                syncMetadataRepository.updateCheckpoint(userId, DOMAIN_TRANSACTIONS, lastSyncedAt, lastSyncedId);
            }
        }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

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
        if (am == null) return false;
        ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(info);
        return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                || info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }
}