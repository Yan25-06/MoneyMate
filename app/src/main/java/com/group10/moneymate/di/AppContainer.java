package com.group10.moneymate.di;

import android.content.Context;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.remote.FirebaseAuthHelper;
import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.data.repository.BudgetRepository;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.DebtRepository;
import com.group10.moneymate.data.repository.EventRepository;
import com.group10.moneymate.data.repository.SyncMetadataRepository;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.data.repository.UserRepository;
import com.group10.moneymate.data.repository.WalletRepository;
import com.group10.moneymate.utils.PrefsManager;
import com.group10.moneymate.workers.SyncScheduler;

public class AppContainer {

    public final AppDatabase database;
    public final FirebaseAuthHelper firebaseAuthHelper;
    public final PrefsManager prefsManager;
    public final AuthRepository authRepository;
    public final UserRepository userRepository;
    public final WalletRepository walletRepository;
    public final CategoryRepository categoryRepository;
    public final TransactionRepository transactionRepository;
    public final BudgetRepository budgetRepository;
    public final DebtRepository debtRepository;
    public final EventRepository eventRepository;
    public final SyncMetadataRepository syncMetadataRepository;
    public final SyncScheduler syncScheduler;

    public AppContainer(Context context) {
        database           = AppDatabase.getInstance(context);
        firebaseAuthHelper = new FirebaseAuthHelper();
        prefsManager       = new PrefsManager(context);
        syncScheduler      = new SyncScheduler(context.getApplicationContext());
        authRepository        = new AuthRepository(firebaseAuthHelper, database.userDao(), prefsManager);
        userRepository        = new UserRepository(database.userDao());
        walletRepository      = new WalletRepository(database.walletDao());
        categoryRepository    = new CategoryRepository(database.categoryDao());
        transactionRepository = new TransactionRepository(database.transactionDao(), database.walletDao(), syncScheduler);
        this.budgetRepository = new BudgetRepository(database.budgetDao(), database, syncScheduler);
        debtRepository        = new DebtRepository(database.debtDao());
        eventRepository       = new EventRepository(database.eventDao());
        syncMetadataRepository = new SyncMetadataRepository(database.syncMetadataDao());
    }

    /**
     * Seed danh mục mặc định nếu chưa có.
     * Gọi sau khi đăng ký hoặc đăng nhập thành công.
     */
    public void seedDefaultCategoriesIfNeeded() {
        categoryRepository.seedDefaults();
    }

    public void ensureVirtualBudgetCategoriesIfNeeded() {
        categoryRepository.ensureVirtualOtherCategoryExists();
    }

    public void bootstrapLocalData() {
        authRepository.ensureLocalUserRecord();
        seedDefaultCategoriesIfNeeded();
        ensureVirtualBudgetCategoriesIfNeeded();
    }
}
