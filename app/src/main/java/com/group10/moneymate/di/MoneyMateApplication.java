package com.group10.moneymate.di;

import android.app.Application;

/**
 * Custom Application class for MoneyMate.
 * Initializes DI container.
 */
public class MoneyMateApplication extends Application {

    private AppContainer appContainer;

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize dependency injection container
        appContainer = new AppContainer(this);
        // Recreate missing local user rows after destructive Room migrations while
        // keeping the current auth session, to avoid foreign-key crashes on writes.
        appContainer.authRepository.ensureLocalUserRecord();
        // Default categories are global/shared and should exist regardless of auth flow.
        appContainer.seedDefaultCategoriesIfNeeded();
        appContainer.ensureVirtualBudgetCategoriesIfNeeded();
    }

    public AppContainer getAppContainer() {
        return appContainer;
    }
}
