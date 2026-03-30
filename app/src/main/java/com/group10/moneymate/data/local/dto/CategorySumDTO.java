package com.group10.moneymate.data.local.dto;

public class CategorySumDTO {

    private String categoryId;
    private String categoryName;
    private String iconName;
    private boolean categoryDeleted;
    private double totalAmount;
    private int transactionCount;

    public CategorySumDTO() {
        // Required for Room/SQLite cursor mapping.
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getIconName() {
        return iconName;
    }

    public void setIconName(String iconName) {
        this.iconName = iconName;
    }

    public boolean isCategoryDeleted() {
        return categoryDeleted;
    }

    public void setCategoryDeleted(boolean categoryDeleted) {
        this.categoryDeleted = categoryDeleted;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public int getTransactionCount() {
        return transactionCount;
    }

    public void setTransactionCount(int transactionCount) {
        this.transactionCount = transactionCount;
    }
}
