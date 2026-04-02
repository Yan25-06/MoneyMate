package com.group10.moneymate.data.repository;

import androidx.lifecycle.LiveData;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.EventDao;
import com.group10.moneymate.data.local.entity.EventEntity;
import com.group10.moneymate.models.SyncStatus;

import java.util.List;
import java.util.UUID;

public class EventRepository {
    private final EventDao eventDao;

    public EventRepository(EventDao eventDao) {
        this.eventDao = eventDao;
    }

    public LiveData<List<EventEntity>> getAllEvents(String userId) {
        return eventDao.getAllEvents(userId);
    }

    public LiveData<List<EventEntity>> getActiveEvents(String userId) {
        return eventDao.getActiveEvents(userId);
    }

    public LiveData<EventEntity> getEventById(String id) {
        return eventDao.getEventById(id);
    }

    public void insert(EventEntity event) {
        upsertEventInternal(event);
    }

    public void update(EventEntity event) {
        upsertEventInternal(event);
    }

    private void upsertEventInternal(EventEntity event) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            if (event.getId() == null || event.getId().trim().isEmpty()) {
                event.setId(UUID.randomUUID().toString());
            }
            if (event.getCreatedAt() <= 0L) {
                event.setCreatedAt(now);
            }
            event.setUpdatedAt(now);
            event.setSyncStatus(SyncStatus.PENDING_UPLOAD);
            eventDao.upsertLocal(event);
        });
    }

    public void softDelete(String id) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                eventDao.softDelete(id, System.currentTimeMillis()));
    }

    public List<EventEntity> getPendingSyncPagedSince(@NonNull String userId,
                                                      long lastSyncedAt,
                                                      @NonNull String lastSyncedId,
                                                      int limit,
                                                      int offset) {
        return eventDao.getPendingSyncEventsPagedSince(userId, lastSyncedAt, lastSyncedId, limit, offset);
    }

    public void markSynced(@NonNull String id) {
        eventDao.markSynced(id);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public void hardDeleteById(@NonNull String id) {
        eventDao.hardDeleteById(id);
    }
}