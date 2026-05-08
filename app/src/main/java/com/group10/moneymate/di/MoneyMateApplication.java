package com.group10.moneymate.di;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import com.group10.moneymate.utils.ForegroundUiNotifier;
import com.group10.moneymate.utils.NotificationHelper;
import com.group10.moneymate.workers.NotificationScheduler;

/**
 * Custom Application class for MoneyMate.
 * Initializes DI container.
 */
public class MoneyMateApplication extends Application implements Configuration.Provider {

    private AppContainer appContainer;
    private MoneyMateWorkerFactory workerFactory;

    @Override
    public void onCreate() {
        super.onCreate();
        ensureContainerInitialized();
        ForegroundUiNotifier.init(this);
        appContainer.bootstrapLocalData();
        appContainer.syncScheduler.ensurePeriodicSync();

        // Đăng ký lifecycle monitor để theo dõi thời gian nền (passcode timeout)
        registerActivityLifecycleCallbacks(appContainer.appLifecycleMonitor);

        // Khởi tạo kênh thông báo và lên lịch alarm
        NotificationHelper.createNotificationChannels(this);
        NotificationScheduler.scheduleAll(this);
    }

    public AppContainer getAppContainer() {
        return appContainer;
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        ensureContainerInitialized();
        return new Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .setMinimumLoggingLevel(Log.INFO)
                .build();
    }

    private void ensureContainerInitialized() {
        if (appContainer == null) {
            appContainer = new AppContainer(this);
        }
        if (workerFactory == null) {
            workerFactory = new MoneyMateWorkerFactory(appContainer);
        }
    }
}
