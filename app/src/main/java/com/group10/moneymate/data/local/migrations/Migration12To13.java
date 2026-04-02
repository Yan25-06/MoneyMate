package com.group10.moneymate.data.local.migrations;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

public final class Migration12To13 {

    public static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP INDEX IF EXISTS `index_transactions_user_sync_updated_id`");
            database.execSQL("DROP INDEX IF EXISTS `index_budgets_user_sync_updated_id`");
            database.execSQL("DROP INDEX IF EXISTS `index_wallets_user_sync_updated`");

            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_tx_user_sync_deleted_updated` "
                            + "ON `transactions` (`user_id`, `sync_status`, `is_deleted`, `updated_at`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_budget_user_sync_deleted_updated` "
                            + "ON `budgets` (`user_id`, `sync_status`, `is_deleted`, `updated_at`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_debt_user_sync_deleted_updated` "
                            + "ON `debts` (`user_id`, `sync_status`, `is_deleted`, `updated_at`)"
            );
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `idx_wallet_user_sync_deleted_updated` "
                            + "ON `wallets` (`user_id`, `sync_status`, `is_deleted`, `updated_at`)"
            );
        }
    };

    private Migration12To13() {
        // Utility class.
    }
}

