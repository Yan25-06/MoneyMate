package com.group10.moneymate.data.repository;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
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
        return firebaseAuthHelper.isLoggedIn();
    }

    public String getCurrentUserId() {
        return firebaseAuthHelper.getCurrentUserId();
    }

    public void signOut() {
        firebaseAuthHelper.signOut();
        prefsManager.clearAll();
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
        final String resolvedName = !TextUtils.isEmpty(displayName) ? displayName
                : (firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "");

        final UserEntity entity = new UserEntity();
        entity.setId(firebaseUser.getUid());
        entity.setEmail(firebaseUser.getEmail());
        entity.setDisplayName(resolvedName);
        entity.setAvatarUrl(null);
        entity.setCurrency("VND");
        entity.setLanguage("vi");
        entity.setThemeMode("system");
        entity.setBalanceHidden(false);
        entity.setLastSync(0L);
        entity.setCreatedAt(now);

        AppDatabase.databaseWriteExecutor.execute(() -> userDao.insertUser(entity));

        // Lưu uid và trạng thái login vào SharedPreferences
        prefsManager.saveUid(firebaseUser.getUid());
        prefsManager.setLoggedIn(true);
    }
}