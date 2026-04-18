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

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * DeltaSyncWorker — Phase 4: Pull delta từ Supabase về Room.
 *
 * Khác với InitialSyncWorker (Phase 3 — pull toàn bộ lần đầu),
 * DeltaSyncWorker chỉ kéo những bản ghi có updated_at > last_synced_at.
 *
 * Được trigger sau mỗi lần SyncWorker push thành công,
 * và theo lịch periodic để nhận thay đổi từ thiết bị khác.
 *
 * Xử lý conflict (Last Write Wins dựa theo updated_at):
 * - Pull về bản ghi remote
 * - So sánh remote.updated_at vs local.updated_at
 * - Nếu remote mới hơn → ghi đè local, set syncStatus = SYNCED
 * - Nếu local mới hơn (đang pending) → giữ nguyên local, bỏ qua remote
 *
 * Thứ tự pull giống InitialSyncWorker:
 *   wallets → categories → debts → events → budgets → transactions
 */
public class DeltaSyncWorker extends Worker {

    // Domain keys dùng namespace riêng để tránh conflict với SyncWorker checkpoint
    // SyncWorker dùng "wallets" cho push cursor
    // DeltaSyncWorker dùng "pull_wallets" cho pull cursor
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
            return Result.success(); // không có session, bỏ qua
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
            if (e.isAuthError()) {
                return Result.failure();
            }
            if (getRunAttemptCount() >= MAX_ATTEMPTS - 1) {
                return Result.failure();
            }
            return Result.retry();
        } catch (Exception e) {
            if (getRunAttemptCount() >= MAX_ATTEMPTS - 1) {
                return Result.failure();
            }
            return Result.retry();
        }
    }

    // ─── Pull logic ───────────────────────────────────────────────────────────

    private void pullDomain(@NonNull String checkpointDomain,
                            @NonNull String tableName,
                            @NonNull String userId,
                            @NonNull String token) throws SupabaseSyncClient.SyncException {
        // Dùng pull_* checkpoint riêng để không ảnh hưởng push cursor của SyncWorker
        SyncMetadataEntity checkpoint = syncMetadataRepository
                .getOrCreateCheckpoint(userId, checkpointDomain);
        long cursor = checkpoint.getLastSyncedAt();

        while (true) {
            JSONArray page = syncClient.fetchPage(tableName, userId, cursor, token);
            if (page.length() == 0) {
                return;
            }

            mergePage(tableName, page);

            JSONObject lastRow = page.optJSONObject(page.length() - 1);
            if (lastRow != null) {
                cursor = lastRow.optLong("updated_at", cursor);
                String lastId = lastRow.optString("id", "");
                syncMetadataRepository.updateCheckpoint(userId, checkpointDomain, cursor, lastId);
            }

            if (page.length() < 500) {
                return;
            }
        }
    }

    /**
     * Merge một trang dữ liệu remote vào Room với conflict resolution.
     *
     * Last Write Wins dựa theo updated_at:
     * - remote.updated_at > local.updated_at → ghi đè (remote thắng)
     * - local.updated_at >= remote.updated_at AND local.syncStatus != SYNCED
     *   → giữ local (đang pending push, sẽ thắng ở lần sync tiếp)
     * - local.syncStatus == SYNCED → luôn nhận remote (thiết bị này không có thay đổi mới)
     */
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
                database.eventDao().upsertLocal(SupabaseToEntityMapper.toEvent(row));
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

    /**
     * Quyết định có nên nhận bản ghi remote hay không.
     *
     * Nhận remote khi:
     * 1. Không có local (localUpdatedAt == -1) → bản ghi mới từ thiết bị khác
     * 2. Local đã synced (syncStatus == SYNCED) → remote luôn là mới nhất
     * 3. Remote mới hơn local (kể cả khi local đang pending) → remote thắng
     *
     * Giữ local khi:
     * - Local đang pending (syncStatus != SYNCED) VÀ local mới hơn hoặc bằng remote
     *   → local chưa được push, sẽ thắng khi SyncWorker chạy
     *
     * @param localUpdatedAt  updated_at của local, -1 nếu không có local
     * @param localSyncStatus sync_status của local
     * @param remoteUpdatedAt updated_at của remote
     */
    private boolean shouldAcceptRemote(long localUpdatedAt, int localSyncStatus, long remoteUpdatedAt) {
        if (localUpdatedAt == -1) {
            return true; // không có local → luôn nhận
        }
        if (localSyncStatus == com.group10.moneymate.models.SyncStatus.SYNCED) {
            return true; // local đã synced → nhận remote (có thể là update từ thiết bị khác)
        }
        // local đang pending: chỉ nhận nếu remote mới hơn hẳn
        return remoteUpdatedAt > localUpdatedAt;
    }
}