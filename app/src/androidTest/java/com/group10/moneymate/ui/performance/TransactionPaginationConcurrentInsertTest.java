package com.group10.moneymate.ui.performance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.models.SyncStatus;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class TransactionPaginationConcurrentInsertTest {

    private static final String USER_ID = "perf_user";
    private static final String WALLET_ID = "perf_wallet";
    private static final int PAGE_SIZE = 30;

    private AppDatabase db;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        seedUserAndWallet();
    }

    @After
    public void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    public void keysetPagination_withConcurrentInserts_hasNoDuplicatesOrSkips() throws Exception {
        seedTransactions(5000, 10_000_000L);

        AtomicBoolean inserterDone = new AtomicBoolean(false);
        CountDownLatch latch = new CountDownLatch(1);
        Thread inserter = new Thread(() -> {
            try {
                // Insert new rows while pagination is running.
                for (int i = 0; i < 100; i++) {
                    long oldTimestamp = 5_000L - i; // older than seeded rows so they can be reached by cursor paging
                    db.transactionDao().upsertLocal(buildTransaction("concurrent_" + i, oldTimestamp));
                    Thread.sleep(500L);
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } finally {
                inserterDone.set(true);
                latch.countDown();
            }
        });
        inserter.start();

        Set<String> seenIds = new HashSet<>();
        long cursorTimestamp = Long.MAX_VALUE;
        String cursorId = "~";

        while (true) {
            List<TransactionEntity> page;
            if (cursorTimestamp == Long.MAX_VALUE) {
                page = db.transactionDao().getFirstTransactionsPageSync(USER_ID, PAGE_SIZE);
            } else {
                page = db.transactionDao().getTransactionsPagedByCursorSync(
                        USER_ID,
                        cursorTimestamp,
                        cursorId,
                        PAGE_SIZE
                );
            }

            if (page == null || page.isEmpty()) {
                if (inserterDone.get()) {
                    break;
                }
                Thread.sleep(50L);
                continue;
            }

            for (TransactionEntity transaction : page) {
                assertTrue("Duplicate transaction id detected: " + transaction.getId(),
                        seenIds.add(transaction.getId()));
            }

            TransactionEntity tail = page.get(page.size() - 1);
            cursorTimestamp = tail.getTimestamp();
            cursorId = tail.getId();
        }

        assertTrue("Concurrent inserter did not finish in expected time", latch.await(65, TimeUnit.SECONDS));
        assertEquals(5100, seenIds.size());
    }

    private void seedUserAndWallet() {
        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setCreatedAt(System.currentTimeMillis());
        user.setUpdatedAt(System.currentTimeMillis());
        db.userDao().insertUser(user);

        WalletEntity wallet = new WalletEntity();
        wallet.setId(WALLET_ID);
        wallet.setUserId(USER_ID);
        wallet.setName("Perf Wallet");
        wallet.setType("CASH");
        wallet.setIconName("ic_wallet_default");
        wallet.setBalance(0d);
        wallet.setCreatedAt(System.currentTimeMillis());
        wallet.setUpdatedAt(System.currentTimeMillis());
        wallet.setDeleted(false);
        wallet.setSyncStatus(SyncStatus.SYNCED);
        db.walletDao().upsertLocal(wallet);
    }

    private void seedTransactions(int count, long startTimestamp) {
        for (int i = 0; i < count; i++) {
            long timestamp = startTimestamp - i;
            db.transactionDao().upsertLocal(buildTransaction("seed_" + i, timestamp));
        }
    }

    private TransactionEntity buildTransaction(String suffix, long timestamp) {
        TransactionEntity tx = new TransactionEntity();
        tx.setId("tx_" + suffix + "_" + UUID.randomUUID());
        tx.setUserId(USER_ID);
        tx.setWalletId(WALLET_ID);
        tx.setCategoryId(null);
        tx.setAmount(1000d);
        tx.setType("EXPENSE");
        tx.setTimestamp(timestamp);
        tx.setCreatedAt(timestamp);
        tx.setUpdatedAt(timestamp);
        tx.setDeleted(false);
        tx.setSyncStatus(SyncStatus.SYNCED);
        tx.setNote("perf");
        return tx;
    }
}

