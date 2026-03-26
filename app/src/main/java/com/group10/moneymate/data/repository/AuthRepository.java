package com.group10.moneymate.data.repository;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseUser;
import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.UserDao;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.remote.FirebaseAuthHelper;
import com.group10.moneymate.utils.PrefsManager;

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

    private final FirebaseAuthHelper firebaseAuthHelper;
    private final UserDao userDao;
    private final PrefsManager prefsManager;

    public AuthRepository(FirebaseAuthHelper firebaseAuthHelper, UserDao userDao,
                          PrefsManager prefsManager) {
        this.firebaseAuthHelper = firebaseAuthHelper;
        this.userDao = userDao;
        this.prefsManager = prefsManager;
    }

    public boolean isLoggedIn() {
        String localUid = prefsManager.getUid();
        if (prefsManager.isLoggedIn() && !TextUtils.isEmpty(localUid)) {
            return true;
        }
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

    public void signOut() {
        firebaseAuthHelper.signOut();
        prefsManager.setLoggedIn(false);
        prefsManager.saveUid(null);
    }

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
                            callback.onError("Login failed: empty result");
                            return;
                        }
                        FirebaseUser firebaseUser = result.getUser();
                        if (firebaseUser == null) {
                            callback.onError("Login failed: no user");
                            return;
                        }
                        handleAuthSuccess(firebaseUser);
                        callback.onSuccess(firebaseUser);
                    } else {
                        Exception e = task.getException();
                        callback.onError(e != null ? e.getMessage() : "Login failed");
                    }
                });
    }

    /**
     * Login ẩn danh.
     */
    public void loginAnonymously(@NonNull final AuthCallback callback) {
        firebaseAuthHelper.signInAnonymously()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        AuthResult result = task.getResult();
                        if (result == null) {
                            callback.onError("Anonymous login failed: empty result");
                            return;
                        }
                        FirebaseUser firebaseUser = result.getUser();
                        if (firebaseUser == null) {
                            callback.onError("Anonymous login failed: no user");
                            return;
                        }
                        handleAuthSuccess(firebaseUser);
                        callback.onSuccess(firebaseUser);
                    } else {
                        Exception e = task.getException();
                        callback.onError(e != null ? e.getMessage() : "Anonymous login failed");
                    }
                });
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

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void handleAuthSuccess(@NonNull FirebaseUser firebaseUser) {
        handleAuthSuccess(firebaseUser,
                firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "");
    }

    /**
     * Map FirebaseUser → UserEntity, lưu vào Room, cập nhật PrefsManager.
     */
    private void handleAuthSuccess(@NonNull FirebaseUser firebaseUser,
                                   @NonNull String displayName) {
        final long now = System.currentTimeMillis();
        final String localUserId = resolveLocalUserId(firebaseUser);
        final String resolvedName = !TextUtils.isEmpty(displayName) ? displayName
                : (firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "");

        AppDatabase.databaseWriteExecutor.execute(() -> {
            UserEntity existing = userDao.getUserByIdSync(localUserId);
            if (existing == null) {
                UserEntity entity = new UserEntity();
                entity.setId(localUserId);
                entity.setEmail(firebaseUser.getEmail());
                entity.setDisplayName(resolvedName);
                entity.setAvatarUrl(null);
                entity.setCurrency("VND");
                entity.setLanguage("vi");
                entity.setThemeMode("system");
                entity.setBalanceHidden(false);
                entity.setLastSync(0L);
                entity.setCreatedAt(now);
                userDao.insertUser(entity);
                return;
            }

            existing.setEmail(firebaseUser.getEmail());
            existing.setDisplayName(resolvedName);
            if (TextUtils.isEmpty(existing.getAvatarUrl())) {
                existing.setAvatarUrl(null);
            }
            userDao.updateUser(existing);
        });

        // Lưu uid local ổn định để query Room nhất quán giữa các phiên.
        prefsManager.saveUid(localUserId);
        prefsManager.setLoggedIn(true);
    }

    private String resolveLocalUserId(@NonNull FirebaseUser firebaseUser) {
        if (firebaseUser.isAnonymous()) {
            return prefsManager.getOrCreateGuestUid();
        }
        return firebaseUser.getUid();
    }
}