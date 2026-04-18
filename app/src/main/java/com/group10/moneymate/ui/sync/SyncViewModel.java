package com.group10.moneymate.ui.sync;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.workers.SyncScheduler;

import java.util.List;

/**
 * SyncViewModel — ViewModel dùng chung, có thể attach vào bất kỳ Fragment nào.
 *
 * Expose:
 * - syncState: trạng thái tổng hợp (IDLE / SYNCING / SUCCESS / FAILED)
 * - hasPendingChanges: true nếu có bản ghi chưa được sync (dùng để hiện badge)
 * - lastSyncTime: thời điểm sync thành công gần nhất (từ PrefsManager)
 *
 * Observe WorkManager LiveData để tự động cập nhật khi SyncWorker chạy.
 * Dùng scope của Activity để dùng chung giữa các Fragment:
 *   viewModel = new ViewModelProvider(requireActivity()).get(SyncViewModel.class);
 */
public class SyncViewModel extends AndroidViewModel {

    public enum SyncState {
        IDLE,     // chưa sync lần nào hoặc đã thành công, không có gì pending
        SYNCING,  // đang chạy SyncWorker hoặc DeltaSyncWorker
        SUCCESS,  // vừa sync xong thành công
        FAILED    // thất bại sau MAX_ATTEMPTS lần retry
    }

    private final MediatorLiveData<SyncState> syncState = new MediatorLiveData<>();
    private final AppContainer container;
    private final WorkManager workManager;

    public SyncViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        container = app.getAppContainer();
        workManager = WorkManager.getInstance(application);

        syncState.setValue(SyncState.IDLE);

        // Observe push worker (SyncWorker)
        LiveData<List<WorkInfo>> pushInfos = workManager.getWorkInfosForUniqueWorkLiveData(
                SyncScheduler.UNIQUE_ONE_TIME_SYNC);
        syncState.addSource(pushInfos, this::updateStateFromWorkInfos);

        // Observe pull worker (DeltaSyncWorker)
        LiveData<List<WorkInfo>> pullInfos = workManager.getWorkInfosForUniqueWorkLiveData(
                SyncScheduler.UNIQUE_ONE_TIME_DELTA);
        syncState.addSource(pullInfos, infos -> {
            // Chỉ update nếu push worker không đang RUNNING
            SyncState current = syncState.getValue();
            if (current != SyncState.SYNCING) {
                updateStateFromWorkInfos(infos);
            }
        });

        // Observe initial sync worker
        LiveData<List<WorkInfo>> initialInfos = workManager.getWorkInfosForUniqueWorkLiveData(
                "initial_sync");
        syncState.addSource(initialInfos, infos -> {
            if (infos == null || infos.isEmpty()) return;
            WorkInfo info = infos.get(0);
            if (info.getState() == WorkInfo.State.RUNNING) {
                syncState.setValue(SyncState.SYNCING);
            }
        });
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    public LiveData<SyncState> getSyncState() {
        return syncState;
    }

    /**
     * Convenience: true khi đang sync (cho loading indicators).
     */
    public LiveData<Boolean> isSyncing() {
        return Transformations.map(syncState, state -> state == SyncState.SYNCING);
    }

    /**
     * Trigger sync thủ công (từ nút "Đồng bộ ngay" trong UI).
     */
    public void syncNow() {
        syncState.setValue(SyncState.SYNCING);
        SyncScheduler.enqueueManualRetryNow(getApplication());
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void updateStateFromWorkInfos(List<WorkInfo> infos) {
        if (infos == null || infos.isEmpty()) {
            return;
        }
        WorkInfo info = infos.get(0);
        switch (info.getState()) {
            case RUNNING:
            case ENQUEUED:
                syncState.setValue(SyncState.SYNCING);
                break;
            case SUCCEEDED:
                syncState.setValue(SyncState.SUCCESS);
                break;
            case FAILED:
            case CANCELLED:
                syncState.setValue(SyncState.FAILED);
                break;
            case BLOCKED:
                // Đang chờ constraint (network) — coi như IDLE
                syncState.setValue(SyncState.IDLE);
                break;
        }
    }
}