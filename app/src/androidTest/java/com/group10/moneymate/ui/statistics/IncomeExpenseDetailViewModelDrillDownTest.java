package com.group10.moneymate.ui.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.data.repository.WalletRepository;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.models.WalletType;
import com.group10.moneymate.utils.Constants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class IncomeExpenseDetailViewModelDrillDownTest {

    private static final String USER_ID = "u_stats_drilldown";
    private static final String WALLET_ID = "wallet_stats_1";
    private static final String ROOT_CATEGORY_ID = "cat_root_food";
    private static final String CHILD_CATEGORY_ID = "cat_child_coffee";
    private static final long AWAIT_SECONDS = 4L;

    private AppDatabase database;
    private IncomeExpenseDetailViewModel viewModel;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();

        seedBaseData();

        TransactionRepository transactionRepository =
                new TransactionRepository(database, database.transactionDao(), database.walletDao());
        WalletRepository walletRepository = new WalletRepository(database.walletDao());
        CategoryRepository categoryRepository = new CategoryRepository(database.categoryDao());

        long now = System.currentTimeMillis();
        long start = now - TimeUnit.DAYS.toMillis(7);
        long end = now + TimeUnit.HOURS.toMillis(12);
        AtomicReference<IncomeExpenseDetailViewModel> viewModelRef = new AtomicReference<>();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> viewModelRef.set(
                new IncomeExpenseDetailViewModel(
                        transactionRepository,
                        walletRepository,
                        categoryRepository,
                        USER_ID,
                        WALLET_ID,
                        start,
                        end,
                        TransactionType.EXPENSE.name()
                )
        ));
        viewModel = viewModelRef.get();
        assertNotNull(viewModel);
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void drillDownStateMachine_rootChildTransactionAndBack_preservesFilterContext() throws InterruptedException {
        StatisticsViewModel.FilterState initialFilter = viewModel.getCurrentFilterState();

        List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel> rootItems = awaitValue(
                viewModel.getCategoryItems(),
                value -> containsCategory(value, ROOT_CATEGORY_ID)
        );
        IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel rootItem =
                findByCategoryId(rootItems, ROOT_CATEGORY_ID);
        assertNotNull(rootItem);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                viewModel.openChildDrillDown(rootItem));

        IncomeExpenseDetailViewModel.DrillDownUiState childState = awaitValue(
                viewModel.getDrillDownState(),
                value -> value != null
                        && value.getState() == IncomeExpenseDetailViewModel.DrillDownState.CHILD_DONUT
                        && ROOT_CATEGORY_ID.equals(value.getRootCategoryId())
        );
        assertNotNull(childState);

        List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel> childItems = awaitValue(
                viewModel.getCategoryItems(),
                value -> containsCategory(value, CHILD_CATEGORY_ID)
        );
        IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel childItem =
                findByCategoryId(childItems, CHILD_CATEGORY_ID);
        assertNotNull(childItem);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                viewModel.openTransactionDrillDown(childItem));

        IncomeExpenseDetailViewModel.DrillDownUiState transactionState = awaitValue(
                viewModel.getDrillDownState(),
                value -> value != null
                        && value.getState() == IncomeExpenseDetailViewModel.DrillDownState.TRANSACTION_LIST
                        && CHILD_CATEGORY_ID.equals(value.getChildCategoryId())
        );
        assertNotNull(transactionState);

        List<TransactionEntity> transactions = awaitValue(
                viewModel.getDrillDownTransactions(),
                value -> value != null && !value.isEmpty()
        );
        assertEquals(CHILD_CATEGORY_ID, transactions.get(0).getCategoryId());

        AtomicReference<Boolean> backOneResult = new AtomicReference<>(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                backOneResult.set(viewModel.navigateUpInDrillDown()));
        assertTrue(backOneResult.get());

        IncomeExpenseDetailViewModel.DrillDownUiState backToChild = awaitValue(
                viewModel.getDrillDownState(),
                value -> value != null
                        && value.getState() == IncomeExpenseDetailViewModel.DrillDownState.CHILD_DONUT
        );
        assertNotNull(backToChild);

        AtomicReference<Boolean> backTwoResult = new AtomicReference<>(false);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                backTwoResult.set(viewModel.navigateUpInDrillDown()));
        assertTrue(backTwoResult.get());

        IncomeExpenseDetailViewModel.DrillDownUiState backToRoot = awaitValue(
                viewModel.getDrillDownState(),
                value -> value != null
                        && value.getState() == IncomeExpenseDetailViewModel.DrillDownState.ROOT_DONUT
        );
        assertNotNull(backToRoot);

        AtomicReference<Boolean> backThreeResult = new AtomicReference<>(true);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                backThreeResult.set(viewModel.navigateUpInDrillDown()));
        assertFalse(backThreeResult.get());

        StatisticsViewModel.FilterState finalFilter = viewModel.getCurrentFilterState();
        assertEquals(initialFilter.getWalletId(), finalFilter.getWalletId());
        assertEquals(initialFilter.getStartDate(), finalFilter.getStartDate());
        assertEquals(initialFilter.getEndDate(), finalFilter.getEndDate());
    }

    @Test
    public void childState_containsRootBucketForDirectRootTransactions() throws InterruptedException {
        List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel> rootItems = awaitValue(
                viewModel.getCategoryItems(),
                value -> containsCategory(value, ROOT_CATEGORY_ID)
        );
        IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel rootItem =
                findByCategoryId(rootItems, ROOT_CATEGORY_ID);
        assertNotNull(rootItem);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                viewModel.openChildDrillDown(rootItem));

        List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel> childItems = awaitValue(
                viewModel.getCategoryItems(),
                value -> containsCategory(value, CHILD_CATEGORY_ID) && containsCategory(value, ROOT_CATEGORY_ID)
        );
        IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel rootBucket =
                findByCategoryId(childItems, ROOT_CATEGORY_ID);
        assertNotNull(rootBucket);

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                viewModel.openTransactionDrillDown(rootBucket));

        List<TransactionEntity> transactions = awaitValue(
                viewModel.getDrillDownTransactions(),
                value -> value != null && !value.isEmpty()
        );
        assertEquals(ROOT_CATEGORY_ID, transactions.get(0).getCategoryId());
    }

    private void seedBaseData() {
        long now = System.currentTimeMillis();

        UserEntity user = new UserEntity();
        user.setId(USER_ID);
        user.setEmail("stats@test.local");
        user.setDisplayName("Stats Tester");
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
        wallet.setBalance(3_000_000d);
        wallet.setExcluded(false);
        wallet.setArchived(false);
        wallet.setDeleted(false);
        wallet.setSyncStatus(SyncStatus.SYNCED);
        wallet.setCreatedAt(now);
        wallet.setUpdatedAt(now);
        database.walletDao().insert(wallet);

        CategoryEntity root = new CategoryEntity();
        root.setId(ROOT_CATEGORY_ID);
        root.setUserId(USER_ID);
        root.setName("Ăn uống");
        root.setType(Constants.TYPE_EXPENSE);
        root.setIconName("ic_category_food");
        root.setParentId(null);
        root.setWalletId(null);
        root.setDefault(false);
        root.setDeleted(false);
        root.setSyncStatus(SyncStatus.SYNCED);
        root.setUpdatedAt(now);
        database.categoryDao().insertCategory(root);

        CategoryEntity child = new CategoryEntity();
        child.setId(CHILD_CATEGORY_ID);
        child.setUserId(USER_ID);
        child.setName("Cà phê");
        child.setType(Constants.TYPE_EXPENSE);
        child.setIconName("ic_category_default");
        child.setParentId(ROOT_CATEGORY_ID);
        child.setWalletId(null);
        child.setDefault(false);
        child.setDeleted(false);
        child.setSyncStatus(SyncStatus.SYNCED);
        child.setUpdatedAt(now);
        database.categoryDao().insertCategory(child);

        TransactionEntity tx = new TransactionEntity();
        tx.setId("tx_stats_1");
        tx.setUserId(USER_ID);
        tx.setWalletId(WALLET_ID);
        tx.setCategoryId(CHILD_CATEGORY_ID);
        tx.setAmount(45_000d);
        tx.setType(TransactionType.EXPENSE.name());
        tx.setTimestamp(now - TimeUnit.DAYS.toMillis(1));
        tx.setNote("Coffee");
        tx.setDeleted(false);
        tx.setSyncStatus(SyncStatus.SYNCED);
        tx.setUpdatedAt(now);
        database.transactionDao().insertTransaction(tx);

        TransactionEntity rootTx = new TransactionEntity();
        rootTx.setId("tx_stats_root_1");
        rootTx.setUserId(USER_ID);
        rootTx.setWalletId(WALLET_ID);
        rootTx.setCategoryId(ROOT_CATEGORY_ID);
        rootTx.setAmount(25_000d);
        rootTx.setType(TransactionType.EXPENSE.name());
        rootTx.setTimestamp(now - TimeUnit.DAYS.toMillis(2));
        rootTx.setNote("Lunch");
        rootTx.setDeleted(false);
        rootTx.setSyncStatus(SyncStatus.SYNCED);
        rootTx.setUpdatedAt(now);
        database.transactionDao().insertTransaction(rootTx);
    }

    @Nullable
    private IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel findByCategoryId(
            @Nullable List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel> items,
            @NonNull String categoryId
    ) {
        if (items == null) {
            return null;
        }
        for (IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel item : items) {
            if (categoryId.equals(item.getCategoryId())) {
                return item;
            }
        }
        return null;
    }

    private boolean containsCategory(@Nullable List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel> items,
                                     @NonNull String categoryId) {
        return findByCategoryId(items, categoryId) != null;
    }

    @NonNull
    private <T> T awaitValue(@NonNull LiveData<T> source,
                             @NonNull Condition<T> condition) throws InterruptedException {
        AtomicReference<T> valueRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        Observer<T> observer = value -> {
            valueRef.set(value);
            if (condition.matches(value)) {
                latch.countDown();
            }
        };

        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> source.observeForever(observer));
        boolean completed = latch.await(AWAIT_SECONDS, TimeUnit.SECONDS);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> source.removeObserver(observer));

        assertTrue("Timeout while waiting for LiveData emission", completed);
        T value = valueRef.get();
        assertNotNull(value);
        return value;
    }

    private interface Condition<T> {
        boolean matches(@Nullable T value);
    }
}




