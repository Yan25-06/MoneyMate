package com.group10.moneymate;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.ui.auth.LoginActivity;
import com.group10.moneymate.ui.main.HomeActivity;

/**
 * Splash/Router Activity.
 * Checks authentication state and routes to LoginActivity or HomeActivity.
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppContainer appContainer = ((MoneyMateApplication) getApplication()).appContainer;

        if (appContainer.authRepository.isLoggedIn()) {
            // User is authenticated via Firebase
            startActivity(new Intent(this, HomeActivity.class));
        } else {
            // No auth state - go to login
            startActivity(new Intent(this, LoginActivity.class));
        }

        finish();
    }
}