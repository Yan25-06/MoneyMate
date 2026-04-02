package com.group10.moneymate.ui.common;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

public abstract class DebounceableViewModel extends ViewModel {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingRunnable;

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

