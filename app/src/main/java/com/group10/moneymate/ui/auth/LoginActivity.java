package com.group10.moneymate.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.group10.moneymate.R;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.ui.main.HomeActivity;
import com.group10.moneymate.ui.security.PasscodeActivity;

/**
 * Activity hosting the authentication flow (login, register, passcode setup).
 *
 * Routing logic khi start:
 *  1. Đã đăng nhập Firebase + passcode được bật → PasscodeActivity (VERIFY mode)
 *  2. Đã đăng nhập Firebase + không có passcode   → HomeActivity
 *  3. Chưa đăng nhập                              → LoginFragment (nav_auth)
 */
public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
    }

    @Override
    protected void onStart() {
        super.onStart();
        redirectIfNeeded();
    }

    private void redirectIfNeeded() {
        AppContainer container = ((MoneyMateApplication) getApplication()).getAppContainer();

        if (!container.authRepository.isLoggedIn()) {
            // Chưa đăng nhập → ở lại LoginFragment
            return;
        }

        if (container.authRepository.isPasscodeEnabled()) {
            // Đã đăng nhập + có passcode → xác thực passcode trước
            openPasscodeVerify();
        } else {
            // Đã đăng nhập + không có passcode → thẳng vào app
            openHomeActivity();
        }
    }

    private void openPasscodeVerify() {
        Intent intent = new Intent(this, PasscodeActivity.class);
        intent.putExtra(PasscodeActivity.EXTRA_MODE, com.group10.moneymate.ui.security.SecurityViewModel.MODE_VERIFY);
        intent.putExtra(PasscodeActivity.EXTRA_FINISH_TO_HOME, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openHomeActivity() {
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}