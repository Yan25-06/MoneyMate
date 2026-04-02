package com.group10.moneymate.workers;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;

public class SyncScheduler {

    public static final String UNIQUE_ONE_TIME_SYNC = "critical_sync";
    public static final String UNIQUE_PERIODIC_SYNC = "periodic_sync";

    private static final long ONE_TIME_DEBOUNCE_SECONDS = 5L;
    private static final long BACKOFF_INITIAL_SECONDS = 30L;

    private final Context applicationContext;

    public SyncScheduler(@NonNull Context context) {
        applicationContext = context.getApplicationContext();
    }

    public void scheduleOneTimeSyncDebounced() {
        WorkManager workManager = WorkManager.getInstance(applicationContext);
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(buildConnectedConstraints())
                .setInitialDelay(ONE_TIME_DEBOUNCE_SECONDS, TimeUnit.SECONDS)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        BACKOFF_INITIAL_SECONDS,
                        TimeUnit.SECONDS
                )
                .build();

        workManager.enqueueUniqueWork(
                UNIQUE_ONE_TIME_SYNC,
                ExistingWorkPolicy.KEEP,
                request
        );
    }

    public void ensurePeriodicSync() {
        WorkManager workManager = WorkManager.getInstance(applicationContext);
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SyncWorker.class,
                1,
                TimeUnit.HOURS
        )
                .setConstraints(buildConnectedConstraints())
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        BACKOFF_INITIAL_SECONDS,
                        TimeUnit.SECONDS
                )
                .build();

        workManager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.KEEP,
                request
        );
    }

    public static void enqueueManualRetryNow(@NonNull Context context) {
        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        UNIQUE_ONE_TIME_SYNC,
                        ExistingWorkPolicy.KEEP,
                        new OneTimeWorkRequest.Builder(SyncWorker.class)
                                .setConstraints(buildConnectedConstraints())
                                .setBackoffCriteria(
                                        BackoffPolicy.EXPONENTIAL,
                                        BACKOFF_INITIAL_SECONDS,
                                        TimeUnit.SECONDS
                                )
                                .build()
                );
    }

    private static Constraints buildConnectedConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }
}

