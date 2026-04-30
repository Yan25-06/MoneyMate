package com.group10.moneymate.data.remote;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * SupabaseSyncClient — Phase 2 + Phase 3.
 *
 * Phase 2 (Push): upsert(), delete()
 * Phase 3 (Pull): fetchPage() — kéo delta từ Supabase về
 *
 * fetchPage() dùng cursor-based pagination:
 *   GET /rest/v1/{table}?user_id=eq.{uid}&updated_at=gt.{cursor}&order=updated_at.asc,id.asc&limit={size}
 * Cursor là updated_at của bản ghi cuối cùng trong trang trước.
 * Khi fetchPage() trả về mảng rỗng → pull xong.
 */
public class SupabaseSyncClient {

    public static class SyncException extends IOException {
        private final int httpCode;

        public SyncException(String message, int httpCode) {
            super(message);
            this.httpCode = httpCode;
        }

        public int getHttpCode() { return httpCode; }

        public boolean isAuthError() { return httpCode == 401 || httpCode == 403; }

        public boolean isServerError() { return httpCode >= 500; }
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final int PAGE_SIZE = 500;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    private final String supabaseUrl;
    private final String supabaseAnonKey;

    public SupabaseSyncClient(@NonNull String supabaseUrl, @NonNull String supabaseAnonKey) {
        this.supabaseUrl = supabaseUrl;
        this.supabaseAnonKey = supabaseAnonKey;
    }

    // ─── Phase 2: Push ────────────────────────────────────────────────────────

    /**
     * Upsert một mảng bản ghi lên Supabase.
     * Dùng POST với "Prefer: resolution=merge-duplicates".
     */
    public void upsert(@NonNull String table,
                       @NonNull JSONArray rows,
                       @NonNull String token) throws SyncException {
        if (rows.length() == 0) return;

        Request request = new Request.Builder()
                .url(supabaseUrl + "/rest/v1/" + table)
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .addHeader("Prefer", "resolution=merge-duplicates")
                .post(RequestBody.create(rows.toString(), JSON))
                .build();

        executeAndCheck(request);
    }

    /**
     * Xóa một bản ghi theo id.
     */
    public void delete(@NonNull String table,
                       @NonNull String id,
                       @NonNull String token) throws SyncException {
        Request request = new Request.Builder()
                .url(supabaseUrl + "/rest/v1/" + table + "?id=eq." + id)
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + token)
                .delete()
                .build();

        executeAndCheck(request);
    }

    // ─── Phase 3: Pull ────────────────────────────────────────────────────────

    /**
     * Kéo một trang dữ liệu từ Supabase kể từ cursor.
     *
     * Cursor là updated_at của bản ghi cuối trong trang trước (millis).
     * Lần đầu tiên pull (thiết bị mới): truyền cursor = 0L để lấy tất cả.
     * Các lần sau (delta sync): truyền cursor = last_synced_at đã lưu.
     *
     * Trả về JSONArray các bản ghi. Rỗng = không còn dữ liệu mới.
     *
     * Query: updated_at > cursor, order by updated_at ASC, id ASC, limit PAGE_SIZE
     * Tại sao gt (>) thay vì gte (>=)?
     *   Vì checkpoint lưu updated_at của bản ghi CUỐI CÙNG đã xử lý.
     *   Nếu dùng >= sẽ pull lại bản ghi đó mỗi lần → lãng phí.
     *   Tuy nhiên: nếu nhiều bản ghi có cùng updated_at, dùng gt có thể bỏ sót.
     *   Giải pháp: dùng (updated_at, id) làm composite cursor — xem InitialSyncWorker.
     *
     * @param table     Tên bảng Supabase
     * @param userId    UUID của user (để RLS hoạt động đúng)
     * @param cursor    updated_at của bản ghi cuối trang trước (0 = lấy tất cả)
     * @param token     Access token
     */
    @NonNull
    public JSONArray fetchPage(@NonNull String table,
                               @NonNull String userId,
                               long cursor,
                               @NonNull String token) throws SyncException {
        // Supabase PostgREST filter: updated_at=gt.{cursor}
        // select=* để lấy tất cả cột
        String url = supabaseUrl + "/rest/v1/" + table
                + "?user_id=eq." + userId
                + "&updated_at=gt." + cursor
                + "&order=updated_at.asc,id.asc"
                + "&limit=" + PAGE_SIZE;

        Request request = new Request.Builder()
                .url(url)
                .addHeader("apikey", supabaseAnonKey)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Accept", "application/json")
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new SyncException(
                        "Supabase pull failed: HTTP " + response.code() + " — " + body,
                        response.code()
                );
            }
            String bodyStr = response.body() != null ? response.body().string() : "[]";
            return new JSONArray(bodyStr);
        } catch (SyncException e) {
            throw e;
        } catch (IOException e) {
            throw new SyncException("Network error: " + e.getMessage(), 0);
        } catch (org.json.JSONException e) {
            throw new SyncException("JSON parse error: " + e.getMessage(), 0);
        }
    }

    /**
     * Đếm số bản ghi remote của user (dùng để phát hiện thiết bị mới).
     * Trả về -1 nếu có lỗi mạng hoặc parse.
     */
    public int countRemoteRecords(@NonNull String table,
                                  @NonNull String userId,
                                  @NonNull String token) {
        try {
            Request request = new Request.Builder()
                    .url(supabaseUrl + "/rest/v1/" + table
                            + "?user_id=eq." + userId
                            + "&select=id"
                            + "&limit=1")
                    .addHeader("apikey", supabaseAnonKey)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Prefer", "count=exact")
                    .get()
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) return -1;
                String countHeader = response.header("Content-Range");
                if (countHeader != null && countHeader.contains("/")) {
                    String total = countHeader.split("/")[1].trim();
                    if (!total.equals("*")) return Integer.parseInt(total);
                }
                return 0;
            }
        } catch (Exception e) {
            return -1;
        }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void executeAndCheck(@NonNull Request request) throws SyncException {
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String body = response.body() != null ? response.body().string() : "";
                throw new SyncException(
                        "Supabase sync failed: HTTP " + response.code() + " — " + body,
                        response.code()
                );
            }
        } catch (SyncException e) {
            throw e;
        } catch (IOException e) {
            throw new SyncException("Network error: " + e.getMessage(), 0);
        }
    }
}