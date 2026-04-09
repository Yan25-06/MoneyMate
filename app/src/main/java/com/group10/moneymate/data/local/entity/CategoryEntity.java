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
    tableName = "categories",
    foreignKeys = {
        @ForeignKey(
            entity = UserEntity.class,
            parentColumns = "id",
            childColumns = "user_id",
            onDelete = ForeignKey.CASCADE
        )
    },
    indices = {
        @Index(name = "index_categories_user_wallet_parent_type_deleted", value = {"user_id", "wallet_id", "parent_id", "type", "is_deleted"})
    }
)
public class CategoryEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @Nullable
    @ColumnInfo(name = "user_id")
    private String userId;

    @ColumnInfo(name = "name")
    private String name;

    @ColumnInfo(name = "type")
    private String type;

    @NonNull
    @ColumnInfo(name = "icon_name")
    private String iconName;

    @Nullable
    @ColumnInfo(name = "parent_id")
    private String parentId;

    @Nullable
    @ColumnInfo(name = "wallet_id")
    private String walletId;

    @ColumnInfo(name = "is_default")
    private boolean isDefault;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    @ColumnInfo(name = "sync_status")
    private int syncStatus;

    @ColumnInfo(name = "is_deleted")
    private boolean isDeleted;

    public CategoryEntity() {
        this.id = "";
        this.iconName = "ic_category_default";
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    @Nullable
    public String getUserId() { return userId; }
    public void setUserId(@Nullable String userId) { this.userId = userId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    @NonNull
    public String getIconName() { return iconName; }
    public void setIconName(@NonNull String iconName) { this.iconName = iconName; }

    @Nullable
    public String getParentId() { return parentId; }
    public void setParentId(@Nullable String parentId) { this.parentId = parentId; }

    @Nullable
    public String getWalletId() { return walletId; }
    public void setWalletId(@Nullable String walletId) { this.walletId = walletId; }

    public boolean isDefault() { return isDefault; }
    public void setDefault(boolean aDefault) { isDefault = aDefault; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    public int getSyncStatus() { return syncStatus; }
    public void setSyncStatus(int syncStatus) { this.syncStatus = syncStatus; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { isDeleted = deleted; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CategoryEntity)) {
            return false;
        }
        CategoryEntity that = (CategoryEntity) o;
        return updatedAt == that.updatedAt && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, updatedAt);
    }
}