package com.group10.moneymate.data.local.migrations;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.group10.moneymate.data.local.AppDatabase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Migration11To12Test {

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void requiredIndexes_existAfterSchemaOpen() {
        SupportSQLiteDatabase sqlite = db.getOpenHelper().getWritableDatabase();
        assertTrue(hasIndex(sqlite, "transactions", "index_transactions_user_deleted_timestamp_id"));
        assertTrue(hasIndex(sqlite, "transactions", "index_transactions_user_sync_updated_id"));
        assertTrue(hasIndex(sqlite, "budgets", "index_budgets_user_sync_updated_id"));
        assertTrue(hasIndex(sqlite, "wallets", "index_wallets_user_sync_updated"));
    }

    private boolean hasIndex(SupportSQLiteDatabase sqlite, String table, String indexName) {
        try (Cursor cursor = sqlite.query("PRAGMA index_list(`" + table + "`)") ) {
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                if (indexName.equals(name)) {
                    return true;
                }
            }
        }
        return false;
    }
}

