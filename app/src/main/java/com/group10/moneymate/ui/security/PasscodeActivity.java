package com.group10.moneymate.ui.security;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.group10.moneymate.R;

/**
 * Activity độc lập cho màn hình passcode.
 * Dùng khi:
 *   - App launch và cần xác thực passcode (VERIFY mode) trước khi vào HomeActivity
 *   - Tạo/đổi passcode từ Settings
 *
 * Extras:
 *   EXTRA_MODE             (int)     — SecurityViewModel.MODE_*
 *   EXTRA_FINISH_TO_HOME   (boolean) — true nếu sau verify sẽ mở HomeActivity
 */
public class PasscodeActivity extends AppCompatActivity {

    public static final String EXTRA_MODE           = "passcode_mode";
    public static final String EXTRA_FINISH_TO_HOME = "passcode_finish_to_home";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passcode);

        int mode         = getIntent().getIntExtra(EXTRA_MODE, SecurityViewModel.MODE_VERIFY);
        boolean toHome   = getIntent().getBooleanExtra(EXTRA_FINISH_TO_HOME, true);

        // Truyền args vào startDestination của nav_passcode
        if (savedInstanceState == null) {
            NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.nav_host_passcode);
            if (navHost != null) {
                NavController navController = navHost.getNavController();
                Bundle args = new Bundle();
                args.putInt("passcode_mode", mode);
                args.putBoolean("passcode_finish_to_home", toHome);
                navController.setGraph(R.navigation.nav_passcode, args);
            }
        }
    }
}