package com.group10.moneymate.data.local.migrations;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

/**
 * Migration for DB v8 -> v9 to standardize created_at/updated_at metadata columns.
 */
public final class Migration8To9 {

    public static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            final long nowUtc = System.currentTimeMillis();

            addColumnIfMissing(database, "users", "updated_at", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(database, "categories", "created_at", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(database, "transactions", "created_at", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(database, "debts", "created_at", "INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(database, "events", "created_at", "INTEGER NOT NULL DEFAULT 0");

            database.execSQL(
                    "UPDATE users SET " +
                            "created_at = CASE " +
                            "WHEN created_at IS NULL OR created_at <= 0 THEN " +
                            "CASE WHEN last_sync > 0 THEN last_sync ELSE " + nowUtc + " END " +
                            "ELSE created_at END, " +
                            "updated_at = CASE " +
                            "WHEN updated_at IS NULL OR updated_at <= 0 THEN " +
                            "CASE " +
                            "WHEN last_sync > 0 THEN last_sync " +
                            "WHEN created_at > 0 THEN created_at " +
                            "ELSE " + nowUtc + " END " +
                            "ELSE updated_at END"
            );

            database.execSQL(
                    "UPDATE wallets SET " +
                            "created_at = CASE WHEN created_at IS NULL OR created_at <= 0 THEN " +
                            "CASE WHEN updated_at > 0 THEN updated_at ELSE " + nowUtc + " END " +
                            "ELSE created_at END, " +
                            "updated_at = CASE WHEN updated_at IS NULL OR updated_at <= 0 THEN " +
                            "CASE WHEN created_at > 0 THEN created_at ELSE " + nowUtc + " END " +
                            "ELSE updated_at END"
            );

            database.execSQL(
                    "UPDATE categories SET " +
                            "created_at = CASE WHEN created_at IS NULL OR created_at <= 0 THEN " +
                            "CASE WHEN updated_at > 0 THEN updated_at ELSE " + nowUtc + " END " +
                            "ELSE created_at END, " +
                            "updated_at = CASE WHEN updated_at IS NULL OR updated_at <= 0 THEN " +
                            "CASE WHEN created_at > 0 THEN created_at ELSE " + nowUtc + " END " +
                            "ELSE updated_at END"
            );

            database.execSQL(
                    "UPDATE transactions SET " +
                            "created_at = CASE WHEN created_at IS NULL OR created_at <= 0 THEN " +
                            "CASE " +
                            "WHEN timestamp > 0 THEN timestamp " +
                            "WHEN updated_at > 0 THEN updated_at " +
                            "ELSE " + nowUtc + " END " +
                            "ELSE created_at END, " +
                            "updated_at = CASE WHEN updated_at IS NULL OR updated_at <= 0 THEN " +
                            "CASE " +
                            "WHEN timestamp > 0 THEN timestamp " +
                            "WHEN created_at > 0 THEN created_at " +
                            "ELSE " + nowUtc + " END " +
                            "ELSE updated_at END"
            );

            database.execSQL(
                    "UPDATE budgets SET " +
                            "created_at = CASE WHEN created_at IS NULL OR created_at <= 0 THEN " +
                            "CASE " +
                            "WHEN updated_at > 0 THEN updated_at " +
                            "WHEN start_date > 0 THEN start_date " +
                            "WHEN end_date > 0 THEN end_date " +
                            "ELSE " + nowUtc + " END " +
                            "ELSE created_at END, " +
                            "updated_at = CASE WHEN updated_at IS NULL OR updated_at <= 0 THEN " +
                            "CASE " +
                            "WHEN created_at > 0 THEN created_at " +
                            "WHEN end_date > 0 THEN end_date " +
                            "WHEN start_date > 0 THEN start_date " +
                            "ELSE " + nowUtc + " END " +
                            "ELSE updated_at END"
            );

            database.execSQL(
                    "UPDATE debts SET " +
                            "created_at = CASE WHEN created_at IS NULL OR created_at <= 0 THEN " +
                            "CASE WHEN updated_at > 0 THEN updated_at ELSE " + nowUtc + " END " +
                            "ELSE created_at END, " +
                            "updated_at = CASE WHEN updated_at IS NULL OR updated_at <= 0 THEN " +
                            "CASE WHEN created_at > 0 THEN created_at ELSE " + nowUtc + " END " +
                            "ELSE updated_at END"
            );

            database.execSQL(
                    "UPDATE events SET " +
                            "created_at = CASE WHEN created_at IS NULL OR created_at <= 0 THEN " +
                            "CASE " +
                            "WHEN updated_at > 0 THEN updated_at " +
                            "WHEN start_date > 0 THEN start_date " +
                            "ELSE " + nowUtc + " END " +
                            "ELSE created_at END, " +
                            "updated_at = CASE WHEN updated_at IS NULL OR updated_at <= 0 THEN " +
                            "CASE " +
                            "WHEN created_at > 0 THEN created_at " +
                            "WHEN end_date > 0 THEN end_date " +
                            "WHEN start_date > 0 THEN start_date " +
                            "ELSE " + nowUtc + " END " +
                            "ELSE updated_at END"
            );
        }
    };

    private static void addColumnIfMissing(
            @NonNull SupportSQLiteDatabase database,
            @NonNull String tableName,
            @NonNull String columnName,
            @NonNull String sqlTypeWithDefault
    ) {
        if (!hasColumn(database, tableName, columnName)) {
            database.execSQL("ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + sqlTypeWithDefault);
        }
    }

    private static boolean hasColumn(
            @NonNull SupportSQLiteDatabase database,
            @NonNull String tableName,
            @NonNull String columnName
    ) {
        try (Cursor cursor = database.query("PRAGMA table_info(`" + tableName + "`)") ) {
            final int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && columnName.equalsIgnoreCase(cursor.getString(nameIndex))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Migration8To9() {
        // Utility class.
    }
}

