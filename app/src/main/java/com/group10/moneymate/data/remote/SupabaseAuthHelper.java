package com.group10.moneymate.data.remote;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Thay thế FirebaseAuthHelper.
 * Giao tiếp với Supabase Auth REST API bằng OkHttp (Java thuần).
 *
 * Cách dùng:
 *   - Thêm vào local.properties:
 *       SUPABASE_URL=https://your-project.supabase.co
 *       SUPABASE_ANON_KEY=your-anon-key
 *   - Thêm vào build.gradle.kts (defaultConfig):
 *       buildConfigField("String", "SUPABASE_URL", "\"${localProperties["SUPABASE_URL"]}\"")
 *       buildConfigField("String", "SUPABASE_ANON_KEY", "\"${localProperties["SUPABASE_ANON_KEY"]}\"")
 *   - Thêm dependency OkHttp vào build.gradle.kts:
 *       implementation("com.squareup.okhttp3:okhttp:4.12.0")
 */
public class SupabaseAuthHelper {

    // ─── Models ───────────────────────────────────────────────────────────────

    /** Thay thế FirebaseUser – chứa thông tin user sau khi auth thành công */
    public static class SupabaseUser {
        public final String id;           // UUID, tương đương firebaseUser.getUid()
        public final String email;
        public final String displayName;  // từ user_metadata.display_name
        public final String avatarUrl;    // từ user_metadata.avatar_url (Google)
        public final String accessToken;

        public SupabaseUser(String id, String email, String displayName,
                            String avatarUrl, String accessToken) {
            this.id          = id;
            this.email       = email;
            this.displayName = displayName;
            this.avatarUrl   = avatarUrl;
            this.accessToken = accessToken;
        }
    }

    public interface AuthCallback {
        void onSuccess(SupabaseUser user);
        void onError(String errorKey); // trả về cùng error key với Firebase cũ
    }

    public interface SimpleCallback {
        void onSuccess();
        void onError(String message);
    }

    // ─── Fields ───────────────────────────────────────────────────────────────

    private final String supabaseUrl;
    private final String supabaseAnonKey;
    private final OkHttpClient httpClient;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    // Session in-memory (token ngắn hạn; uid dài hạn lưu ở PrefsManager)
    private SupabaseUser currentUser = null;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public SupabaseAuthHelper(String supabaseUrl, String supabaseAnonKey) {
        this.supabaseUrl      = supabaseUrl;
        this.supabaseAnonKey  = supabaseAnonKey;
        this.httpClient       = new OkHttpClient();
    }

    // ─── Session ──────────────────────────────────────────────────────────────

    public SupabaseUser getCurrentUser() { return currentUser; }

    public boolean isLoggedIn() { return currentUser != null; }

    public String getCurrentUserId() {
        return currentUser != null ? currentUser.id : null;
    }

    public void signOut() {
        currentUser = null;
    }

    // ─── Sign Up ──────────────────────────────────────────────────────────────

    /**
     * Tương đương firebaseAuth.createUserWithEmailAndPassword()
     * displayName được lưu vào user_metadata ngay lúc sign up.
     */
    public void signUpWithEmail(String email, String password, String displayName,
                                AuthCallback callback) {
        try {
            JSONObject meta = new JSONObject();
            meta.put("display_name", displayName != null ? displayName : "");

            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);
            body.put("data", meta);

            post("/auth/v1/signup", body.toString(), null, new RawCallback() {
                @Override public void onSuccess(JSONObject json) {
                    SupabaseUser user = parseSession(json);
                    if (user == null) { fireError(callback, "auth_login_failed"); return; }
                    currentUser = user;
                    fireSuccess(callback, user);
                }
                @Override public void onError(int httpCode, String body) {
                    fireError(callback, mapHttpError(httpCode, body, false));
                }
            });
        } catch (JSONException e) {
            fireError(callback, "auth_login_failed");
        }
    }

    // ─── Sign In ──────────────────────────────────────────────────────────────

    /**
     * Tương đương firebaseAuth.signInWithEmailAndPassword()
     */
    public void signInWithEmail(String email, String password, AuthCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);
            body.put("password", password);

            post("/auth/v1/token?grant_type=password", body.toString(), null, new RawCallback() {
                @Override public void onSuccess(JSONObject json) {
                    SupabaseUser user = parseSession(json);
                    if (user == null) { fireError(callback, "auth_login_failed"); return; }
                    currentUser = user;
                    fireSuccess(callback, user);
                }
                @Override public void onError(int httpCode, String body) {
                    fireError(callback, mapHttpError(httpCode, body, false));
                }
            });
        } catch (JSONException e) {
            fireError(callback, "auth_login_failed");
        }
    }

    // ─── Google Sign-In ───────────────────────────────────────────────────────

    /**
     * Tương đương firebaseAuth.signInWithCredential(GoogleAuthProvider.getCredential(idToken))
     * Supabase nhận id_token của Google qua /auth/v1/token?grant_type=id_token
     *
     * Lưu ý: Nếu email đã tồn tại với provider email/password, Supabase sẽ
     * tự động link (khác Firebase). Không cần xử lý collision thủ công.
     */
    public void signInWithGoogle(String idToken, AuthCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("provider", "google");
            body.put("id_token", idToken);

            post("/auth/v1/token?grant_type=id_token", body.toString(), null, new RawCallback() {
                @Override public void onSuccess(JSONObject json) {
                    SupabaseUser user = parseSession(json);
                    if (user == null) { fireError(callback, "auth_login_failed"); return; }
                    currentUser = user;
                    fireSuccess(callback, user);
                }
                @Override public void onError(int httpCode, String body) {
                    fireError(callback, mapHttpError(httpCode, body, false));
                }
            });
        } catch (JSONException e) {
            fireError(callback, "auth_login_failed");
        }
    }

    // ─── Update Display Name ──────────────────────────────────────────────────

    /**
     * Tương đương user.updateProfile(new UserProfileChangeRequest.Builder().setDisplayName())
     */
    public void updateDisplayName(String accessToken, String displayName, SimpleCallback callback) {
        try {
            JSONObject meta = new JSONObject();
            meta.put("display_name", displayName);

            JSONObject body = new JSONObject();
            body.put("data", meta);

            put("/auth/v1/user", body.toString(), accessToken, new RawCallback() {
                @Override public void onSuccess(JSONObject json) {
                    // Cập nhật currentUser nếu đang dùng cùng token
                    if (currentUser != null && accessToken.equals(currentUser.accessToken)) {
                        currentUser = new SupabaseUser(
                                currentUser.id, currentUser.email,
                                displayName, currentUser.avatarUrl, currentUser.accessToken);
                    }
                    mainHandler.post(callback::onSuccess);
                }
                @Override public void onError(int httpCode, String body) {
                    mainHandler.post(() -> callback.onError("Failed to update profile"));
                }
            });
        } catch (JSONException e) {
            mainHandler.post(() -> callback.onError("Failed to update profile"));
        }
    }

    // ─── Password Reset ───────────────────────────────────────────────────────

    /**
     * Tương đương firebaseAuth.sendPasswordResetEmail()
     */
    public void sendPasswordResetEmail(String email, SimpleCallback callback) {
        try {
            JSONObject body = new JSONObject();
            body.put("email", email);

            post("/auth/v1/recover", body.toString(), null, new RawCallback() {
                @Override public void onSuccess(JSONObject json) {
                    mainHandler.post(callback::onSuccess);
                }
                @Override public void onError(int httpCode, String body) {
                    mainHandler.post(() -> callback.onError("Failed to send reset email"));
                }
            });
        } catch (JSONException e) {
            mainHandler.post(() -> callback.onError("Failed to send reset email"));
        }
    }

    // ─── HTTP helpers ─────────────────────────────────────────────────────────

    private interface RawCallback {
        void onSuccess(JSONObject json);
        void onError(int httpCode, String body);
    }

    private void post(String path, String jsonBody, String bearerToken, RawCallback callback) {
        Request.Builder builder = new Request.Builder()
                .url(supabaseUrl + path)
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON));

        if (bearerToken != null) {
            builder.addHeader("Authorization", "Bearer " + bearerToken);
        }

        httpClient.newCall(builder.build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError(0, e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try { callback.onSuccess(new JSONObject(responseBody)); }
                    catch (JSONException e) { callback.onError(response.code(), responseBody); }
                } else {
                    callback.onError(response.code(), responseBody);
                }
            }
        });
    }

    private void put(String path, String jsonBody, String bearerToken, RawCallback callback) {
        Request.Builder builder = new Request.Builder()
                .url(supabaseUrl + path)
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Content-Type", "application/json")
                .put(RequestBody.create(jsonBody, JSON));

        if (bearerToken != null) {
            builder.addHeader("Authorization", "Bearer " + bearerToken);
        }

        httpClient.newCall(builder.build()).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                callback.onError(0, e.getMessage());
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (response.isSuccessful()) {
                    try { callback.onSuccess(new JSONObject(responseBody)); }
                    catch (JSONException e) { callback.onError(response.code(), responseBody); }
                } else {
                    callback.onError(response.code(), responseBody);
                }
            }
        });
    }

    // ─── Parse helpers ────────────────────────────────────────────────────────

    /**
     * Parse response từ /token (sign-in) hoặc /signup.
     * Cả hai đều trả về cùng cấu trúc JSON:
     * { access_token, user: { id, email, user_metadata: { display_name, avatar_url } } }
     */
    private SupabaseUser parseSession(JSONObject json) {
        try {
            String accessToken = json.optString("access_token", null);
            JSONObject userObj = json.optJSONObject("user");
            if (userObj == null) return null;

            String id    = userObj.optString("id", null);
            String email = userObj.optString("email", null);

            String displayName = null;
            String avatarUrl   = null;
            JSONObject meta    = userObj.optJSONObject("user_metadata");
            if (meta != null) {
                displayName = meta.optString("display_name",
                        meta.optString("full_name",
                                meta.optString("name", "")));
                avatarUrl   = meta.optString("avatar_url", null);
            }

            return new SupabaseUser(id, email, displayName, avatarUrl, accessToken);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Map HTTP error code → error key khớp với những gì AuthRepository/AuthViewModel đang dùng.
     */
    private String mapHttpError(int httpCode, String body, boolean isRegistration) {
        if (httpCode == 0) return "auth_network_timeout"; // IOException
        if (httpCode == 400) {
            // Supabase trả về message trong body JSON
            try {
                JSONObject err = new JSONObject(body);
                String msg = err.optString("message", err.optString("error_description", ""));
                String code = err.optString("error_code", "");

                if (msg.contains("Invalid login credentials")) return "auth_wrong_password";
                if (msg.contains("Email not confirmed"))        return "auth_user_not_found";
                if (msg.contains("User already registered") || code.equals("user_already_exists"))
                    return "auth_account_exists";
                if (msg.contains("Password should be at least")) return "auth_weak_password";
            } catch (JSONException ignored) {}
            return isRegistration ? "auth_registration_failed" : "auth_wrong_password";
        }
        if (httpCode == 422) return "auth_user_not_found";
        if (httpCode == 429) return "auth_network_timeout"; // rate limit
        if (httpCode >= 500) return "auth_network_timeout";
        return "auth_login_failed";
    }

    // ─── Dispatch helpers ─────────────────────────────────────────────────────

    private void fireSuccess(AuthCallback callback, SupabaseUser user) {
        mainHandler.post(() -> callback.onSuccess(user));
    }

    private void fireError(AuthCallback callback, String key) {
        mainHandler.post(() -> callback.onError(key));
    }
}