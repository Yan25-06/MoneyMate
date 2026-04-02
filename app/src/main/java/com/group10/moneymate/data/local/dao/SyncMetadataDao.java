package com.group10.moneymate.data.local.dao;

import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.group10.moneymate.data.local.entity.SyncMetadataEntity;

@Dao
public interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE user_id = :userId AND domain = :domain LIMIT 1")
    SyncMetadataEntity getByUserAndDomain(String userId, String domain);

    @RawQuery
    int upsertRaw(SupportSQLiteQuery query);

    default void upsert(String userId, String domain, long lastSyncedAt, String lastSyncedId) {
        String sql = "INSERT INTO sync_metadata (user_id, domain, last_synced_at, last_synced_id) "
                + "VALUES (?, ?, ?, ?) "
                + "ON CONFLICT(user_id, domain) DO UPDATE SET "
                + "last_synced_at = excluded.last_synced_at, "
                + "last_synced_id = excluded.last_synced_id";
        upsertRaw(new SimpleSQLiteQuery(sql, new Object[] {
                userId,
                domain,
                lastSyncedAt,
                lastSyncedId
        }));
    }

    default void upsert(SyncMetadataEntity entity) {
        upsert(entity.getUserId(), entity.getDomain(), entity.getLastSyncedAt(), entity.getLastSyncedId());
    }

    @Query("UPDATE sync_metadata SET last_synced_at = :lastSyncedAt, last_synced_id = :lastSyncedId " +
            "WHERE user_id = :userId AND domain = :domain")
    void updateCheckpoint(String userId, String domain, long lastSyncedAt, String lastSyncedId);
}

