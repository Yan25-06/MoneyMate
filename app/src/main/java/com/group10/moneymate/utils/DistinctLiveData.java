package com.group10.moneymate.utils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import java.util.Objects;

public class DistinctLiveData<T> extends MediatorLiveData<T> {

    private T lastValue;

    public DistinctLiveData(LiveData<T> source) {
        addSource(source, newValue -> {
            if (!Objects.equals(lastValue, newValue)) {
                lastValue = newValue;
                setValue(newValue);
            }
        });
    }

    public static <T> LiveData<T> distinctUntilChanged(LiveData<T> source) {
        return new DistinctLiveData<>(source);
    }
}

