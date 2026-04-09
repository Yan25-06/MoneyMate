package com.group10.moneymate.di;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.ListenableWorker;
import androidx.work.WorkerFactory;
import androidx.work.WorkerParameters;

import com.group10.moneymate.workers.AIReceiptScannerWorker;
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
                    appContainer.authRepository
            );
        }

        if (AIReceiptScannerWorker.class.getName().equals(workerClassName)) {
            return new AIReceiptScannerWorker(appContext, workerParameters);
        }

        return null;
    }
}

