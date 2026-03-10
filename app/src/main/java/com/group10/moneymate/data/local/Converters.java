package com.group10.moneymate.data.local;

import androidx.room.TypeConverter;

import com.group10.moneymate.models.CategoryType;
import com.group10.moneymate.models.DebtStatus;
import com.group10.moneymate.models.DebtType;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.models.WalletType;

import java.util.Date;

public class Converters {

    // ── Date ─────────────────────────────────────────────────────────────────
    @TypeConverter
    public static Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public static Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }

    // ── TransactionType ──────────────────────────────────────────────────────
    @TypeConverter
    public static String fromTransactionType(TransactionType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    public static TransactionType toTransactionType(String value) {
        return value == null ? null : TransactionType.valueOf(value);
    }

    // ── WalletType ───────────────────────────────────────────────────────────
    @TypeConverter
    public static String fromWalletType(WalletType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    public static WalletType toWalletType(String value) {
        return value == null ? null : WalletType.valueOf(value);
    }

    // ── CategoryType ─────────────────────────────────────────────────────────
    @TypeConverter
    public static String fromCategoryType(CategoryType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    public static CategoryType toCategoryType(String value) {
        return value == null ? null : CategoryType.valueOf(value);
    }

    // ── DebtType ─────────────────────────────────────────────────────────────
    @TypeConverter
    public static String fromDebtType(DebtType type) {
        return type == null ? null : type.name();
    }

    @TypeConverter
    public static DebtType toDebtType(String value) {
        return value == null ? null : DebtType.valueOf(value);
    }

    // ── DebtStatus ───────────────────────────────────────────────────────────
    @TypeConverter
    public static String fromDebtStatus(DebtStatus status) {
        return status == null ? null : status.name();
    }

    @TypeConverter
    public static DebtStatus toDebtStatus(String value) {
        return value == null ? null : DebtStatus.valueOf(value);
    }
}