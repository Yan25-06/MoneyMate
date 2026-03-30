package com.group10.moneymate.data.local.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
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
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.models.WalletType;
import com.group10.moneymate.utils.Constants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class WalletDaoTransactionalSoftDeleteTest {

    private static final String USER_ID = "u_wallet_txn_delete";
    private static final String WALLET_TARGET = "wallet_target";
    private static final String WALLET_OTHER = "wallet_other";
    private static final String CATEGORY_ID = "cat_expense";

    private AppDatabase database;
    private WalletDao walletDao;
    private TransactionDao transactionDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        walletDao = database.walletDao();
        transactionDao = database.transactionDao();

        seedData();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void softDeleteWalletWithRelatedTransactions_updatesWalletAndBothWalletRelations() {
        long updatedAt = System.currentTimeMillis();

        walletDao.softDeleteWalletWithRelatedTransactions(USER_ID, WALLET_TARGET, updatedAt);

        WalletEntity wallet = walletDao.getByIdSync(WALLET_TARGET);
        assertNotNull(wallet);
        assertTrue(wallet.isDeleted());
        assertEquals(SyncStatus.PENDING_DELETE, wallet.getSyncStatus());
        assertEquals(updatedAt, wallet.getUpdatedAt());

        assertDeletedWithPendingDelete("tx_wallet_id", updatedAt);
        assertDeletedWithPendingDelete("tx_to_wallet_id", updatedAt);

        assertNotDeleted("tx_unrelated");

        WalletEntity otherWallet = walletDao.getByIdSync(WALLET_OTHER);
        assertNotNull(otherWallet);
        // tx_to_wallet_id was EXPENSE from WALLET_OTHER to WALLET_TARGET, deleting target wallet
        // must undo the debit on WALLET_OTHER (as if transfer never existed).
        assertEquals(1_000_000d, otherWallet.getBalance(), 0.001d);
        assertEquals(SyncStatus.PENDING_UPLOAD, otherWallet.getSyncStatus());

        List<TransactionEntity> pending = transactionDao.getPendingSyncTransactions(USER_ID);
        assertTrue(containsId(pending, "tx_wallet_id"));
        assertTrue(containsId(pending, "tx_to_wallet_id"));
    }

    @Test
    public void archiveWallet_keepsHistoricalTransactionsIntact() {
        long updatedAt = System.currentTimeMillis();

        walletDao.archive(WALLET_TARGET, updatedAt);

        WalletEntity wallet = walletDao.getByIdSync(WALLET_TARGET);
        assertNotNull(wallet);
        assertTrue(wallet.isArchived());
        assertFalse(wallet.isDeleted());
        assertEquals(SyncStatus.PENDING_UPLOAD, wallet.getSyncStatus());
        assertEquals(updatedAt, wallet.getUpdatedAt());

        assertNotDeleted("tx_wallet_id");
        assertNotDeleted("tx_to_wallet_id");
    }

    private void assertDeletedWithPendingDelete(String txId, long updatedAt) {
        SupportSQLiteDatabase sqliteDatabase = database.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = sqliteDatabase.query(
                new SimpleSQLiteQuery(
                        "SELECT is_deleted, sync_status, updated_at FROM transactions WHERE id = ?",
                        new Object[]{txId}
                )
        )) {
            assertTrue(cursor.moveToFirst());
            assertEquals(1, cursor.getInt(0));
            assertEquals(SyncStatus.PENDING_DELETE, cursor.getInt(1));
            assertEquals(updatedAt, cursor.getLong(2));
        }
    }

    private void assertNotDeleted(String txId) {
        SupportSQLiteDatabase sqliteDatabase = database.getOpenHelper().getWritableDatabase();
        try (Cursor cursor = sqliteDatabase.query(
                new SimpleSQLiteQuery(
                        "SELECT is_deleted, sync_status FROM transactions WHERE id = ?",
                        new Object[]{txId}
                )
        )) {
            assertTrue(cursor.moveToFirst());
            assertEquals(0, cursor.getInt(0));
            assertEquals(SyncStatus.SYNCED, cursor.getInt(1));
        }
    }

    private boolean containsId(List<TransactionEntity> list, String id) {
        for (TransactionEntity entity : list) {
            if (id.equals(entity.getId())) {
                return true;
            }
        }
        return false;
    }

    private void seedData() {
        long now = System.currentTimeMillis();

        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setEmail("wallet@test.local");
        user.setDisplayName("Wallet Test");
        user.setCurrency("VND");
        user.setLanguage("vi");
        user.setCreatedAt(now);
        database.userDao().insertUser(user);

        WalletEntity target = buildWallet(WALLET_TARGET, now);
        WalletEntity other = buildWallet(WALLET_OTHER, now);
        // Source wallet already reflects an existing transfer out of 10,000.
        other.setBalance(990_000d);
        walletDao.insert(target);
        walletDao.insert(other);

        CategoryEntity category = new CategoryEntity();
        category.setId(CATEGORY_ID);
        category.setUserId(USER_ID);
        category.setName("Ăn uống");
        category.setType(Constants.TYPE_EXPENSE);
        category.setIconName("ic_category_default");
        category.setDefault(false);
        category.setDeleted(false);
        category.setSyncStatus(SyncStatus.SYNCED);
        category.setUpdatedAt(now);
        database.categoryDao().insertCategory(category);

        transactionDao.insertTransaction(buildExpense("tx_wallet_id", WALLET_TARGET, null, now - 3_000L));
        transactionDao.insertTransaction(buildTransfer("tx_to_wallet_id", WALLET_OTHER, WALLET_TARGET, now - 2_000L));
        transactionDao.insertTransaction(buildExpense("tx_unrelated", WALLET_OTHER, null, now - 1_000L));
    }

    private WalletEntity buildWallet(String id, long now) {
        WalletEntity wallet = new WalletEntity();
        wallet.setId(id);
        wallet.setUserId(USER_ID);
        wallet.setName(id);
        wallet.setType(WalletType.CASH.name());
        wallet.setIconName("ic_wallet_default");
        wallet.setBalance(1_000_000d);
        wallet.setExcluded(false);
        wallet.setArchived(false);
        wallet.setDeleted(false);
        wallet.setSyncStatus(SyncStatus.SYNCED);
        wallet.setCreatedAt(now);
        wallet.setUpdatedAt(now);
        return wallet;
    }

    private TransactionEntity buildExpense(String id, String walletId, String toWalletId, long timestamp) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setUserId(USER_ID);
        transaction.setWalletId(walletId);
        transaction.setToWalletId(toWalletId);
        transaction.setCategoryId(CATEGORY_ID);
        transaction.setType(TransactionType.EXPENSE.name());
        transaction.setAmount(10_000d);
        transaction.setTimestamp(timestamp);
        transaction.setNote("test");
        transaction.setDeleted(false);
        transaction.setSyncStatus(SyncStatus.SYNCED);
        transaction.setUpdatedAt(timestamp);
        return transaction;
    }

    private TransactionEntity buildTransfer(String id, String walletId, String toWalletId, long timestamp) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setUserId(USER_ID);
        transaction.setWalletId(walletId);
        transaction.setToWalletId(toWalletId);
        transaction.setCategoryId(CATEGORY_ID);
        transaction.setType("TRANSFER");
        transaction.setAmount(10_000d);
        transaction.setTimestamp(timestamp);
        transaction.setNote("transfer");
        transaction.setDeleted(false);
        transaction.setSyncStatus(SyncStatus.SYNCED);
        transaction.setUpdatedAt(timestamp);
        return transaction;
    }
}





