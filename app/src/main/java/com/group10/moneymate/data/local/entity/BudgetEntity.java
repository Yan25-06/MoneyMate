package com.group10.moneymate.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.group10.moneymate.models.SyncStatus;

import java.util.UUID;

@Entity(
    tableName = "budgets",
    foreignKeys = @ForeignKey(
        entity = CategoryEntity.class,
        parentColumns = "id",
        childColumns = "category_id",
        onDelete = ForeignKey.NO_ACTION
    ),
    indices = {@Index("category_id")}
)
public class BudgetEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @NonNull
    @ColumnInfo(name = "category_id")
    private String categoryId;

    @ColumnInfo(name = "amount")
    private double amount;

    @ColumnInfo(name = "start_date")
    private long startDate;

    @ColumnInfo(name = "end_date")
    private long endDate;

    @Nullable
    @ColumnInfo(name = "wallet_id")
    private String walletId;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    @ColumnInfo(name = "is_deleted")
    private boolean isDeleted;

    @ColumnInfo(name = "sync_status")
    private int syncStatus;

    public BudgetEntity() {
        this.id = UUID.randomUUID().toString();
        this.categoryId = "";
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
        this.isDeleted = false;
        this.syncStatus = SyncStatus.PENDING_UPLOAD;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(@NonNull String categoryId) {
        this.categoryId = categoryId;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public long getStartDate() {
        return startDate;
    }

    public void setStartDate(long startDate) {
        this.startDate = startDate;
    }

    public long getEndDate() {
        return endDate;
    }

    public void setEndDate(long endDate) {
        this.endDate = endDate;
    }

    @Nullable
    public String getWalletId() {
        return walletId;
    }

    public void setWalletId(@Nullable String walletId) {
        this.walletId = walletId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
    }

    public int getSyncStatus() {
        return syncStatus;
    }

    public void setSyncStatus(int syncStatus) {
        this.syncStatus = syncStatus;
    }
}
