package com.example.moneymate.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.example.moneymate.data.local.dao.BudgetDao;
import com.example.moneymate.data.local.dao.CategoryDao;
import com.example.moneymate.data.local.dao.TransactionDao;
import com.example.moneymate.data.local.dao.UserDao;
import com.example.moneymate.data.local.dao.WalletDao;
import com.example.moneymate.data.local.entity.BudgetEntity;
import com.example.moneymate.data.local.entity.CategoryEntity;
import com.example.moneymate.data.local.entity.TransactionEntity;
import com.example.moneymate.data.local.entity.UserEntity;
import com.example.moneymate.data.local.entity.WalletEntity;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(
    entities = {
        UserEntity.class,
        TransactionEntity.class,
        CategoryEntity.class,
        BudgetEntity.class,
        WalletEntity.class
    },
    version = 1,
    exportSchema = false
)
@TypeConverters({Converters.class})
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(4);

    public abstract UserDao userDao();
    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();
    public abstract BudgetDao budgetDao();
    public abstract WalletDao walletDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "moneymate_database"
                    ).build();
                }
            }
        }
        return INSTANCE;
    }
}
