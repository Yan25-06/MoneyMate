package com.group10.moneymate.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;

/**
 * SharedPreferences wrapper for app settings.
 */
public class PrefsManager {

    private static final String KEY_UID              = "uid";
    private static final String KEY_LOGGED_IN        = "is_logged_in";
    private static final String KEY_GUEST_UID        = "guest_local_uid";
    private static final String KEY_PASSCODE_HASH    = "passcode_hash";
    private static final String KEY_PASSCODE_UID     = "passcode_uid";
    private static final String KEY_PASSCODE_ENABLED = "passcode_enabled";

    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ─── Auth ─────────────────────────────────────────────────────────────────

    public String getUid() {
        return prefs.getString(KEY_UID, null);
    }

    public void saveUid(String uid) {
        prefs.edit().putString(KEY_UID, uid).apply();
    }

    public String getOrCreateGuestUid() {
        String guestUid = prefs.getString(KEY_GUEST_UID, null);
        if (guestUid != null && !guestUid.trim().isEmpty()) {
            return guestUid;
        }
        guestUid = "guest_" + UUID.randomUUID().toString();
        prefs.edit().putString(KEY_GUEST_UID, guestUid).apply();
        return guestUid;
    }

    public boolean isLoggedIn() {
        return prefs.getBoolean(KEY_LOGGED_IN, false);
    }

    public void setLoggedIn(boolean loggedIn) {
        prefs.edit().putBoolean(KEY_LOGGED_IN, loggedIn).apply();
    }

    // ─── Passcode ─────────────────────────────────────────────────────────────

    /**
     * Lưu hash passcode cho một user cụ thể.
     * Dùng sau khi user tạo passcode lần đầu hoặc đổi passcode.
     */
    public void savePasscodeHash(String uid, String hash) {
        prefs.edit()
                .putString(KEY_PASSCODE_HASH, hash)
                .putString(KEY_PASSCODE_UID, uid)
                .putBoolean(KEY_PASSCODE_ENABLED, true)
                .apply();
    }

    /**
     * Xóa passcode (ví dụ: khi user logout hoặc tắt passcode).
     */
    public void clearPasscode() {
        prefs.edit()
                .remove(KEY_PASSCODE_HASH)
                .remove(KEY_PASSCODE_UID)
                .putBoolean(KEY_PASSCODE_ENABLED, false)
                .apply();
    }

    /**
     * Lấy hash passcode đã lưu. Trả về null nếu chưa có.
     */
    public String getPasscodeHash() {
        return prefs.getString(KEY_PASSCODE_HASH, null);
    }

    /**
     * Lấy UID của user gắn với passcode đang lưu.
     */
    public String getPasscodeUid() {
        return prefs.getString(KEY_PASSCODE_UID, null);
    }

    /**
     * Kiểm tra xem passcode có được bật không.
     * Chỉ true khi đã có passcode hash được lưu.
     */
    public boolean isPasscodeEnabled() {
        return prefs.getBoolean(KEY_PASSCODE_ENABLED, false)
                && prefs.getString(KEY_PASSCODE_HASH, null) != null;
    }

    // ─── Theme ────────────────────────────────────────────────────────────────

    public boolean isDarkTheme() {
        return prefs.getBoolean(Constants.PREF_THEME, false);
    }

    public void setDarkTheme(boolean isDark) {
        prefs.edit().putBoolean(Constants.PREF_THEME, isDark).apply();
    }

    // ─── Language ─────────────────────────────────────────────────────────────

    public String getLanguage() {
        return prefs.getString(Constants.PREF_LANGUAGE, Constants.DEFAULT_LANGUAGE);
    }

    public void setLanguage(String language) {
        prefs.edit().putString(Constants.PREF_LANGUAGE, language).apply();
    }

    // ─── Currency ─────────────────────────────────────────────────────────────

    public String getCurrency() {
        return prefs.getString(Constants.PREF_CURRENCY, Constants.DEFAULT_CURRENCY);
    }

    public void setCurrency(String currency) {
        prefs.edit().putString(Constants.PREF_CURRENCY, currency).apply();
    }

    // ─── Date Format ──────────────────────────────────────────────────────────

    public String getDateFormat() {
        return prefs.getString(Constants.PREF_DATE_FORMAT, Constants.DEFAULT_DATE_FORMAT);
    }

    public void setDateFormat(String format) {
        prefs.edit().putString(Constants.PREF_DATE_FORMAT, format).apply();
    }

    // ─── Hide Balance ─────────────────────────────────────────────────────────

    public boolean isHideBalance() {
        return prefs.getBoolean(Constants.PREF_HIDE_BALANCE, false);
    }

    public void setHideBalance(boolean hide) {
        prefs.edit().putBoolean(Constants.PREF_HIDE_BALANCE, hide).apply();
    }

    // ─── Clear ────────────────────────────────────────────────────────────────

    public void clearAll() {
        prefs.edit().clear().apply();
    }
}