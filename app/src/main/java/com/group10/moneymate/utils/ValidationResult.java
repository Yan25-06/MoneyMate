package com.group10.moneymate.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Lightweight validation result used across auth and passcode flows.
 */
public class ValidationResult {

    public static final String FIELD_NONE = "none";
    public static final String FIELD_USERNAME = "username";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_LOGIN = "login";
    public static final String FIELD_PASSWORD = "password";
    public static final String FIELD_CONFIRM_PASSWORD = "confirm_password";
    public static final String FIELD_PASSCODE = "passcode";

    private final boolean success;
    @Nullable
    private final String errorMessage;
    @NonNull
    private final String errorField;

    private ValidationResult(boolean success, @Nullable String errorMessage, @NonNull String errorField) {
        this.success = success;
        this.errorMessage = errorMessage;
        this.errorField = errorField;
    }

    public static ValidationResult success() {
        return new ValidationResult(true, null, FIELD_NONE);
    }

    public static ValidationResult error(@NonNull String field, @NonNull String message) {
        return new ValidationResult(false, message, field);
    }

    public boolean isSuccess() {
        return success;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    @NonNull
    public String getErrorField() {
        return errorField;
    }
}

