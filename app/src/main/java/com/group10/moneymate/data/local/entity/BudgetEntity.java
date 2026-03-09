package com.group10.moneymate.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
    tableName = "budgets",
    foreignKeys = {
        @ForeignKey(
            entity = UserEntity.class,
            parentColumns = "id",
            childColumns = "user_id",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = CategoryEntity.class,
            parentColumns = "id",
            childColumns = "category_id",
            onDelete = ForeignKey.SET_NULL
        )
    },
    indices = {
        @Index("user_id"),
        @Index("category_id")
    }
)
public class BudgetEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @ColumnInfo(name = "user_id")
    private String userId;

    @Nullable
    @ColumnInfo(name = "category_id")
    private String categoryId;

    @ColumnInfo(name = "limit_amount")
    private double limitAmount;

    @ColumnInfo(name = "alert_threshold")
    private float alertThreshold;

    @ColumnInfo(name = "month")
    private int month;

    @ColumnInfo(name = "year")
    private int year;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    @ColumnInfo(name = "sync_status")
    private int syncStatus;

    @ColumnInfo(name = "is_deleted")
    private boolean isDeleted;

    public BudgetEntity() {
        this.id = "";
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @Nullable public String getCategoryId() { return categoryId; }
    public void setCategoryId(@Nullable String categoryId) { this.categoryId = categoryId; }

    public double getLimitAmount() { return limitAmount; }
    public void setLimitAmount(double limitAmount) { this.limitAmount = limitAmount; }

    public float getAlertThreshold() { return alertThreshold; }
    public void setAlertThreshold(float alertThreshold) { this.alertThreshold = alertThreshold; }

    public int getMonth() { return month; }
    public void setMonth(int month) { this.month = month; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getSyncStatus() { return syncStatus; }
    public void setSyncStatus(int syncStatus) { this.syncStatus = syncStatus; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }
}