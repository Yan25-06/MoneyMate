package com.group10.moneymate.di;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;

import com.group10.moneymate.workers.AIReceiptScannerWorker;
import com.group10.moneymate.workers.DeltaSyncWorker;
import com.group10.moneymate.workers.InitialSyncWorker;
import com.group10.moneymate.workers.SyncWorker;

public class MoneyMateWorkerFactory extends WorkerFactory {

    private final AppContainer appContainer;

    public MoneyMateWorkerFactory(@NonNull AppContainer appContainer) {
        this.appContainer = appContainer;
    }

    @Nullable
    @Override
    public ListenableWorker createWorker(@NonNull Context appContext,
                                         @NonNull String workerClassName,
                                         @NonNull WorkerParameters workerParameters) {
        // Phase 2: Push local → Supabase
        if (SyncWorker.class.getName().equals(workerClassName)) {
            return new SyncWorker(
                    appContext,
                    workerParameters,
                    appContainer.transactionRepository,
                    appContainer.budgetRepository,
                    appContainer.categoryRepository,
                    appContainer.walletRepository,
                    appContainer.debtRepository,
                    appContainer.eventRepository,
                    appContainer.syncMetadataRepository,
                    appContainer.authRepository,
                    appContainer.supabaseSyncClient
            );
        }

        // Phase 3: Pull toàn bộ khi thiết bị mới
        if (InitialSyncWorker.class.getName().equals(workerClassName)) {
            return new InitialSyncWorker(
                    appContext,
                    workerParameters,
                    appContainer.database,
                    appContainer.supabaseSyncClient,
                    appContainer.syncMetadataRepository,
                    appContainer.authRepository
            );
        }

        // Phase 4: Pull delta định kỳ từ Supabase
        if (DeltaSyncWorker.class.getName().equals(workerClassName)) {
            return new DeltaSyncWorker(
                    appContext,
                    workerParameters,
                    appContainer.database,
                    appContainer.supabaseSyncClient,
                    appContainer.syncMetadataRepository,
                    appContainer.authRepository
            );
        }

        if (AIReceiptScannerWorker.class.getName().equals(workerClassName)) {
            return new AIReceiptScannerWorker(
                    appContext,
                    workerParameters,
                    appContainer.receiptParserBridge,
                    appContainer.geminiService,
                    appContainer.categoryRepository,
                    appContainer.authRepository
            );
        }

        return null;
    }
}