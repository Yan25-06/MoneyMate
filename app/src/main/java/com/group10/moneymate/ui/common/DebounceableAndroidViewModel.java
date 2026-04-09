package com.group10.moneymate.ui.common;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;

public abstract class DebounceableAndroidViewModel extends AndroidViewModel {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingRunnable;

    public DebounceableAndroidViewModel(@NonNull Application application) {
        super(application);
    }

    protected void debounce(@NonNull Runnable action, long delayMs) {
        if (pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable);
        }
        pendingRunnable = action;
        handler.postDelayed(action, delayMs);
    }

    @Override
    protected void onCleared() {
        if (pendingRunnable != null) {
            handler.removeCallbacks(pendingRunnable);
        }
        super.onCleared();
    }
}

