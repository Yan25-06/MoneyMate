package com.group10.moneymate.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Quản lý cài đặt thông báo trong SharedPreferences.
 *
 * Các khóa:
 *  - notif_global_enabled: bật/tắt toàn bộ thông báo
 *  - notif_daily_entry_enabled: nhắc nhập liệu hàng ngày
 *  - notif_daily_entry_hour/minute: giờ nhắc nhập liệu (default 21:00)
 *  - notif_debt_enabled: nhắc khoản nợ
 *  - notif_debt_hour/minute: giờ nhắc nợ (default 08:00)
 *  - notif_budget_enabled: nhắc ngân sách
 *  - notif_budget_threshold: ngưỡng % (default 80)
 *  - notif_budget_hour/minute: giờ check ngân sách (default 19:00)
 */
public class NotificationPreferenceManager {

    private static final String PREFS_NAME = "moneymate_notification_prefs";

    // Keys
    private static final String KEY_GLOBAL_ENABLED         = "notif_global_enabled";
    private static final String KEY_DAILY_ENTRY_ENABLED     = "notif_daily_entry_enabled";
    private static final String KEY_DAILY_ENTRY_HOUR        = "notif_daily_entry_hour";
    private static final String KEY_DAILY_ENTRY_MINUTE      = "notif_daily_entry_minute";
    private static final String KEY_DEBT_ENABLED            = "notif_debt_enabled";
    private static final String KEY_DEBT_HOUR               = "notif_debt_hour";
    private static final String KEY_DEBT_MINUTE             = "notif_debt_minute";
    private static final String KEY_BUDGET_ENABLED          = "notif_budget_enabled";
    private static final String KEY_BUDGET_THRESHOLD        = "notif_budget_threshold";
    private static final String KEY_BUDGET_HOUR             = "notif_budget_hour";
    private static final String KEY_BUDGET_MINUTE           = "notif_budget_minute";

    // Defaults
    public static final int DEFAULT_DAILY_ENTRY_HOUR   = 21;
    public static final int DEFAULT_DAILY_ENTRY_MINUTE = 0;
    public static final int DEFAULT_DEBT_HOUR          = 8;
    public static final int DEFAULT_DEBT_MINUTE        = 0;
    public static final int DEFAULT_BUDGET_THRESHOLD   = 80;
    public static final int DEFAULT_BUDGET_HOUR        = 19;
    public static final int DEFAULT_BUDGET_MINUTE      = 0;

    private static volatile NotificationPreferenceManager INSTANCE;

    private final SharedPreferences prefs;

    private NotificationPreferenceManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static NotificationPreferenceManager getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (NotificationPreferenceManager.class) {
                if (INSTANCE == null) {
                    INSTANCE = new NotificationPreferenceManager(context);
                }
            }
        }
        return INSTANCE;
    }

    // ─── Global ───────────────────────────────────────────────────────────────

    public boolean isGlobalEnabled() {
        return prefs.getBoolean(KEY_GLOBAL_ENABLED, true);
    }

    public void setGlobalEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_GLOBAL_ENABLED, enabled).apply();
    }

    // ─── Daily Entry ──────────────────────────────────────────────────────────

    public boolean isDailyEntryEnabled() {
        return prefs.getBoolean(KEY_DAILY_ENTRY_ENABLED, true);
    }

    public void setDailyEntryEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DAILY_ENTRY_ENABLED, enabled).apply();
    }

    public int getDailyEntryHour() {
        return prefs.getInt(KEY_DAILY_ENTRY_HOUR, DEFAULT_DAILY_ENTRY_HOUR);
    }

    public int getDailyEntryMinute() {
        return prefs.getInt(KEY_DAILY_ENTRY_MINUTE, DEFAULT_DAILY_ENTRY_MINUTE);
    }

    public void setDailyEntryTime(int hour, int minute) {
        prefs.edit()
                .putInt(KEY_DAILY_ENTRY_HOUR, hour)
                .putInt(KEY_DAILY_ENTRY_MINUTE, minute)
                .apply();
    }

    // ─── Debt Reminder ────────────────────────────────────────────────────────

    public boolean isDebtEnabled() {
        return prefs.getBoolean(KEY_DEBT_ENABLED, true);
    }

    public void setDebtEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_DEBT_ENABLED, enabled).apply();
    }

    public int getDebtHour() {
        return prefs.getInt(KEY_DEBT_HOUR, DEFAULT_DEBT_HOUR);
    }

    public int getDebtMinute() {
        return prefs.getInt(KEY_DEBT_MINUTE, DEFAULT_DEBT_MINUTE);
    }

    public void setDebtTime(int hour, int minute) {
        prefs.edit()
                .putInt(KEY_DEBT_HOUR, hour)
                .putInt(KEY_DEBT_MINUTE, minute)
                .apply();
    }

    // ─── Budget Alert ─────────────────────────────────────────────────────────

    public boolean isBudgetEnabled() {
        return prefs.getBoolean(KEY_BUDGET_ENABLED, true);
    }

    public void setBudgetEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_BUDGET_ENABLED, enabled).apply();
    }

    public int getBudgetThresholdPercent() {
        return prefs.getInt(KEY_BUDGET_THRESHOLD, DEFAULT_BUDGET_THRESHOLD);
    }

    public void setBudgetThresholdPercent(int percent) {
        prefs.edit().putInt(KEY_BUDGET_THRESHOLD, percent).apply();
    }

    public int getBudgetHour() {
        return prefs.getInt(KEY_BUDGET_HOUR, DEFAULT_BUDGET_HOUR);
    }

    public int getBudgetMinute() {
        return prefs.getInt(KEY_BUDGET_MINUTE, DEFAULT_BUDGET_MINUTE);
    }

    public void setBudgetTime(int hour, int minute) {
        prefs.edit()
                .putInt(KEY_BUDGET_HOUR, hour)
                .putInt(KEY_BUDGET_MINUTE, minute)
                .apply();
    }
}
