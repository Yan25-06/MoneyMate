package com.group10.moneymate.ui.security;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.PasscodeHasher;
import com.group10.moneymate.utils.PrefsManager;

/**
 * ViewModel for PasscodeFragment.
 * Hỗ trợ 3 mode:
 *   MODE_CREATE  (0) — tạo passcode lần đầu sau đăng ký
 *   MODE_CONFIRM (1) — xác nhận lại passcode (so sánh với lần nhập trước)
 *   MODE_VERIFY  (2) — nhập passcode để đăng nhập (online & offline)
 */
public class SecurityViewModel extends AndroidViewModel {

    // ── Mode constants ────────────────────────────────────────────────────────
    public static final int MODE_CREATE  = 0;
    public static final int MODE_CONFIRM = 1;
    public static final int MODE_VERIFY  = 2;

    // ── Max attempts trước khi lock ───────────────────────────────────────────
    private static final int MAX_ATTEMPTS = 5;

    private final AuthRepository authRepository;
    private final PrefsManager prefsManager;

    // ── State ─────────────────────────────────────────────────────────────────
    private final MutableLiveData<PasscodeState> passcodeState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> failedAttempts = new MutableLiveData<>(0);

    /** Passcode nhập ở mode CREATE, dùng để so sánh khi sang mode CONFIRM */
    private String pendingPasscode = null;

    public enum PasscodeState {
        IDLE,
        /** Passcode hợp lệ và đã lưu (CREATE + CONFIRM xong) */
        PASSCODE_SAVED,
        /** Passcode đúng (VERIFY thành công) */
        PASSCODE_VERIFIED,
        /** Sai passcode */
        PASSCODE_WRONG,
        /** Sai quá MAX_ATTEMPTS lần */
        LOCKED_OUT,
        ERROR
    }

    public SecurityViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        authRepository = container.authRepository;
        prefsManager   = container.prefsManager;
        passcodeState.setValue(PasscodeState.IDLE);
    }

    // ─── Expose ───────────────────────────────────────────────────────────────

    public LiveData<PasscodeState> getPasscodeState() { return passcodeState; }

    public LiveData<String> getErrorMessage() { return errorMessage; }

    public LiveData<Integer> getFailedAttempts() { return failedAttempts; }

    public void resetState() {
        passcodeState.setValue(PasscodeState.IDLE);
        errorMessage.setValue(null);
    }

    // ─── Passcode logic ───────────────────────────────────────────────────────

    /**
     * Xử lý khi user hoàn thành nhập 6 số.
     *
     * @param passcode  6 chữ số người dùng vừa nhập
     * @param mode      MODE_CREATE / MODE_CONFIRM / MODE_VERIFY
     */
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
                passcodeState.setValue(PasscodeState.ERROR);
        }
    }

    /**
     * Lưu passcode tạm thời ở MODE_CREATE, sau đó UI chuyển sang MODE_CONFIRM.
     */
    private void handleCreate(String passcode) {
        pendingPasscode = passcode;
        // state vẫn IDLE — UI sẽ navigate sang CONFIRM mode
        // Không set state gì ở đây; Fragment xử lý navigate khi 6 dots đầy
    }

    /**
     * So sánh với pendingPasscode. Nếu khớp → lưu, emit PASSCODE_SAVED.
     */
    private void handleConfirm(String passcode) {
        if (pendingPasscode == null) {
            passcodeState.setValue(PasscodeState.ERROR);
            return;
        }
        if (pendingPasscode.equals(passcode)) {
            String uid = prefsManager.getUid();
            if (uid != null) {
                authRepository.savePasscode(uid, passcode);
            }
            pendingPasscode = null;
            passcodeState.setValue(PasscodeState.PASSCODE_SAVED);
        } else {
            pendingPasscode = null;
            errorMessage.setValue(null); // Fragment lấy từ string resources
            passcodeState.setValue(PasscodeState.PASSCODE_WRONG);
        }
    }

    /**
     * Xác thực passcode — offline-capable.
     * Tăng failedAttempts khi sai; LOCKED_OUT khi vượt MAX_ATTEMPTS.
     */
    private void handleVerify(String passcode) {
        authRepository.verifyPasscode(passcode, new AuthRepository.PasscodeCallback() {
            @Override
            public void onSuccess(String uid) {
                failedAttempts.postValue(0);
                passcodeState.postValue(PasscodeState.PASSCODE_VERIFIED);
            }

            @Override
            public void onError(String message) {
                if ("wrong_passcode".equals(message)) {
                    int current = failedAttempts.getValue() != null
                            ? failedAttempts.getValue() : 0;
                    int next = current + 1;
                    failedAttempts.postValue(next);
                    if (next >= MAX_ATTEMPTS) {
                        passcodeState.postValue(PasscodeState.LOCKED_OUT);
                    } else {
                        passcodeState.postValue(PasscodeState.PASSCODE_WRONG);
                    }
                } else {
                    passcodeState.postValue(PasscodeState.ERROR);
                }
            }
        });
    }

    /**
     * Lấy passcode tạm (dùng khi navigate từ CREATE → CONFIRM để truyền vào Fragment mới).
     */
    public String getPendingPasscode() {
        return pendingPasscode;
    }

    /**
     * Set pendingPasscode từ bên ngoài (khi Fragment CONFIRM cần khởi tạo lại).
     */
    public void setPendingPasscode(String passcode) {
        pendingPasscode = passcode;
    }

    /**
     * Reset failed attempts (gọi sau khi user login thành công bằng cách khác).
     */
    public void resetFailedAttempts() {
        failedAttempts.setValue(0);
    }

    public boolean isPasscodeEnabled() {
        return authRepository.isPasscodeEnabled();
    }
}