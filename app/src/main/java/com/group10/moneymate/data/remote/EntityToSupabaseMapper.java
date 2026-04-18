package com.group10.moneymate.data.remote;

import androidx.annotation.NonNull;

import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.local.entity.EventEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Chuyển Room Entity → JSONObject để gửi lên Supabase REST API.
 *
 * Quy tắc mapping:
 * - Tên field Java (camelCase) → tên cột Supabase (snake_case)
 * - Timestamps giữ nguyên dạng bigint (epoch millis) — khớp với schema Supabase
 * - Nullable field: nếu null thì put(key, JSONObject.NULL) để không mất field
 * - is_deleted và sync_status luôn được gửi lên để đồng bộ trạng thái xóa mềm
 */
public class EntityToSupabaseMapper {

    private EntityToSupabaseMapper() {}

    // ─── Wallet ───────────────────────────────────────────────────────────────

    @NonNull
    public static JSONObject fromWallet(@NonNull WalletEntity wallet) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", wallet.getId());
        json.put("user_id", wallet.getUserId());
        json.put("name", wallet.getName());
        json.put("balance", wallet.getBalance());
        json.put("type", wallet.getType());
        json.put("icon_name", wallet.getIconName());
        json.put("is_archived", wallet.isArchived());
        json.put("is_excluded", wallet.isExcluded());
        json.put("is_deleted", wallet.isDeleted());
        json.put("created_at", wallet.getCreatedAt());
        json.put("updated_at", wallet.getUpdatedAt());
        // sync_status KHÔNG gửi lên Supabase (field local only)
        return json;
    }

    // ─── Category ─────────────────────────────────────────────────────────────

    @NonNull
    public static JSONObject fromCategory(@NonNull CategoryEntity category) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", category.getId());
        putNullable(json, "user_id", category.getUserId());
        json.put("name", category.getName());
        json.put("type", category.getType());
        json.put("icon_name", category.getIconName());
        putNullable(json, "parent_id", category.getParentId());
        putNullable(json, "wallet_id", category.getWalletId());
        json.put("is_default", category.isDefault());
        json.put("is_deleted", category.isDeleted());
        json.put("created_at", category.getCreatedAt());
        json.put("updated_at", category.getUpdatedAt());
        return json;
    }

    // ─── Transaction ──────────────────────────────────────────────────────────

    @NonNull
    public static JSONObject fromTransaction(@NonNull TransactionEntity tx) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", tx.getId());
        json.put("user_id", tx.getUserId());
        json.put("wallet_id", tx.getWalletId());
        putNullable(json, "category_id", tx.getCategoryId());
        putNullable(json, "debt_id", tx.getDebtId());
        putNullable(json, "event_id", tx.getEventId());
        json.put("amount", tx.getAmount());
        json.put("type", tx.getType());
        putNullable(json, "to_wallet_id", tx.getToWalletId());
        putNullable(json, "note", tx.getNote());
        json.put("timestamp", tx.getTimestamp());
        putNullable(json, "image_path", tx.getImagePath());
        json.put("is_deleted", tx.isDeleted());
        json.put("created_at", tx.getCreatedAt());
        json.put("updated_at", tx.getUpdatedAt());
        return json;
    }

    // ─── Budget ───────────────────────────────────────────────────────────────

    @NonNull
    public static JSONObject fromBudget(@NonNull BudgetEntity budget) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", budget.getId());
        json.put("user_id", budget.getUserId());
        putNullable(json, "category_id", budget.getCategoryId());
        putNullable(json, "wallet_id", budget.getWalletId());
        json.put("amount", budget.getAmount());
        json.put("start_date", budget.getStartDate());
        json.put("end_date", budget.getEndDate());
        json.put("is_deleted", budget.isDeleted());
        json.put("created_at", budget.getCreatedAt());
        json.put("updated_at", budget.getUpdatedAt());
        return json;
    }

    // ─── Debt ─────────────────────────────────────────────────────────────────

    @NonNull
    public static JSONObject fromDebt(@NonNull DebtEntity debt) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", debt.getId());
        json.put("user_id", debt.getUserId());
        json.put("person_name", debt.getPersonName());
        json.put("type", debt.getType());
        json.put("amount", debt.getAmount());
        json.put("remaining_amount", debt.getRemainingAmount());
        if (debt.getDueDate() != null) {
            json.put("due_date", debt.getDueDate());
        } else {
            json.put("due_date", JSONObject.NULL);
        }
        json.put("status", debt.getStatus());
        putNullable(json, "note", debt.getNote());
        json.put("is_deleted", debt.isDeleted());
        json.put("created_at", debt.getCreatedAt());
        json.put("updated_at", debt.getUpdatedAt());
        return json;
    }

    // ─── Event ────────────────────────────────────────────────────────────────

    @NonNull
    public static JSONObject fromEvent(@NonNull EventEntity event) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", event.getId());
        json.put("user_id", event.getUserId());
        json.put("name", event.getName());
        if (event.getBudgetLimit() != null) {
            json.put("budget_limit", event.getBudgetLimit());
        } else {
            json.put("budget_limit", JSONObject.NULL);
        }
        json.put("start_date", event.getStartDate());
        json.put("end_date", event.getEndDate());
        json.put("is_active", event.isActive());
        json.put("is_deleted", event.isDeleted());
        json.put("created_at", event.getCreatedAt());
        json.put("updated_at", event.getUpdatedAt());
        return json;
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private static void putNullable(@NonNull JSONObject json,
                                    @NonNull String key,
                                    @androidx.annotation.Nullable String value) throws JSONException {
        if (value != null) {
            json.put(key, value);
        } else {
            json.put(key, JSONObject.NULL);
        }
    }
}