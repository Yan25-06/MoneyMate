package com.group10.moneymate.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.group10.moneymate.models.SyncStatus;

import java.util.Objects;
import java.util.UUID;

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
            onDelete = ForeignKey.NO_ACTION
        )
    },
    indices = {
        @Index("user_id"),
        @Index("category_id"),
        @Index(value = {"user_id", "wallet_id", "start_date", "end_date", "category_id"}, unique = true),
        @Index(name = "idx_budget_user_sync_deleted_updated", value = {"user_id", "sync_status", "is_deleted", "updated_at"})
    }
)
public class BudgetEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @ColumnInfo(name = "category_id")
    private String categoryId;

    @Nullable
    @ColumnInfo(name = "user_id")
    private String userId;

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
        this.categoryId = null;
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

    @Nullable
    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(@Nullable String categoryId) {
        this.categoryId = categoryId;
    }

    @Nullable
    public String getUserId() {
        return userId;
    }

    public void setUserId(@Nullable String userId) {
        this.userId = userId;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BudgetEntity)) {
            return false;
        }
        BudgetEntity that = (BudgetEntity) o;
        return updatedAt == that.updatedAt && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, updatedAt);
    }
}
