package com.group10.moneymate.ui.statistics;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.Visibility;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isRoot;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.testing.FragmentScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.models.WalletType;
import com.group10.moneymate.utils.Constants;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class IncomeExpenseDetailFragmentState3EspressoTest {

    private String userId;
    private String walletId;
    private String rootCategoryId;
    private String childCategoryId;
    private String rootCategoryName;
    private String childCategoryName;
    private String transactionNote;
    private long startDate;
    private long endDate;

    private AppContainer appContainer;

    @Before
    public void setUp() throws InterruptedException {
        MoneyMateApplication application =
                (MoneyMateApplication) ApplicationProvider.getApplicationContext();
        appContainer = application.getAppContainer();

        String suffix = UUID.randomUUID().toString().substring(0, 8);
        userId = "ui_stats_" + suffix;
        walletId = "wallet_ui_" + suffix;
        rootCategoryId = "root_ui_" + suffix;
        childCategoryId = "child_ui_" + suffix;
        rootCategoryName = "Root UI " + suffix;
        childCategoryName = "Child UI " + suffix;
        transactionNote = "Espresso tx " + suffix;

        long now = System.currentTimeMillis();
        startDate = now - TimeUnit.DAYS.toMillis(7);
        endDate = now + TimeUnit.HOURS.toMillis(12);

        appContainer.prefsManager.saveUid(userId);
        seedData(now);
    }

    @After
    public void tearDown() {
        if (appContainer != null) {
            appContainer.prefsManager.saveUid(null);
        }
    }

    @Test
    public void state3_hidesDonutAndShowsTransactionList() {
        FragmentScenario.launchInContainer(
                IncomeExpenseDetailFragment.class,
                buildArgs(),
                R.style.Theme_MoneyMate
        );

        onView(withText(rootCategoryName)).check(matches(isDisplayed()));
        onView(withText(rootCategoryName)).perform(click());

        onView(withText(childCategoryName)).check(matches(isDisplayed()));
        onView(withText(childCategoryName)).perform(click());

        // Wait for fade animation from state transition to finish.
        onView(isRoot()).perform(waitFor(350));

        onView(withId(R.id.donutCategoryBreakdown))
                .check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withId(R.id.recyclerCategoryBreakdown))
                .check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withId(R.id.recyclerDrillTransactions))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        onView(withText(transactionNote)).check(matches(isDisplayed()));

        pressBack();
        onView(isRoot()).perform(waitFor(350));

        onView(withId(R.id.donutCategoryBreakdown))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        onView(withId(R.id.recyclerCategoryBreakdown))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE)));
        onView(withId(R.id.recyclerDrillTransactions))
                .check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withText(childCategoryName)).check(matches(isDisplayed()));
    }

    @NonNull
    private android.os.Bundle buildArgs() {
        android.os.Bundle args = new android.os.Bundle();
        args.putString("walletId", walletId);
        args.putLong("startDate", startDate);
        args.putLong("endDate", endDate);
        args.putString("transactionType", TransactionType.EXPENSE.name());
        return args;
    }

    private void seedData(long now) throws InterruptedException {
        AppDatabase database = appContainer.database;
        CountDownLatch latch = new CountDownLatch(1);

        AppDatabase.databaseWriteExecutor.execute(() -> {
            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setEmail("espresso@test.local");
            user.setDisplayName("Espresso User");
            user.setCurrency("VND");
            user.setLanguage("vi");
            user.setCreatedAt(now);
            database.userDao().insertUser(user);

            WalletEntity wallet = new WalletEntity();
            wallet.setId(walletId);
            wallet.setUserId(userId);
            wallet.setName("UI Wallet");
            wallet.setType(WalletType.CASH.name());
            wallet.setIconName("ic_wallet_default");
            wallet.setBalance(500_000d);
            wallet.setArchived(false);
            wallet.setExcluded(false);
            wallet.setDeleted(false);
            wallet.setSyncStatus(SyncStatus.SYNCED);
            wallet.setCreatedAt(now);
            wallet.setUpdatedAt(now);
            database.walletDao().insert(wallet);

            CategoryEntity root = new CategoryEntity();
            root.setId(rootCategoryId);
            root.setUserId(userId);
            root.setName(rootCategoryName);
            root.setType(Constants.TYPE_EXPENSE);
            root.setIconName("ic_category_default");
            root.setParentId(null);
            root.setWalletId(null);
            root.setDefault(false);
            root.setDeleted(false);
            root.setSyncStatus(SyncStatus.SYNCED);
            root.setUpdatedAt(now);
            database.categoryDao().insertCategory(root);

            CategoryEntity child = new CategoryEntity();
            child.setId(childCategoryId);
            child.setUserId(userId);
            child.setName(childCategoryName);
            child.setType(Constants.TYPE_EXPENSE);
            child.setIconName("ic_category_default");
            child.setParentId(rootCategoryId);
            child.setWalletId(null);
            child.setDefault(false);
            child.setDeleted(false);
            child.setSyncStatus(SyncStatus.SYNCED);
            child.setUpdatedAt(now);
            database.categoryDao().insertCategory(child);

            TransactionEntity transaction = new TransactionEntity();
            transaction.setId("tx_ui_" + UUID.randomUUID());
            transaction.setUserId(userId);
            transaction.setWalletId(walletId);
            transaction.setCategoryId(childCategoryId);
            transaction.setAmount(55_000d);
            transaction.setType(TransactionType.EXPENSE.name());
            transaction.setTimestamp(now - TimeUnit.DAYS.toMillis(1));
            transaction.setNote(transactionNote);
            transaction.setDeleted(false);
            transaction.setSyncStatus(SyncStatus.SYNCED);
            transaction.setUpdatedAt(now);
            database.transactionDao().insertTransaction(transaction);

            latch.countDown();
        });

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue("Failed to seed statistics UI test data", completed);
    }

    @NonNull
    private ViewAction waitFor(long millis) {
        return new ViewAction() {
            @NonNull
            @Override
            public Matcher<View> getConstraints() {
                return isRoot();
            }

            @NonNull
            @Override
            public String getDescription() {
                return "wait for " + millis + " milliseconds";
            }

            @Override
            public void perform(@NonNull UiController uiController, @NonNull View view) {
                uiController.loopMainThreadForAtLeast(millis);
            }
        };
    }
}




