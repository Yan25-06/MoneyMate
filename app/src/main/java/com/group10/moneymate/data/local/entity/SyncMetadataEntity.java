package com.group10.moneymate.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;

@Entity(tableName = "sync_metadata", primaryKeys = {"user_id", "domain"})
public class SyncMetadataEntity {

    @NonNull
    @ColumnInfo(name = "user_id")
    private String userId;

    @NonNull
    @ColumnInfo(name = "domain")
    private String domain;

    @ColumnInfo(name = "last_synced_at")
    private long lastSyncedAt;

    @NonNull
    @ColumnInfo(name = "last_synced_id")
    private String lastSyncedId;

    public SyncMetadataEntity() {
        userId = "";
        domain = "";
        lastSyncedId = "";
    }

    @NonNull
    public String getUserId() {
        return userId;
    }

    public void setUserId(@NonNull String userId) {
        this.userId = userId;
    }

    @NonNull
    public String getDomain() {
        return domain;
    }

    public void setDomain(@NonNull String domain) {
        this.domain = domain;
    }

    public long getLastSyncedAt() {
        return lastSyncedAt;
    }

    public void setLastSyncedAt(long lastSyncedAt) {
        this.lastSyncedAt = lastSyncedAt;
    }

    @NonNull
    public String getLastSyncedId() {
        return lastSyncedId;
    }

    public void setLastSyncedId(@NonNull String lastSyncedId) {
        this.lastSyncedId = lastSyncedId;
    }
}

