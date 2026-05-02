package com.group10.moneymate.workers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.Nullable;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.utils.NotificationHelper;
import com.group10.moneymate.utils.NotificationPreferenceManager;
import com.group10.moneymate.utils.PrefsManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Nhận alarm kiểm tra ngân sách hàng ngày.
 * Tìm các ngân sách active có chi tiêu >= ngưỡng % đã cài đặt và thông báo.
 */
public class BudgetCheckerReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationPreferenceManager prefs = NotificationPreferenceManager.getInstance(context);
        if (!prefs.isGlobalEnabled() || !prefs.isBudgetEnabled()) {
            NotificationScheduler.scheduleBudgetChecker(context);
            return;
        }

        final PendingResult pendingResult = goAsync();
        AppDatabase.databaseWriteExecutor.execute(() -> {
            try {
                String userId = new PrefsManager(context).getUid();
                if (userId == null || userId.isEmpty()) return;

                long now = System.currentTimeMillis();
                AppDatabase db = AppDatabase.getInstance(context);

                List<BudgetEntity> activeBudgets = db.budgetDao()
                        .getActiveBudgetsSync(userId, now);

                int threshold = prefs.getBudgetThresholdPercent();
                List<NotificationHelper.BudgetAlertInfo> alerts = new ArrayList<>();

                for (BudgetEntity budget : activeBudgets) {
                    double spent = db.transactionDao().getTotalExpenseByCategoryAndWalletSync(
                            userId,
                            budget.getCategoryId(),
                            budget.getWalletId(),
                            budget.getStartDate(),
                            budget.getEndDate()
                    );
                    double percent = budget.getAmount() > 0
                            ? (spent / budget.getAmount()) * 100.0
                            : 0;

                    // Thông báo khi >= threshold% hoặc đã vượt
                    if (percent >= threshold) {
                        String budgetName = resolveBudgetName(db, budget);
                        alerts.add(new NotificationHelper.BudgetAlertInfo(
                                budgetName, budget.getAmount(), spent));
                    }
                }

                if (!alerts.isEmpty()) {
                    NotificationHelper.showBudgetAlertNotification(context, alerts);
                }
            } finally {
                pendingResult.finish();
                // Reschedule cho ngày mai
                NotificationScheduler.scheduleBudgetChecker(context);
            }
        });
    }

    @Nullable
    private String resolveBudgetName(@androidx.annotation.NonNull AppDatabase db,
                                     @androidx.annotation.NonNull BudgetEntity budget) {
        if (budget.getCategoryId() == null) return "Tổng ngân sách";
        CategoryEntity category = db.categoryDao().getCategoryByIdSync(budget.getCategoryId());
        return category != null ? category.getName() : "Ngân sách";
    }
}
