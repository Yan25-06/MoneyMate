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
        appContainer = new AppContainer(this);
        appContainer.bootstrapLocalData();
    }

    public AppContainer getAppContainer() {
        return appContainer;
    }
}
