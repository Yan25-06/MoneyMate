package com.group10.moneymate.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

/**
 * Theo dõi trạng thái foreground/background của toàn bộ ứng dụng.
 *
 * Khi app vào nền (tất cả Activity bị stop): lưu timestamp hiện tại.
 * Khi app trở lại foreground: không làm gì — caller tự kiểm tra hasPasscodeTimeoutOccurred().
 *
 * Đăng ký bằng cách gọi:
 *   application.registerActivityLifecycleCallbacks(new AppLifecycleMonitor(prefsManager));
 */
public class AppLifecycleMonitor implements Application.ActivityLifecycleCallbacks {

    /** Thời gian nền tối đa trước khi yêu cầu xác thực lại (30 giây). */
    public static final long TIMEOUT_MS = 30_000L;

    private final PrefsManager prefsManager;
    private int startedActivityCount = 0;

    public AppLifecycleMonitor(PrefsManager prefsManager) {
        this.prefsManager = prefsManager;
    }

    // ─── Lifecycle callbacks ──────────────────────────────────────────────────

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        startedActivityCount++;
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        startedActivityCount--;
        if (startedActivityCount <= 0) {
            startedActivityCount = 0;
            // App vừa vào nền → lưu timestamp
            prefsManager.saveLastPauseTime(System.currentTimeMillis());
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Kiểm tra xem thời gian ở nền có vượt quá TIMEOUT_MS không.
     * @return true nếu cần yêu cầu nhập lại passcode
     */
    public boolean hasPasscodeTimeoutOccurred() {
        long lastPause = prefsManager.getLastPauseTime();
        if (lastPause <= 0) return false;
        return (System.currentTimeMillis() - lastPause) >= TIMEOUT_MS;
    }

    /**
     * Reset timeout (gọi sau khi người dùng xác thực thành công).
     */
    public void resetTimeout() {
        prefsManager.saveLastPauseTime(0L);
    }

    // ─── Unused callbacks ─────────────────────────────────────────────────────

    @Override public void onActivityCreated(@NonNull Activity a, Bundle b) {}
    @Override public void onActivityResumed(@NonNull Activity a) {}
    @Override public void onActivityPaused(@NonNull Activity a) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
    @Override public void onActivityDestroyed(@NonNull Activity a) {}
}
