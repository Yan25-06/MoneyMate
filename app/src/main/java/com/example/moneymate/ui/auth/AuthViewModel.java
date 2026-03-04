package com.example.moneymate.ui.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

/**
 * Shared ViewModel for authentication fragments.
 */
public class AuthViewModel extends ViewModel {

    private final MutableLiveData<AuthState> authState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public enum AuthState {
        IDLE,
        LOADING,
        AUTHENTICATED,
        ERROR
    }

    public AuthViewModel() {
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

    // TODO: Add methods for login, register
}
