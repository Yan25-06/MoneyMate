package com.group10.moneymate.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.group10.moneymate.data.local.entity.SyncMetadataEntity;

@Dao
public interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE user_id = :userId AND domain = :domain LIMIT 1")
    SyncMetadataEntity getByUserAndDomain(String userId, String domain);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsert(SyncMetadataEntity entity);

    @Query("UPDATE sync_metadata SET last_synced_at = :lastSyncedAt, last_synced_id = :lastSyncedId " +
            "WHERE user_id = :userId AND domain = :domain")
    void updateCheckpoint(String userId, String domain, long lastSyncedAt, String lastSyncedId);
}

