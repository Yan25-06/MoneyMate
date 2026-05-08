package com.group10.moneymate.ui.auth;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.group10.moneymate.R;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.ui.main.HomeActivity;
import com.group10.moneymate.ui.security.PasscodeActivity;
import com.group10.moneymate.ui.security.SecurityViewModel;

/**
 * Activity hosting the authentication flow (login, register).
 *
 * Routing logic khi đã logged in:
 *  1. Có passcode  → PasscodeActivity (VERIFY mode)
 *  2. Chưa có passcode → PasscodeActivity (CREATE mode) — bắt buộc tạo
 *  3. Chưa đăng nhập → LoginFragment (nav_auth)
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
            return; // Ở lại LoginFragment
        }

        if (container.authRepository.isPasscodeEnabled()) {
            openPasscodeVerify();
        } else {
            // Chưa có PIN → bắt buộc tạo trước khi vào app
            openPasscodeCreate();
        }
    }

    private void openPasscodeVerify() {
        Intent intent = new Intent(this, PasscodeActivity.class);
        intent.putExtra(PasscodeActivity.EXTRA_MODE, SecurityViewModel.MODE_VERIFY);
        intent.putExtra(PasscodeActivity.EXTRA_FINISH_TO_HOME, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void openPasscodeCreate() {
        Intent intent = new Intent(this, PasscodeActivity.class);
        intent.putExtra(PasscodeActivity.EXTRA_MODE, SecurityViewModel.MODE_CREATE);
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