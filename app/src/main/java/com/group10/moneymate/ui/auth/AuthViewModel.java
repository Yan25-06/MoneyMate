package com.group10.moneymate.ui.auth;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.AndroidViewModel;

import com.google.firebase.auth.FirebaseUser;
import com.group10.moneymate.R;
import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;

/**
 * Shared ViewModel for authentication fragments.
 */
public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

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

    // ─── Expose ───────────────────────────────────────────────────────────────

    public LiveData<AuthState> getAuthState() { return authState; }

    public LiveData<String> getErrorMessage() { return errorMessage; }

    public boolean isLoggedIn() { return authRepository.isLoggedIn(); }

    public boolean isPasscodeEnabled() { return authRepository.isPasscodeEnabled(); }

    public void setAuthState(AuthState state) { authState.setValue(state); }

    public void setError(String message) {
        errorMessage.setValue(message);
        authState.setValue(AuthState.ERROR);
    }

    // ─── Firebase Auth ────────────────────────────────────────────────────────

    public void login(String email, String password) {
        authState.setValue(AuthState.LOADING);
        authRepository.login(email, password, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                authState.postValue(AuthState.AUTHENTICATED);
            }

            @Override
            public void onError(String message) {
                errorMessage.postValue(message);
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    /**
     * Đăng ký tài khoản.
     * Khi thành công → state REGISTERED_NEEDS_PASSCODE để UI điều hướng sang tạo passcode.
     */
    public void register(String email, String password, String confirmPassword, String displayName) {
        String trimmedDisplayName = displayName != null ? displayName.trim() : "";
        if (TextUtils.isEmpty(trimmedDisplayName)) {
            setError(getApplication().getString(R.string.error_display_name_required));
            return;
        }
        if (TextUtils.isEmpty(confirmPassword)) {
            setError(getApplication().getString(R.string.error_confirm_password_required));
            return;
        }
        if (!TextUtils.equals(password, confirmPassword)) {
            setError(getApplication().getString(R.string.error_passwords_do_not_match));
            return;
        }

        authState.setValue(AuthState.LOADING);
        authRepository.register(email, password, trimmedDisplayName, new AuthRepository.AuthCallback() {
            @Override
            public void onSuccess(FirebaseUser user) {
                // Sau đăng ký → phải tạo passcode trước khi vào app
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
                    errorMessage.postValue(
                            getApplication().getString(R.string.error_passcode_wrong));
                } else if ("no_passcode_set".equals(message)) {
                    errorMessage.postValue(
                            getApplication().getString(R.string.error_passcode_not_set));
                } else {
                    errorMessage.postValue(message);
                }
                authState.postValue(AuthState.ERROR);
            }
        });
    }
}