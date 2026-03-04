package com.example.moneymate.di;

import android.content.Context;

import com.example.moneymate.data.local.AppDatabase;
import com.example.moneymate.data.remote.FirebaseAuthHelper;
import com.example.moneymate.data.repository.AuthRepository;
import com.example.moneymate.data.repository.BudgetRepository;
import com.example.moneymate.data.repository.CategoryRepository;
import com.example.moneymate.data.repository.TransactionRepository;
import com.example.moneymate.data.repository.UserRepository;
import com.example.moneymate.data.repository.WalletRepository;
import com.example.moneymate.utils.PrefsManager;

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
