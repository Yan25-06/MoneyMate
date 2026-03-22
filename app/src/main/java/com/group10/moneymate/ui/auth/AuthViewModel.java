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
        PASSWORD_RESET_EMAIL_SENT,
        ERROR
    }

    public AuthViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        authRepository = container.authRepository;
        authState.setValue(AuthState.IDLE);
    }

    public LiveData<AuthState> getAuthState() {
        return authState;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void setAuthState(AuthState state) {
        authState.setValue(state);
    }

    public void setError(String message) {
        errorMessage.setValue(message);
        authState.setValue(AuthState.ERROR);
    }

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
                authState.postValue(AuthState.AUTHENTICATED);
            }

            @Override
            public void onError(String message) {
                errorMessage.postValue(message);
                authState.postValue(AuthState.ERROR);
            }
        });
    }

    public void loginAnonymously() {
        authState.setValue(AuthState.LOADING);
        authRepository.loginAnonymously(new AuthRepository.AuthCallback() {
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

    public boolean isLoggedIn() {
        return authRepository.isLoggedIn();
    }
}
