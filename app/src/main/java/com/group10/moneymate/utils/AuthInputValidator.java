package com.group10.moneymate.utils;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.group10.moneymate.R;

import java.util.regex.Pattern;

/**
 * Pure regex and deterministic checks for auth/passcode input.
 */
public final class AuthInputValidator {

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9]{6,20}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");

    private AuthInputValidator() {
    }

    @NonNull
    public static ValidationResult validateRegisterInput(@NonNull Context context,
                                                         String username,
                                                         String email,
                                                         String password,
                                                         String confirmPassword) {
        String trimmedUsername = username != null ? username.trim() : "";
        String trimmedEmail = email != null ? email.trim() : "";
        String rawPassword = password != null ? password : "";
        String rawConfirmPassword = confirmPassword != null ? confirmPassword : "";

        if (TextUtils.isEmpty(trimmedUsername)) {
            return ValidationResult.error(ValidationResult.FIELD_USERNAME,
                    context.getString(R.string.error_username_required));
        }
        if (!USERNAME_PATTERN.matcher(trimmedUsername).matches()) {
            return ValidationResult.error(ValidationResult.FIELD_USERNAME,
                    context.getString(R.string.error_username_invalid));
        }
        if (TextUtils.isEmpty(trimmedEmail)) {
            return ValidationResult.error(ValidationResult.FIELD_EMAIL,
                    context.getString(R.string.error_email_required));
        }
        if (!EMAIL_PATTERN.matcher(trimmedEmail).matches()) {
            return ValidationResult.error(ValidationResult.FIELD_EMAIL,
                    context.getString(R.string.error_email_invalid));
        }
        if (TextUtils.isEmpty(rawPassword)) {
            return ValidationResult.error(ValidationResult.FIELD_PASSWORD,
                    context.getString(R.string.error_password_required));
        }
        if (!PASSWORD_PATTERN.matcher(rawPassword).matches()) {
            return ValidationResult.error(ValidationResult.FIELD_PASSWORD,
                    context.getString(R.string.error_password_policy));
        }
        if (TextUtils.isEmpty(rawConfirmPassword)) {
            return ValidationResult.error(ValidationResult.FIELD_CONFIRM_PASSWORD,
                    context.getString(R.string.error_confirm_password_required));
        }
        if (!TextUtils.equals(rawPassword, rawConfirmPassword)) {
            return ValidationResult.error(ValidationResult.FIELD_CONFIRM_PASSWORD,
                    context.getString(R.string.error_passwords_do_not_match));
        }
        return ValidationResult.success();
    }

    @NonNull
    public static ValidationResult validateLoginInput(@NonNull Context context,
                                                      String loginIdentifier,
                                                      String password) {
        String trimmedLogin = loginIdentifier != null ? loginIdentifier.trim() : "";
        String rawPassword = password != null ? password : "";

        if (TextUtils.isEmpty(trimmedLogin)) {
            return ValidationResult.error(ValidationResult.FIELD_EMAIL,
                    context.getString(R.string.error_email_required));
        }
        if (!EMAIL_PATTERN.matcher(trimmedLogin).matches()) {
            return ValidationResult.error(ValidationResult.FIELD_EMAIL,
                    context.getString(R.string.error_email_invalid));
        }
        if (TextUtils.isEmpty(rawPassword)) {
            return ValidationResult.error(ValidationResult.FIELD_PASSWORD,
                    context.getString(R.string.error_password_required));
        }
        return ValidationResult.success();
    }

    @NonNull
    public static ValidationResult validatePasscodeForCreate(@NonNull Context context,
                                                             String passcode,
                                                             int requiredLength) {
        if (TextUtils.isEmpty(passcode) || !passcode.matches("^\\d+$")) {
            return ValidationResult.error(ValidationResult.FIELD_PASSCODE,
                    context.getString(R.string.error_passcode_invalid));
        }

        if (requiredLength != 4 && requiredLength != 6) {
            requiredLength = 6;
        }

        if (passcode.length() != requiredLength) {
            return ValidationResult.error(ValidationResult.FIELD_PASSCODE,
                    context.getString(R.string.error_passcode_length, requiredLength));
        }

        if (isTooEasyPasscode(passcode)) {
            return ValidationResult.error(ValidationResult.FIELD_PASSCODE,
                    context.getString(R.string.error_passcode_too_easy));
        }

        return ValidationResult.success();
    }

    public static boolean isTooEasyPasscode(@NonNull String passcode) {
        return isAllSameDigits(passcode)
                || "1234".equals(passcode)
                || "9876".equals(passcode)
                || "123456".equals(passcode)
                || "987654".equals(passcode);
    }

    private static boolean isAllSameDigits(@NonNull String passcode) {
        char first = passcode.charAt(0);
        for (int i = 1; i < passcode.length(); i++) {
            if (passcode.charAt(i) != first) {
                return false;
            }
        }
        return true;
    }
}
