package com.group10.moneymate.ui.auth;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.group10.moneymate.R;
import com.group10.moneymate.data.remote.SupabaseAuthHelper;
import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.AuthInputValidator;
import com.group10.moneymate.utils.ValidationResult;

/**
 * AuthViewModel – thay FirebaseUser bằng SupabaseAuthHelper.SupabaseUser.
 *
 * THAY ĐỔI DUY NHẤT so với phiên bản Firebase:
 *  - import FirebaseUser → import SupabaseAuthHelper.SupabaseUser
 *  - AuthCallback.onSuccess(FirebaseUser) → AuthCallback.onSuccess(SupabaseUser)
 *  - Tất cả logic, state, flow giữ nguyên 100%
 *
 * GHI CHÚ về Google link flow:
 *  - Supabase tự link account cùng email → GOOGLE_LINK_REQUIRED sẽ không bao giờ xuất hiện
 *  - Giữ lại state + method để LoginFragment không cần sửa
 */
public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<ValidationResult> validationError = new MutableLiveData<>();

    private String pendingGoogleIdToken;
    private String pendingGoogleEmail;

    public enum AuthState {
        IDLE, LOADING, AUTHENTICATED, LOGGED_OUT, PASSWORD_RESET_EMAIL_SENT,
        REGISTERED_NEEDS_PASSCODE,
        GOOGLE_LINK_REQUIRED,
        ERROR
    }

    public AuthViewModel(@NonNull Application application) {
        super(application);
        authRepository = ((MoneyMateApplication) application).getAppContainer().authRepository;
        authState.setValue(AuthState.IDLE);
    }

    public LiveData<AuthState> getAuthState() { return authState; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<ValidationResult> getValidationError() { return validationError; }
    public boolean isLoggedIn() { return authRepository.isLoggedIn(); }
    public boolean isPasscodeEnabled() { return authRepository.isPasscodeEnabled(); }
    public void setAuthState(AuthState state) { authState.setValue(state); }
    public void clearValidationError() { validationError.setValue(null); }
    public String getPendingGoogleEmail() { return pendingGoogleEmail; }

    public void setError(String message) {
        errorMessage.setValue(message);
        authState.setValue(AuthState.ERROR);
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    public void login(String loginIdentifier, String password) {
        ValidationResult result = AuthInputValidator.validateLoginInput(
                getApplication(), loginIdentifier, password);
        if (!result.isSuccess()) {
            validationError.setValue(result);
            setError(result.getErrorMessage());
            return;
        }
        authState.setValue(AuthState.LOADING);
        authRepository.login(loginIdentifier, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(SupabaseAuthHelper.SupabaseUser user) {
                authState.postValue(AuthState.AUTHENTICATED);
            }
            @Override
            public void onError(String message) {
                errorMessage.postValue(mapLoginErrorMessage(message));
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    // ─── Google Login ─────────────────────────────────────────────────────────

    public void loginWithGoogle(String idToken) {
        authState.setValue(AuthState.LOADING);
        authRepository.loginWithGoogle(idToken, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(SupabaseAuthHelper.SupabaseUser user) {
                authState.postValue(AuthState.AUTHENTICATED);
            }
            @Override
            public void onError(String message) {
                // Supabase không có collision case nhưng giữ lại để LoginFragment không phải sửa
                if (message != null && message.startsWith("auth_google_link_needed:")) {
                    pendingGoogleIdToken = idToken;
                    pendingGoogleEmail = message.substring("auth_google_link_needed:".length());
                    authState.postValue(AuthState.GOOGLE_LINK_REQUIRED);
                    return;
                }
                errorMessage.postValue(mapLoginErrorMessage(message));
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    public void linkPendingGoogle(String password) {
        if (pendingGoogleIdToken == null || pendingGoogleEmail == null) {
            setError(getApplication().getString(R.string.error_auth_login_failed));
            return;
        }
        authState.setValue(AuthState.LOADING);
        authRepository.linkGoogleToEmailAccount(
                pendingGoogleEmail, password, pendingGoogleIdToken,
                new AuthRepository.AuthCallback() {
                    @Override
                    public void onSuccess(SupabaseAuthHelper.SupabaseUser user) {
                        pendingGoogleIdToken = null;
                        pendingGoogleEmail   = null;
                        authState.postValue(AuthState.AUTHENTICATED);
                    }
                    @Override
                    public void onError(String message) {
                        errorMessage.postValue(mapLoginErrorMessage(message));
                        authState.postValue(AuthState.ERROR);
                    }
                });
    }

    // ─── Register ─────────────────────────────────────────────────────────────

    public void register(String email, String password, String confirmPassword, String displayName) {
        ValidationResult result = AuthInputValidator.validateRegisterInput(
                getApplication(), displayName, email, password, confirmPassword);
        if (!result.isSuccess()) {
            validationError.setValue(result);
            setError(result.getErrorMessage());
            return;
        }
        String trimmedDisplayName = displayName != null ? displayName.trim() : "";
        authState.setValue(AuthState.LOADING);
        authRepository.register(email, password, trimmedDisplayName, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(SupabaseAuthHelper.SupabaseUser user) {
                // Sau đăng ký → bắt buộc tạo PIN
                authState.postValue(AuthState.REGISTERED_NEEDS_PASSCODE);
            }
            @Override
            public void onError(String message) {
                errorMessage.postValue(message);
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    // ─── Password reset ───────────────────────────────────────────────────────

    public void sendPasswordResetEmail(String email) {
        authState.setValue(AuthState.LOADING);
        authRepository.sendPasswordResetEmail(email, new AuthRepository.SimpleCallback() {
            @Override public void onSuccess() {
                errorMessage.postValue(null);
                authState.postValue(AuthState.PASSWORD_RESET_EMAIL_SENT);
            }
            @Override public void onError(String message) {
                errorMessage.postValue(message);
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    // ─── Logout ───────────────────────────────────────────────────────────────

    public void logout() {
        authRepository.signOut();
        authState.setValue(AuthState.LOGGED_OUT);
    }

    // ─── Error mapping ────────────────────────────────────────────────────────

    private String mapLoginErrorMessage(String errorKey) {
        if ("auth_user_not_found".equals(errorKey))
            return getApplication().getString(R.string.error_auth_user_not_found);
        if ("auth_wrong_password".equals(errorKey))
            return getApplication().getString(R.string.error_auth_wrong_password);
        if ("auth_network_timeout".equals(errorKey))
            return getApplication().getString(R.string.error_auth_network_timeout);
        if ("auth_login_failed".equals(errorKey))
            return getApplication().getString(R.string.error_auth_login_failed);
        if (!TextUtils.isEmpty(errorKey)) return errorKey;
        return getApplication().getString(R.string.error_auth_login_failed);
    }
}