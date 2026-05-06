package com.group10.moneymate.ui.notification;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.group10.moneymate.R;
import com.group10.moneymate.utils.NotificationPreferenceManager;
import com.group10.moneymate.workers.NotificationScheduler;

import java.util.Locale;

/**
 * Màn hình cài đặt thông báo.
 * Cho phép bật/tắt từng loại thông báo và cấu hình thời gian nhắc.
 */
public class NotificationSettingsFragment extends Fragment {

    private NotificationPreferenceManager prefs;

    // Views — Global
    private SwitchMaterial swGlobal;

    private final androidx.activity.result.ActivityResultLauncher<String> requestPermissionLauncher =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.RequestPermission(), isGranted -> {
                if (isGranted) {
                    prefs.setGlobalEnabled(true);
                    if (swGlobal != null) swGlobal.setChecked(true);
                    updateSubsectionEnabled(true);
                    NotificationScheduler.scheduleAll(requireContext());
                } else {
                    prefs.setGlobalEnabled(false);
                    if (swGlobal != null) swGlobal.setChecked(false);
                }
            });

    // Views — Daily Entry
    private SwitchMaterial swDailyEntry;
    private View rowDailyEntryTime;
    private TextView tvDailyEntryTime;

    // Views — Debt
    private SwitchMaterial swDebt;
    private View rowDebtTime;
    private TextView tvDebtTime;

    // Views — Budget
    private SwitchMaterial swBudget;
    private View rowBudgetThreshold;
    private TextView tvBudgetThreshold;
    private View rowBudgetTime;
    private TextView tvBudgetTime;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_notification_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        prefs = NotificationPreferenceManager.getInstance(requireContext());

        setupToolbar(view);
        bindViews(view);
        loadPreferences();
        setupListeners();
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private void setupToolbar(@NonNull View view) {
        MaterialToolbar toolbar = view.findViewById(R.id.tb_notification_settings);
        toolbar.setNavigationOnClickListener(v -> {
            if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            } else {
                requireActivity().onBackPressed();
            }
        });
    }

    private void bindViews(@NonNull View view) {
        swGlobal = view.findViewById(R.id.sw_global_enabled);

        swDailyEntry = view.findViewById(R.id.sw_daily_entry_enabled);
        rowDailyEntryTime = view.findViewById(R.id.row_daily_entry_time);
        tvDailyEntryTime = view.findViewById(R.id.tv_daily_entry_time);

        swDebt = view.findViewById(R.id.sw_debt_enabled);
        rowDebtTime = view.findViewById(R.id.row_debt_time);
        tvDebtTime = view.findViewById(R.id.tv_debt_time);

        swBudget = view.findViewById(R.id.sw_budget_enabled);
        rowBudgetThreshold = view.findViewById(R.id.row_budget_threshold);
        tvBudgetThreshold = view.findViewById(R.id.tv_budget_threshold);
        rowBudgetTime = view.findViewById(R.id.row_budget_time);
        tvBudgetTime = view.findViewById(R.id.tv_budget_time);
    }

    private boolean hasNotificationPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            return androidx.core.content.ContextCompat.checkSelfPermission(requireContext(), android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void loadPreferences() {
        boolean hasPerm = hasNotificationPermission();
        boolean globalEnabled = prefs.isGlobalEnabled() && hasPerm;
        
        if (prefs.isGlobalEnabled() && !hasPerm) {
            prefs.setGlobalEnabled(false);
        }

        // Global
        swGlobal.setChecked(globalEnabled);

        // Daily Entry
        swDailyEntry.setChecked(prefs.isDailyEntryEnabled());
        tvDailyEntryTime.setText(formatTime(prefs.getDailyEntryHour(), prefs.getDailyEntryMinute()));

        // Debt
        swDebt.setChecked(prefs.isDebtEnabled());
        tvDebtTime.setText(formatTime(prefs.getDebtHour(), prefs.getDebtMinute()));

        // Budget
        swBudget.setChecked(prefs.isBudgetEnabled());
        tvBudgetThreshold.setText(prefs.getBudgetThresholdPercent() + "%");
        tvBudgetTime.setText(formatTime(prefs.getBudgetHour(), prefs.getBudgetMinute()));

        // Cập nhật trạng thái enabled của các row con theo master toggle
        updateSubsectionEnabled(globalEnabled);
    }

    private void setupListeners() {
        // Global toggle
        swGlobal.setOnClickListener(v -> {
            boolean isChecked = swGlobal.isChecked();
            if (isChecked && !hasNotificationPermission()) {
                swGlobal.setChecked(false);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS);
                }
            } else {
                prefs.setGlobalEnabled(isChecked);
                updateSubsectionEnabled(isChecked);
                if (isChecked) {
                    NotificationScheduler.scheduleAll(requireContext());
                } else {
                    // Cần huỷ lịch nếu tắt - chưa triển khai ở đây, nhưng logic scheduleAll sẽ bỏ qua nếu disable.
                }
            }
        });

        // Daily Entry toggle
        swDailyEntry.setOnCheckedChangeListener((btn, checked) -> {
            prefs.setDailyEntryEnabled(checked);
            rowDailyEntryTime.setAlpha(checked ? 1f : 0.4f);
            rowDailyEntryTime.setEnabled(checked);
            NotificationScheduler.scheduleDailyEntry(requireContext());
        });

        // Daily Entry time
        rowDailyEntryTime.setOnClickListener(v -> showTimePicker(
                prefs.getDailyEntryHour(), prefs.getDailyEntryMinute(),
                (h, m) -> {
                    prefs.setDailyEntryTime(h, m);
                    tvDailyEntryTime.setText(formatTime(h, m));
                    NotificationScheduler.scheduleDailyEntry(requireContext());
                }
        ));

        // Debt toggle
        swDebt.setOnCheckedChangeListener((btn, checked) -> {
            prefs.setDebtEnabled(checked);
            rowDebtTime.setAlpha(checked ? 1f : 0.4f);
            rowDebtTime.setEnabled(checked);
            NotificationScheduler.scheduleDebtReminder(requireContext());
        });

        // Debt time
        rowDebtTime.setOnClickListener(v -> showTimePicker(
                prefs.getDebtHour(), prefs.getDebtMinute(),
                (h, m) -> {
                    prefs.setDebtTime(h, m);
                    tvDebtTime.setText(formatTime(h, m));
                    NotificationScheduler.scheduleDebtReminder(requireContext());
                }
        ));

        // Budget toggle
        swBudget.setOnCheckedChangeListener((btn, checked) -> {
            prefs.setBudgetEnabled(checked);
            rowBudgetThreshold.setAlpha(checked ? 1f : 0.4f);
            rowBudgetThreshold.setEnabled(checked);
            rowBudgetTime.setAlpha(checked ? 1f : 0.4f);
            rowBudgetTime.setEnabled(checked);
            NotificationScheduler.scheduleBudgetChecker(requireContext());
        });

        // Budget threshold
        rowBudgetThreshold.setOnClickListener(v -> showThresholdPicker());

        // Budget time
        rowBudgetTime.setOnClickListener(v -> showTimePicker(
                prefs.getBudgetHour(), prefs.getBudgetMinute(),
                (h, m) -> {
                    prefs.setBudgetTime(h, m);
                    tvBudgetTime.setText(formatTime(h, m));
                    NotificationScheduler.scheduleBudgetChecker(requireContext());
                }
        ));
    }

    // ─── Helper methods ───────────────────────────────────────────────────────

    private void updateSubsectionEnabled(boolean globalEnabled) {
        float alpha = globalEnabled ? 1f : 0.4f;
        swDailyEntry.setEnabled(globalEnabled);
        rowDailyEntryTime.setEnabled(globalEnabled && prefs.isDailyEntryEnabled());
        swDebt.setEnabled(globalEnabled);
        rowDebtTime.setEnabled(globalEnabled && prefs.isDebtEnabled());
        swBudget.setEnabled(globalEnabled);
        rowBudgetThreshold.setEnabled(globalEnabled && prefs.isBudgetEnabled());
        rowBudgetTime.setEnabled(globalEnabled && prefs.isBudgetEnabled());

        // Visual feedback
        swDailyEntry.setAlpha(alpha);
        rowDailyEntryTime.setAlpha(globalEnabled ? (prefs.isDailyEntryEnabled() ? 1f : 0.4f) : 0.4f);
        swDebt.setAlpha(alpha);
        rowDebtTime.setAlpha(globalEnabled ? (prefs.isDebtEnabled() ? 1f : 0.4f) : 0.4f);
        swBudget.setAlpha(alpha);
        rowBudgetThreshold.setAlpha(globalEnabled ? (prefs.isBudgetEnabled() ? 1f : 0.4f) : 0.4f);
        rowBudgetTime.setAlpha(globalEnabled ? (prefs.isBudgetEnabled() ? 1f : 0.4f) : 0.4f);
    }

    private void showTimePicker(int currentHour, int currentMinute, TimePickedCallback callback) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(currentHour)
                .setMinute(currentMinute)
                .setTitleText(R.string.notif_time_label)
                .build();

        picker.addOnPositiveButtonClickListener(v -> {
            callback.onTimePicked(picker.getHour(), picker.getMinute());
        });

        picker.show(getChildFragmentManager(), "TIME_PICKER");
    }

    private void showThresholdPicker() {
        int[] values = {50, 55, 60, 65, 70, 75, 80, 85, 90, 95};
        String[] displayValues = new String[values.length];
        int currentThreshold = prefs.getBudgetThresholdPercent();
        int selectedIndex = 6; // default 80%
        for (int i = 0; i < values.length; i++) {
            displayValues[i] = values[i] + "%";
            if (values[i] == currentThreshold) selectedIndex = i;
        }

        final int[] pickedIndex = {selectedIndex};
        NumberPicker picker = new NumberPicker(requireContext());
        picker.setMinValue(0);
        picker.setMaxValue(values.length - 1);
        picker.setDisplayedValues(displayValues);
        picker.setValue(selectedIndex);
        picker.setWrapSelectorWheel(false);
        picker.setOnValueChangedListener((np, old, newVal) -> pickedIndex[0] = newVal);

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.notif_budget_threshold_label))
                .setView(picker)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int percent = values[pickedIndex[0]];
                    prefs.setBudgetThresholdPercent(percent);
                    tvBudgetThreshold.setText(percent + "%");
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private String formatTime(int hour, int minute) {
        return String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
    }

    // ─── Callback ─────────────────────────────────────────────────────────────

    @FunctionalInterface
    private interface TimePickedCallback {
        void onTimePicked(int hour, int minute);
    }
}
