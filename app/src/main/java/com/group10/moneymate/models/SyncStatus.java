package com.group10.moneymate.models;

/**
 * Constants for sync_status column in offline-first entities.
 * 0 = SYNCED: Data is in sync with cloud
 * 1 = PENDING_UPLOAD: Local changes waiting to be uploaded
 * 2 = PENDING_DELETE: Marked for deletion on cloud
 */
public final class SyncStatus {
    public static final int SYNCED = 0;
    public static final int PENDING_UPLOAD = 1;
    public static final int PENDING_DELETE = 2;

    private SyncStatus() {}
}
