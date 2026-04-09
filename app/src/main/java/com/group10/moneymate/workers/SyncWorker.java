package com.group10.moneymate.workers;

import android.content.Context;
import android.app.ActivityManager;

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

import java.util.List;

public class SyncWorker extends Worker {

    public static final String DOMAIN_TRANSACTIONS = "transactions";
    public static final String DOMAIN_BUDGETS = "budgets";
    public static final String DOMAIN_CATEGORIES = "categories";
    public static final String DOMAIN_WALLETS = "wallets";
    public static final String DOMAIN_DEBTS = "debts";
    public static final String DOMAIN_EVENTS = "events";

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

    public SyncWorker(@NonNull Context context,
                      @NonNull WorkerParameters workerParameters,
                      @NonNull TransactionRepository transactionRepository,
                      @NonNull BudgetRepository budgetRepository,
                      @NonNull CategoryRepository categoryRepository,
                      @NonNull WalletRepository walletRepository,
                      @NonNull DebtRepository debtRepository,
                      @NonNull EventRepository eventRepository,
                      @NonNull SyncMetadataRepository syncMetadataRepository,
                      @NonNull AuthRepository authRepository) {
        super(context, workerParameters);
        this.transactionRepository = transactionRepository;
        this.budgetRepository = budgetRepository;
        this.categoryRepository = categoryRepository;
        this.walletRepository = walletRepository;
        this.debtRepository = debtRepository;
        this.eventRepository = eventRepository;
        this.syncMetadataRepository = syncMetadataRepository;
        this.authRepository = authRepository;
    }

    @NonNull
    @Override
    public Result doWork() {
        String userId = authRepository.getCurrentUserId();
        if (userId == null || userId.trim().isEmpty()) {
            return Result.success();
        }

        try {
            syncTransactions(userId);
            syncBudgets(userId);
            syncCategories(userId);
            syncWallets(userId);
            syncDebts(userId);
            syncEvents(userId);
            return Result.success();
        } catch (Exception exception) {
            if (getRunAttemptCount() >= MAX_ATTEMPTS - 1) {
                notifySyncFailure();
                return Result.failure();
            }
            return Result.retry();
        }
    }

    private void syncTransactions(@NonNull String userId) {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId,
                DOMAIN_TRANSACTIONS
        );
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<TransactionEntity> pending = transactionRepository.getPendingSyncPagedSince(
                    userId,
                    lastSyncedAt,
                    lastSyncedId,
                    SYNC_BATCH_SIZE,
                    0
            );
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (TransactionEntity transaction : pending) {
                // Placeholder for remote sync gateway. Treated as successful in Phase 5 scaffolding.
                if (transaction.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    transactionRepository.hardDeleteById(transaction.getId());
                } else {
                    transactionRepository.markSynced(transaction.getId());
                }

                lastSyncedAt = transaction.getUpdatedAt();
                lastSyncedId = transaction.getId();
                syncMetadataRepository.updateCheckpoint(
                        userId,
                        DOMAIN_TRANSACTIONS,
                        lastSyncedAt,
                        lastSyncedId
                );
            }
        }
    }

    private void syncCategories(@NonNull String userId) {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(userId, DOMAIN_CATEGORIES);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<CategoryEntity> pending = categoryRepository.getPendingSyncPagedSince(
                    userId,
                    lastSyncedAt,
                    lastSyncedId,
                    SYNC_BATCH_SIZE,
                    0
            );
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (CategoryEntity category : pending) {
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

    private void syncWallets(@NonNull String userId) {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(userId, DOMAIN_WALLETS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<WalletEntity> pending = walletRepository.getPendingSyncPagedSince(
                    userId,
                    lastSyncedAt,
                    lastSyncedId,
                    SYNC_BATCH_SIZE,
                    0
            );
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (WalletEntity wallet : pending) {
                if (wallet.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    walletRepository.hardDeleteById(wallet.getId());
                } else {
                    walletRepository.markSynced(wallet.getId());
                }

                lastSyncedAt = wallet.getUpdatedAt();
                lastSyncedId = wallet.getId();
                syncMetadataRepository.updateCheckpoint(userId, DOMAIN_WALLETS, lastSyncedAt, lastSyncedId);
            }
        }
    }

    private void syncDebts(@NonNull String userId) {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(userId, DOMAIN_DEBTS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<DebtEntity> pending = debtRepository.getPendingSyncPagedSince(
                    userId,
                    lastSyncedAt,
                    lastSyncedId,
                    SYNC_BATCH_SIZE,
                    0
            );
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (DebtEntity debt : pending) {
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

    private void syncEvents(@NonNull String userId) {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(userId, DOMAIN_EVENTS);
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<EventEntity> pending = eventRepository.getPendingSyncPagedSince(
                    userId,
                    lastSyncedAt,
                    lastSyncedId,
                    SYNC_BATCH_SIZE,
                    0
            );
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (EventEntity event : pending) {
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

    private void syncBudgets(@NonNull String userId) {
        SyncMetadataEntity checkpoint = syncMetadataRepository.getOrCreateCheckpoint(
                userId,
                DOMAIN_BUDGETS
        );
        long lastSyncedAt = checkpoint.getLastSyncedAt();
        String lastSyncedId = checkpoint.getLastSyncedId();

        while (true) {
            List<BudgetEntity> pending = budgetRepository.getPendingSyncSince(
                    userId,
                    lastSyncedAt,
                    lastSyncedId,
                    SYNC_BATCH_SIZE
            );
            if (pending == null || pending.isEmpty()) {
                return;
            }

            for (BudgetEntity budget : pending) {
                // Placeholder for remote sync gateway. Treated as successful in Phase 5 scaffolding.
                if (budget.getSyncStatus() == SyncStatus.PENDING_DELETE) {
                    budgetRepository.hardDeleteById(budget.getId());
                } else {
                    budgetRepository.markSynced(budget.getId());
                }

                lastSyncedAt = budget.getUpdatedAt();
                lastSyncedId = budget.getId();
                syncMetadataRepository.updateCheckpoint(
                        userId,
                        DOMAIN_BUDGETS,
                        lastSyncedAt,
                        lastSyncedId
                );
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
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager == null) {
            return false;
        }

        ActivityManager.RunningAppProcessInfo processInfo =
                new ActivityManager.RunningAppProcessInfo();
        ActivityManager.getMyMemoryState(processInfo);
        return processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                || processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
    }
}



