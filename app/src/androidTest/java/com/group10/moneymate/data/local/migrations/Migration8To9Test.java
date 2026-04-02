package com.group10.moneymate.data.local.migrations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.group10.moneymate.data.local.AppDatabase;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public class Migration8To9Test {

    private static final String TEST_DB = "migration-8-9-test";

    @Rule
    public final MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            AppDatabase.class.getCanonicalName(),
            new FrameworkSQLiteOpenHelperFactory()
    );

    @Test
    public void migrate8To9_addsMissingMetadataColumnsAndBackfills() throws IOException {
        SupportSQLiteDatabase db = helper.createDatabase(TEST_DB, 8);
        runSqlAsset(db, "db/v8_pre_metadata.sql");

        assertEquals(1, getCount(db, "users"));
        assertEquals(1, getCount(db, "transactions"));
        assertEquals(1, getCount(db, "categories"));
        assertEquals(1, getCount(db, "debts"));
        assertEquals(1, getCount(db, "events"));
        db.close();

        SupportSQLiteDatabase migratedDb = helper.runMigrationsAndValidate(
                TEST_DB,
                9,
                true,
                Migration8To9.MIGRATION_8_9
        );

        assertHasColumn(migratedDb, "users", "updated_at");
        assertHasColumn(migratedDb, "categories", "created_at");
        assertHasColumn(migratedDb, "transactions", "created_at");
        assertHasColumn(migratedDb, "debts", "created_at");
        assertHasColumn(migratedDb, "events", "created_at");

        assertTrue(getLong(migratedDb, "SELECT updated_at FROM users WHERE id = 'u1'") > 0L);
        assertTrue(getLong(migratedDb, "SELECT created_at FROM categories WHERE id = 'c1'") > 0L);
        assertTrue(getLong(migratedDb, "SELECT created_at FROM transactions WHERE id = 't1'") > 0L);
        assertTrue(getLong(migratedDb, "SELECT created_at FROM debts WHERE id = 'd1'") > 0L);
        assertTrue(getLong(migratedDb, "SELECT created_at FROM events WHERE id = 'e1'") > 0L);

        assertEquals(1, getCount(migratedDb, "users"));
        assertEquals(1, getCount(migratedDb, "transactions"));
        assertEquals(1, getCount(migratedDb, "categories"));
        assertEquals(1, getCount(migratedDb, "debts"));
        assertEquals(1, getCount(migratedDb, "events"));

        migratedDb.close();
    }

    private void runSqlAsset(SupportSQLiteDatabase db, String assetPath) throws IOException {
        Context context = ApplicationProvider.getApplicationContext();
        String sql = readAssetAsString(context, assetPath);
        for (String statement : sql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                db.execSQL(trimmed);
            }
        }
    }

    private String readAssetAsString(Context context, String assetPath) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream inputStream = context.getAssets().open(assetPath);
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(inputStreamReader)) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append('\n');
            }
        }
        return builder.toString();
    }

    private void assertHasColumn(SupportSQLiteDatabase db, String table, String column) {
        try (Cursor cursor = db.query("PRAGMA table_info(`" + table + "`)") ) {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && column.equalsIgnoreCase(cursor.getString(nameIndex))) {
                    return;
                }
            }
        }
        throw new AssertionError("Missing column " + column + " in table " + table);
    }

    private int getCount(SupportSQLiteDatabase db, String table) {
        try (Cursor cursor = db.query("SELECT COUNT(*) FROM `" + table + "`")) {
            if (cursor.moveToFirst()) {
                return cursor.getInt(0);
            }
        }
        return 0;
    }

    private long getLong(SupportSQLiteDatabase db, String sql) {
        try (Cursor cursor = db.query(sql)) {
            if (cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        }
        return 0L;
    }
}

