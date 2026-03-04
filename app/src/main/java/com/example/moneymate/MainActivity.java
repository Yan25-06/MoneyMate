package com.example.moneymate;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.moneymate.di.AppContainer;
import com.example.moneymate.di.MoneyMateApplication;
import com.example.moneymate.ui.auth.LoginActivity;
import com.example.moneymate.ui.main.HomeActivity;

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