package com.group10.moneymate.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SharedPreferences wrapper for app settings.
 */
public class PrefsManager {
    private final SharedPreferences prefs;

    public PrefsManager(Context context) {
        prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
    }

    // Theme
    public boolean isDarkTheme() {
        return prefs.getBoolean(Constants.PREF_THEME, false);
    }

    public void setDarkTheme(boolean isDark) {
        prefs.edit().putBoolean(Constants.PREF_THEME, isDark).apply();
    }

    // Language
    public String getLanguage() {
        return prefs.getString(Constants.PREF_LANGUAGE, Constants.DEFAULT_LANGUAGE);
    }

    public void setLanguage(String language) {
        prefs.edit().putString(Constants.PREF_LANGUAGE, language).apply();
    }

    // Currency
    public String getCurrency() {
        return prefs.getString(Constants.PREF_CURRENCY, Constants.DEFAULT_CURRENCY);
    }

    public void setCurrency(String currency) {
        prefs.edit().putString(Constants.PREF_CURRENCY, currency).apply();
    }

    // Date Format
    public String getDateFormat() {
        return prefs.getString(Constants.PREF_DATE_FORMAT, Constants.DEFAULT_DATE_FORMAT);
    }

    public void setDateFormat(String format) {
        prefs.edit().putString(Constants.PREF_DATE_FORMAT, format).apply();
    }

    // Hide Balance
    public boolean isHideBalance() {
        return prefs.getBoolean(Constants.PREF_HIDE_BALANCE, false);
    }

    public void setHideBalance(boolean hide) {
        prefs.edit().putBoolean(Constants.PREF_HIDE_BALANCE, hide).apply();
    }

    // Clear all
    public void clearAll() {
        prefs.edit().clear().apply();
    }
}
