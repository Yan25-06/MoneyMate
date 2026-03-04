package com.group10.moneymate.di;

import android.app.Application;

/**
 * Custom Application class for MoneyMate.
 * Initializes DI container.
 */
public class MoneyMateApplication extends Application {

    public AppContainer appContainer;

    @Override
    public void onCreate() {
        super.onCreate();
        // Initialize dependency injection container
        appContainer = new AppContainer(this);
    }
}
