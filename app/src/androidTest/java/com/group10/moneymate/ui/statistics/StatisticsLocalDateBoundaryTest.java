package com.group10.moneymate.ui.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dto.DailyTrendDTO;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.models.WalletType;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.utils.TimeWindowUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class StatisticsLocalDateBoundaryTest {

    private static final String USER_ID = "u_stats_local_boundary";
    private static final String WALLET_ID = "wallet_stats_local";
    private static final String CATEGORY_ID = "cat_stats_local_expense";
    private static final String DAILY_PERIOD_FORMAT = "%Y-%m-%d";

    private TimeZone originalTimeZone;
    private AppDatabase database;

    @Before
    public void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));

        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        seedBaseData();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
        if (originalTimeZone != null) {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    @Test
    public void monthFilter_includesFirstDayTransactionInDeviceLocalMonth() throws InterruptedException {
        LocalDate mayFirst = LocalDate.of(2026, 5, 1);
        LocalDate aprilThirtieth = LocalDate.of(2026, 4, 30);
        insertExpense("tx_apr_30", aprilThirtieth, 50_000d);
        insertExpense("tx_may_01", mayFirst, 100_000d);

        StatisticsViewModel.FilterState aprilFilter =
                StatisticsViewModel.FilterState.createMonth(null, aprilThirtieth);
        StatisticsViewModel.FilterState mayFilter =
                StatisticsViewModel.FilterState.createMonth(null, mayFirst);

        assertEquals(TimeWindowUtils.startOfDayLocalDateUtc(mayFirst), mayFilter.getStartDate());
        assertEquals(TimeWindowUtils.endOfDayLocalDateUtc(mayFirst.withDayOfMonth(31)), mayFilter.getEndDate());

        Double aprilTotal = awaitValue(database.transactionDao().getTotalExpenseFiltered(
                USER_ID,
                aprilFilter.getStartDate(),
                aprilFilter.getEndDate(),
                null
        ));
        Double mayTotal = awaitValue(database.transactionDao().getTotalExpenseFiltered(
                USER_ID,
                mayFilter.getStartDate(),
                mayFilter.getEndDate(),
                null
        ));

        assertEquals(50_000d, aprilTotal != null ? aprilTotal : 0d, 0.001d);
        assertEquals(100_000d, mayTotal != null ? mayTotal : 0d, 0.001d);
    }

    @Test
    public void dailyTrend_groupsByDeviceLocalDateAcrossMonthBoundary() throws InterruptedException {
        insertExpense("tx_apr_30", LocalDate.of(2026, 4, 30), 50_000d);
        insertExpense("tx_may_01", LocalDate.of(2026, 5, 1), 100_000d);

        long start = TimeWindowUtils.startOfDayLocalDateUtc(LocalDate.of(2026, 4, 30));
        long end = TimeWindowUtils.endOfDayLocalDateUtc(LocalDate.of(2026, 5, 1));
        List<DailyTrendDTO> trend = awaitValue(database.transactionDao().getAmountTrend(
                USER_ID,
                TransactionType.EXPENSE.name(),
                start,
                end,
                null,
                DAILY_PERIOD_FORMAT
        ));

        assertNotNull(trend);
        assertEquals(2, trend.size());
        assertEquals("2026-04-30", trend.get(0).getPeriodLabel());
        assertEquals(50_000d, trend.get(0).getTotalAmount(), 0.001d);
        assertEquals("2026-05-01", trend.get(1).getPeriodLabel());
        assertEquals(100_000d, trend.get(1).getTotalAmount(), 0.001d);
    }

    private void seedBaseData() {
        long now = System.currentTimeMillis();

        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setEmail("stats-local@test.local");
        user.setDisplayName("Stats Local Tester");
        user.setCurrency("VND");
        user.setLanguage("vi");
        user.setCreatedAt(now);
        database.userDao().insertUser(user);

        WalletEntity wallet = new WalletEntity();
        wallet.setId(WALLET_ID);
        wallet.setUserId(USER_ID);
        wallet.setName("Cash");
        wallet.setType(WalletType.CASH.name());
        wallet.setIconName("ic_wallet_default");
        wallet.setBalance(1_000_000d);
        wallet.setExcluded(false);
        wallet.setArchived(false);
        wallet.setDeleted(false);
        wallet.setSyncStatus(SyncStatus.SYNCED);
        wallet.setCreatedAt(now);
        wallet.setUpdatedAt(now);
        database.walletDao().insert(wallet);

        CategoryEntity category = new CategoryEntity();
        category.setId(CATEGORY_ID);
        category.setUserId(USER_ID);
        category.setName("Ăn uống");
        category.setType(Constants.TYPE_EXPENSE);
        category.setIconName("ic_category_food");
        category.setParentId(null);
        category.setWalletId(null);
        category.setDefault(false);
        category.setDeleted(false);
        category.setSyncStatus(SyncStatus.SYNCED);
        category.setUpdatedAt(now);
        database.categoryDao().insertCategory(category);
    }

    private void insertExpense(@NonNull String id,
                               @NonNull LocalDate localDate,
                               double amount) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(id);
        transaction.setUserId(USER_ID);
        transaction.setWalletId(WALLET_ID);
        transaction.setCategoryId(CATEGORY_ID);
        transaction.setAmount(amount);
        transaction.setType(TransactionType.EXPENSE.name());
        transaction.setTimestamp(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli());
        transaction.setNote(id);
        transaction.setDeleted(false);
        transaction.setSyncStatus(SyncStatus.SYNCED);
        transaction.setCreatedAt(System.currentTimeMillis());
        transaction.setUpdatedAt(System.currentTimeMillis());
        database.transactionDao().insertTransaction(transaction);
    }

    @NonNull
    private <T> T awaitValue(@NonNull LiveData<T> source) throws InterruptedException {
        AtomicReference<T> valueRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Observer<T> observer = value -> {
            valueRef.set(value);
            latch.countDown();
        };

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> source.observeForever(observer));
        boolean completed = latch.await(4L, TimeUnit.SECONDS);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> source.removeObserver(observer));

        assertTrue("Timeout while waiting for LiveData emission", completed);
        T value = valueRef.get();
        assertNotNull(value);
        return value;
    }
}
