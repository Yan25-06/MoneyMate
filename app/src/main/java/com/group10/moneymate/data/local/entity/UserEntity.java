package com.group10.moneymate.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class UserEntity {

    @PrimaryKey
    @NonNull
    @ColumnInfo(name = "id")
    private String id;

    @ColumnInfo(name = "email")
    private String email;

    @ColumnInfo(name = "display_name")
    private String displayName;

    @ColumnInfo(name = "hashed_passcode")
    private String hashedPasscode;

    @ColumnInfo(name = "currency")
    private String currency;

    @ColumnInfo(name = "theme_mode")
    private String themeMode;

    @ColumnInfo(name = "language")
    private String language;

    @ColumnInfo(name = "is_balance_hidden")
    private boolean isBalanceHidden;

    @ColumnInfo(name = "last_sync")
    private long lastSync;

    @ColumnInfo(name = "avatar_url")
    private String avatarUrl;

    @ColumnInfo(name = "created_at")
    private long createdAt;

    @ColumnInfo(name = "updated_at")
    private long updatedAt;

    public UserEntity() {
        this.id = "";
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getHashedPasscode() { return hashedPasscode; }
    public void setHashedPasscode(String hashedPasscode) { this.hashedPasscode = hashedPasscode; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getThemeMode() { return themeMode; }
    public void setThemeMode(String themeMode) { this.themeMode = themeMode; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public boolean isBalanceHidden() { return isBalanceHidden; }
    public void setBalanceHidden(boolean balanceHidden) { isBalanceHidden = balanceHidden; }

    public long getLastSync() { return lastSync; }
    public void setLastSync(long lastSync) { this.lastSync = lastSync; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }
}
