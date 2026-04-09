package com.group10.moneymate.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.Constants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class TransactionRepositoryOcrSaveAllTest {

    private static final String USER_ID = "ocr_save_all_user";
    private static final String WALLET_ID = "ocr_save_all_wallet";
    private static final String CATEGORY_ID = "ocr_save_all_category";

    private AppDatabase database;
    private TransactionRepository repository;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repository = new TransactionRepository(
                database,
                database.transactionDao(),
                database.walletDao(),
                null
        );
        seedBaseData();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void insertTransactions_shouldPersistAllWithPendingUploadMetadata() throws Exception {
        List<TransactionEntity> transactions = new ArrayList<>();
        transactions.add(buildOcrTransaction(35_000d, "Tra dao"));
        transactions.add(buildOcrTransaction(42_000d, "Com tam"));

        awaitWriteSuccess(transactions);

        List<TransactionEntity> stored = database.transactionDao()
                .getFirstTransactionsPageSync(USER_ID, 10);
        assertEquals(2, stored.size());

        for (TransactionEntity transaction : stored) {
            assertNotNull(transaction.getId());
            assertFalse(transaction.getId().trim().isEmpty());
            assertEquals(USER_ID, transaction.getUserId());
            assertEquals(WALLET_ID, transaction.getWalletId());
            assertEquals(CATEGORY_ID, transaction.getCategoryId());
            assertEquals(Constants.TYPE_EXPENSE, transaction.getType());
            assertEquals(SyncStatus.PENDING_UPLOAD, transaction.getSyncStatus());
            assertFalse(transaction.isDeleted());
            assertTrue(transaction.getCreatedAt() > 0L);
            assertTrue(transaction.getUpdatedAt() >= transaction.getCreatedAt());
        }
    }

    @Test
    public void insertTransactions_shouldRemainAtomicWhenOneTransactionIsInvalid() throws Exception {
        List<TransactionEntity> transactions = new ArrayList<>();
        transactions.add(buildOcrTransaction(28_000d, "Pho bo"));

        TransactionEntity invalidTransaction = buildOcrTransaction(12_000d, "Nuoc suoi");
        invalidTransaction.setCategoryId(null);
        transactions.add(invalidTransaction);

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.insertTransactions(transactions, new TransactionRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                latch.countDown();
            }
        });

        assertTrue("Timed out waiting for OCR batch save error", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(errorRef.get());
        assertTrue(errorRef.get() instanceof TransactionRepository.TransactionValidationException);
        assertTrue(database.transactionDao().getFirstTransactionsPageSync(USER_ID, 10).isEmpty());
    }

    private void awaitWriteSuccess(List<TransactionEntity> transactions) throws Exception {
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.insertTransactions(transactions, new TransactionRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                errorRef.set(throwable);
                latch.countDown();
            }
        });

        assertTrue("Timed out waiting for OCR batch save", latch.await(5, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError("Unexpected OCR batch save error", errorRef.get());
        }
    }

    private void seedBaseData() {
        long now = System.currentTimeMillis();

        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setEmail("ocr-save-all@test.local");
        user.setDisplayName("OCR Save All");
        user.setCurrency("VND");
        user.setLanguage("vi");
        user.setCreatedAt(now);
        user.setUpdatedAt(now);
        database.userDao().insertUser(user);

        WalletEntity wallet = new WalletEntity();
        wallet.setId(WALLET_ID);
        wallet.setUserId(USER_ID);
        wallet.setName("OCR Wallet");
        wallet.setBalance(500_000d);
        wallet.setType("CASH");
        wallet.setIconName("ic_wallet_default");
        wallet.setArchived(false);
        wallet.setExcluded(false);
        wallet.setDeleted(false);
        wallet.setSyncStatus(SyncStatus.SYNCED);
        wallet.setCreatedAt(now);
        wallet.setUpdatedAt(now);
        database.walletDao().upsertLocal(wallet);

        CategoryEntity category = new CategoryEntity();
        category.setId(CATEGORY_ID);
        category.setUserId(USER_ID);
        category.setName("Ăn uống");
        category.setType(Constants.TYPE_EXPENSE);
        category.setIconName("ic_category_food");
        category.setDefault(false);
        category.setDeleted(false);
        category.setSyncStatus(SyncStatus.SYNCED);
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        database.categoryDao().insertCategory(category);
    }

    private TransactionEntity buildOcrTransaction(double amount, String note) {
        long now = System.currentTimeMillis();
        TransactionEntity transaction = new TransactionEntity();
        transaction.setUserId(USER_ID);
        transaction.setWalletId(WALLET_ID);
        transaction.setCategoryId(CATEGORY_ID);
        transaction.setAmount(amount);
        transaction.setType(Constants.TYPE_EXPENSE);
        transaction.setTimestamp(now);
        transaction.setNote(note);
        transaction.setImagePath(null);
        transaction.setDeleted(false);
        transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        transaction.setCreatedAt(0L);
        transaction.setUpdatedAt(0L);
        return transaction;
    }
}
