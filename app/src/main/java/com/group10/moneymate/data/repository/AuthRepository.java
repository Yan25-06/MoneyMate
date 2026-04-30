package com.group10.moneymate.data.repository;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.UserDao;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.remote.SupabaseAuthHelper;
import com.group10.moneymate.data.remote.SupabaseSyncClient;
import com.group10.moneymate.utils.PasscodeHasher;
import com.group10.moneymate.utils.PrefsManager;
import com.group10.moneymate.workers.InitialSyncWorker;

import java.util.concurrent.TimeUnit;

/**
 * AuthRepository — Phase 3.
 *
 * Thay đổi so với phiên bản cũ:
 * - Thêm getCurrentAccessToken() cho SyncWorker
 * - handleAuthSuccess() kiểm tra thiết bị mới → enqueue InitialSyncWorker
 * - Thiết bị mới = Room không có wallet nào NHƯNG Supabase có dữ liệu
 * - Cần Context để enqueue WorkManager (inject qua constructor)
 */
public class AuthRepository {

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

    private static final String UNIQUE_INITIAL_SYNC = "initial_sync";

    private final SupabaseAuthHelper supabaseAuthHelper;
    private final SupabaseSyncClient syncClient;
    private final UserDao userDao;
    private final AppDatabase database;
    private final PrefsManager prefsManager;
    private final Context applicationContext;

    public AuthRepository(@NonNull SupabaseAuthHelper supabaseAuthHelper,
                          @NonNull SupabaseSyncClient syncClient,
                          @NonNull UserDao userDao,
                          @NonNull AppDatabase database,
                          @NonNull PrefsManager prefsManager,
                          @NonNull Context applicationContext) {
        this.supabaseAuthHelper = supabaseAuthHelper;
        this.syncClient = syncClient;
        this.userDao = userDao;
        this.database = database;
        this.prefsManager = prefsManager;
        this.applicationContext = applicationContext;
    }

    // ─── Auth state ───────────────────────────────────────────────────────────

    public boolean isLoggedIn() {
        return prefsManager.isLoggedIn();
    }

    @NonNull
    public String getCurrentUserId() {
        String localUid = prefsManager.getUid();
        if (!TextUtils.isEmpty(localUid)) return localUid;
        String supabaseUid = supabaseAuthHelper.getCurrentUserId();
        return supabaseUid != null ? supabaseUid : "";
    }

    /**
     * THÊM MỚI Phase 2: Trả về access token hiện tại cho SyncWorker.
     */
    @Nullable
    public String getCurrentAccessToken() {
        SupabaseAuthHelper.SupabaseUser user = supabaseAuthHelper.getCurrentUser();
        return user != null ? user.accessToken : null;
    }

    public void ensureLocalUserRecord() {
        final String localUserId = getCurrentUserId();
        if (TextUtils.isEmpty(localUserId)) return;

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
                        // Đăng ký mới → không cần pull, Room đang trống là đúng
                        handleAuthSuccess(user, trimmedName, false);
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
                        // Login → có thể là thiết bị mới, cần kiểm tra
                        handleAuthSuccess(user, user.displayName != null ? user.displayName : "", true);
                        callback.onSuccess(user);
                    }
                    @Override
                    public void onError(String errorKey) {
                        callback.onError(errorKey);
                    }
                });
    }

    // ─── Google Login ─────────────────────────────────────────────────────────

    public void loginWithGoogle(String idToken, @NonNull final AuthCallback callback) {
        supabaseAuthHelper.signInWithGoogle(idToken,
                new SupabaseAuthHelper.AuthCallback() {
                    @Override
                    public void onSuccess(SupabaseAuthHelper.SupabaseUser user) {
                        handleAuthSuccess(user, user.displayName != null ? user.displayName : "", true);
                        callback.onSuccess(user);
                    }
                    @Override
                    public void onError(String errorKey) {
                        callback.onError(errorKey);
                    }
                });
    }

    public void linkGoogleToEmailAccount(String email, String password, String idToken,
                                         @NonNull final AuthCallback callback) {
        loginWithGoogle(idToken, callback);
    }

    // ─── Password reset ───────────────────────────────────────────────────────

    public void sendPasswordResetEmail(String email, @NonNull final SimpleCallback callback) {
        supabaseAuthHelper.sendPasswordResetEmail(email, new SupabaseAuthHelper.SimpleCallback() {
            @Override public void onSuccess() { callback.onSuccess(); }
            @Override public void onError(String message) { callback.onError(message); }
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

    /**
     * Xử lý sau khi auth thành công:
     * 1. Lưu/cập nhật UserEntity vào Room
     * 2. Lưu uid + isLoggedIn vào PrefsManager
     * 3. Nếu checkForRestore=true: kiểm tra thiết bị mới → enqueue InitialSyncWorker
     *
     * @param checkForRestore true khi login (không phải register)
     */
    private void handleAuthSuccess(@NonNull SupabaseAuthHelper.SupabaseUser user,
                                   @NonNull String displayName,
                                   boolean checkForRestore) {
        final long now = System.currentTimeMillis();
        final String resolvedName = !TextUtils.isEmpty(displayName) ? displayName
                : (user.displayName != null ? user.displayName : "");
        final String token = user.accessToken;

        AppDatabase.databaseWriteExecutor.execute(() -> {
            // 1. Upsert UserEntity
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

            // 2. THÊM MỚI Phase 3: phát hiện thiết bị mới và trigger initial sync
            if (checkForRestore && token != null) {
                maybeEnqueueInitialSync(user.id, token);
            }
        });

        prefsManager.saveUid(user.id);
        prefsManager.setLoggedIn(true);
    }

    /**
     * Kiểm tra xem có cần pull từ Supabase không.
     *
     * Điều kiện thiết bị mới:
     *   - Room không có wallet nào của user này (localCount == 0)
     *   - Supabase có ít nhất 1 wallet (remoteCount > 0)
     *
     * Dùng wallet làm proxy vì:
     *   - Wallet là dependency đầu tiên trong chain
     *   - Nếu user chưa có wallet nào thực sự thì không cần pull gì cả
     *   - Query nhẹ (chỉ đếm)
     *
     * Chạy trên databaseWriteExecutor (đã ở background thread khi được gọi).
     */
    private void maybeEnqueueInitialSync(@NonNull String userId, @NonNull String token) {
        // Đếm wallet local (không lọc is_deleted để tránh false positive)
        int localCount = database.walletDao().countWalletsByUser(userId);

        if (localCount > 0) {
            // Đã có dữ liệu local → không phải thiết bị mới → SyncWorker định kỳ lo phần còn lại
            return;
        }

        // Room trống → kiểm tra Supabase
        int remoteCount = syncClient.countRemoteRecords("wallets", userId, token);

        if (remoteCount <= 0) {
            // Supabase cũng trống (user mới đăng ký) → không cần pull
            return;
        }

        // Thiết bị mới xác nhận → enqueue InitialSyncWorker
        enqueueInitialSync(userId, token);
    }

    private void enqueueInitialSync(@NonNull String userId, @NonNull String token) {
        Data inputData = new Data.Builder()
                .putString(InitialSyncWorker.KEY_USER_ID, userId)
                .putString(InitialSyncWorker.KEY_TOKEN, token)
                .build();

        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(InitialSyncWorker.class)
                .setConstraints(new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build())
                .setInputData(inputData)
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        30L,
                        TimeUnit.SECONDS
                )
                .build();

        WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                        UNIQUE_INITIAL_SYNC,
                        ExistingWorkPolicy.KEEP, // không enqueue lại nếu đang chạy
                        request
                );
    }

    private String mapRegisterError(String errorKey) {
        if ("auth_account_exists".equals(errorKey))
            return "Email này đã được đăng ký. Vui lòng đăng nhập.";
        if ("auth_weak_password".equals(errorKey))
            return "Mật khẩu phải có ít nhất 6 ký tự.";
        return errorKey;
    }
}