package com.group10.moneymate.ui.security;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.group10.moneymate.R;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;

/**
 * Activity độc lập chứa PasscodeFragment.
 *
 * Dùng FLAG_ACTIVITY_CLEAR_TASK khi mở để user không thể nhấn Back để thoát.
 *
 * Extras:
 *   EXTRA_MODE           (int)     — 0=CREATE, 1=CONFIRM, 2=VERIFY, 3=CHANGE
 *   EXTRA_FINISH_TO_HOME (boolean) — sau khi xong thì mở HomeActivity hay chỉ finish()
 */
public class PasscodeActivity extends AppCompatActivity {

    public static final String EXTRA_MODE           = "passcode_mode";
    public static final String EXTRA_FINISH_TO_HOME = "passcode_finish_to_home";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passcode);
    }

    /**
     * Gọi từ PasscodeFragment khi xác thực/tạo PIN thành công.
     * Đánh dấu session đã xác thực rồi chuyển sang HomeActivity.
     */
    public void onPasscodeSuccess() {
        // Đánh dấu session đã xác thực → reset timeout flag
        AppContainer container = ((MoneyMateApplication) getApplication()).getAppContainer();
        container.appLifecycleMonitor.markAuthenticated();

        boolean finishToHome = getIntent().getBooleanExtra(EXTRA_FINISH_TO_HOME, false);
        if (finishToHome) {
            navigateToHomeAndFinish();
        } else {
            finish();
        }
    }

    /** Chuyển sang HomeActivity và clear back stack. */
    public void navigateToHomeAndFinish() {
        Intent homeIntent = new Intent(this, com.group10.moneymate.ui.main.HomeActivity.class);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(homeIntent);
        finish();
    }
}
