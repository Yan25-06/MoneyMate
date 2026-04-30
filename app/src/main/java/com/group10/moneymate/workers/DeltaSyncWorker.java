package com.group10.moneymate.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.local.entity.EventEntity;
import com.group10.moneymate.data.local.entity.SyncMetadataEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.remote.SupabaseSyncClient;
import com.group10.moneymate.data.remote.SupabaseToEntityMapper;
import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.data.repository.SyncMetadataRepository;
import com.group10.moneymate.models.SyncStatus;

import org.json.JSONArray;
import org.json.JSONObject;

public class DeltaSyncWorker extends Worker {

    private static final String PULL_PREFIX = "pull_";
    public static final String DOMAIN_PULL_WALLETS = PULL_PREFIX + "wallets";
    public static final String DOMAIN_PULL_CATEGORIES = PULL_PREFIX + "categories";
    public static final String DOMAIN_PULL_DEBTS = PULL_PREFIX + "debts";
    public static final String DOMAIN_PULL_EVENTS = PULL_PREFIX + "events";
    public static final String DOMAIN_PULL_BUDGETS = PULL_PREFIX + "budgets";
    public static final String DOMAIN_PULL_TRANSACTIONS = PULL_PREFIX + "transactions";

    private static final int MAX_ATTEMPTS = 3;

    private final AppDatabase database;
    private final SupabaseSyncClient syncClient;
    private final SyncMetadataRepository syncMetadataRepository;
    private final AuthRepository authRepository;

    public DeltaSyncWorker(@NonNull Context context,
                           @NonNull WorkerParameters params,
                           @NonNull AppDatabase database,
                           @NonNull SupabaseSyncClient syncClient,
                           @NonNull SyncMetadataRepository syncMetadataRepository,
                           @NonNull AuthRepository authRepository) {
        super(context, params);
        this.database = database;
        this.syncClient = syncClient;
        this.syncMetadataRepository = syncMetadataRepository;
        this.authRepository = authRepository;
    }

    @NonNull
    @Override
    public Result doWork() {
        String userId = authRepository.getCurrentUserId();
        String token = authRepository.getCurrentAccessToken();

        if (userId == null || userId.trim().isEmpty()
                || token == null || token.trim().isEmpty()) {
            return Result.success();
        }

        try {
            pullDomain(DOMAIN_PULL_WALLETS, "wallets", userId, token);
            pullDomain(DOMAIN_PULL_CATEGORIES, "categories", userId, token);
            pullDomain(DOMAIN_PULL_DEBTS, "debts", userId, token);
            pullDomain(DOMAIN_PULL_EVENTS, "events", userId, token);
            pullDomain(DOMAIN_PULL_BUDGETS, "budgets", userId, token);
            pullDomain(DOMAIN_PULL_TRANSACTIONS, "transactions", userId, token);
            return Result.success();

        } catch (SupabaseSyncClient.SyncException e) {
            if (e.isAuthError()) return Result.failure();
            if (getRunAttemptCount() >= MAX_ATTEMPTS - 1) return Result.failure();
            return Result.retry();
        } catch (Exception e) {
            if (getRunAttemptCount() >= MAX_ATTEMPTS - 1) return Result.failure();
            return Result.retry();
        }
    }

    private void pullDomain(@NonNull String checkpointDomain,
                            @NonNull String tableName,
                            @NonNull String userId,
                            @NonNull String token) throws SupabaseSyncClient.SyncException {
        SyncMetadataEntity checkpoint = syncMetadataRepository
                .getOrCreateCheckpoint(userId, checkpointDomain);
        long cursor = checkpoint.getLastSyncedAt();

        while (true) {
            JSONArray page = syncClient.fetchPage(tableName, userId, cursor, token);
            if (page.length() == 0) return;

            mergePage(tableName, page);

            JSONObject lastRow = page.optJSONObject(page.length() - 1);
            if (lastRow != null) {
                cursor = lastRow.optLong("updated_at", cursor);
                String lastId = lastRow.optString("id", "");
                syncMetadataRepository.updateCheckpoint(userId, checkpointDomain, cursor, lastId);
            }

            if (page.length() < 500) return;
        }
    }

    private void mergePage(@NonNull String tableName, @NonNull JSONArray page) {
        database.runInTransaction(() -> {
            for (int i = 0; i < page.length(); i++) {
                JSONObject row = page.optJSONObject(i);
                if (row == null) continue;
                try {
                    mergeRow(tableName, row);
                } catch (Exception e) {
                    android.util.Log.w("DeltaSyncWorker",
                            "Skip row in " + tableName + ": " + e.getMessage());
                }
            }
        });
    }

    private void mergeRow(@NonNull String tableName, @NonNull JSONObject row) {
        String id = row.optString("id", "");
        if (id.isEmpty()) return;
        long remoteUpdatedAt = row.optLong("updated_at", 0L);

        switch (tableName) {
            case "wallets": {
                WalletEntity local = database.walletDao().getByIdSync(id);
                if (shouldAcceptRemote(local == null ? -1 : local.getUpdatedAt(),
                        local == null ? 0 : local.getSyncStatus(),
                        remoteUpdatedAt)) {
                    database.walletDao().upsertLocal(SupabaseToEntityMapper.toWallet(row));
                }
                break;
            }
            case "categories": {
                CategoryEntity local = database.categoryDao().getCategoryByIdSync(id);
                if (shouldAcceptRemote(local == null ? -1 : local.getUpdatedAt(),
                        local == null ? 0 : local.getSyncStatus(),
                        remoteUpdatedAt)) {
                    database.categoryDao().upsertLocal(SupabaseToEntityMapper.toCategory(row));
                }
                break;
            }
            case "debts": {
                DebtEntity local = database.debtDao().getByIdSync(id);
                if (shouldAcceptRemote(local == null ? -1 : local.getUpdatedAt(),
                        local == null ? 0 : local.getSyncStatus(),
                        remoteUpdatedAt)) {
                    database.debtDao().upsertLocal(SupabaseToEntityMapper.toDebt(row));
                }
                break;
            }
            case "events": {
                // BUG FIX: thêm shouldAcceptRemote để tránh ghi đè event đang PENDING_UPLOAD
                // Trước đây upsert thẳng không có conflict check → event local bị xóa khi
                // remote có updated_at cũ hơn nhưng vẫn được pull về trong delta
                EventEntity local = database.eventDao().getByIdSync(id);
                if (shouldAcceptRemote(local == null ? -1 : local.getUpdatedAt(),
                        local == null ? 0 : local.getSyncStatus(),
                        remoteUpdatedAt)) {
                    database.eventDao().upsertLocal(SupabaseToEntityMapper.toEvent(row));
                }
                break;
            }
            case "budgets": {
                BudgetEntity local = database.budgetDao().getBudgetByIdSync(
                        row.optString("user_id", ""), id);
                if (shouldAcceptRemote(local == null ? -1 : local.getUpdatedAt(),
                        local == null ? 0 : local.getSyncStatus(),
                        remoteUpdatedAt)) {
                    database.budgetDao().upsertLocal(SupabaseToEntityMapper.toBudget(row));
                }
                break;
            }
            case "transactions": {
                TransactionEntity local = database.transactionDao().getByIdSync(id);
                if (shouldAcceptRemote(local == null ? -1 : local.getUpdatedAt(),
                        local == null ? 0 : local.getSyncStatus(),
                        remoteUpdatedAt)) {
                    database.transactionDao().upsertLocal(SupabaseToEntityMapper.toTransaction(row));
                }
                break;
            }
        }
    }

    private boolean shouldAcceptRemote(long localUpdatedAt, int localSyncStatus, long remoteUpdatedAt) {
        if (localUpdatedAt == -1) return true;
        if (localSyncStatus == SyncStatus.SYNCED) return true;
        return remoteUpdatedAt > localUpdatedAt;
    }
}