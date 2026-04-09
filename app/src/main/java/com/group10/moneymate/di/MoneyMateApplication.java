package com.group10.moneymate.di;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Configuration;

import com.group10.moneymate.utils.ForegroundUiNotifier;

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
