package com.group10.moneymate.data.repository;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser;
import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.UserDao;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.remote.FirebaseAuthHelper;
import com.group10.moneymate.utils.PasscodeHasher;
import com.group10.moneymate.utils.PrefsManager;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;

import java.io.IOException;
import java.net.SocketTimeoutException;

/**
 * Repository handling authentication (Firebase Auth) and local user persistence.
 */
public class AuthRepository {

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);

        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();

        void onError(String message);
    }

    /**
     * Callback cho passcode login (offline-capable, không cần FirebaseUser).
     */
    public interface PasscodeCallback {
        void onSuccess(String uid);

        void onError(String message);
    }

    private final FirebaseAuthHelper firebaseAuthHelper;
    private final UserDao userDao;
    private final PrefsManager prefsManager;

    public AuthRepository(FirebaseAuthHelper firebaseAuthHelper, UserDao userDao,
                          PrefsManager prefsManager) {
        this.firebaseAuthHelper = firebaseAuthHelper;
        this.userDao = userDao;
        this.prefsManager = prefsManager;
    }

    // ─── Auth state ───────────────────────────────────────────────────────────

    public boolean isLoggedIn() {
        return firebaseAuthHelper.isLoggedIn();
    }

    public String getCurrentUserId() {
        String localUid = prefsManager.getUid();
        if (!TextUtils.isEmpty(localUid)) {
            return localUid;
        }
        String firebaseUid = firebaseAuthHelper.getCurrentUserId();
        return firebaseUid != null ? firebaseUid : "";
    }

    public void ensureLocalUserRecord() {
        final String localUserId = getCurrentUserId();
        if (TextUtils.isEmpty(localUserId)) return;

        final FirebaseUser firebaseUser = firebaseAuthHelper.getCurrentUser();
        final long now = System.currentTimeMillis();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            UserEntity existing = userDao.getUserByIdSync(localUserId);
            if (existing != null) return;

            UserEntity entity = new UserEntity();
            entity.setId(localUserId);
            entity.setEmail(firebaseUser != null ? firebaseUser.getEmail() : null);
            entity.setDisplayName(firebaseUser != null && firebaseUser.getDisplayName() != null
                    ? firebaseUser.getDisplayName() : "");
            entity.setAvatarUrl(null);
            entity.setCurrency("VND");
            entity.setLanguage("vi");
            entity.setThemeMode("system");
            entity.setBalanceHidden(false);
            entity.setLastSync(0L);
            entity.setCreatedAt(now);
            userDao.insertUser(entity);
        });
    }

    public void signOut() {
        firebaseAuthHelper.signOut();
        prefsManager.setLoggedIn(false);
        prefsManager.saveUid(null);
        // Không xóa passcode khi sign out — passcode vẫn dùng được offline
    }

    // ─── Firebase Auth ────────────────────────────────────────────────────────

    /**
     * Register với email/password, cập nhật display name, lưu UserEntity vào Room.
     */
    public void register(String email, String password, String displayName,
                         @NonNull final AuthCallback callback) {
        final String trimmedDisplayName = displayName != null ? displayName.trim() : "";
        firebaseAuthHelper.signUpWithEmail(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        AuthResult result = task.getResult();
                        if (result == null) {
                            callback.onError("Registration failed: empty result");
                            return;
                        }
                        FirebaseUser firebaseUser = result.getUser();
                        if (firebaseUser == null) {
                            callback.onError("Registration failed: no user");
                            return;
                        }

                        if (TextUtils.isEmpty(trimmedDisplayName)) {
                            handleAuthSuccess(firebaseUser, trimmedDisplayName);
                            callback.onSuccess(firebaseUser);
                            return;
                        }

                        firebaseAuthHelper.updateDisplayName(firebaseUser, trimmedDisplayName)
                                .addOnCompleteListener(updateTask -> {
                                    handleAuthSuccess(firebaseUser, trimmedDisplayName);
                                    callback.onSuccess(firebaseUser);
                                });
                    } else {
                        Exception e = task.getException();
                        callback.onError(e != null ? e.getMessage() : "Registration failed");
                    }
                });
    }

    /**
     * Login với email/password, đảm bảo UserEntity tồn tại trong Room.
     */
    public void login(String email, String password, @NonNull final AuthCallback callback) {
        firebaseAuthHelper.signInWithEmail(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        AuthResult result = task.getResult();
                        if (result == null) {
                            callback.onError("auth_login_failed");
                            return;
                        }
                        FirebaseUser firebaseUser = result.getUser();
                        if (firebaseUser == null) {
                            callback.onError("auth_login_failed");
                            return;
                        }
                        handleAuthSuccess(firebaseUser);
                        callback.onSuccess(firebaseUser);
                        return;
                    }

                    Exception e = task.getException();
                    callback.onError(mapLoginErrorKey(e));
                });
    }

    /**
     * Đăng nhập bằng Google ID Token.
     * Tự động tạo UserEntity trong Room nếu user mới.
     */
    public void loginWithGoogle(String idToken, @NonNull final AuthCallback callback) {
        firebaseAuthHelper.signInWithGoogle(idToken)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        AuthResult result = task.getResult();
                        if (result == null) {
                            callback.onError("auth_login_failed");
                            return;
                        }
                        FirebaseUser firebaseUser = result.getUser();
                        if (firebaseUser == null) {
                            callback.onError("auth_login_failed");
                            return;
                        }
                        handleAuthSuccess(firebaseUser);
                        callback.onSuccess(firebaseUser);
                    } else {
                        Exception e = task.getException();
                        callback.onError(mapLoginErrorKey(e));
                    }
                });
    }

    private String mapLoginErrorKey(Exception exception) {
        if (exception == null) {
            return "auth_login_failed";
        }
        if (exception instanceof FirebaseAuthInvalidUserException) {
            return "auth_user_not_found";
        }
        if (exception instanceof FirebaseAuthInvalidCredentialsException) {
            return "auth_wrong_password";
        }
        if (exception instanceof FirebaseNetworkException
                || exception instanceof SocketTimeoutException
                || exception instanceof IOException) {
            return "auth_network_timeout";
        }
        return "auth_login_failed";
    }

    /**
     * Gửi email đặt lại mật khẩu.
     */
    public void sendPasswordResetEmail(String email, @NonNull final SimpleCallback callback) {
        firebaseAuthHelper.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        Exception e = task.getException();
                        callback.onError(e != null ? e.getMessage()
                                : "Failed to send password reset email");
                    }
                });
    }

    // ─── Passcode ─────────────────────────────────────────────────────────────

    public void savePasscode(String uid, String passcode) {
        String hash = PasscodeHasher.hash(passcode);
        prefsManager.savePasscodeHash(uid, hash);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            UserEntity user = userDao.getUserByIdSync(uid);
            if (user != null) {
                user.setHashedPasscode(hash);
                user.setUpdatedAt(System.currentTimeMillis());
                userDao.updateUser(user);
            }
        });
    }

    public void verifyPasscode(String passcode, @NonNull final PasscodeCallback callback) {
        String storedHash = prefsManager.getPasscodeHash();
        String storedUid = prefsManager.getPasscodeUid();

        if (!TextUtils.isEmpty(storedHash) && !TextUtils.isEmpty(storedUid)) {
            if (PasscodeHasher.verify(passcode, storedHash)) {
                prefsManager.saveUid(storedUid);
                prefsManager.setLoggedIn(true);
                callback.onSuccess(storedUid);
            } else {
                callback.onError("wrong_passcode");
            }
            return;
        }

        String currentUid = getCurrentUserId();
        if (TextUtils.isEmpty(currentUid)) {
            callback.onError("no_passcode_set");
            return;
        }

        AppDatabase.databaseWriteExecutor.execute(() -> {
            UserEntity user = userDao.getUserByIdSync(currentUid);
            if (user == null || TextUtils.isEmpty(user.getHashedPasscode())) {
                callback.onError("no_passcode_set");
                return;
            }
            if (PasscodeHasher.verify(passcode, user.getHashedPasscode())) {
                prefsManager.savePasscodeHash(currentUid, user.getHashedPasscode());
                prefsManager.saveUid(currentUid);
                prefsManager.setLoggedIn(true);
                callback.onSuccess(currentUid);
            } else {
                callback.onError("wrong_passcode");
            }
        });
    }

    public boolean isPasscodeEnabled() {
        return prefsManager.isPasscodeEnabled();
    }

    public void clearPasscode(String uid) {
        prefsManager.clearPasscode();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            UserEntity user = userDao.getUserByIdSync(uid);
            if (user != null) {
                user.setHashedPasscode(null);
                user.setUpdatedAt(System.currentTimeMillis());
                userDao.updateUser(user);
            }
        });
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void handleAuthSuccess(@NonNull FirebaseUser firebaseUser) {
        handleAuthSuccess(firebaseUser,
                firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "");
    }

    private void handleAuthSuccess(@NonNull FirebaseUser firebaseUser,
                                   @NonNull String displayName) {
        final long now = System.currentTimeMillis();
        final String localUserId = firebaseUser.getUid();
        final String resolvedName = !TextUtils.isEmpty(displayName) ? displayName
                : (firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "");

        // Lấy avatar URL từ Google nếu có
        final String photoUrl = firebaseUser.getPhotoUrl() != null
                ? firebaseUser.getPhotoUrl().toString() : null;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            UserEntity existing = userDao.getUserByIdSync(localUserId);
            if (existing == null) {
                UserEntity entity = new UserEntity();
                entity.setId(localUserId);
                entity.setEmail(firebaseUser.getEmail());
                entity.setDisplayName(resolvedName);
                entity.setAvatarUrl(photoUrl);
                entity.setCurrency("VND");
                entity.setLanguage("vi");
                entity.setThemeMode("system");
                entity.setBalanceHidden(false);
                entity.setLastSync(0L);
                entity.setCreatedAt(now);
                userDao.insertUser(entity);
            } else {
                existing.setEmail(firebaseUser.getEmail());
                existing.setDisplayName(resolvedName);
                // Cập nhật avatar từ Google nếu chưa có avatar custom
                if (TextUtils.isEmpty(existing.getAvatarUrl()) && !TextUtils.isEmpty(photoUrl)) {
                    existing.setAvatarUrl(photoUrl);
                }
                userDao.updateUser(existing);
            }
        });

        prefsManager.saveUid(localUserId);
        prefsManager.setLoggedIn(true);
    }
}
