package com.group10.moneymate.ui.sync;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Transformations;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

// BUG FIX: xóa import AppContainer và MoneyMateApplication không cần dùng
// (field container được tạo nhưng không bao giờ được dùng)
import com.group10.moneymate.workers.SyncScheduler;

import java.util.List;

public class SyncViewModel extends AndroidViewModel {

    public enum SyncState {
        IDLE,
        SYNCING,
        SUCCESS,
        FAILED
    }

    private final MediatorLiveData<SyncState> syncState = new MediatorLiveData<>();
    // BUG FIX: xóa field container — không dùng ở đâu cả, gây memory leak tiềm ẩn
    // (AppContainer giữ tham chiếu đến database, repositories, v.v.)
    private final WorkManager workManager;

    public SyncViewModel(@NonNull Application application) {
        super(application);
        workManager = WorkManager.getInstance(application);
        syncState.setValue(SyncState.IDLE);

        // Observe SyncWorker (push)
        LiveData<List<WorkInfo>> pushInfos = workManager.getWorkInfosForUniqueWorkLiveData(
                SyncScheduler.UNIQUE_ONE_TIME_SYNC);
        syncState.addSource(pushInfos, this::updateStateFromWorkInfos);

        // Observe DeltaSyncWorker (pull) — chỉ update nếu push không đang RUNNING
        LiveData<List<WorkInfo>> pullInfos = workManager.getWorkInfosForUniqueWorkLiveData(
                SyncScheduler.UNIQUE_ONE_TIME_DELTA);
        syncState.addSource(pullInfos, infos -> {
            SyncState current = syncState.getValue();
            if (current != SyncState.SYNCING) {
                updateStateFromWorkInfos(infos);
            }
        });

        // Observe InitialSyncWorker
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

    public LiveData<SyncState> getSyncState() {
        return syncState;
    }

    public LiveData<Boolean> isSyncing() {
        return Transformations.map(syncState, state -> state == SyncState.SYNCING);
    }

    public void syncNow() {
        syncState.setValue(SyncState.SYNCING);
        SyncScheduler.enqueueManualRetryNow(getApplication());
    }

    private void updateStateFromWorkInfos(List<WorkInfo> infos) {
        if (infos == null || infos.isEmpty()) return;
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
                syncState.setValue(SyncState.IDLE);
                break;
        }
    }
}