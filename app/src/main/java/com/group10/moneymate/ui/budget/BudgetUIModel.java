package com.group10.moneymate.ui.budget;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.group10.moneymate.data.local.entity.BudgetEntity;

public class BudgetUIModel {

    private final BudgetEntity budgetEntity;
    private final String categoryName;
    private final String categoryIcon;
    private final double spentAmount;
    private final String categoryColorHex;
    private final String walletName;
    private final boolean active;

    public BudgetUIModel(@NonNull BudgetEntity budgetEntity,
                         @Nullable String categoryName,
                         @Nullable String categoryIcon,
                         double spentAmount,
                         @Nullable String categoryColorHex,
                         @Nullable String walletName,
                         boolean active) {
        this.budgetEntity = budgetEntity;
        this.categoryName = categoryName != null ? categoryName : "Danh mục";
        this.categoryIcon = categoryIcon != null ? categoryIcon : "";
        this.spentAmount = spentAmount;
        this.categoryColorHex = categoryColorHex != null ? categoryColorHex : "";
        this.walletName = walletName != null ? walletName : "";
        this.active = active;
    }

    @NonNull
    public BudgetEntity getBudgetEntity() {
        return budgetEntity;
    }

    @NonNull
    public String getCategoryName() {
        return categoryName;
    }

    @NonNull
    public String getCategoryIcon() {
        return categoryIcon;
    }

    public double getSpentAmount() {
        return spentAmount;
    }

    @NonNull
    public String getCategoryColorHex() {
        return categoryColorHex;
    }

    @NonNull
    public String getWalletName() {
        return walletName;
    }

    public boolean isActive() {
        return active;
    }

    public double getRemainingAmount() {
        return budgetEntity.getAmount() - spentAmount;
    }

    public float getPercent() {
        if (budgetEntity.getAmount() <= 0d) {
            return 0f;
        }
        return (float) ((spentAmount / budgetEntity.getAmount()) * 100f);
    }

    public boolean isOverspent() {
        return getRemainingAmount() < 0d;
    }
}
