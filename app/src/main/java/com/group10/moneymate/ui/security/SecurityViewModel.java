package com.group10.moneymate.ui.security;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.PrefsManager;

/**
 * ViewModel quản lý trạng thái màn hình Passcode.
 *
 * Hỗ trợ 4 mode:
 *   MODE_CREATE  (0) — người dùng tạo PIN mới lần đầu
 *   MODE_CONFIRM (1) — xác nhận lại PIN vừa tạo
 *   MODE_VERIFY  (2) — nhập PIN để mở khóa app
 *   MODE_CHANGE  (3) — đổi PIN (verify cũ → create mới → confirm mới)
 */
public class SecurityViewModel extends AndroidViewModel {

    // ─── Mode constants ───────────────────────────────────────────────────────

    public static final int MODE_CREATE  = 0;
    public static final int MODE_CONFIRM = 1;
    public static final int MODE_VERIFY  = 2;
    public static final int MODE_CHANGE  = 3;

    public static final int PASSCODE_LENGTH   = 6;
    public static final int MAX_ATTEMPTS      = 5;
    public static final long LOCKOUT_DURATION = 30_000L; // 30 giây

    // ─── UI State enum ────────────────────────────────────────────────────────

    public enum UiState {
        IDLE,       // đang chờ nhập
        SUCCESS,    // passcode đúng / đã lưu thành công
        ERROR,      // sai passcode → trigger shake
        LOCKED      // đã vượt MAX_ATTEMPTS → bị khóa
    }

    // ─── LiveData ─────────────────────────────────────────────────────────────

    private final MutableLiveData<Integer> filledDots   = new MutableLiveData<>(0);
    private final MutableLiveData<UiState> uiState      = new MutableLiveData<>(UiState.IDLE);
    private final MutableLiveData<String>  errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentMode  = new MutableLiveData<>(MODE_CREATE);
    private final MutableLiveData<Long>    lockoutUntil = new MutableLiveData<>(0L);

    public LiveData<Integer> getFilledDots()   { return filledDots; }
    public LiveData<UiState> getUiState()      { return uiState; }
    public LiveData<String>  getErrorMessage() { return errorMessage; }
    public LiveData<Integer> getCurrentMode()  { return currentMode; }
    public LiveData<Long>    getLockoutUntil() { return lockoutUntil; }

    // ─── Internal state ───────────────────────────────────────────────────────

    private final StringBuilder buffer      = new StringBuilder();
    private       String        pendingHash = null; // PIN tạm khi ở CREATE, chờ CONFIRM
    private       boolean       isChangingPasscode = false; // true khi đang trong flow CHANGE

    private final AuthRepository authRepository;
    private final PrefsManager   prefsManager;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public SecurityViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        authRepository = app.getAppContainer().authRepository;
        prefsManager   = app.getAppContainer().prefsManager;
    }

    // ─── Init ─────────────────────────────────────────────────────────────────

    /** Gọi từ PasscodeFragment.onViewCreated() để khởi tạo mode. */
    public void init(int mode) {
        currentMode.setValue(mode);
        buffer.setLength(0);
        filledDots.setValue(0);
        uiState.setValue(UiState.IDLE);
        errorMessage.setValue(null);
        isChangingPasscode = (mode == MODE_CHANGE);

        // Kiểm tra lockout ngay khi vào VERIFY hoặc CHANGE
        if (mode == MODE_VERIFY || mode == MODE_CHANGE) {
            checkLockout();
        }
    }

    // ─── Input handlers ───────────────────────────────────────────────────────

    /** Gọi khi người dùng nhấn một chữ số trên numpad. */
    public void onDigitEntered(int digit) {
        if (uiState.getValue() == UiState.LOCKED) return;
        if (buffer.length() >= PASSCODE_LENGTH) return;

        buffer.append(digit);
        filledDots.setValue(buffer.length());

        if (buffer.length() == PASSCODE_LENGTH) {
            processInput();
        }
    }

    /** Gọi khi người dùng nhấn Backspace. */
    public void onBackspace() {
        if (uiState.getValue() == UiState.LOCKED) return;
        if (buffer.length() == 0) return;

        buffer.deleteCharAt(buffer.length() - 1);
        filledDots.setValue(buffer.length());

        // Reset error khi user bắt đầu sửa
        if (uiState.getValue() == UiState.ERROR) {
            uiState.setValue(UiState.IDLE);
            errorMessage.setValue(null);
        }
    }

    /** Reset về trạng thái nhập rỗng (gọi sau khi shake animation kết thúc). */
    public void resetAfterError() {
        buffer.setLength(0);
        filledDots.setValue(0);
        uiState.setValue(UiState.IDLE);
    }

    // ─── Processing ───────────────────────────────────────────────────────────

    private void processInput() {
        String pin = buffer.toString();
        Integer mode = currentMode.getValue();
        if (mode == null) return;

        switch (mode) {
            case MODE_CREATE:
                handleCreate(pin);
                break;
            case MODE_CONFIRM:
                handleConfirm(pin);
                break;
            case MODE_VERIFY:
                handleVerify(pin);
                break;
            case MODE_CHANGE:
                // CHANGE bắt đầu bằng VERIFY PIN cũ.
                // Sau khi verify thành công, handleVerify() sẽ tự chuyển sang MODE_CREATE.
                handleVerify(pin);
                break;
        }
    }

    private void handleCreate(String pin) {
        // Lưu tạm, chờ CONFIRM
        pendingHash = pin;
        buffer.setLength(0);
        filledDots.setValue(0);
        currentMode.setValue(MODE_CONFIRM);
    }

    private void handleConfirm(String pin) {
        if (pendingHash != null && pendingHash.equals(pin)) {
            // Khớp → lưu passcode
            String uid = prefsManager.getUid();
            if (uid != null) {
                authRepository.savePasscode(uid, pin);
                prefsManager.setFailedAttempts(0);
                prefsManager.setLockoutUntil(0L);
            }
            pendingHash = null;
            uiState.setValue(UiState.SUCCESS);
        } else {
            // Không khớp → báo lỗi, quay lại CREATE
            pendingHash = null;
            errorMessage.setValue("mismatch");
            uiState.setValue(UiState.ERROR);
        }
    }

    private void handleVerify(String pin) {
        authRepository.verifyPasscode(pin, new AuthRepository.PasscodeCallback() {
            @Override
            public void onSuccess(String uid) {
                prefsManager.setFailedAttempts(0);
                prefsManager.setLockoutUntil(0L);

                if (isChangingPasscode) {
                    // Đã xác nhận PIN cũ thành công → chuyển sang nhập PIN mới
                    isChangingPasscode = false;
                    buffer.setLength(0);
                    filledDots.postValue(0);
                    errorMessage.postValue(null);
                    currentMode.postValue(MODE_CREATE);
                    // Giữ UiState = IDLE (không post SUCCESS)
                } else {
                    uiState.postValue(UiState.SUCCESS);
                }
            }

            @Override
            public void onError(String message) {
                int attempts = prefsManager.getFailedAttempts() + 1;
                prefsManager.setFailedAttempts(attempts);

                if (attempts >= MAX_ATTEMPTS) {
                    long until = System.currentTimeMillis() + LOCKOUT_DURATION;
                    prefsManager.setLockoutUntil(until);
                    lockoutUntil.postValue(until);
                    uiState.postValue(UiState.LOCKED);
                } else {
                    errorMessage.postValue("wrong:" + (MAX_ATTEMPTS - attempts));
                    uiState.postValue(UiState.ERROR);
                }
            }
        });
    }

    // ─── Lockout helpers ──────────────────────────────────────────────────────

    private void checkLockout() {
        long until = prefsManager.getLockoutUntil();
        if (until > 0 && System.currentTimeMillis() < until) {
            lockoutUntil.setValue(until);
            uiState.setValue(UiState.LOCKED);
        } else if (until > 0) {
            // Lockout đã hết hạn → reset
            prefsManager.setLockoutUntil(0L);
            prefsManager.setFailedAttempts(0);
        }
    }

    public boolean isLocked() {
        long until = prefsManager.getLockoutUntil();
        return until > 0 && System.currentTimeMillis() < until;
    }

    public long getRemainingLockoutMs() {
        long until = prefsManager.getLockoutUntil();
        if (until <= 0) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }
}
