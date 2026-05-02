package com.group10.moneymate.workers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.group10.moneymate.utils.NotificationHelper;
import com.group10.moneymate.utils.NotificationPreferenceManager;

/**
 * Nhận alarm nhắc nhập liệu hàng ngày.
 * Sau khi hiển thị thông báo, tự reschedule cho ngày hôm sau.
 */
public class DailyEntryReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationPreferenceManager prefs = NotificationPreferenceManager.getInstance(context);
        if (prefs.isGlobalEnabled() && prefs.isDailyEntryEnabled()) {
            NotificationHelper.showDailyEntryNotification(context);
        }
        // Reschedule cho ngày mai
        NotificationScheduler.scheduleDailyEntry(context);
    }
}
