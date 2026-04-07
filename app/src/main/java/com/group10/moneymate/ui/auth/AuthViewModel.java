package com.group10.moneymate.ui.auth;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseUser;
import com.group10.moneymate.R;
import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.AuthInputValidator;
import com.group10.moneymate.utils.ValidationResult;

/**
 * Shared ViewModel for authentication fragments.
 */
public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<ValidationResult> validationError = new MutableLiveData<>();

    public enum AuthState {
        IDLE,
        LOADING,
        AUTHENTICATED,
        LOGGED_OUT,
        PASSWORD_RESET_EMAIL_SENT,
        /** Đăng ký xong — cần chuyển sang màn tạo passcode */
        REGISTERED_NEEDS_PASSCODE,
        /** Passcode đã được lưu thành công */
        PASSCODE_SAVED,
        /** Passcode login thành công (online hoặc offline) */
        PASSCODE_VERIFIED,
        ERROR
    }

    public AuthViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        authRepository = container.authRepository;
        authState.setValue(AuthState.IDLE);
    }

    public LiveData<AuthState> getAuthState() { return authState; }

    public LiveData<String> getErrorMessage() { return errorMessage; }

    public LiveData<ValidationResult> getValidationError() { return validationError; }

    public boolean isLoggedIn() { return authRepository.isLoggedIn(); }

    public boolean isPasscodeEnabled() { return authRepository.isPasscodeEnabled(); }

    public void setAuthState(AuthState state) { authState.setValue(state); }

    public void clearValidationError() { validationError.setValue(null); }

    public void setError(String message) {
        errorMessage.setValue(message);
        authState.setValue(AuthState.ERROR);
    }

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
            public void onSuccess(FirebaseUser user) {
                authState.postValue(AuthState.AUTHENTICATED);
            }

            @Override
            public void onError(String message) {
                errorMessage.postValue(mapLoginErrorMessage(message));
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    private String mapLoginErrorMessage(String errorKey) {
        if ("auth_user_not_found".equals(errorKey)) {
            return getApplication().getString(R.string.error_auth_user_not_found);
        }
        if ("auth_wrong_password".equals(errorKey)) {
            return getApplication().getString(R.string.error_auth_wrong_password);
        }
        if ("auth_network_timeout".equals(errorKey)) {
            return getApplication().getString(R.string.error_auth_network_timeout);
        }
        if ("auth_login_failed".equals(errorKey)) {
            return getApplication().getString(R.string.error_auth_login_failed);
        }
        if (!TextUtils.isEmpty(errorKey)) {
            return errorKey;
        }
        return getApplication().getString(R.string.error_auth_login_failed);
    }

    /**
     * Đăng ký tài khoản.
     * Khi thành công → state REGISTERED_NEEDS_PASSCODE để UI điều hướng sang tạo passcode.
     */
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
            public void onSuccess(FirebaseUser user) {
                authState.postValue(AuthState.REGISTERED_NEEDS_PASSCODE);
            }

            @Override
            public void onError(String message) {
                errorMessage.postValue(message);
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    public void sendPasswordResetEmail(String email) {
        authState.setValue(AuthState.LOADING);
        authRepository.sendPasswordResetEmail(email, new AuthRepository.SimpleCallback() {
            @Override
            public void onSuccess() {
                errorMessage.postValue(null);
                authState.postValue(AuthState.PASSWORD_RESET_EMAIL_SENT);
            }

            @Override
            public void onError(String message) {
                errorMessage.postValue(message);
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    /**
     * Đăng xuất: xóa Firebase session.
     * UI phải navigate về LoginActivity với FLAG_CLEAR_TASK sau khi observe LOGGED_OUT.
     */
    public void logout() {
        authRepository.signOut();
        authState.setValue(AuthState.LOGGED_OUT);
    }

    // ─── Passcode ─────────────────────────────────────────────────────────────

    /**
     * Lưu passcode cho user hiện tại.
     * Gọi sau khi user xác nhận passcode (CONFIRM mode).
     */
    public void savePasscode(String uid, String passcode) {
        authState.setValue(AuthState.LOADING);
        authRepository.savePasscode(uid, passcode);
        authState.setValue(AuthState.PASSCODE_SAVED);
    }

    /**
     * Xác thực passcode — hoạt động OFFLINE.
     * Kết quả: PASSCODE_VERIFIED hoặc ERROR.
     */
    public void verifyPasscode(String passcode) {
        authState.setValue(AuthState.LOADING);
        authRepository.verifyPasscode(passcode, new AuthRepository.PasscodeCallback() {
            @Override
            public void onSuccess(String uid) {
                authState.postValue(AuthState.PASSCODE_VERIFIED);
            }

            @Override
            public void onError(String message) {
                if ("wrong_passcode".equals(message)) {
                    errorMessage.postValue(getApplication().getString(R.string.error_passcode_wrong));
                } else if ("no_passcode_set".equals(message)) {
                    errorMessage.postValue(getApplication().getString(R.string.error_passcode_not_set));
                } else if (!TextUtils.isEmpty(message)) {
                    errorMessage.postValue(message);
                } else {
                    errorMessage.postValue(getApplication().getString(R.string.common_save_failed));
                }
                authState.postValue(AuthState.ERROR);
            }
        });
    }
}
