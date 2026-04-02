package com.group10.moneymate.data.local.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Objects;

@Entity(
    tableName = "transactions",
    foreignKeys = {
        @ForeignKey(
            entity = WalletEntity.class,
            parentColumns = "id",
            childColumns = "wallet_id",
            onDelete = ForeignKey.CASCADE
        ),
        @ForeignKey(
            entity = CategoryEntity.class,
            parentColumns = "id",
            childColumns = "category_id",
            onDelete = ForeignKey.NO_ACTION
        ),
        @ForeignKey(
            entity = DebtEntity.class,
            parentColumns = "id",
            childColumns = "debt_id",
            onDelete = ForeignKey.SET_NULL
        ),
        @ForeignKey(
            entity = EventEntity.class,
            parentColumns = "id",
            childColumns = "event_id",
            onDelete = ForeignKey.SET_NULL
        ),
        @ForeignKey(
            entity = WalletEntity.class,
            parentColumns = "id",
            childColumns = "to_wallet_id",
            onDelete = ForeignKey.SET_NULL
        )
    },
    indices = {
        @Index("wallet_id"),
        @Index("category_id"),
        @Index("debt_id"),
        @Index("event_id"),
        @Index("to_wallet_id"),
        @Index("user_id"),
        @Index(name = "idx_transactions_wallet_deleted_type_timestamp", value = {"wallet_id", "is_deleted", "type", "timestamp"}),
        @Index(name = "index_transactions_user_deleted_timestamp_id", value = {"user_id", "is_deleted", "timestamp", "id"}),
        @Index(name = "idx_tx_user_sync_deleted_updated", value = {"user_id", "sync_status", "is_deleted", "updated_at"})
    }
)
public class TransactionEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @ColumnInfo(name = "wallet_id")
    private String walletId;

    @ColumnInfo(name = "category_id")
    private String categoryId;

    @Nullable
    @ColumnInfo(name = "debt_id")
    private String debtId;

    @Nullable
    @ColumnInfo(name = "event_id")
    private String eventId;

    @ColumnInfo(name = "amount")
    private double amount;

    @ColumnInfo(name = "type")
    private String type;

    @Nullable
    @ColumnInfo(name = "to_wallet_id")
    private String toWalletId;

    @ColumnInfo(name = "note")
    private String note;

    @ColumnInfo(name = "timestamp")
    private long timestamp;

    @Nullable
    @ColumnInfo(name = "image_path")
    private String imagePath;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    @ColumnInfo(name = "sync_status")
    private int syncStatus;

    @ColumnInfo(name = "is_deleted")
    private boolean isDeleted;

    // Denormalized for efficient user-scoped queries
    @ColumnInfo(name = "user_id")
    private String userId;

    public TransactionEntity() {
        this.id = "";
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getWalletId() { return walletId; }
    public void setWalletId(String walletId) { this.walletId = walletId; }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    @Nullable public String getDebtId() { return debtId; }
    public void setDebtId(@Nullable String debtId) { this.debtId = debtId; }

    @Nullable public String getEventId() { return eventId; }
    public void setEventId(@Nullable String eventId) { this.eventId = eventId; }

    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @Nullable public String getToWalletId() { return toWalletId; }
    public void setToWalletId(@Nullable String toWalletId) { this.toWalletId = toWalletId; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    @Nullable public String getImagePath() { return imagePath; }
    public void setImagePath(@Nullable String imagePath) { this.imagePath = imagePath; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getSyncStatus() { return syncStatus; }
    public void setSyncStatus(int syncStatus) { this.syncStatus = syncStatus; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TransactionEntity)) {
            return false;
        }
        TransactionEntity that = (TransactionEntity) o;
        return updatedAt == that.updatedAt && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, updatedAt);
    }
}