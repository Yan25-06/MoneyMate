package com.group10.moneymate.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;

/**
 * Theo dõi trạng thái foreground/background của toàn bộ ứng dụng.
 *
 * Có 2 cơ chế bảo vệ:
 *
 * 1. Process bị kill / app mới khởi động:
 *    sessionAuthenticated = false theo mặc định (biến in-memory).
 *    Sau khi user xác thực thành công → gọi markAuthenticated() → set = true.
 *    Khi process bị kill → biến mất → lần sau mở app lại buộc xác thực.
 *
 * 2. App chạy dưới nền > 30 giây:
 *    Khi tất cả Activity bị stopped → lưu lastPauseTime.
 *    Khi Activity trở lại (onResume) → kiểm tra thời gian đã qua.
 *    Nếu >= TIMEOUT_MS → reset sessionAuthenticated = false → yêu cầu nhập lại.
 *
 * Dùng:
 *   needsAuthentication() → true nếu cần nhập passcode
 *   markAuthenticated()   → gọi sau khi user xác thực thành công
 */
public class AppLifecycleMonitor implements Application.ActivityLifecycleCallbacks {

    /** Thời gian nền tối đa trước khi yêu cầu xác thực lại (30 giây). */
    public static final long TIMEOUT_MS = 30_000L;

    private final PrefsManager prefsManager;

    /** Đếm số Activity đang started (để biết app có đang ở foreground không). */
    private int startedCount = 0;

    /**
     * Session flag — in-memory only, không persist.
     * false = chưa xác thực trong session này (process mới / timeout).
     * true  = đã xác thực, an toàn để vào app.
     */
    private boolean sessionAuthenticated = false;

    public AppLifecycleMonitor(PrefsManager prefsManager) {
        this.prefsManager = prefsManager;
    }

    // ─── Lifecycle callbacks ──────────────────────────────────────────────────

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        startedCount++;
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        startedCount--;
        if (startedCount <= 0) {
            startedCount = 0;
            // App vừa vào nền → lưu timestamp
            prefsManager.saveLastPauseTime(System.currentTimeMillis());
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        // Kiểm tra xem có timeout chưa — nếu có thì reset session
        // (HomeActivity sẽ tự detect qua needsAuthentication() và chuyển màn hình)
        if (hasBackgroundTimeoutOccurred()) {
            sessionAuthenticated = false;
            // Reset timestamp để tránh double-trigger
            prefsManager.saveLastPauseTime(0L);
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Kiểm tra xem app có cần nhập passcode không.
     *
     * Trả về true nếu:
     *  - Session chưa được xác thực (process mới, hoặc đã bị reset do timeout)
     */
    public boolean needsAuthentication() {
        return !sessionAuthenticated;
    }

    /**
     * Đánh dấu session đã xác thực thành công.
     * Gọi từ PasscodeActivity/PasscodeFragment sau khi PIN đúng hoặc PIN được tạo xong.
     */
    public void markAuthenticated() {
        sessionAuthenticated = true;
        prefsManager.saveLastPauseTime(0L); // clear timeout flag
    }

    /**
     * Kiểm tra xem thời gian ở nền có vượt quá TIMEOUT_MS không.
     * Chỉ dùng nội bộ.
     */
    private boolean hasBackgroundTimeoutOccurred() {
        long lastPause = prefsManager.getLastPauseTime();
        if (lastPause <= 0) return false;
        return (System.currentTimeMillis() - lastPause) >= TIMEOUT_MS;
    }

    // ─── Unused callbacks ─────────────────────────────────────────────────────

    @Override public void onActivityCreated(@NonNull Activity a, Bundle b) {}
    @Override public void onActivityPaused(@NonNull Activity a) {}
    @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle b) {}
    @Override public void onActivityDestroyed(@NonNull Activity a) {}
}
