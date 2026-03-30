package com.group10.moneymate.data.local.migrations;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Full SQL temp-table migration for DB v7 -> v8.
 */
public final class Migration7To8 {

    public static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            final int walletsBefore = countRows(database, "wallets");
            final int categoriesBefore = countRows(database, "categories");

            database.execSQL("PRAGMA foreign_keys=OFF");
            try {
                // 1) Create wallets_new with v8 schema.
                database.execSQL(
                        "CREATE TABLE IF NOT EXISTS `wallets_new` (" +
                                "`id` TEXT NOT NULL, " +
                                "`user_id` TEXT, " +
                                "`name` TEXT, " +
                                "`balance` REAL NOT NULL, " +
                                "`type` TEXT, " +
                                "`icon_name` TEXT NOT NULL DEFAULT 'ic_wallet_default', " +
                                "`is_archived` INTEGER NOT NULL DEFAULT 0, " +
                                "`is_excluded` INTEGER NOT NULL, " +
                                "`updated_at` INTEGER NOT NULL, " +
                                "`sync_status` INTEGER NOT NULL, " +
                                "`is_deleted` INTEGER NOT NULL, " +
                                "`created_at` INTEGER NOT NULL, " +
                                "PRIMARY KEY(`id`), " +
                                "FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                );

                // 2) Copy wallets with deterministic icon_name mapping + default archive state.
                database.execSQL(
                        "INSERT INTO `wallets_new` (" +
                                "`id`, `user_id`, `name`, `balance`, `type`, " +
                                "`icon_name`, `is_archived`, `is_excluded`, " +
                                "`updated_at`, `sync_status`, `is_deleted`, `created_at`) " +
                                "SELECT " +
                                "`id`, `user_id`, `name`, `balance`, `type`, " +
                                "'ic_wallet_default' AS `icon_name`, " +
                                "0 AS `is_archived`, `is_excluded`, `updated_at`, " +
                                "`sync_status`, `is_deleted`, `created_at` " +
                                "FROM `wallets`"
                );

                final int walletsCopied = countRows(database, "wallets_new");
                if (walletsCopied != walletsBefore) {
                    throw new IllegalStateException(
                            "Migration 7->8 failed before wallets swap: copied rows mismatch (before="
                                    + walletsBefore + ", copied=" + walletsCopied + ")"
                    );
                }

                // 3) Swap wallets tables.
                database.execSQL("DROP TABLE `wallets`");
                database.execSQL("ALTER TABLE `wallets_new` RENAME TO `wallets`");

                // 4) Create categories_new with v8 schema.
                database.execSQL(
                        "CREATE TABLE IF NOT EXISTS `categories_new` (" +
                                "`id` TEXT NOT NULL, " +
                                "`user_id` TEXT, " +
                                "`name` TEXT, " +
                                "`type` TEXT, " +
                                "`icon_name` TEXT NOT NULL DEFAULT 'ic_category_default', " +
                                "`parent_id` TEXT, " +
                                "`wallet_id` TEXT, " +
                                "`is_default` INTEGER NOT NULL, " +
                                "`updated_at` INTEGER NOT NULL, " +
                                "`sync_status` INTEGER NOT NULL, " +
                                "`is_deleted` INTEGER NOT NULL, " +
                                "PRIMARY KEY(`id`), " +
                                "FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                );

                // 5) Copy categories and initialize hierarchy/wallet scope fields.
                database.execSQL(
                        "INSERT INTO `categories_new` (" +
                                "`id`, `user_id`, `name`, `type`, `icon_name`, " +
                                "`parent_id`, `wallet_id`, `is_default`, " +
                                "`updated_at`, `sync_status`, `is_deleted`) " +
                                "SELECT " +
                                "`id`, `user_id`, `name`, `type`, " +
                                "COALESCE(NULLIF(TRIM(`icon_res_id`), ''), 'ic_category_default') AS `icon_name`, " +
                                "NULL AS `parent_id`, NULL AS `wallet_id`, " +
                                "`is_default`, `updated_at`, `sync_status`, `is_deleted` " +
                                "FROM `categories`"
                );

                final int categoriesCopied = countRows(database, "categories_new");
                if (categoriesCopied != categoriesBefore) {
                    throw new IllegalStateException(
                            "Migration 7->8 failed before categories swap: copied rows mismatch (before="
                                    + categoriesBefore + ", copied=" + categoriesCopied + ")"
                    );
                }

                // 6) Swap categories tables.
                database.execSQL("DROP TABLE `categories`");
                database.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`");

                // 7) Recreate v8 indexes.
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_categories_user_wallet_parent_type_deleted` ON `categories` (`user_id`, `wallet_id`, `parent_id`, `type`, `is_deleted`)");
                database.execSQL("CREATE INDEX IF NOT EXISTS `index_wallets_user_archived_deleted` ON `wallets` (`user_id`, `is_archived`, `is_deleted`)");

                // 8) Validate row-count preservation after swap.
                final int walletsAfter = countRows(database, "wallets");
                final int categoriesAfter = countRows(database, "categories");
                if (walletsBefore != walletsAfter) {
                    throw new IllegalStateException(
                            "Migration 7->8 failed after wallets swap: row count mismatch (before="
                                    + walletsBefore + ", after=" + walletsAfter + ")"
                    );
                }
                if (categoriesBefore != categoriesAfter) {
                    throw new IllegalStateException(
                            "Migration 7->8 failed after categories swap: row count mismatch (before="
                                    + categoriesBefore + ", after=" + categoriesAfter + ")"
                    );
                }
            } finally {
                database.execSQL("PRAGMA foreign_keys=ON");
            }
        }
    };

    private static int countRows(@NonNull SupportSQLiteDatabase database, @NonNull String tableName) {
        try (Cursor cursor = database.query("SELECT COUNT(*) FROM `" + tableName + "`")) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
            return 0;
        }
    }

    private Migration7To8() {
        // Utility class.
    }
}




