package com.group10.moneymate.utils;

import android.content.Context;
import android.content.res.ColorStateList;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;

public final class WalletSelectorButtonHelper {

    private WalletSelectorButtonHelper() {
    }

    public static void bindStatisticsWalletSelector(@NonNull MaterialButton button,
                                                    @NonNull Context context,
                                                    @Nullable WalletEntity wallet,
                                                    @Nullable CharSequence label,
                                                    @StringRes int defaultLabelRes) {
        CharSequence fallbackLabel = !TextUtils.isEmpty(label)
                ? label
                : context.getString(defaultLabelRes);
        if (wallet == null) {
            button.setText(fallbackLabel);
            button.setIconResource(R.drawable.outline_account_balance_wallet_24);
            button.setIconTint(ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.statistics_wallet_icon)
            ));
            return;
        }

        // Always prefer live wallet name so UI follows wallet rename in real-time.
        button.setText(wallet.getName());
        button.setIconResource(IconProvider.resolveWalletIcon(
                context,
                wallet.getIconName(),
                wallet.getType()
        ));
        button.setIconTint(null);
    }
}
