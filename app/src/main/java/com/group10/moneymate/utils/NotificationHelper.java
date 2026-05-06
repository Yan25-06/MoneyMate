package com.group10.moneymate.utils;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.ui.main.HomeActivity;
import com.group10.moneymate.workers.SyncRetryReceiver;

import java.util.List;
import java.util.Locale;

/**
 * Tập trung logic tạo Notification Channel và hiển thị các loại thông báo.
 *
 * Channel IDs:
 *  - ch_daily_entry:  Nhắc nhập liệu hàng ngày
 *  - ch_debt_reminder: Nhắc khoản nợ đến hạn
 *  - ch_budget_alert:  Cảnh báo ngân sách
 */
public class NotificationHelper {

    // ─── Channel IDs ──────────────────────────────────────────────────────────
    public static final String CHANNEL_DAILY_ENTRY  = "ch_daily_entry";
    public static final String CHANNEL_DEBT         = "ch_debt_reminder";
    public static final String CHANNEL_BUDGET       = "ch_budget_alert";

    // ─── Notification IDs ─────────────────────────────────────────────────────
    private static final int NOTIF_ID_DAILY_ENTRY = 1001;
    private static final int NOTIF_ID_DEBT        = 1002;
    private static final int NOTIF_ID_BUDGET       = 1003;

    private NotificationHelper() { /* utility class */ }

    /**
     * Tạo 3 Notification Channel. Gọi một lần khi app khởi động.
     * An toàn khi gọi nhiều lần (Android bỏ qua nếu channel đã tồn tại).
     */
    public static void createNotificationChannels(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel channelDailyEntry = new NotificationChannel(
                CHANNEL_DAILY_ENTRY,
                context.getString(R.string.notif_channel_daily_entry_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channelDailyEntry.setDescription(context.getString(R.string.notif_channel_daily_entry_desc));
        manager.createNotificationChannel(channelDailyEntry);

        NotificationChannel channelDebt = new NotificationChannel(
                CHANNEL_DEBT,
                context.getString(R.string.notif_channel_debt_name),
                NotificationManager.IMPORTANCE_HIGH);
        channelDebt.setDescription(context.getString(R.string.notif_channel_debt_desc));
        manager.createNotificationChannel(channelDebt);

        NotificationChannel channelBudget = new NotificationChannel(
                CHANNEL_BUDGET,
                context.getString(R.string.notif_channel_budget_name),
                NotificationManager.IMPORTANCE_HIGH);
        channelBudget.setDescription(context.getString(R.string.notif_channel_budget_desc));
        manager.createNotificationChannel(channelBudget);
    }

    // ─── Daily Entry ──────────────────────────────────────────────────────────

    public static void showDailyEntryNotification(@NonNull Context context) {
        Notification notification = buildBase(context, CHANNEL_DAILY_ENTRY)
                .setSmallIcon(R.drawable.outline_edit_24)
                .setContentTitle(context.getString(R.string.notif_daily_entry_title))
                .setContentText(context.getString(R.string.notif_daily_entry_text))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.notif_daily_entry_text)))
                .build();
        post(context, NOTIF_ID_DAILY_ENTRY, notification);
    }

    // ─── Debt Reminder ────────────────────────────────────────────────────────

    public static void showDebtReminderNotification(@NonNull Context context,
                                                    @NonNull List<DebtEntity> debts) {
        if (debts.isEmpty()) return;

        String title = context.getString(R.string.notif_debt_title);
        String text;
        if (debts.size() == 1) {
            DebtEntity debt = debts.get(0);
            String typeLabel = "BORROWING".equals(debt.getType())
                    ? context.getString(R.string.notif_debt_type_borrow)
                    : context.getString(R.string.notif_debt_type_lend);
            text = String.format(Locale.getDefault(),
                    context.getString(R.string.notif_debt_text_single),
                    typeLabel,
                    debt.getPersonName(),
                    CurrencyFormatter.format(debt.getRemainingAmount(), "VND"));
        } else {
            text = String.format(Locale.getDefault(),
                    context.getString(R.string.notif_debt_text_multiple),
                    debts.size());
        }

        Notification notification = buildBase(context, CHANNEL_DEBT)
                .setSmallIcon(R.drawable.outline_receipt_24)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        post(context, NOTIF_ID_DEBT, notification);
    }

    // ─── Budget Alert ─────────────────────────────────────────────────────────

    public static void showBudgetAlertNotification(@NonNull Context context,
                                                   @NonNull List<BudgetAlertInfo> alerts) {
        if (alerts.isEmpty()) return;

        StringBuilder sb = new StringBuilder();
        boolean hasExceeded = false;
        for (BudgetAlertInfo alert : alerts) {
            if (alert.isExceeded) {
                hasExceeded = true;
                sb.append(String.format(Locale.getDefault(),
                        context.getString(R.string.notif_budget_exceeded_item),
                        alert.budgetName,
                        CurrencyFormatter.format(alert.spentAmount, "VND"),
                        CurrencyFormatter.format(alert.budgetAmount, "VND")));
            } else {
                sb.append(String.format(Locale.getDefault(),
                        context.getString(R.string.notif_budget_warning_item),
                        alert.budgetName,
                        (int) alert.percentUsed,
                        CurrencyFormatter.format(alert.budgetAmount, "VND")));
            }
            sb.append("\n");
        }

        String title = hasExceeded
                ? context.getString(R.string.notif_budget_exceeded_title)
                : context.getString(R.string.notif_budget_warning_title);

        Notification notification = buildBase(context, CHANNEL_BUDGET)
                .setSmallIcon(R.drawable.outline_bar_chart_24)
                .setContentTitle(title)
                .setContentText(sb.toString().trim())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(sb.toString().trim()))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();
        post(context, NOTIF_ID_BUDGET, notification);
    }

    // ─── Sync Failed ──────────────────────────────────────────────────────────

    private static final String SYNC_CHANNEL_ID = "sync_status_channel";
    private static final int SYNC_FAILED_NOTIFICATION_ID = 6101;

    /**
     * Hiển thị thông báo lỗi đồng bộ khi app ở background.
     * Gọi bởi SyncWorker khi đồng bộ thất bại và app không ở foreground.
     */
    public static void showSyncFailedNotification(@NonNull Context context) {
        if (!canPostNotifications(context)) {
            return;
        }

        NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) {
            return;
        }

        ensureSyncChannel(notificationManager, context);

        PendingIntent retryIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(context, SyncRetryReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, SYNC_CHANNEL_ID)
                .setSmallIcon(R.drawable.outline_notifications_24)
                .setContentTitle(context.getString(R.string.sync_failed_title))
                .setContentText(context.getString(R.string.sync_failed_message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .addAction(
                        R.drawable.outline_notifications_24,
                        context.getString(R.string.sync_retry_action),
                        retryIntent
                );

        notificationManager.notify(SYNC_FAILED_NOTIFICATION_ID, builder.build());
    }

    private static void ensureSyncChannel(@NonNull NotificationManager notificationManager,
                                          @NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                SYNC_CHANNEL_ID,
                context.getString(R.string.sync_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.sync_channel_description));
        notificationManager.createNotificationChannel(channel);
    }

    private static boolean canPostNotifications(@NonNull Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────


    private static NotificationCompat.Builder buildBase(@NonNull Context context,
                                                        @NonNull String channelId) {
        Intent openIntent = new Intent(context, HomeActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, openIntent, flags);

        return new NotificationCompat.Builder(context, channelId)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);
    }

    private static void post(@NonNull Context context, int notifId,
                             @NonNull Notification notification) {
        try {
            NotificationManagerCompat.from(context).notify(notifId, notification);
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS permission chua duoc cap (Android 13+)
        }
    }

    // ─── Inner model ──────────────────────────────────────────────────────────

    /** Thông tin về một ngân sách vượt/sắp vượt ngưỡng. */
    public static class BudgetAlertInfo {
        public final String budgetName;
        public final double budgetAmount;
        public final double spentAmount;
        public final double percentUsed;
        public final boolean isExceeded;

        public BudgetAlertInfo(@NonNull String budgetName,
                               double budgetAmount,
                               double spentAmount) {
            this.budgetName = budgetName;
            this.budgetAmount = budgetAmount;
            this.spentAmount = spentAmount;
            this.percentUsed = budgetAmount > 0 ? (spentAmount / budgetAmount) * 100.0 : 0;
            this.isExceeded = spentAmount >= budgetAmount;
        }
    }
}
