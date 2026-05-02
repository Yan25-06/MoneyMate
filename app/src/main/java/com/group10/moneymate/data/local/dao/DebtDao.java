package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.annotation.RestrictTo;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.group10.moneymate.data.local.entity.DebtEntity;

import java.util.List;

@Dao
public interface DebtDao {
    @RawQuery
    int upsertLocalRaw(SupportSQLiteQuery query);

    default void upsertLocal(DebtEntity debt) {
        String sql = "INSERT INTO debts ("
                + "id, user_id, person_name, type, amount, remaining_amount, due_date, status, note, "
                + "created_at, updated_at, sync_status, is_deleted"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET "
                + "user_id = excluded.user_id, "
                + "person_name = excluded.person_name, "
                + "type = excluded.type, "
                + "amount = excluded.amount, "
                + "remaining_amount = excluded.remaining_amount, "
                + "due_date = excluded.due_date, "
                + "status = excluded.status, "
                + "note = excluded.note, "
                + "updated_at = excluded.updated_at, "
                + "sync_status = CASE WHEN debts.sync_status = 2 THEN 2 ELSE excluded.sync_status END, "
                + "is_deleted = CASE WHEN debts.is_deleted = 1 THEN 1 ELSE excluded.is_deleted END, "
                + "created_at = CASE WHEN debts.created_at IS NULL OR debts.created_at <= 0 THEN excluded.created_at ELSE debts.created_at END";
        upsertLocalRaw(new SimpleSQLiteQuery(sql, new Object[] {
                debt.getId(),
                debt.getUserId(),
                debt.getPersonName(),
                debt.getType(),
                debt.getAmount(),
                debt.getRemainingAmount(),
                debt.getDueDate(),
                debt.getStatus(),
                debt.getNote(),
                debt.getCreatedAt(),
                debt.getUpdatedAt(),
                debt.getSyncStatus(),
                debt.isDeleted() ? 1 : 0
        }));
    }

    default void insertDebt(DebtEntity debt) {
        upsertLocal(debt);
    }

    default void updateDebt(DebtEntity debt) {
        upsertLocal(debt);
    }

    @Query("SELECT * FROM debts WHERE user_id = :userId AND is_deleted = 0 ORDER BY updated_at DESC")
    LiveData<List<DebtEntity>> getAllDebts(String userId);

    @Query("SELECT * FROM debts WHERE user_id = :userId AND type = :type AND is_deleted = 0 ORDER BY updated_at DESC")
    LiveData<List<DebtEntity>> getDebtsByType(String userId, String type);

    @Query("SELECT * FROM debts WHERE user_id = :userId AND type = :type AND status = :status AND is_deleted = 0 ORDER BY updated_at DESC")
    LiveData<List<DebtEntity>> getDebtsByTypeAndStatus(String userId, String type, String status);

    @Query("SELECT * FROM debts WHERE user_id = :userId AND type = :type AND status = 'ACTIVE' AND is_deleted = 0 ORDER BY updated_at DESC")
    LiveData<List<DebtEntity>> getOngoingDebtsByType(String userId, String type);

    @Query("SELECT * FROM debts WHERE user_id = :userId AND type = :type AND status = 'ACTIVE' AND is_deleted = 0 ORDER BY updated_at DESC")
    List<DebtEntity> getOngoingDebtsByTypeSync(String userId, String type);

    @Query("SELECT * FROM debts WHERE user_id = :userId AND status = :status AND is_deleted = 0 ORDER BY updated_at DESC")
    LiveData<List<DebtEntity>> getDebtsByStatus(String userId, String status);

    @Query("SELECT * FROM debts WHERE id = :id AND is_deleted = 0")
    LiveData<DebtEntity> getDebtById(String id);

    @Query("SELECT * FROM debts WHERE id = :id AND is_deleted = 0 LIMIT 1")
    DebtEntity getByIdSync(String id);

    @Query("UPDATE debts SET remaining_amount = :remainingAmount, status = :status, " +
            "sync_status = 1, updated_at = :updatedAt WHERE id = :id")
    void updateRemainingAmount(String id, double remainingAmount, String status, long updatedAt);

    @Query("UPDATE debts SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
    void softDelete(String id, long updatedAt);

    @Query("SELECT * FROM debts WHERE user_id = :userId AND sync_status != 0")
    @Deprecated
    List<DebtEntity> getPendingSyncDebts(String userId);

    @Query("SELECT * FROM debts WHERE user_id = :userId AND sync_status != 0 " +
            "AND (updated_at > :lastSyncedAt OR (updated_at = :lastSyncedAt AND id > :lastSyncedId)) " +
            "ORDER BY updated_at ASC, id ASC LIMIT :limit OFFSET :offset")
    List<DebtEntity> getPendingSyncPagedSince(String userId,
            long lastSyncedAt,
            String lastSyncedId,
            int limit,
            int offset);

    @Query("UPDATE debts SET sync_status = 0 WHERE id = :id")
    void markSynced(String id);

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Query("DELETE FROM debts WHERE id = :id")
    void hardDeleteById(String id);

    @Query("UPDATE debts SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt " +
            "WHERE user_id = :userId AND is_deleted = 0")
    void softDeleteAllByUser(String userId, long updatedAt);
}