package com.group10.moneymate.ui.security;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.group10.moneymate.R;

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

        // Truyền arguments vào PasscodeFragment qua defaultArgs của NavController
        // (Fragment tự đọc args từ intent extras trong onViewCreated)
    }

    /** Gọi từ PasscodeFragment khi xác thực thành công và cần chuyển sang HomeActivity. */
    public void navigateToHomeAndFinish() {
        Intent homeIntent = new Intent(this, com.group10.moneymate.ui.main.HomeActivity.class);
        homeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(homeIntent);
        finish();
    }
}
