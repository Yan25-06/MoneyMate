package com.group10.moneymate.data.local.migrations;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class Migration8To9Test {

    private static final String TEST_DB = "migration-8-9-test";

    @Test
    public void migrate8To9_addsMissingMetadataColumnsAndBackfills() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(TEST_DB);

        SupportSQLiteOpenHelper.Configuration configuration =
                SupportSQLiteOpenHelper.Configuration.builder(context)
                        .name(TEST_DB)
                        .callback(new SupportSQLiteOpenHelper.Callback(8) {
                            @Override
                            public void onCreate(SupportSQLiteDatabase db) {
                                // Schema is created from SQL fixture.
                            }

                            @Override
                            public void onUpgrade(SupportSQLiteDatabase db, int oldVersion, int newVersion) {
                                // Not used in this direct migration test.
                            }
                        })
                        .build();

        SupportSQLiteOpenHelper openHelper = new FrameworkSQLiteOpenHelperFactory().create(configuration);
        SupportSQLiteDatabase db = openHelper.getWritableDatabase();
        createLegacyV8Schema(db);
        seedLegacyV8Data(db);

        assertEquals(1, getCount(db, "users"));
        assertEquals(1, getCount(db, "transactions"));
        assertEquals(1, getCount(db, "categories"));
        assertEquals(1, getCount(db, "debts"));
        assertEquals(1, getCount(db, "events"));

        Migration8To9.MIGRATION_8_9.migrate(db);
        db.execSQL("PRAGMA user_version = 9");
        SupportSQLiteDatabase migratedDb = db;

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

        openHelper.close();
        context.deleteDatabase(TEST_DB);
    }

    private void createLegacyV8Schema(SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `users` (`id` TEXT NOT NULL, `email` TEXT, `display_name` TEXT, `hashed_passcode` TEXT, `currency` TEXT, `theme_mode` TEXT, `language` TEXT, `is_balance_hidden` INTEGER NOT NULL, `last_sync` INTEGER NOT NULL, `avatar_url` TEXT, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `wallets` (`id` TEXT NOT NULL, `user_id` TEXT, `name` TEXT, `balance` REAL NOT NULL, `type` TEXT, `icon_name` TEXT NOT NULL, `is_archived` INTEGER NOT NULL, `is_excluded` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `sync_status` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` TEXT NOT NULL, `user_id` TEXT, `name` TEXT, `type` TEXT, `icon_name` TEXT NOT NULL, `parent_id` TEXT, `wallet_id` TEXT, `is_default` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `sync_status` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `debts` (`id` TEXT NOT NULL, `user_id` TEXT, `person_name` TEXT, `type` TEXT, `amount` REAL NOT NULL, `remaining_amount` REAL NOT NULL, `due_date` INTEGER, `status` TEXT, `note` TEXT, `updated_at` INTEGER NOT NULL, `sync_status` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `events` (`id` TEXT NOT NULL, `user_id` TEXT, `name` TEXT, `budget_limit` REAL, `start_date` INTEGER NOT NULL, `end_date` INTEGER NOT NULL, `is_active` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `sync_status` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`id` TEXT NOT NULL, `wallet_id` TEXT, `category_id` TEXT, `debt_id` TEXT, `event_id` TEXT, `amount` REAL NOT NULL, `type` TEXT, `to_wallet_id` TEXT, `note` TEXT, `timestamp` INTEGER NOT NULL, `image_path` TEXT, `updated_at` INTEGER NOT NULL, `sync_status` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, `user_id` TEXT, PRIMARY KEY(`id`), FOREIGN KEY(`wallet_id`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION, FOREIGN KEY(`debt_id`) REFERENCES `debts`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`event_id`) REFERENCES `events`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL, FOREIGN KEY(`to_wallet_id`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `budgets` (`id` TEXT NOT NULL, `category_id` TEXT, `user_id` TEXT, `amount` REAL NOT NULL, `start_date` INTEGER NOT NULL, `end_date` INTEGER NOT NULL, `wallet_id` TEXT, `created_at` INTEGER NOT NULL, `updated_at` INTEGER NOT NULL, `is_deleted` INTEGER NOT NULL, `sync_status` INTEGER NOT NULL, PRIMARY KEY(`id`), FOREIGN KEY(`user_id`) REFERENCES `users`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION)");
    }

    private void seedLegacyV8Data(SupportSQLiteDatabase db) {
        db.execSQL("INSERT INTO `users` (`id`,`email`,`display_name`,`hashed_passcode`,`currency`,`theme_mode`,`language`,`is_balance_hidden`,`last_sync`,`avatar_url`,`created_at`) VALUES ('u1','u1@test.local','User One',NULL,'VND','system','vi',0,1710000000000,NULL,0)");
        db.execSQL("INSERT INTO `wallets` (`id`,`user_id`,`name`,`balance`,`type`,`icon_name`,`is_archived`,`is_excluded`,`updated_at`,`sync_status`,`is_deleted`,`created_at`) VALUES ('w1','u1','Cash',1000000,'CASH','ic_wallet_default',0,0,1710000001000,0,0,0)");
        db.execSQL("INSERT INTO `categories` (`id`,`user_id`,`name`,`type`,`icon_name`,`parent_id`,`wallet_id`,`is_default`,`updated_at`,`sync_status`,`is_deleted`) VALUES ('c1','u1','Food','EXPENSE','ic_category_default',NULL,NULL,0,1710000002000,0,0)");
        db.execSQL("INSERT INTO `debts` (`id`,`user_id`,`person_name`,`type`,`amount`,`remaining_amount`,`due_date`,`status`,`note`,`updated_at`,`sync_status`,`is_deleted`) VALUES ('d1','u1','Alice','BORROW',100000,100000,NULL,'ACTIVE','note',1710000003000,0,0)");
        db.execSQL("INSERT INTO `events` (`id`,`user_id`,`name`,`budget_limit`,`start_date`,`end_date`,`is_active`,`updated_at`,`sync_status`,`is_deleted`) VALUES ('e1','u1','Trip',2000000,1710000000000,1712600000000,1,0,0,0)");
        db.execSQL("INSERT INTO `transactions` (`id`,`wallet_id`,`category_id`,`debt_id`,`event_id`,`amount`,`type`,`to_wallet_id`,`note`,`timestamp`,`image_path`,`updated_at`,`sync_status`,`is_deleted`,`user_id`) VALUES ('t1','w1','c1',NULL,NULL,25000,'EXPENSE',NULL,'Lunch',1710000004000,NULL,0,0,0,'u1')");
        db.execSQL("INSERT INTO `budgets` (`id`,`category_id`,`user_id`,`amount`,`start_date`,`end_date`,`wallet_id`,`created_at`,`updated_at`,`is_deleted`,`sync_status`) VALUES ('b1','c1','u1',500000,1710000000000,1712600000000,NULL,0,1710000005000,0,0)");
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



