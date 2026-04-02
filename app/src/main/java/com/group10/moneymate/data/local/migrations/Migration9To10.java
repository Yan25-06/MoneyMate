package com.group10.moneymate.data.local.migrations;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Migration for DB v9 -> v10.
 * Adds unique budget scope index required by conditional UPSERT in BudgetDao.
 */
public final class Migration9To10 {

    public static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Keep one row per scope before creating unique index.
            database.execSQL(
                    "DELETE FROM budgets " +
                            "WHERE rowid NOT IN (" +
                            "SELECT MIN(rowid) FROM budgets " +
                            "GROUP BY user_id, wallet_id, start_date, end_date, category_id" +
                            ")"
            );

            database.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_budgets_scope_unique` " +
                            "ON `budgets` (`user_id`, `wallet_id`, `start_date`, `end_date`, `category_id`)"
            );
        }
    };

    private Migration9To10() {
        // Utility class.
    }
}

