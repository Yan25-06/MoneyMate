package com.example.moneymate.utils;

/**
 * Application-wide constants.
 */
public class Constants {
    // SharedPreferences
    public static final String PREFS_NAME = "moneymate_prefs";
    public static final String PREF_THEME = "pref_theme";
    public static final String PREF_LANGUAGE = "pref_language";
    public static final String PREF_CURRENCY = "pref_currency";
    public static final String PREF_DATE_FORMAT = "pref_date_format";
    public static final String PREF_HIDE_BALANCE = "pref_hide_balance";

    // Defaults
    public static final String DEFAULT_CURRENCY = "VND";
    public static final String DEFAULT_LANGUAGE = "vi";
    public static final String DEFAULT_DATE_FORMAT = "dd/MM/yyyy";
    public static final int RECENT_TRANSACTION_LIMIT = 10;

    private Constants() {
        // Prevent instantiation
    }
}
