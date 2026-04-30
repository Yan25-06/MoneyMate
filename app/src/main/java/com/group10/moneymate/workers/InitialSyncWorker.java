package com.group10.moneymate.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.BudgetDao;
import com.group10.moneymate.data.local.dao.CategoryDao;
import com.group10.moneymate.data.local.dao.DebtDao;
import com.group10.moneymate.data.local.dao.EventDao;
import com.group10.moneymate.data.local.dao.TransactionDao;
import com.group10.moneymate.data.local.dao.WalletDao;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.local.entity.EventEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.remote.SupabaseSyncClient;
import com.group10.moneymate.data.remote.SupabaseToEntityMapper;
import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.data.repository.SyncMetadataRepository;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * InitialSyncWorker — Phase 3: Khôi phục dữ liệu khi đăng nhập thiết bị mới.
 *
 * Điều kiện kích hoạt: AuthRepository.login() phát hiện Room trống
 * (không có wallet nào) nhưng Supabase có dữ liệu → enqueue worker này.
 *
 * Luồng:
 *   1. Pull từng domain theo thứ tự dependency:
 *      wallets → categories → debts → events → budgets → transactions
 *   2. Với mỗi domain: phân trang bằng cursor updated_at, mỗi trang 500 bản ghi
 *   3. Insert vào Room qua upsertLocal() với syncStatus = SYNCED
 *   4. Lưu checkpoint vào sync_metadata sau mỗi trang
 *   5. Sau khi xong tất cả: đánh dấu PrefsManager.setInitialSyncDone(true)
 *
 * Idempotent: nếu bị interrupt giữa chừng (mất mạng, app bị kill),
 * lần chạy tiếp theo tiếp tục từ checkpoint đã lưu.
 *
 * Sau khi InitialSyncWorker xong, SyncWorker định kỳ sẽ tiếp quản để sync delta.
 */
public class InitialSyncWorker extends Worker {

    // Input data key — truyền qua WorkRequest.Builder.setInputData()
    public static final String KEY_USER_ID = "user_id";
    public static final String KEY_TOKEN = "token";

    // Domain names — khớp với SyncWorker và bảng Supabase
    private static final String DOMAIN_WALLETS = "wallets";
    private static final String DOMAIN_CATEGORIES = "categories";
    private static final String DOMAIN_DEBTS = "debts";
    private static final String DOMAIN_EVENTS = "events";
    private static final String DOMAIN_BUDGETS = "budgets";
    private static final String DOMAIN_TRANSACTIONS = "transactions";

    private static final int MAX_ATTEMPTS = 3;

    private final AppDatabase database;
    private final SupabaseSyncClient syncClient;
    private final SyncMetadataRepository syncMetadataRepository;
    private final AuthRepository authRepository;

    public InitialSyncWorker(@NonNull Context context,
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
        // Ưu tiên lấy từ InputData (truyền lúc enqueue), fallback sang session hiện tại
        String userId = getInputData().getString(KEY_USER_ID);
        String token = getInputData().getString(KEY_TOKEN);

        if (userId == null || userId.trim().isEmpty()) {
            userId = authRepository.getCurrentUserId();
        }
        if (token == null || token.trim().isEmpty()) {
            token = authRepository.getCurrentAccessToken();
        }
        if (userId == null || userId.trim().isEmpty() || token == null || token.trim().isEmpty()) {
            return Result.failure(); // không có session → không thể pull
        }

        try {
            pullDomain(DOMAIN_WALLETS, userId, token);
            pullDomain(DOMAIN_CATEGORIES, userId, token);
            pullDomain(DOMAIN_DEBTS, userId, token);
            pullDomain(DOMAIN_EVENTS, userId, token);
            pullDomain(DOMAIN_BUDGETS, userId, token);
            pullDomain(DOMAIN_TRANSACTIONS, userId, token);
            return Result.success();

        } catch (SupabaseSyncClient.SyncException e) {
            if (e.isAuthError()) {
                return Result.failure(); // token hết hạn, không retry
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

    /**
     * Pull toàn bộ một domain từ cursor đã lưu trong sync_metadata.
     * Idempotent: nếu đã có checkpoint thì tiếp tục từ đó.
     */
    private void pullDomain(@NonNull String domain,
                            @NonNull String userId,
                            @NonNull String token) throws SupabaseSyncClient.SyncException {
        // Đọc checkpoint — nếu đây là lần đầu thì cursor = 0 (lấy tất cả)
        long cursor = syncMetadataRepository
                .getOrCreateCheckpoint(userId, domain)
                .getLastSyncedAt();

        while (true) {
            JSONArray page = syncClient.fetchPage(domain, userId, cursor, token);
            if (page.length() == 0) {
                return; // không còn dữ liệu mới
            }

            insertPage(domain, page);

            // Cập nhật cursor = updated_at của bản ghi cuối trong trang
            JSONObject lastRow = page.optJSONObject(page.length() - 1);
            if (lastRow != null) {
                cursor = lastRow.optLong("updated_at", cursor);
                String lastId = lastRow.optString("id", "");
                syncMetadataRepository.updateCheckpoint(userId, domain, cursor, lastId);
            }

            // Nếu trang trả về ít hơn PAGE_SIZE → hết dữ liệu
            if (page.length() < 500) {
                return;
            }
        }
    }

    /**
     * Insert một trang JSON vào Room, dispatch theo domain.
     * Mỗi bản ghi dùng upsertLocal() để an toàn khi chạy lại (idempotent).
     */
    private void insertPage(@NonNull String domain, @NonNull JSONArray page) {
        database.runInTransaction(() -> {
            for (int i = 0; i < page.length(); i++) {
                JSONObject row = page.optJSONObject(i);
                if (row == null) continue;

                try {
                    switch (domain) {
                        case DOMAIN_WALLETS:
                            WalletEntity wallet = SupabaseToEntityMapper.toWallet(row);
                            database.walletDao().upsertLocal(wallet);
                            break;

                        case DOMAIN_CATEGORIES:
                            CategoryEntity category = SupabaseToEntityMapper.toCategory(row);
                            database.categoryDao().upsertLocal(category);
                            break;

                        case DOMAIN_DEBTS:
                            DebtEntity debt = SupabaseToEntityMapper.toDebt(row);
                            database.debtDao().upsertLocal(debt);
                            break;

                        case DOMAIN_EVENTS:
                            EventEntity event = SupabaseToEntityMapper.toEvent(row);
                            database.eventDao().upsertLocal(event);
                            break;

                        case DOMAIN_BUDGETS:
                            BudgetEntity budget = SupabaseToEntityMapper.toBudget(row);
                            database.budgetDao().upsertLocal(budget);
                            break;

                        case DOMAIN_TRANSACTIONS:
                            TransactionEntity tx = SupabaseToEntityMapper.toTransaction(row);
                            database.transactionDao().upsertLocal(tx);
                            break;
                    }
                } catch (Exception e) {
                    // Bỏ qua bản ghi lỗi, tiếp tục các bản ghi còn lại
                    // Không throw để không làm fail toàn bộ transaction
                    android.util.Log.w("InitialSyncWorker",
                            "Failed to insert row in domain " + domain + ": " + e.getMessage());
                }
            }
        });
    }
}