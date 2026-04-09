package com.group10.moneymate.data.local.migrations;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public final class Migration10To11 {

    public static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `sync_metadata` ("
                    + "`user_id` TEXT NOT NULL, "
                    + "`domain` TEXT NOT NULL, "
                    + "`last_synced_at` INTEGER NOT NULL DEFAULT 0, "
                    + "`last_synced_id` TEXT NOT NULL DEFAULT '', "
                    + "PRIMARY KEY(`user_id`, `domain`))");

            database.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_metadata_user_domain` "
                    + "ON `sync_metadata` (`user_id`, `domain`)");
        }
    };

    private Migration10To11() {
        // Utility class.
    }
}

