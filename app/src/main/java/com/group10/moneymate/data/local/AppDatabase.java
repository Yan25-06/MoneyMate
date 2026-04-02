package com.group10.moneymate.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.group10.moneymate.data.local.dao.BudgetDao;
import com.group10.moneymate.data.local.dao.CategoryDao;
import com.group10.moneymate.data.local.dao.DebtDao;
import com.group10.moneymate.data.local.dao.EventDao;
import com.group10.moneymate.data.local.dao.SyncMetadataDao;
import com.group10.moneymate.data.local.dao.TransactionDao;
import com.group10.moneymate.data.local.dao.UserDao;
import com.group10.moneymate.data.local.dao.WalletDao;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.local.entity.EventEntity;
import com.group10.moneymate.data.local.entity.SyncMetadataEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.local.migrations.Migration10To11;
import com.group10.moneymate.data.local.migrations.Migration7To8;
import com.group10.moneymate.data.local.migrations.Migration8To9;
import com.group10.moneymate.data.local.migrations.Migration9To10;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
    entities = {
        UserEntity.class,
        WalletEntity.class,
        CategoryEntity.class,
        TransactionEntity.class,
        BudgetEntity.class,
        DebtEntity.class,
        EventEntity.class,
        SyncMetadataEntity.class
    },
    version = 11,
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    public abstract UserDao userDao();
    public abstract WalletDao walletDao();
    public abstract CategoryDao categoryDao();
    public abstract TransactionDao transactionDao();
    public abstract BudgetDao budgetDao();
    public abstract DebtDao debtDao();
    public abstract EventDao eventDao();
    public abstract SyncMetadataDao syncMetadataDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "moneymate_database"
                    )
                    .addMigrations(
                            Migration7To8.MIGRATION_7_8,
                            Migration8To9.MIGRATION_8_9,
                            Migration9To10.MIGRATION_9_10,
                            Migration10To11.MIGRATION_10_11
                    )
                    .build();
                }
            }
        }
        return INSTANCE;
    }
}
