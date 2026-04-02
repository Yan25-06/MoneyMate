package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import com.group10.moneymate.data.local.entity.EventEntity;

import java.util.List;

@Dao
public interface EventDao {
    @Query("INSERT INTO events (" +
            "id, user_id, name, budget_limit, start_date, end_date, is_active, " +
            "created_at, updated_at, sync_status, is_deleted" +
            ") VALUES (" +
            ":id, :userId, :name, :budgetLimit, :startDate, :endDate, :isActive, " +
            ":createdAt, :updatedAt, :syncStatus, :isDeleted" +
            ") ON CONFLICT(id) DO UPDATE SET " +
            "user_id = excluded.user_id, " +
            "name = excluded.name, " +
            "budget_limit = excluded.budget_limit, " +
            "start_date = excluded.start_date, " +
            "end_date = excluded.end_date, " +
            "is_active = excluded.is_active, " +
            "updated_at = excluded.updated_at, " +
            "sync_status = CASE WHEN events.sync_status = 2 THEN 2 ELSE excluded.sync_status END, " +
            "is_deleted = CASE WHEN events.is_deleted = 1 THEN 1 ELSE excluded.is_deleted END, " +
            "created_at = CASE " +
            "WHEN events.created_at IS NULL OR events.created_at <= 0 THEN excluded.created_at " +
            "ELSE events.created_at END")
    void upsertLocalRaw(String id,
                        String userId,
                        String name,
                        Double budgetLimit,
                        long startDate,
                        long endDate,
                        boolean isActive,
                        long createdAt,
                        long updatedAt,
                        int syncStatus,
                        boolean isDeleted);

    default void upsertLocal(EventEntity event) {
        upsertLocalRaw(
                event.getId(),
                event.getUserId(),
                event.getName(),
                event.getBudgetLimit(),
                event.getStartDate(),
                event.getEndDate(),
                event.isActive(),
                event.getCreatedAt(),
                event.getUpdatedAt(),
                event.getSyncStatus(),
                event.isDeleted()
        );
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
    List<EventEntity> getPendingSyncEvents(String userId);

    @Query("UPDATE events SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt " +
            "WHERE user_id = :userId AND is_deleted = 0")
    void softDeleteAllByUser(String userId, long updatedAt);
}