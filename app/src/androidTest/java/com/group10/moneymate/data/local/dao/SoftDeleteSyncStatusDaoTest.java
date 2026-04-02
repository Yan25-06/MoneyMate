package com.group10.moneymate.data.local.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Room;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.local.entity.EventEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.models.WalletType;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SoftDeleteSyncStatusDaoTest {

    private static final String USER_ID = "u_soft_delete";
    private static final String WALLET_ID = "w_soft_delete";
    private static final String CATEGORY_ID = "c_soft_delete";

    private AppDatabase database;
    private TransactionDao transactionDao;
    private DebtDao debtDao;
    private EventDao eventDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        transactionDao = database.transactionDao();
        debtDao = database.debtDao();
        eventDao = database.eventDao();
        seedBaseData();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void transactionSoftDelete_setsPendingDeleteSyncStatus() {
        String transactionId = "tx_soft_delete";
        long updatedAt = System.currentTimeMillis();

        transactionDao.insertTransaction(buildTransaction(transactionId, updatedAt - 10_000L));
        transactionDao.softDelete(transactionId, updatedAt);

        assertSoftDeleted("transactions", transactionId, updatedAt);
    }

    @Test
    public void debtSoftDelete_setsPendingDeleteSyncStatus() {
        String debtId = "debt_soft_delete";
        long updatedAt = System.currentTimeMillis();

        debtDao.insertDebt(buildDebt(debtId, updatedAt - 10_000L));
        debtDao.softDelete(debtId, updatedAt);

        assertSoftDeleted("debts", debtId, updatedAt);
    }

    @Test
    public void eventSoftDelete_setsPendingDeleteSyncStatus() {
        String eventId = "event_soft_delete";
        long updatedAt = System.currentTimeMillis();

        eventDao.insertEvent(buildEvent(eventId, updatedAt - 10_000L));
        eventDao.softDelete(eventId, updatedAt);

        assertSoftDeleted("events", eventId, updatedAt);
    }

    @Test
    public void transactionSoftDeleteAllByUser_marksRecordsPendingDelete() {
        String firstId = "tx_soft_delete_all_1";
        String secondId = "tx_soft_delete_all_2";
        long updatedAt = System.currentTimeMillis();

        transactionDao.insertTransaction(buildTransaction(firstId, updatedAt - 20_000L));
        transactionDao.insertTransaction(buildTransaction(secondId, updatedAt - 10_000L));
        transactionDao.softDeleteAllByUser(USER_ID, updatedAt);

        assertSoftDeleted("transactions", firstId, updatedAt);
        assertSoftDeleted("transactions", secondId, updatedAt);
    }

    @Test
    public void debtSoftDeleteAllByUser_marksRecordsPendingDelete() {
        String firstId = "debt_soft_delete_all_1";
        String secondId = "debt_soft_delete_all_2";
        long updatedAt = System.currentTimeMillis();

        debtDao.insertDebt(buildDebt(firstId, updatedAt - 20_000L));
        debtDao.insertDebt(buildDebt(secondId, updatedAt - 10_000L));
        debtDao.softDeleteAllByUser(USER_ID, updatedAt);

        assertSoftDeleted("debts", firstId, updatedAt);
        assertSoftDeleted("debts", secondId, updatedAt);
    }

    @Test
    public void eventSoftDeleteAllByUser_marksRecordsPendingDelete() {
        String firstId = "event_soft_delete_all_1";
        String secondId = "event_soft_delete_all_2";
        long updatedAt = System.currentTimeMillis();

        eventDao.insertEvent(buildEvent(firstId, updatedAt - 20_000L));
        eventDao.insertEvent(buildEvent(secondId, updatedAt - 10_000L));
        eventDao.softDeleteAllByUser(USER_ID, updatedAt);

        assertSoftDeleted("events", firstId, updatedAt);
        assertSoftDeleted("events", secondId, updatedAt);
    }

    private void assertSoftDeleted(String tableName, String id, long updatedAt) {
        SupportSQLiteDatabase sqlite = database.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = sqlite.query(new SimpleSQLiteQuery(
                "SELECT is_deleted, sync_status, updated_at FROM " + tableName + " WHERE id = ?",
                new Object[]{id}
        ))) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
            assertEquals(SyncStatus.PENDING_DELETE, cursor.getInt(1));
            assertEquals(updatedAt, cursor.getLong(2));
        }
    }

    private void seedBaseData() {
        long now = System.currentTimeMillis();

        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setEmail("soft@test.local");
        user.setDisplayName("Soft Delete Test");
        user.setCurrency("VND");
        user.setLanguage("vi");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        database.userDao().insertUser(user);

        WalletEntity wallet = new WalletEntity();
        wallet.setId(WALLET_ID);
        wallet.setUserId(USER_ID);
        wallet.setName("Cash");
        wallet.setBalance(1_000_000d);
        wallet.setType(WalletType.CASH.name());
        wallet.setIconName("ic_wallet_default");
        wallet.setArchived(false);
        wallet.setExcluded(false);
        wallet.setDeleted(false);
        wallet.setSyncStatus(SyncStatus.SYNCED);
        wallet.setCreatedAt(now);
        wallet.setUpdatedAt(now);
        database.walletDao().insert(wallet);

        CategoryEntity category = new CategoryEntity();
        category.setId(CATEGORY_ID);
        category.setUserId(USER_ID);
        category.setName("Food");
        category.setType(TransactionType.EXPENSE.name());
        category.setIconName("ic_category_default");
        category.setDefault(false);
        category.setDeleted(false);
        category.setSyncStatus(SyncStatus.SYNCED);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        database.categoryDao().insertCategory(category);
    }

    private TransactionEntity buildTransaction(String id, long timestamp) {
        TransactionEntity entity = new TransactionEntity();
        entity.setId(id);
        entity.setUserId(USER_ID);
        entity.setWalletId(WALLET_ID);
        entity.setCategoryId(CATEGORY_ID);
        entity.setAmount(10_000d);
        entity.setType(TransactionType.EXPENSE.name());
        entity.setTimestamp(timestamp);
        entity.setNote("soft-delete");
        entity.setDeleted(false);
        entity.setSyncStatus(SyncStatus.SYNCED);
        entity.setCreatedAt(timestamp);
        entity.setUpdatedAt(timestamp);
        return entity;
    }

    private DebtEntity buildDebt(String id, long now) {
        DebtEntity entity = new DebtEntity();
        entity.setId(id);
        entity.setUserId(USER_ID);
        entity.setPersonName("Alice");
        entity.setType("BORROW");
        entity.setAmount(100_000d);
        entity.setRemainingAmount(100_000d);
        entity.setStatus("ACTIVE");
        entity.setNote("soft-delete");
        entity.setDeleted(false);
        entity.setSyncStatus(SyncStatus.SYNCED);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }

    private EventEntity buildEvent(String id, long now) {
        EventEntity entity = new EventEntity();
        entity.setId(id);
        entity.setUserId(USER_ID);
        entity.setName("Trip");
        entity.setBudgetLimit(1_000_000d);
        entity.setStartDate(now - 86_400_000L);
        entity.setEndDate(now + 86_400_000L);
        entity.setActive(true);
        entity.setDeleted(false);
        entity.setSyncStatus(SyncStatus.SYNCED);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return entity;
    }
}


