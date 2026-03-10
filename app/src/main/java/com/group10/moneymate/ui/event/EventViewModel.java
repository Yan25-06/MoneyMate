package com.group10.moneymate.ui.event;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.group10.moneymate.data.local.entity.EventEntity;
import com.group10.moneymate.data.repository.EventRepository;
import java.util.List;

public class EventViewModel extends ViewModel {
    private final EventRepository eventRepository;

    public EventViewModel(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public LiveData<List<EventEntity>> getAllEvents(String userId) {
        return eventRepository.getAllEvents(userId);
    }

    public LiveData<List<EventEntity>> getActiveEvents(String userId) {
        return eventRepository.getActiveEvents(userId);
    }

    public void insert(EventEntity event) { eventRepository.insert(event); }
    public void update(EventEntity event) { eventRepository.update(event); }
    public void softDelete(String id) { eventRepository.softDelete(id); }
}