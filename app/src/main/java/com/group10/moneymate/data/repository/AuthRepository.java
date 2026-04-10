package com.group10.moneymate.data.repository;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.UserDao;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.remote.SupabaseAuthHelper;
import com.group10.moneymate.utils.PasscodeHasher;
import com.group10.moneymate.utils.PrefsManager;

/**
 * AuthRepository dùng Supabase thay Firebase.
 *
 * THAY ĐỔI SO VỚI PHIÊN BẢN FIREBASE:
 *  - FirebaseUser → SupabaseAuthHelper.SupabaseUser trong các callback nội bộ
 *  - AuthCallback.onSuccess vẫn nhận kiểu USER_TYPE nhưng giờ là SupabaseUser
 *    (AuthViewModel không dùng object này, chỉ check onSuccess() được gọi)
 *  - linkGoogleToEmailAccount() bỏ đi vì Supabase tự động link providers cùng email
 *  - loginWithGoogle() vẫn giữ, logic đơn giản hơn (không có collision case)
 *  - mapLoginErrorKey() dùng string key giống hệt cũ → AuthViewModel không thay đổi
 */
public class AuthRepository {

    // ─── Callbacks (interface giữ nguyên 100% so với Firebase version) ────────

    /**
     * Callback auth – AuthViewModel KHÔNG dùng tham số user,
     * chỉ cần biết onSuccess/onError. Interface giữ nguyên để không phải sửa ViewModel.
     */
    public interface AuthCallback {
        void onSuccess(SupabaseAuthHelper.SupabaseUser user);
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

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final SupabaseAuthHelper supabaseAuthHelper;
    private final UserDao userDao;
    private final PrefsManager prefsManager;

    public AuthRepository(SupabaseAuthHelper supabaseAuthHelper, UserDao userDao,
                          PrefsManager prefsManager) {
        this.supabaseAuthHelper = supabaseAuthHelper;
        this.userDao            = userDao;
        this.prefsManager       = prefsManager;
    }

    // ─── Auth state ───────────────────────────────────────────────────────────

    public boolean isLoggedIn() {
        // Supabase không giữ session in-memory sau khi restart app.
        // Ta dùng PrefsManager làm source of truth (giống behavior cũ).
        return prefsManager.isLoggedIn();
    }

    public String getCurrentUserId() {
        String localUid = prefsManager.getUid();
        if (!TextUtils.isEmpty(localUid)) return localUid;
        String supabaseUid = supabaseAuthHelper.getCurrentUserId();
        return supabaseUid != null ? supabaseUid : "";
    }

    /**
     * Tạo UserEntity local nếu chưa có (gọi khi app khởi động sau khi đã login).
     * Không cần network – đọc từ PrefsManager.
     */
    public void ensureLocalUserRecord() {
        final String localUserId = getCurrentUserId();
        if (TextUtils.isEmpty(localUserId)) return;

        // Lấy thông tin từ in-memory session (có nếu vừa login), hoặc fallback null
        final SupabaseAuthHelper.SupabaseUser sessionUser = supabaseAuthHelper.getCurrentUser();
        final long now = System.currentTimeMillis();

        AppDatabase.databaseWriteExecutor.execute(() -> {
            UserEntity existing = userDao.getUserByIdSync(localUserId);
            if (existing != null) return;

            UserEntity entity = new UserEntity();
            entity.setId(localUserId);
            entity.setEmail(sessionUser != null ? sessionUser.email : null);
            entity.setDisplayName(sessionUser != null && sessionUser.displayName != null
                    ? sessionUser.displayName : "");
            entity.setAvatarUrl(sessionUser != null ? sessionUser.avatarUrl : null);
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
        supabaseAuthHelper.signOut();
        prefsManager.setLoggedIn(false);
        prefsManager.saveUid(null);
    }

    // ─── Register ─────────────────────────────────────────────────────────────

    public void register(String email, String password, String displayName,
                         @NonNull final AuthCallback callback) {
        final String trimmedName = displayName != null ? displayName.trim() : "";

        supabaseAuthHelper.signUpWithEmail(email, password, trimmedName,
                new SupabaseAuthHelper.AuthCallback() {
                    @Override
                    public void onSuccess(SupabaseAuthHelper.SupabaseUser user) {
                        handleAuthSuccess(user, trimmedName);
                        callback.onSuccess(user);
                    }
                    @Override
                    public void onError(String errorKey) {
                        callback.onError(mapRegisterError(errorKey));
                    }
                });
    }

    // ─── Login ────────────────────────────────────────────────────────────────

    public void login(String email, String password, @NonNull final AuthCallback callback) {
        supabaseAuthHelper.signInWithEmail(email, password,
                new SupabaseAuthHelper.AuthCallback() {
                    @Override
                    public void onSuccess(SupabaseAuthHelper.SupabaseUser user) {
                        handleAuthSuccess(user);
                        callback.onSuccess(user);
                    }
                    @Override
                    public void onError(String errorKey) {
                        callback.onError(errorKey);
                    }
                });
    }

    // ─── Google Login ─────────────────────────────────────────────────────────

    /**
     * Supabase tự động link account khi cùng email → không cần xử lý collision.
     * AuthViewModel vẫn gọi loginWithGoogle() và linkPendingGoogle() theo flow cũ,
     * nhưng GOOGLE_LINK_REQUIRED state sẽ không bao giờ được trigger nữa.
     */
    public void loginWithGoogle(String idToken, @NonNull final AuthCallback callback) {
        supabaseAuthHelper.signInWithGoogle(idToken,
                new SupabaseAuthHelper.AuthCallback() {
                    @Override
                    public void onSuccess(SupabaseAuthHelper.SupabaseUser user) {
                        handleAuthSuccess(user);
                        callback.onSuccess(user);
                    }
                    @Override
                    public void onError(String errorKey) {
                        callback.onError(errorKey);
                    }
                });
    }

    /**
     * Giữ lại method để AuthViewModel compile được mà không cần sửa.
     * Với Supabase, link xảy ra tự động → method này chỉ thực hiện login Google bình thường.
     */
    public void linkGoogleToEmailAccount(String email, String password, String idToken,
                                         @NonNull final AuthCallback callback) {
        // Supabase link tự động, chỉ cần login với Google token
        loginWithGoogle(idToken, callback);
    }

    // ─── Password reset ───────────────────────────────────────────────────────

    public void sendPasswordResetEmail(String email, @NonNull final SimpleCallback callback) {
        supabaseAuthHelper.sendPasswordResetEmail(email, new SupabaseAuthHelper.SimpleCallback() {
            @Override public void onSuccess() { callback.onSuccess(); }
            @Override public void onError(String message) { callback.onError(message); }
        });
    }

    // ─── Passcode (không thay đổi logic) ─────────────────────────────────────

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

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void handleAuthSuccess(@NonNull SupabaseAuthHelper.SupabaseUser user) {
        handleAuthSuccess(user, user.displayName != null ? user.displayName : "");
    }

    private void handleAuthSuccess(@NonNull SupabaseAuthHelper.SupabaseUser user,
                                   @NonNull String displayName) {
        final long now = System.currentTimeMillis();
        final String resolvedName = !TextUtils.isEmpty(displayName) ? displayName
                : (user.displayName != null ? user.displayName : "");

        AppDatabase.databaseWriteExecutor.execute(() -> {
            UserEntity existing = userDao.getUserByIdSync(user.id);
            if (existing == null) {
                UserEntity entity = new UserEntity();
                entity.setId(user.id);
                entity.setEmail(user.email);
                entity.setDisplayName(resolvedName);
                entity.setAvatarUrl(user.avatarUrl);
                entity.setCurrency("VND");
                entity.setLanguage("vi");
                entity.setThemeMode("system");
                entity.setBalanceHidden(false);
                entity.setLastSync(0L);
                entity.setCreatedAt(now);
                userDao.insertUser(entity);
            } else {
                existing.setEmail(user.email);
                existing.setDisplayName(resolvedName);
                if (TextUtils.isEmpty(existing.getAvatarUrl())
                        && !TextUtils.isEmpty(user.avatarUrl)) {
                    existing.setAvatarUrl(user.avatarUrl);
                }
                userDao.updateUser(existing);
            }
        });

        prefsManager.saveUid(user.id);
        prefsManager.setLoggedIn(true);
    }

    private String mapRegisterError(String errorKey) {
        if ("auth_account_exists".equals(errorKey))
            return "Email này đã được đăng ký. Vui lòng đăng nhập.";
        if ("auth_weak_password".equals(errorKey))
            return "Mật khẩu phải có ít nhất 6 ký tự.";
        return errorKey;
    }
}