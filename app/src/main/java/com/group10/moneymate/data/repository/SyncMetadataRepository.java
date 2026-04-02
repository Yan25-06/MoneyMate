package com.group10.moneymate.data.repository;

import androidx.annotation.NonNull;

import com.group10.moneymate.data.local.dao.SyncMetadataDao;
import com.group10.moneymate.data.local.entity.SyncMetadataEntity;

public class SyncMetadataRepository {

    private final SyncMetadataDao syncMetadataDao;

    public SyncMetadataRepository(@NonNull SyncMetadataDao syncMetadataDao) {
        this.syncMetadataDao = syncMetadataDao;
    }

    @NonNull
    public SyncMetadataEntity getOrCreateCheckpoint(@NonNull String userId, @NonNull String domain) {
        SyncMetadataEntity existing = syncMetadataDao.getByUserAndDomain(userId, domain);
        if (existing != null) {
            return existing;
        }

        SyncMetadataEntity created = new SyncMetadataEntity();
        created.setUserId(userId);
        created.setDomain(domain);
        created.setLastSyncedAt(0L);
        created.setLastSyncedId("");
        syncMetadataDao.upsert(created);
        return created;
    }

    public void updateCheckpoint(@NonNull String userId,
                                 @NonNull String domain,
                                 long lastSyncedAt,
                                 @NonNull String lastSyncedId) {
        syncMetadataDao.updateCheckpoint(userId, domain, lastSyncedAt, lastSyncedId);
    }
}

