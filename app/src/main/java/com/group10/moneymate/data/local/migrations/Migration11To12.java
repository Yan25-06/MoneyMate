package com.group10.moneymate.data.local.migrations;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public final class Migration11To12 {

    public static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_user_deleted_timestamp_id` "
                            + "ON `transactions` (`user_id`, `is_deleted`, `timestamp`, `id`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_user_sync_updated_id` "
                            + "ON `transactions` (`user_id`, `sync_status`, `updated_at`, `id`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_transactions_wallet_deleted_type_timestamp` "
                            + "ON `transactions` (`wallet_id`, `is_deleted`, `type`, `timestamp`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_budgets_user_sync_updated_id` "
                            + "ON `budgets` (`user_id`, `sync_status`, `updated_at`, `id`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_wallets_user_sync_updated` "
                            + "ON `wallets` (`user_id`, `sync_status`, `updated_at`)"
            );
        }
    };

    private Migration11To12() {
        // Utility class.
    }
}

