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

    public AuthRepository(FirebaseAuthHelper firebaseAuthHelper, UserDao userDao, PrefsManager prefsManager) {
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
        // Clear all user-related preferences (theme, language, etc.)
        prefsManager.clearAll();
    }

    /**
     * Register with email/password, update Firebase display name, then persist a UserEntity locally.
     */
    public void register(String email, String password, String displayName, @NonNull final AuthCallback callback) {
        final String trimmedDisplayName = displayName != null ? displayName.trim() : "";
        Task<AuthResult> task = firebaseAuthHelper.signUpWithEmail(email, password);
        task.addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
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
                            .addOnCompleteListener(new OnCompleteListener<Void>() {
                                @Override
                                public void onComplete(@NonNull Task<Void> updateTask) {
                                    handleAuthSuccess(firebaseUser, trimmedDisplayName);
                                    callback.onSuccess(firebaseUser);
                                }
                            });
                } else {
                    Exception e = task.getException();
                    callback.onError(e != null ? e.getMessage() : "Registration failed");
                }
            }
        });
    }

    /**
     * Login with email/password, then ensure UserEntity exists locally.
     */
    public void login(String email, String password, @NonNull final AuthCallback callback) {
        Task<AuthResult> task = firebaseAuthHelper.signInWithEmail(email, password);
        task.addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
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
            }
        });
    }

    /**
     * Login anonymously, then ensure UserEntity exists locally.
     */
    public void loginAnonymously(@NonNull final AuthCallback callback) {
        Task<AuthResult> task = firebaseAuthHelper.signInAnonymously();
        task.addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
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
            }
        });
    }

    /**
     * Send password reset email via Firebase Auth.
     */
    public void sendPasswordResetEmail(String email, @NonNull final SimpleCallback callback) {
        Task<Void> task = firebaseAuthHelper.sendPasswordResetEmail(email);
        task.addOnCompleteListener(new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                if (task.isSuccessful()) {
                    callback.onSuccess();
                } else {
                    Exception e = task.getException();
                    callback.onError(e != null ? e.getMessage() : "Failed to send password reset email");
                }
            }
        });
    }

    /**
     * Internal helper: map FirebaseUser → UserEntity and save to Room.
     */
    private void handleAuthSuccess(@NonNull final FirebaseUser firebaseUser) {
        handleAuthSuccess(firebaseUser, firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "");
    }

    /**
     * Internal helper: map FirebaseUser → UserEntity and save to Room.
     */
    private void handleAuthSuccess(@NonNull final FirebaseUser firebaseUser, @NonNull String displayName) {
        final long now = System.currentTimeMillis();
        final String resolvedDisplayName = !TextUtils.isEmpty(displayName)
                ? displayName
                : (firebaseUser.getDisplayName() != null ? firebaseUser.getDisplayName() : "");

        final UserEntity entity = new UserEntity();
        entity.setId(firebaseUser.getUid());
        entity.setEmail(firebaseUser.getEmail());
        entity.setDisplayName(resolvedDisplayName);
        entity.setAvatarUrl(null);
        entity.setCurrency("VND");
        entity.setLanguage("vi");
        entity.setThemeMode("system");
        entity.setBalanceHidden(false);
        entity.setLastSync(0L);
        entity.setCreatedAt(now);

        AppDatabase.databaseWriteExecutor.execute(new Runnable() {
            @Override
            public void run() {
                userDao.insertUser(entity);
            }
        });
    }
}
