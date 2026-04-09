package com.group10.moneymate.ui.security;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.group10.moneymate.R;
import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.AuthInputValidator;
import com.group10.moneymate.utils.PrefsManager;
import com.group10.moneymate.utils.ValidationResult;

/**
 * ViewModel for PasscodeFragment.
 */
public class SecurityViewModel extends AndroidViewModel {

    public static final int MODE_CREATE = 0;
    public static final int MODE_CONFIRM = 1;
    public static final int MODE_VERIFY = 2;

    public static final int PASSCODE_LENGTH = 6;
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 60_000L;

    private final AuthRepository authRepository;
    private final PrefsManager prefsManager;

    private final MutableLiveData<PasscodeState> passcodeState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> failedAttempts = new MutableLiveData<>(0);

    private String pendingPasscode = null;
    private long lockoutUntilMillis = 0L;

    public enum PasscodeState {
        IDLE,
        PASSCODE_SAVED,
        PASSCODE_VERIFIED,
        PASSCODE_WRONG,
        LOCKED_OUT,
        ERROR
    }

    public SecurityViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        authRepository = container.authRepository;
        prefsManager = container.prefsManager;
        passcodeState.setValue(PasscodeState.IDLE);
    }

    public LiveData<PasscodeState> getPasscodeState() { return passcodeState; }

    public LiveData<String> getErrorMessage() { return errorMessage; }

    public LiveData<Integer> getFailedAttempts() { return failedAttempts; }

    public void resetState() {
        passcodeState.setValue(PasscodeState.IDLE);
    }

    public ValidationResult validateCreatePasscode(String passcode) {
        return AuthInputValidator.validatePasscodeForCreate(getApplication(), passcode, PASSCODE_LENGTH);
    }

    public void submitPasscode(String passcode, int mode) {
        switch (mode) {
            case MODE_CREATE:
                handleCreate(passcode);
                break;
            case MODE_CONFIRM:
                handleConfirm(passcode);
                break;
            case MODE_VERIFY:
                handleVerify(passcode);
                break;
            default:
                errorMessage.setValue(getApplication().getString(R.string.common_save_failed));
                passcodeState.setValue(PasscodeState.ERROR);
        }
    }

    private void handleCreate(String passcode) {
        ValidationResult validationResult = validateCreatePasscode(passcode);
        if (!validationResult.isSuccess()) {
            errorMessage.setValue(validationResult.getErrorMessage());
            passcodeState.setValue(PasscodeState.ERROR);
            return;
        }
        pendingPasscode = passcode;
        passcodeState.setValue(PasscodeState.IDLE);
    }

    private void handleConfirm(String passcode) {
        if (pendingPasscode == null) {
            errorMessage.setValue(getApplication().getString(R.string.error_passcode_not_set));
            passcodeState.setValue(PasscodeState.ERROR);
            return;
        }
        if (!pendingPasscode.equals(passcode)) {
            pendingPasscode = null;
            errorMessage.setValue(getApplication().getString(R.string.error_passcode_mismatch));
            passcodeState.setValue(PasscodeState.PASSCODE_WRONG);
            return;
        }

        String uid = prefsManager.getUid();
        if (uid != null) {
            authRepository.savePasscode(uid, passcode);
        }
        pendingPasscode = null;
        passcodeState.setValue(PasscodeState.PASSCODE_SAVED);
    }

    private void handleVerify(String passcode) {
        long remainingMillis = getRemainingLockoutMillis();
        if (remainingMillis > 0) {
            long remainingSeconds = (remainingMillis + 999L) / 1000L;
            errorMessage.setValue(getApplication().getString(
                    R.string.error_passcode_locked_with_time, remainingSeconds));
            passcodeState.setValue(PasscodeState.LOCKED_OUT);
            return;
        }

        authRepository.verifyPasscode(passcode, new AuthRepository.PasscodeCallback() {
            @Override
            public void onSuccess(String uid) {
                failedAttempts.postValue(0);
                lockoutUntilMillis = 0L;
                passcodeState.postValue(PasscodeState.PASSCODE_VERIFIED);
            }

            @Override
            public void onError(String message) {
                if (!"wrong_passcode".equals(message)) {
                    errorMessage.postValue(getApplication().getString(R.string.error_passcode_not_set));
                    passcodeState.postValue(PasscodeState.ERROR);
                    return;
                }

                int current = failedAttempts.getValue() != null ? failedAttempts.getValue() : 0;
                int next = current + 1;
                failedAttempts.postValue(next);

                if (next >= MAX_ATTEMPTS) {
                    lockoutUntilMillis = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
                    errorMessage.postValue(getApplication().getString(R.string.error_passcode_locked_with_time,
                            LOCKOUT_DURATION_MS / 1000L));
                    passcodeState.postValue(PasscodeState.LOCKED_OUT);
                    return;
                }

                int attemptsLeft = MAX_ATTEMPTS - next;
                errorMessage.postValue(getApplication().getString(
                        R.string.error_passcode_wrong_with_attempts, attemptsLeft));
                passcodeState.postValue(PasscodeState.PASSCODE_WRONG);
            }
        });
    }

    public String getPendingPasscode() {
        return pendingPasscode;
    }

    public void setPendingPasscode(String passcode) {
        pendingPasscode = passcode;
    }

    public void resetFailedAttempts() {
        failedAttempts.setValue(0);
        lockoutUntilMillis = 0L;
    }

    public long getRemainingLockoutMillis() {
        long remaining = lockoutUntilMillis - System.currentTimeMillis();
        return Math.max(remaining, 0L);
    }

    public boolean isPasscodeEnabled() {
        return authRepository.isPasscodeEnabled();
    }
}