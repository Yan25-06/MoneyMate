package com.group10.moneymate.di;

import android.content.Context;

import com.group10.moneymate.BuildConfig;
import com.group10.moneymate.ai.receipt.GeminiService;
import com.group10.moneymate.ai.receipt.MlKitReceiptParserBridge;
import com.group10.moneymate.ai.receipt.ReceiptParserBridge;
import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.remote.SupabaseAuthHelper;
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
    public final SupabaseAuthHelper supabaseAuthHelper;   // thay firebaseAuthHelper
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
    public final ReceiptParserBridge receiptParserBridge;
    public final GeminiService geminiService;

    public AppContainer(Context context) {
        database             = AppDatabase.getInstance(context);
        prefsManager         = new PrefsManager(context);
        syncScheduler        = new SyncScheduler(context.getApplicationContext());
        receiptParserBridge  = new MlKitReceiptParserBridge();
        geminiService        = new GeminiService(BuildConfig.GEMINI_API_KEY);

        // Supabase – URL và ANON KEY đọc từ BuildConfig
        // (thêm vào local.properties + build.gradle.kts, xem README migration)
        supabaseAuthHelper   = new SupabaseAuthHelper(
                BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_ANON_KEY
        );

        authRepository        = new AuthRepository(supabaseAuthHelper, database.userDao(), prefsManager);
        userRepository        = new UserRepository(database.userDao());
        walletRepository      = new WalletRepository(database.walletDao());
        categoryRepository    = new CategoryRepository(database.categoryDao());
        transactionRepository = new TransactionRepository(
                database,
                database.transactionDao(),
                database.walletDao(),
                syncScheduler
        );
        budgetRepository      = new BudgetRepository(database.budgetDao(), database, syncScheduler);
        debtRepository        = new DebtRepository(database.debtDao());
        eventRepository       = new EventRepository(database.eventDao());
        syncMetadataRepository = new SyncMetadataRepository(database.syncMetadataDao());
    }

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