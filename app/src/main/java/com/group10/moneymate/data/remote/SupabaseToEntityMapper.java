package com.group10.moneymate.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.local.entity.EventEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.models.SyncStatus;

import org.json.JSONObject;

/**
 * Chuyển JSONObject từ Supabase REST API → Room Entity.
 * Đối xứng với EntityToSupabaseMapper.
 *
 * Quy tắc quan trọng:
 * - Tất cả Entity được set syncStatus = SYNCED (0) vì dữ liệu vừa lấy từ server
 * - is_deleted được map đúng để Phase 2 của thiết bị khác xử lý soft-delete
 * - Timestamp giữ nguyên dạng bigint (epoch millis)
 * - Nullable field: nếu JSON null thì set null trong Entity
 */
public class SupabaseToEntityMapper {

    private SupabaseToEntityMapper() {}

    // ─── Wallet ───────────────────────────────────────────────────────────────

    @NonNull
    public static WalletEntity toWallet(@NonNull JSONObject json) {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(json.optString("id"));
        wallet.setUserId(json.optString("user_id"));
        wallet.setName(json.optString("name"));
        wallet.setBalance(json.optDouble("balance", 0.0));
        wallet.setType(json.optString("type"));
        wallet.setIconName(json.optString("icon_name", "ic_wallet_default"));
        wallet.setArchived(json.optBoolean("is_archived", false));
        wallet.setExcluded(json.optBoolean("is_excluded", false));
        wallet.setDeleted(json.optBoolean("is_deleted", false));
        wallet.setCreatedAt(json.optLong("created_at", 0L));
        wallet.setUpdatedAt(json.optLong("updated_at", 0L));
        // Dữ liệu từ server → SYNCED, không cần push lại
        wallet.setSyncStatus(SyncStatus.SYNCED);
        return wallet;
    }

    // ─── Category ─────────────────────────────────────────────────────────────

    @NonNull
    public static CategoryEntity toCategory(@NonNull JSONObject json) {
        CategoryEntity category = new CategoryEntity();
        category.setId(json.optString("id"));
        category.setUserId(nullableString(json, "user_id"));
        category.setName(json.optString("name"));
        category.setType(json.optString("type"));
        category.setIconName(json.optString("icon_name", "ic_category_default"));
        category.setParentId(nullableString(json, "parent_id"));
        category.setWalletId(nullableString(json, "wallet_id"));
        category.setDefault(json.optBoolean("is_default", false));
        category.setDeleted(json.optBoolean("is_deleted", false));
        category.setCreatedAt(json.optLong("created_at", 0L));
        category.setUpdatedAt(json.optLong("updated_at", 0L));
        category.setSyncStatus(SyncStatus.SYNCED);
        return category;
    }

    // ─── Transaction ──────────────────────────────────────────────────────────

    @NonNull
    public static TransactionEntity toTransaction(@NonNull JSONObject json) {
        TransactionEntity tx = new TransactionEntity();
        tx.setId(json.optString("id"));
        tx.setUserId(json.optString("user_id"));
        tx.setWalletId(json.optString("wallet_id"));
        tx.setCategoryId(nullableString(json, "category_id"));
        tx.setDebtId(nullableString(json, "debt_id"));
        tx.setEventId(nullableString(json, "event_id"));
        tx.setAmount(json.optDouble("amount", 0.0));
        tx.setType(json.optString("type"));
        tx.setToWalletId(nullableString(json, "to_wallet_id"));
        tx.setNote(nullableString(json, "note"));
        tx.setTimestamp(json.optLong("timestamp", 0L));
        tx.setImagePath(nullableString(json, "image_path"));
        tx.setDeleted(json.optBoolean("is_deleted", false));
        tx.setCreatedAt(json.optLong("created_at", 0L));
        tx.setUpdatedAt(json.optLong("updated_at", 0L));
        tx.setSyncStatus(SyncStatus.SYNCED);
        return tx;
    }

    // ─── Budget ───────────────────────────────────────────────────────────────

    @NonNull
    public static BudgetEntity toBudget(@NonNull JSONObject json) {
        BudgetEntity budget = new BudgetEntity();
        budget.setId(json.optString("id"));
        budget.setUserId(json.optString("user_id"));
        budget.setCategoryId(nullableString(json, "category_id"));
        budget.setWalletId(nullableString(json, "wallet_id"));
        budget.setAmount(json.optDouble("amount", 0.0));
        budget.setStartDate(json.optLong("start_date", 0L));
        budget.setEndDate(json.optLong("end_date", 0L));
        budget.setDeleted(json.optBoolean("is_deleted", false));
        budget.setCreatedAt(json.optLong("created_at", 0L));
        budget.setUpdatedAt(json.optLong("updated_at", 0L));
        // QUAN TRỌNG: SYNCED, không phải PENDING_UPLOAD
        // (BudgetEntity constructor cũ set PENDING_UPLOAD — đã fix ở Phase 1)
        budget.setSyncStatus(SyncStatus.SYNCED);
        return budget;
    }

    // ─── Debt ─────────────────────────────────────────────────────────────────

    @NonNull
    public static DebtEntity toDebt(@NonNull JSONObject json) {
        DebtEntity debt = new DebtEntity();
        debt.setId(json.optString("id"));
        debt.setUserId(json.optString("user_id"));
        debt.setPersonName(json.optString("person_name"));
        debt.setType(json.optString("type"));
        debt.setAmount(json.optDouble("amount", 0.0));
        debt.setRemainingAmount(json.optDouble("remaining_amount", 0.0));
        // due_date nullable
        if (!json.isNull("due_date")) {
            debt.setDueDate(json.optLong("due_date", 0L));
        } else {
            debt.setDueDate(null);
        }
        debt.setStatus(json.optString("status"));
        debt.setNote(nullableString(json, "note"));
        debt.setDeleted(json.optBoolean("is_deleted", false));
        debt.setCreatedAt(json.optLong("created_at", 0L));
        debt.setUpdatedAt(json.optLong("updated_at", 0L));
        debt.setSyncStatus(SyncStatus.SYNCED);
        return debt;
    }

    // ─── Event ────────────────────────────────────────────────────────────────

    @NonNull
    public static EventEntity toEvent(@NonNull JSONObject json) {
        EventEntity event = new EventEntity();
        event.setId(json.optString("id"));
        event.setUserId(json.optString("user_id"));
        event.setName(json.optString("name"));
        // budget_limit nullable
        if (!json.isNull("budget_limit")) {
            event.setBudgetLimit(json.optDouble("budget_limit"));
        } else {
            event.setBudgetLimit(null);
        }
        event.setStartDate(json.optLong("start_date", 0L));
        event.setEndDate(json.optLong("end_date", 0L));
        event.setActive(json.optBoolean("is_active", true));
        event.setDeleted(json.optBoolean("is_deleted", false));
        event.setCreatedAt(json.optLong("created_at", 0L));
        event.setUpdatedAt(json.optLong("updated_at", 0L));
        event.setSyncStatus(SyncStatus.SYNCED);
        return event;
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    @Nullable
    private static String nullableString(@NonNull JSONObject json, @NonNull String key) {
        if (json.isNull(key)) {
            return null;
        }
        String value = json.optString(key, null);
        return (value == null || value.isEmpty()) ? null : value;
    }
}