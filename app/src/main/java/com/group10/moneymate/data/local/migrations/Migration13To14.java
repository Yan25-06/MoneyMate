package com.group10.moneymate.data.local.migrations;

import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Migration 13 -> 14
 *
 * Keep schema aligned with current Entity definitions:
 * - wallets: rebuild idx_wallet_user_sync_deleted_updated with trailing id column
 * - transactions: add idx_transactions_wallet_to_wallet_deleted for wallet soft delete path
 */
public class Migration13To14 {
    public static final Migration MIGRATION_13_14 = new Migration(13, 14) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Rebuild wallet sync index to match WalletEntity (same name, new column set).
            database.execSQL("DROP INDEX IF EXISTS idx_wallet_user_sync_deleted_updated");
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_wallet_user_sync_deleted_updated " +
                            "ON wallets(user_id, sync_status, is_deleted, updated_at, id)"
            );

            // Index for wallet-related soft delete updates on transactions.
            database.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_transactions_wallet_to_wallet_deleted " +
                            "ON transactions(wallet_id, to_wallet_id, is_deleted)"
            );
        }
    };
}