package com.group10.moneymate.workers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Khôi phục các alarm thông báo sau khi thiết bị reboot
 * hoặc sau khi app được cập nhật.
 *
 * AlarmManager bị xóa khi thiết bị tắt nguồn, nên cần đặt lại sau khi boot.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            NotificationScheduler.scheduleAll(context);
        }
    }
}
