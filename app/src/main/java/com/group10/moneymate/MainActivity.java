package com.group10.moneymate;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.ui.auth.LoginActivity;
import com.group10.moneymate.ui.main.HomeActivity;
import com.group10.moneymate.ui.security.PasscodeActivity;
import com.group10.moneymate.ui.security.SecurityViewModel;
import com.group10.moneymate.utils.AppLifecycleMonitor;

/**
 * Splash/Router Activity — khởi chạy khi user mở app.
 *
 * Routing:
 *   Chưa đăng nhập              → LoginActivity
 *   Đã đăng nhập + chưa có PIN  → PasscodeActivity(CREATE)  [bắt buộc tạo]
 *   Đã đăng nhập + có PIN       → PasscodeActivity(VERIFY)  [process mới hoặc timeout]
 *   (HomeActivity tự handle onResume timeout khi app đang chạy)
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppContainer appContainer = ((MoneyMateApplication) getApplication()).getAppContainer();
        AppLifecycleMonitor monitor = appContainer.appLifecycleMonitor;

        if (!appContainer.authRepository.isLoggedIn()) {
            // Chưa đăng nhập → LoginActivity
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));

        } else if (!appContainer.authRepository.isPasscodeEnabled()) {
            // Đã đăng nhập nhưng chưa có PIN → bắt buộc tạo
            Intent intent = new Intent(this, PasscodeActivity.class);
            intent.putExtra(PasscodeActivity.EXTRA_MODE, SecurityViewModel.MODE_CREATE);
            intent.putExtra(PasscodeActivity.EXTRA_FINISH_TO_HOME, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

        } else if (monitor.needsAuthentication()) {
            // Process mới HOẶC background timeout → xác nhận lại PIN
            Intent intent = new Intent(this, PasscodeActivity.class);
            intent.putExtra(PasscodeActivity.EXTRA_MODE, SecurityViewModel.MODE_VERIFY);
            intent.putExtra(PasscodeActivity.EXTRA_FINISH_TO_HOME, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);

        } else {
            // Phiên còn hiệu lực → vào thẳng Home
            startActivity(new Intent(this, HomeActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        }

        finish();
    }
}