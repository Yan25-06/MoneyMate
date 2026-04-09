package com.group10.moneymate.utils;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.snackbar.Snackbar;
import com.group10.moneymate.R;
import com.group10.moneymate.workers.SyncScheduler;

import java.lang.ref.WeakReference;

public final class ForegroundUiNotifier implements Application.ActivityLifecycleCallbacks {

    private static ForegroundUiNotifier instance;

    @Nullable
    private WeakReference<Activity> resumedActivityRef;

    private ForegroundUiNotifier() {
        // Singleton.
    }

    public static synchronized void init(@NonNull Application application) {
        if (instance != null) {
            return;
        }
        instance = new ForegroundUiNotifier();
        application.registerActivityLifecycleCallbacks(instance);
    }

    public static boolean isAppForeground() {
        Activity activity = getResumedActivity();
        return activity != null && !activity.isFinishing();
    }

    public static void showSyncFailedSnackbar() {
        Activity activity = getResumedActivity();
        if (activity == null) {
            return;
        }
        activity.runOnUiThread(() -> {
            View root = activity.findViewById(android.R.id.content);
            if (root == null) {
                return;
            }
            Snackbar.make(root, R.string.sync_failed_message, Snackbar.LENGTH_LONG)
                    .setAction(R.string.sync_retry_action,
                            v -> SyncScheduler.enqueueManualRetryNow(
                                    activity.getApplicationContext()
                            ))
                    .show();
        });
    }

    @Nullable
    private static Activity getResumedActivity() {
        if (instance == null || instance.resumedActivityRef == null) {
            return null;
        }
        return instance.resumedActivityRef.get();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        // No-op.
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        // No-op.
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        resumedActivityRef = new WeakReference<>(activity);
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        Activity resumedActivity = getResumedActivity();
        if (resumedActivity == activity) {
            resumedActivityRef = null;
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        // No-op.
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        // No-op.
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        Activity resumedActivity = getResumedActivity();
        if (resumedActivity == activity) {
            resumedActivityRef = null;
        }
    }
}

