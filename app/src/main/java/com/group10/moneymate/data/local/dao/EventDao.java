package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.annotation.RestrictTo;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.group10.moneymate.data.local.entity.EventEntity;

import java.util.List;

@Dao
public interface EventDao {
    @RawQuery
    int upsertLocalRaw(SupportSQLiteQuery query);

    default void upsertLocal(EventEntity event) {
        String sql = "INSERT INTO events ("
                + "id, user_id, name, budget_limit, start_date, end_date, is_active, "
                + "created_at, updated_at, sync_status, is_deleted"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET "
                + "user_id = excluded.user_id, "
                + "name = excluded.name, "
                + "budget_limit = excluded.budget_limit, "
                + "start_date = excluded.start_date, "
                + "end_date = excluded.end_date, "
                + "is_active = excluded.is_active, "
                + "updated_at = excluded.updated_at, "
                + "sync_status = CASE WHEN events.sync_status = 2 THEN 2 ELSE excluded.sync_status END, "
                + "is_deleted = CASE WHEN events.is_deleted = 1 THEN 1 ELSE excluded.is_deleted END, "
                + "created_at = CASE WHEN events.created_at IS NULL OR events.created_at <= 0 THEN excluded.created_at ELSE events.created_at END";
        upsertLocalRaw(new SimpleSQLiteQuery(sql, new Object[] {
                event.getId(),
                event.getUserId(),
                event.getName(),
                event.getBudgetLimit(),
                event.getStartDate(),
                event.getEndDate(),
                event.isActive() ? 1 : 0,
                event.getCreatedAt(),
                event.getUpdatedAt(),
                event.getSyncStatus(),
                event.isDeleted() ? 1 : 0
        }));
    }

    default void insertEvent(EventEntity event) {
        upsertLocal(event);
    }

    default void updateEvent(EventEntity event) {
        upsertLocal(event);
    }

    @Query("SELECT * FROM events WHERE user_id = :userId AND is_deleted = 0 ORDER BY start_date DESC")
    LiveData<List<EventEntity>> getAllEvents(String userId);

    @Query("SELECT * FROM events WHERE user_id = :userId AND is_active = 1 AND is_deleted = 0 ORDER BY start_date DESC")
    LiveData<List<EventEntity>> getActiveEvents(String userId);

    @Query("SELECT * FROM events WHERE id = :id AND is_deleted = 0")
    LiveData<EventEntity> getEventById(String id);

    @Query("UPDATE events SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
    void softDelete(String id, long updatedAt);

    @Query("SELECT * FROM events WHERE user_id = :userId AND sync_status != 0")
    @Deprecated
    List<EventEntity> getPendingSyncEvents(String userId);

    @Query("SELECT * FROM events WHERE user_id = :userId AND sync_status != 0 " +
            "AND (updated_at > :lastSyncedAt OR (updated_at = :lastSyncedAt AND id > :lastSyncedId)) " +
            "ORDER BY updated_at ASC, id ASC LIMIT :limit OFFSET :offset")
    List<EventEntity> getPendingSyncEventsPagedSince(String userId,
                                                     long lastSyncedAt,
                                                     String lastSyncedId,
                                                     int limit,
                                                     int offset);

    @Query("UPDATE events SET sync_status = 0 WHERE id = :id")
    void markSynced(String id);

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Query("DELETE FROM events WHERE id = :id")
    void hardDeleteById(String id);

    @Query("UPDATE events SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt " +
            "WHERE user_id = :userId AND is_deleted = 0")
    void softDeleteAllByUser(String userId, long updatedAt);
}