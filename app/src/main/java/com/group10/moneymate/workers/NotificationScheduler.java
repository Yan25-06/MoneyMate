package com.group10.moneymate.workers;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;

import com.group10.moneymate.utils.NotificationPreferenceManager;

import java.util.Calendar;

/**
 * Lên lịch các thông báo định kỳ bằng AlarmManager.
 *
 * Dùng AlarmManager thay vì WorkManager vì:
 * - WorkManager không đảm bảo thời điểm chính xác (có thể delay 15–30 phút)
 * - AlarmManager với setExactAndAllowWhileIdle() đảm bảo đúng giờ kể cả khi máy ngủ
 *
 * Chiến lược reschedule:
 * - Mỗi BroadcastReceiver sau khi xử lý xong sẽ tự gọi lại scheduleXxx() để đặt lịch cho ngày mai.
 * - BootReceiver gọi scheduleAll() để khôi phục sau khi máy reboot.
 */
public class NotificationScheduler {

    // Request codes cho PendingIntent (phải unique)
    private static final int RC_DAILY_ENTRY = 2001;
    private static final int RC_DEBT        = 2002;
    private static final int RC_BUDGET      = 2003;

    private NotificationScheduler() { /* utility class */ }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Lên lịch (hoặc cập nhật) tất cả 3 loại thông báo.
     * Gọi khi: app khởi động, user thay đổi cài đặt.
     */
    public static void scheduleAll(@NonNull Context context) {
        scheduleDailyEntry(context);
        scheduleDebtReminder(context);
        scheduleBudgetChecker(context);
    }

    public static void scheduleDailyEntry(@NonNull Context context) {
        NotificationPreferenceManager prefs = NotificationPreferenceManager.getInstance(context);
        if (!prefs.isGlobalEnabled() || !prefs.isDailyEntryEnabled()) {
            cancelDailyEntry(context);
            return;
        }
        scheduleExact(context, RC_DAILY_ENTRY,
                DailyEntryReminderReceiver.class,
                prefs.getDailyEntryHour(),
                prefs.getDailyEntryMinute());
    }

    public static void cancelDailyEntry(@NonNull Context context) {
        cancel(context, RC_DAILY_ENTRY, DailyEntryReminderReceiver.class);
    }

    public static void scheduleDebtReminder(@NonNull Context context) {
        NotificationPreferenceManager prefs = NotificationPreferenceManager.getInstance(context);
        if (!prefs.isGlobalEnabled() || !prefs.isDebtEnabled()) {
            cancelDebtReminder(context);
            return;
        }
        scheduleExact(context, RC_DEBT,
                DebtReminderReceiver.class,
                prefs.getDebtHour(),
                prefs.getDebtMinute());
    }

    public static void cancelDebtReminder(@NonNull Context context) {
        cancel(context, RC_DEBT, DebtReminderReceiver.class);
    }

    public static void scheduleBudgetChecker(@NonNull Context context) {
        NotificationPreferenceManager prefs = NotificationPreferenceManager.getInstance(context);
        if (!prefs.isGlobalEnabled() || !prefs.isBudgetEnabled()) {
            cancelBudgetChecker(context);
            return;
        }
        scheduleExact(context, RC_BUDGET,
                BudgetCheckerReceiver.class,
                prefs.getBudgetHour(),
                prefs.getBudgetMinute());
    }

    public static void cancelBudgetChecker(@NonNull Context context) {
        cancel(context, RC_BUDGET, BudgetCheckerReceiver.class);
    }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    /**
     * Lên lịch alarm đúng vào giờ:phút chỉ định.
     * Nếu giờ đó đã qua trong ngày hôm nay, đặt cho ngày mai.
     */
    private static void scheduleExact(@NonNull Context context,
                                      int requestCode,
                                      @NonNull Class<?> receiverClass,
                                      int hour,
                                      int minute) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;

        PendingIntent pendingIntent = buildPendingIntent(context, requestCode, receiverClass);

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        // Nếu giờ đã qua thì đặt cho ngày mai
        if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent);
        } else {
            alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.getTimeInMillis(),
                    pendingIntent);
        }
    }

    private static void cancel(@NonNull Context context,
                               int requestCode,
                               @NonNull Class<?> receiverClass) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) return;
        PendingIntent pendingIntent = buildPendingIntent(context, requestCode, receiverClass);
        alarmManager.cancel(pendingIntent);
    }

    private static PendingIntent buildPendingIntent(@NonNull Context context,
                                                    int requestCode,
                                                    @NonNull Class<?> receiverClass) {
        Intent intent = new Intent(context, receiverClass);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }
}
