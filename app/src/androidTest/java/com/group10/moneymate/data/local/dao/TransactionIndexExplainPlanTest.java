package com.group10.moneymate.data.local.dao;

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
public class TransactionIndexExplainPlanTest {

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
    public void explainPlan_ledgerQuery_usesLedgerIndex() {
        String query = "EXPLAIN QUERY PLAN SELECT * FROM transactions "
                + "WHERE user_id = 'u1' AND is_deleted = 0 "
                + "ORDER BY timestamp DESC, id DESC LIMIT 30";

        String plan = collectPlan(db.getOpenHelper().getWritableDatabase(), query);
        assertTrue(plan.contains("index_transactions_user_deleted_timestamp_id"));
    }

    @Test
    public void explainPlan_syncQuery_usesSyncIndex() {
        String query = "EXPLAIN QUERY PLAN SELECT * FROM transactions "
                + "WHERE user_id = 'u1' AND sync_status IN (1, 2) "
                + "AND (updated_at > 0 OR (updated_at = 0 AND id > '')) "
                + "ORDER BY updated_at ASC, id ASC LIMIT 100";

        String plan = collectPlan(db.getOpenHelper().getWritableDatabase(), query);
        assertTrue(plan.contains("index_transactions_user_sync_updated_id"));
    }

    private String collectPlan(SupportSQLiteDatabase sqliteDatabase, String sql) {
        StringBuilder builder = new StringBuilder();
        try (Cursor cursor = sqliteDatabase.query(sql)) {
            while (cursor.moveToNext()) {
                builder.append(cursor.getString(cursor.getColumnIndexOrThrow("detail"))).append('\n');
            }
        }
        return builder.toString();
    }
}

