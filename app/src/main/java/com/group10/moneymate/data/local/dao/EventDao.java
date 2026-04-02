package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.group10.moneymate.data.local.entity.EventEntity;

import java.util.List;

@Dao
public interface EventDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertEvent(EventEntity event);

    @Update
    void updateEvent(EventEntity event);

    @Delete
    void deleteEvent(EventEntity event);

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

    @Query("DELETE FROM events WHERE user_id = :userId")
    void deleteAllByUser(String userId);
}