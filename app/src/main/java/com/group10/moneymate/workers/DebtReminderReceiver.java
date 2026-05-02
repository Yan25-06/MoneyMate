package com.group10.moneymate.workers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.utils.NotificationHelper;
import com.group10.moneymate.utils.NotificationPreferenceManager;
import com.group10.moneymate.utils.PrefsManager;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Nhận alarm nhắc khoản nợ đến hạn hôm nay.
 * Query DB trên background thread để tìm các khoản nợ ACTIVE có due_date = hôm nay.
 */
public class DebtReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationPreferenceManager prefs = NotificationPreferenceManager.getInstance(context);
        if (!prefs.isGlobalEnabled() || !prefs.isDebtEnabled()) {
            NotificationScheduler.scheduleDebtReminder(context);
            return;
        }

        final PendingResult pendingResult = goAsync();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userId = new PrefsManager(context).getUid();
                if (userId == null || userId.isEmpty()) return;

                // Khoảng thời gian của ngày hôm nay (local timezone)
                LocalDate today = LocalDate.now(ZoneId.systemDefault());
                long startOfToday = today.atStartOfDay(ZoneId.systemDefault())
                        .toInstant().toEpochMilli();
                long endOfToday = today.plusDays(1).atStartOfDay(ZoneId.systemDefault())
                        .toInstant().toEpochMilli() - 1L;

                List<DebtEntity> debts = AppDatabase.getInstance(context)
                        .debtDao()
                        .getDebtsDueTodaySync(userId, startOfToday, endOfToday);

                if (!debts.isEmpty()) {
                    NotificationHelper.showDebtReminderNotification(context, debts);
                }
            } finally {
                pendingResult.finish();
                // Reschedule cho ngày mai
                NotificationScheduler.scheduleDebtReminder(context);
            }
        });
    }
}
