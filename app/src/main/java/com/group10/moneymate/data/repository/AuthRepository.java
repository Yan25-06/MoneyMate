package com.group10.moneymate.data.repository;

import android.text.TextUtils;
import androidx.annotation.NonNull;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.UserDao;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.remote.FirebaseAuthHelper;
import com.group10.moneymate.utils.PasscodeHasher;
import com.group10.moneymate.utils.PrefsManager;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import java.io.IOException;
import java.net.SocketTimeoutException;

public class AuthRepository {

    public interface AuthCallback {
        void onSuccess(FirebaseUser user);
        void onError(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

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

    // Auth state

    public boolean isLoggedIn() { return firebaseAuthHelper.isLoggedIn(); }

    public String getCurrentUserId() {
        String localUid = prefsManager.getUid();
        if (!TextUtils.isEmpty(localUid)) return localUid;
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
    }

    // Firebase Auth

    public void register(String email, String password, String displayName,
                         @NonNull final AuthCallback callback) {
        final String trimmedDisplayName = displayName != null ? displayName.trim() : "";
        firebaseAuthHelper.signUpWithEmail(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        AuthResult result = task.getResult();
                        if (result == null) { callback.onError("Registration failed: empty result"); return; }
                        FirebaseUser firebaseUser = result.getUser();
                        if (firebaseUser == null) { callback.onError("Registration failed: no user"); return; }
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

    public void login(String email, String password, @NonNull final AuthCallback callback) {
        firebaseAuthHelper.signInWithEmail(email, password)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        AuthResult result = task.getResult();
                        if (result == null) { callback.onError("auth_login_failed"); return; }
                        FirebaseUser firebaseUser = result.getUser();
                        if (firebaseUser == null) { callback.onError("auth_login_failed"); return; }
                        handleAuthSuccess(firebaseUser);
                        callback.onSuccess(firebaseUser);
                        return;
                    }
                    callback.onError(mapLoginErrorKey(task.getException()));
                });
    }

    public void loginWithGoogle(String idToken, @NonNull final AuthCallback callback) {
        AuthCredential googleCredential = GoogleAuthProvider.getCredential(idToken, null);
        firebaseAuthHelper.signInWithCredential(googleCredential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        AuthResult result = task.getResult();
                        if (result == null) { callback.onError("auth_login_failed"); return; }
                        FirebaseUser firebaseUser = result.getUser();
                        if (firebaseUser == null) { callback.onError("auth_login_failed"); return; }
                        handleAuthSuccess(firebaseUser);
                        callback.onSuccess(firebaseUser);
                        return;
                    }
                    Exception e = task.getException();
                    if (e instanceof FirebaseAuthUserCollisionException) {
                        // Email đã tồn tại với provider khác
                        // Trả về error key đặc biệt để UI hỏi password rồi link
                        String email = ((FirebaseAuthUserCollisionException) e).getEmail();
                        callback.onError("auth_google_link_needed:" + (email != null ? email : ""));
                        return;
                    }
                    callback.onError(mapLoginErrorKey(e));
                });
    }

    /**
     * Đăng nhập email/password rồi link Google credential vào.
     * Gọi khi loginWithGoogle() trả về "auth_google_link_needed".
     * Sau khi link xong, account có CẢ HAI provider:
     *   - Có thể đăng nhập bằng email/password
     *   - Có thể đăng nhập bằng Google
     *
     * @param email    email của account
     * @param password password để xác thực lần đầu
     * @param idToken  Google ID Token từ Google Sign-In
     * @param callback kết quả
     */
    public void linkGoogleToEmailAccount(String email, String password, String idToken,
                                         @NonNull final AuthCallback callback) {
        // Bước 1: đăng nhập bằng email/password để lấy FirebaseUser
        firebaseAuthHelper.signInWithEmail(email, password)
                .addOnCompleteListener(loginTask -> {
                    if (!loginTask.isSuccessful()) {
                        callback.onError(mapLoginErrorKey(loginTask.getException()));
                        return;
                    }
                    FirebaseUser user = loginTask.getResult() != null
                            ? loginTask.getResult().getUser() : null;
                    if (user == null) { callback.onError("auth_login_failed"); return; }

                    // Bước 2: link Google credential vào account này
                    AuthCredential googleCredential = GoogleAuthProvider.getCredential(idToken, null);
                    user.linkWithCredential(googleCredential)
                            .addOnCompleteListener(linkTask -> {
                                if (linkTask.isSuccessful()) {
                                    FirebaseUser linkedUser = linkTask.getResult() != null
                                            ? linkTask.getResult().getUser() : user;
                                    if (linkedUser == null) { callback.onError("auth_login_failed"); return; }
                                    handleAuthSuccess(linkedUser);
                                    callback.onSuccess(linkedUser);
                                } else {
                                    // Google đã được link rồi → vẫn cho đăng nhập
                                    handleAuthSuccess(user);
                                    callback.onSuccess(user);
                                }
                            });
                });
    }

    private String mapLoginErrorKey(Exception exception) {
        if (exception == null) return "auth_login_failed";
        if (exception instanceof FirebaseAuthInvalidUserException) return "auth_user_not_found";
        if (exception instanceof FirebaseAuthInvalidCredentialsException) return "auth_wrong_password";
        if (exception instanceof FirebaseAuthUserCollisionException) return "auth_account_exists_with_different_credential";
        if (exception instanceof FirebaseNetworkException
                || exception instanceof SocketTimeoutException
                || exception instanceof IOException) return "auth_network_timeout";
        return "auth_login_failed";
    }

    public void sendPasswordResetEmail(String email, @NonNull final SimpleCallback callback) {
        firebaseAuthHelper.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        callback.onSuccess();
                    } else {
                        Exception e = task.getException();
                        callback.onError(e != null ? e.getMessage() : "Failed to send password reset email");
                    }
                });
    }

    // Passcode

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
        String storedUid  = prefsManager.getPasscodeUid();
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
        if (TextUtils.isEmpty(currentUid)) { callback.onError("no_passcode_set"); return; }
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

    public boolean isPasscodeEnabled() { return prefsManager.isPasscodeEnabled(); }

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

    // Private helpers

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