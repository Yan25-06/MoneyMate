package com.group10.moneymate.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.group10.moneymate.R;
import com.group10.moneymate.workers.SyncRetryReceiver;

public final class NotificationHelper {

    private static final String SYNC_CHANNEL_ID = "sync_status_channel";
    private static final int SYNC_FAILED_NOTIFICATION_ID = 6101;

    private NotificationHelper() {
        // Utility class.
    }

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
}



