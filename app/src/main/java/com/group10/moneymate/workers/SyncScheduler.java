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

/**
 * SyncScheduler — Phase 4.
 *
 * Thay đổi so với Phase 2:
 * 1. ensurePeriodicSync() đăng ký thêm periodic DeltaSyncWorker (pull)
 * 2. scheduleOneTimeSyncDebounced() enqueue cả push (SyncWorker) lẫn pull (DeltaSyncWorker)
 * 3. enqueueManualRetryNow() gọi cả hai worker để sync 2 chiều ngay lập tức
 * 4. Thêm scheduleDeltaPullDebounced() để trigger pull riêng (dùng sau InitialSync xong)
 *
 * Lịch periodic:
 * - SyncWorker (push):       mỗi 1 giờ
 * - DeltaSyncWorker (pull):  mỗi 15 phút (nhận thay đổi từ thiết bị khác nhanh hơn)
 *
 * One-time sync (sau mỗi write):
 * - SyncWorker:     debounce 5 giây (gom nhiều write liên tiếp)
 * - DeltaSyncWorker: debounce 10 giây (chạy sau push để nhận phản hồi)
 */
public class SyncScheduler {

    // Push worker
    public static final String UNIQUE_ONE_TIME_SYNC = "critical_sync";
    public static final String UNIQUE_PERIODIC_SYNC = "periodic_sync";

    // Pull worker
    public static final String UNIQUE_ONE_TIME_DELTA = "critical_delta_pull";
    public static final String UNIQUE_PERIODIC_DELTA = "periodic_delta_pull";

    private static final long PUSH_DEBOUNCE_SECONDS = 5L;
    private static final long PULL_DEBOUNCE_SECONDS = 10L;
    private static final long BACKOFF_INITIAL_SECONDS = 30L;
    private static final long PERIODIC_PUSH_HOURS = 1L;
    private static final long PERIODIC_PULL_MINUTES = 15L;

    private final Context applicationContext;

    public SyncScheduler(@NonNull Context context) {
        applicationContext = context.getApplicationContext();
    }

    // ─── Push (SyncWorker) ────────────────────────────────────────────────────

    /**
     * Debounced push: gom nhiều write liên tiếp vào 1 lần sync.
     * ExistingWorkPolicy.KEEP: nếu đã có request đang chờ thì không enqueue thêm.
     */
    public void scheduleOneTimeSyncDebounced() {
        WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                        UNIQUE_ONE_TIME_SYNC,
                        ExistingWorkPolicy.KEEP,
                        buildOneTimePushRequest(PUSH_DEBOUNCE_SECONDS)
                );
    }

    /**
     * Push + Pull ngay sau khi write.
     * Dùng khi cần đảm bảo cả 2 chiều được sync (vd: sau khi resolve conflict thủ công).
     */
    public void scheduleFullSyncDebounced() {
        WorkManager manager = WorkManager.getInstance(applicationContext);
        manager.enqueueUniqueWork(
                UNIQUE_ONE_TIME_SYNC,
                ExistingWorkPolicy.KEEP,
                buildOneTimePushRequest(PUSH_DEBOUNCE_SECONDS)
        );
        manager.enqueueUniqueWork(
                UNIQUE_ONE_TIME_DELTA,
                ExistingWorkPolicy.KEEP,
                buildOneTimePullRequest(PULL_DEBOUNCE_SECONDS)
        );
    }

    /**
     * Trigger delta pull riêng (không push).
     * Dùng sau khi InitialSyncWorker hoàn thành để nhận thêm delta mới nhất.
     */
    public void scheduleDeltaPullDebounced() {
        WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                        UNIQUE_ONE_TIME_DELTA,
                        ExistingWorkPolicy.KEEP,
                        buildOneTimePullRequest(PULL_DEBOUNCE_SECONDS)
                );
    }

    // ─── Periodic ─────────────────────────────────────────────────────────────

    /**
     * Đăng ký cả push và pull periodic khi app khởi động.
     * ExistingPeriodicWorkPolicy.KEEP: không reset nếu đã có lịch.
     */
    public void ensurePeriodicSync() {
        WorkManager manager = WorkManager.getInstance(applicationContext);

        // Push: mỗi 1 giờ
        manager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_SYNC,
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(SyncWorker.class, PERIODIC_PUSH_HOURS, TimeUnit.HOURS)
                        .setConstraints(buildConnectedConstraints())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INITIAL_SECONDS, TimeUnit.SECONDS)
                        .build()
        );

        // Pull: mỗi 15 phút — nhận thay đổi từ thiết bị khác nhanh hơn
        manager.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_DELTA,
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(DeltaSyncWorker.class, PERIODIC_PULL_MINUTES, TimeUnit.MINUTES)
                        .setConstraints(buildConnectedConstraints())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INITIAL_SECONDS, TimeUnit.SECONDS)
                        .build()
        );
    }

    // ─── Manual retry (gọi từ NetworkConnectivityReceiver & SyncRetryReceiver) ──

    /**
     * Trigger cả push lẫn pull ngay lập tức, không debounce.
     * Gọi khi: mạng trở lại (NetworkConnectivityReceiver), user nhấn retry.
     * ExistingWorkPolicy.REPLACE: hủy request cũ đang chờ và enqueue mới ngay.
     */
    public static void enqueueManualRetryNow(@NonNull Context context) {
        WorkManager manager = WorkManager.getInstance(context);

        manager.enqueueUniqueWork(
                UNIQUE_ONE_TIME_SYNC,
                ExistingWorkPolicy.REPLACE, // replace để chạy ngay, không đợi debounce cũ
                new OneTimeWorkRequest.Builder(SyncWorker.class)
                        .setConstraints(buildConnectedConstraints())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INITIAL_SECONDS, TimeUnit.SECONDS)
                        .build()
        );

        manager.enqueueUniqueWork(
                UNIQUE_ONE_TIME_DELTA,
                ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(DeltaSyncWorker.class)
                        .setConstraints(buildConnectedConstraints())
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INITIAL_SECONDS, TimeUnit.SECONDS)
                        .build()
        );
    }

    // ─── Builder helpers ──────────────────────────────────────────────────────

    private static OneTimeWorkRequest buildOneTimePushRequest(long delaySeconds) {
        return new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(buildConnectedConstraints())
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INITIAL_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    private static OneTimeWorkRequest buildOneTimePullRequest(long delaySeconds) {
        return new OneTimeWorkRequest.Builder(DeltaSyncWorker.class)
                .setConstraints(buildConnectedConstraints())
                .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_INITIAL_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    private static Constraints buildConnectedConstraints() {
        return new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
    }
}