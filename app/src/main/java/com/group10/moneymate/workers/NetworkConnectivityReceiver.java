package com.group10.moneymate.workers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import androidx.annotation.NonNull;

/**
 * NetworkConnectivityReceiver — Phase 4.
 *
 * Lắng nghe sự kiện mạng quay trở lại (CONNECTIVITY_ACTION).
 * Khi có mạng → trigger SyncScheduler.enqueueManualRetryNow() ngay lập tức
 * để đẩy dữ liệu pending lên Supabase mà không cần đợi lịch periodic.
 *
 * Đăng ký trong AndroidManifest.xml:
 *
 *   <receiver
 *       android:name=".workers.NetworkConnectivityReceiver"
 *       android:exported="false">
 *     <intent-filter>
 *       <action android:name="android.net.conn.CONNECTIVITY_CHANGE"/>
 *     </intent-filter>
 *   </receiver>
 *
 * Lưu ý: CONNECTIVITY_CHANGE bị hạn chế từ Android 7+ với static receiver.
 * Với thiết bị API 24+, receiver này chỉ hoạt động khi app đang foreground
 * (đủ cho use case "user mở app sau khi có mạng trở lại").
 * WorkManager tự handle background sync qua NetworkType.CONNECTED constraint.
 *
 * Để nhận khi app background trên API 24+, cần dùng dynamic registration
 * trong một Service — xem NetworkCallbackService (file riêng).
 */
public class NetworkConnectivityReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(@NonNull Context context, Intent intent) {
        if (!isNetworkAvailable(context)) {
            return; // mạng mất → không làm gì
        }

        // Mạng vừa có → trigger sync ngay, không debounce
        SyncScheduler.enqueueManualRetryNow(context.getApplicationContext());
    }

    private boolean isNetworkAvailable(@NonNull Context context) {
        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }
}