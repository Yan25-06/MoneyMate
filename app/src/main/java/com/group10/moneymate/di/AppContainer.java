package com.group10.moneymate.di;

import android.content.Context;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.remote.FirebaseAuthHelper;
import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.data.repository.BudgetRepository;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.data.repository.UserRepository;
import com.group10.moneymate.data.repository.WalletRepository;
import com.group10.moneymate.utils.PrefsManager;

/**
 * Manual dependency injection container.
 * Provides singleton instances of database, helpers, and repositories.
 */
public class AppContainer {
    // Database
    public final AppDatabase database;

    // Helpers
    public final FirebaseAuthHelper firebaseAuthHelper;
    public final PrefsManager prefsManager;

    // Repositories
    public final AuthRepository authRepository;
    public final UserRepository userRepository;
    public final TransactionRepository transactionRepository;
    public final CategoryRepository categoryRepository;
    public final BudgetRepository budgetRepository;
    public final WalletRepository walletRepository;

    public AppContainer(Context context) {
        // Initialize database
        database = AppDatabase.getInstance(context);

        // Initialize helpers
        firebaseAuthHelper = new FirebaseAuthHelper();
        prefsManager = new PrefsManager(context);

        // Initialize repositories
        authRepository = new AuthRepository(firebaseAuthHelper);
        userRepository = new UserRepository(database.userDao());
        transactionRepository = new TransactionRepository(database.transactionDao());
        categoryRepository = new CategoryRepository(database.categoryDao());
        budgetRepository = new BudgetRepository(database.budgetDao());
        walletRepository = new WalletRepository(database.walletDao());
    }
}
