package com.group10.moneymate.data.local.migrations;

import static org.junit.Assert.assertEquals;
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
public class Migration9To10Test {

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
    public void budgetScopeUniqueIndex_exists() {
        SupportSQLiteDatabase sqlite = db.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = sqlite.query("PRAGMA index_list(`budgets`)") ) {
            boolean found = false;
            while (cursor.moveToNext()) {
                String indexName = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                if ("index_budgets_scope_unique".equals(indexName)
                        || "index_budgets_user_id_wallet_id_start_date_end_date_category_id".equals(indexName)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found);
        }
    }

    @Test
    public void budgetScopeUniqueIndex_isUnique() {
        SupportSQLiteDatabase sqlite = db.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = sqlite.query("PRAGMA index_list(`budgets`)") ) {
            int uniqueFlag = -1;
            while (cursor.moveToNext()) {
                String name = cursor.getString(cursor.getColumnIndexOrThrow("name"));
                if ("index_budgets_scope_unique".equals(name)
                        || "index_budgets_user_id_wallet_id_start_date_end_date_category_id".equals(name)) {
                    uniqueFlag = cursor.getInt(cursor.getColumnIndexOrThrow("unique"));
                    break;
                }
            }
            assertEquals(1, uniqueFlag);
        }
    }
}

