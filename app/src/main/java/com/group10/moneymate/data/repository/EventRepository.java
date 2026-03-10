package com.group10.moneymate.data.repository;

import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.EventDao;
import com.group10.moneymate.data.local.entity.EventEntity;

import java.util.List;

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
        AppDatabase.databaseWriteExecutor.execute(() -> eventDao.insertEvent(event));
    }

    public void update(EventEntity event) {
        AppDatabase.databaseWriteExecutor.execute(() -> eventDao.updateEvent(event));
    }

    public void softDelete(String id) {
        AppDatabase.databaseWriteExecutor.execute(() ->
                eventDao.softDelete(id, System.currentTimeMillis()));
    }
}