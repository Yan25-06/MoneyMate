package com.group10.moneymate.workers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

/**
 * SyncRetryReceiver — Phase 4.
 *
 * Không thay đổi về chức năng so với phiên bản cũ.
 * Chỉ đảm bảo gọi SyncScheduler.enqueueManualRetryNow() đã được cập nhật
 * để trigger cả push (SyncWorker) lẫn pull (DeltaSyncWorker).
 *
 * Được gọi từ notification "Sync thất bại" khi user nhấn nút Thử lại.
 */
public class SyncRetryReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NonNull Context context, Intent intent) {
        // enqueueManualRetryNow đã được cập nhật trong SyncScheduler Phase 4
        // để trigger cả SyncWorker (push) và DeltaSyncWorker (pull)
        SyncScheduler.enqueueManualRetryNow(context.getApplicationContext());
    }
}