package com.group10.moneymate.ui.transaction;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.os.Bundle;

import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.group10.moneymate.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TransactionConfirmationFragmentWarningTest {

    @Test
    public void lowConfidenceItem_shouldShowWarningText() {
        FragmentScenario.launchInContainer(
                TransactionConfirmationFragment.class,
                buildArgs(
                        "[{\"name\":\"Ca phe sua\",\"amount\":\"25000\",\"category_hint\":\"Ăn uống\",\"confidence\":0}]",
                        0
                ),
                R.style.Theme_MoneyMate
        );

        onView(withText(R.string.transaction_scan_confirmation_warning_low_confidence))
                .check(matches(isDisplayed()));
    }

    @Test
    public void highConfidenceCompleteItem_shouldNotShowLowConfidenceWarningText() {
        FragmentScenario.launchInContainer(
                TransactionConfirmationFragment.class,
                buildArgs(
                        "[{\"name\":\"Ca phe sua\",\"amount\":\"25000\",\"category_hint\":\"Ăn uống\",\"confidence\":2}]",
                        2
                ),
                R.style.Theme_MoneyMate
        );

        onView(withText(R.string.transaction_scan_confirmation_warning_low_confidence))
                .check(doesNotExist());
    }

    private Bundle buildArgs(String itemsJson, int confidence) {
        Bundle args = new Bundle();
        args.putString("imagePath", null);
        args.putString("amount", "25000");
        args.putLong("timestamp", System.currentTimeMillis());
        args.putString("merchant", "HIGHLANDS COFFEE");
        args.putString("categoryHint", "Ăn uống");
        args.putString("itemsJson", itemsJson);
        args.putInt("confidence", confidence);
        return args;
    }
}
