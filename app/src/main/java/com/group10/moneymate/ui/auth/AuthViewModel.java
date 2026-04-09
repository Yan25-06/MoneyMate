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

public class AuthViewModel extends AndroidViewModel {

    private final AuthRepository authRepository;
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<ValidationResult> validationError = new MutableLiveData<>();

    // Luu tam Google idToken va email khi gap collision de link sau
    private String pendingGoogleIdToken;
    private String pendingGoogleEmail;

    public enum AuthState {
        IDLE, LOADING, AUTHENTICATED, LOGGED_OUT, PASSWORD_RESET_EMAIL_SENT,
        REGISTERED_NEEDS_PASSCODE, PASSCODE_SAVED, PASSCODE_VERIFIED,
        /** Can user nhap password de link Google vao account email/password hien co */
        GOOGLE_LINK_REQUIRED,
        ERROR
    }

    public AuthViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        authRepository = app.getAppContainer().authRepository;
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
            public void onSuccess(FirebaseUser user) { authState.postValue(AuthState.AUTHENTICATED); }
            public void onError(String message) {
                errorMessage.postValue(mapLoginErrorMessage(message));
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    /**
     * Dang nhap bang Google.
     * Neu email da ton tai voi provider email/password:
     *   -> luu pendingGoogleIdToken + pendingGoogleEmail
     *   -> post state GOOGLE_LINK_REQUIRED
     *   -> Fragment hien dialog hoi password
     *   -> sau do goi linkPendingGoogle(password)
     */
    public void loginWithGoogle(String idToken) {
        authState.setValue(AuthState.LOADING);
        authRepository.loginWithGoogle(idToken, new AuthRepository.AuthCallback() {
            public void onSuccess(FirebaseUser user) { authState.postValue(AuthState.AUTHENTICATED); }
            public void onError(String message) {
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

    /**
     * Duoc goi sau khi user nhap password trong dialog link.
     * Dang nhap email/password truoc, sau do link Google credential vao.
     */
    public void linkPendingGoogle(String password) {
        if (pendingGoogleIdToken == null || pendingGoogleEmail == null) {
            setError(getApplication().getString(R.string.error_auth_login_failed));
            return;
        }
        authState.setValue(AuthState.LOADING);
        authRepository.linkGoogleToEmailAccount(
                pendingGoogleEmail, password, pendingGoogleIdToken,
                new AuthRepository.AuthCallback() {
                    public void onSuccess(FirebaseUser user) {
                        pendingGoogleIdToken = null;
                        pendingGoogleEmail = null;
                        authState.postValue(AuthState.AUTHENTICATED);
                    }
                    public void onError(String message) {
                        errorMessage.postValue(mapLoginErrorMessage(message));
                        authState.postValue(AuthState.ERROR);
                    }
                });
    }

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
            public void onSuccess(FirebaseUser user) { authState.postValue(AuthState.REGISTERED_NEEDS_PASSCODE); }
            public void onError(String message) {
                errorMessage.postValue(message);
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    public void sendPasswordResetEmail(String email) {
        authState.setValue(AuthState.LOADING);
        authRepository.sendPasswordResetEmail(email, new AuthRepository.SimpleCallback() {
            public void onSuccess() {
                errorMessage.postValue(null);
                authState.postValue(AuthState.PASSWORD_RESET_EMAIL_SENT);
            }
            public void onError(String message) {
                errorMessage.postValue(message);
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    public void logout() {
        authRepository.signOut();
        authState.setValue(AuthState.LOGGED_OUT);
    }

    public void savePasscode(String uid, String passcode) {
        authState.setValue(AuthState.LOADING);
        authRepository.savePasscode(uid, passcode);
        authState.setValue(AuthState.PASSCODE_SAVED);
    }

    public void verifyPasscode(String passcode) {
        authState.setValue(AuthState.LOADING);
        authRepository.verifyPasscode(passcode, new AuthRepository.PasscodeCallback() {
            public void onSuccess(String uid) { authState.postValue(AuthState.PASSCODE_VERIFIED); }
            public void onError(String message) {
                if ("wrong_passcode".equals(message))
                    errorMessage.postValue(getApplication().getString(R.string.error_passcode_wrong));
                else if ("no_passcode_set".equals(message))
                    errorMessage.postValue(getApplication().getString(R.string.error_passcode_not_set));
                else if (!TextUtils.isEmpty(message))
                    errorMessage.postValue(message);
                else
                    errorMessage.postValue(getApplication().getString(R.string.common_save_failed));
                authState.postValue(AuthState.ERROR);
            }
        });
    }
}