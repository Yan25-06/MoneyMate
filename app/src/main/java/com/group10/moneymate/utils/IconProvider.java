package com.group10.moneymate.utils;

import android.content.Context;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.group10.moneymate.R;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Central resolver for icon names and deterministic accent colors.
 */
public final class IconProvider {

    private static final Pattern ICON_NAME_PATTERN =
            Pattern.compile("^ic_[a-z0-9]+_[a-z0-9_]+$");

    private static final Map<String, Integer> DRAWABLES_BY_NAME = createDrawableMap();
    @ColorInt
    public static int getCategoryColor(@NonNull Context context,
                                       @Nullable String categoryId,
                                       boolean isExpense) {
        int[] palette = context.getResources().getIntArray(
                isExpense ? R.array.expense_colors : R.array.income_colors
        );
        if (palette.length == 0) {
            return ContextCompat.getColor(context,
                    isExpense ? R.color.expense_red : R.color.transfer_blue);
        }
        String key = categoryId == null ? "" : categoryId;
        int index = Math.abs(key.hashCode()) % palette.length;
        return palette[index];
    }


    private IconProvider() {
    }

    public static int resolveCategoryIcon(@NonNull Context context, @Nullable String iconName) {
        int resolved = resolveDrawableByName(context, iconName);
        return resolved != 0 ? resolved : R.drawable.ic_category_default;
    }

    public static int resolveCategoryIconByType(@NonNull Context context,
                                                @Nullable String iconName,
                                                @Nullable String transactionType) {
        int resolved = resolveDrawableByName(context, iconName);
        if (resolved != 0) {
            return resolved;
        }
        if ("INCOME".equals(transactionType)) {
            return R.drawable.ic_category_salary;
        }
        if ("TRANSFER".equals(transactionType)) {
            return R.drawable.outline_payments_24;
        }
        return R.drawable.ic_category_spending;
    }

    public static int resolveWalletIcon(@NonNull Context context,
                                        @Nullable String iconName,
                                        @Nullable String walletType) {
        int resolved = resolveDrawableByName(context, iconName);
        if (resolved != 0) {
            return resolved;
        }
        if ("BANK".equals(walletType)) {
            return R.drawable.outline_account_balance_24;
        }
        if ("E_WALLET".equals(walletType)) {
            return R.drawable.outline_credit_card_24;
        }
        return R.drawable.ic_wallet_default;
    }

    public static boolean isCompliantIconName(@Nullable String iconName) {
        return iconName != null && ICON_NAME_PATTERN.matcher(iconName).matches();
    }

    private static int resolveDrawableByName(@NonNull Context context, @Nullable String iconName) {
        if (iconName == null) {
            return 0;
        }
        String normalized = iconName.trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        Integer resId = DRAWABLES_BY_NAME.get(normalized);
        if (resId != null) {
            return resId;
        }
        if (!ICON_NAME_PATTERN.matcher(normalized).matches()) {
            return 0;
        }
        return context.getResources().getIdentifier(
                normalized,
                "drawable",
                context.getPackageName()
        );
    }


    @NonNull
    private static Map<String, Integer> createDrawableMap() {
        Map<String, Integer> map = new HashMap<>();
        map.put("ic_category_default", R.drawable.ic_category_default);
        map.put("ic_category_other", R.drawable.ic_category_other);
        map.put("ic_category_other_in", R.drawable.ic_category_other_in);
        map.put("ic_category_food", R.drawable.ic_category_food);
        map.put("ic_category_transport", R.drawable.ic_category_transport);
        map.put("ic_category_shopping", R.drawable.ic_category_shopping);
        map.put("ic_category_entertain", R.drawable.ic_category_entertain);
        map.put("ic_category_health", R.drawable.ic_category_health);
        map.put("ic_category_education", R.drawable.ic_category_education);
        map.put("ic_category_bill", R.drawable.ic_category_bill);
        map.put("ic_category_house", R.drawable.ic_category_house);
        map.put("ic_category_travel", R.drawable.ic_category_travel);
        map.put("ic_category_spending", R.drawable.ic_category_spending);
        map.put("ic_category_salary", R.drawable.ic_category_salary);
        map.put("ic_category_bonus", R.drawable.ic_category_bonus);
        map.put("ic_category_invest", R.drawable.ic_category_invest);
        map.put("ic_category_sale", R.drawable.ic_category_sale);
        map.put("ic_category_gift", R.drawable.ic_category_gift);
        map.put("ic_wallet_default", R.drawable.ic_wallet_default);
        map.put("ic_wallet_cash", R.drawable.ic_wallet_cash);
        map.put("ic_wallet_bank", R.drawable.ic_wallet_bank);
        map.put("ic_wallet_ewallet", R.drawable.ic_wallet_ewallet);
        map.put("ic_wallet_home", R.drawable.ic_wallet_home);
        map.put("ic_wallet_receipt", R.drawable.ic_wallet_receipt);
        map.put("outline_attach_money_24", R.drawable.outline_attach_money_24);
        map.put("outline_payments_24", R.drawable.outline_payments_24);
        map.put("outline_account_balance_24", R.drawable.outline_account_balance_24);
        map.put("outline_credit_card_24", R.drawable.outline_credit_card_24);
        map.put("outline_home_24", R.drawable.outline_home_24);
        map.put("outline_receipt_24", R.drawable.outline_receipt_24);
        map.put("outline_account_balance_wallet_24", R.drawable.outline_account_balance_wallet_24);
        map.put("outline_calendar_today_24", R.drawable.outline_calendar_today_24);
        map.put("ic_category_transfer_out", R.drawable.outline_payment_arrow_up_24);
        map.put("ic_category_transfer_in", R.drawable.outline_payment_arrow_down_24);
        // Debt categories (reuse payment arrows: up = money out, down = money in)
        map.put("ic_category_debt_lend",       R.drawable.outline_payment_arrow_up_24);
        map.put("ic_category_debt_repayment",  R.drawable.outline_payment_arrow_up_24);
        map.put("ic_category_debt_borrow",     R.drawable.outline_payment_arrow_down_24);
        map.put("ic_category_debt_collection", R.drawable.outline_payment_arrow_down_24);
        return Collections.unmodifiableMap(map);
    }
}
