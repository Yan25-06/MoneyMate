package com.group10.moneymate.ui.transaction;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.DialogStatisticsCustomRangeBinding;
import com.group10.moneymate.databinding.FragmentReportTransactionListBinding;
import com.group10.moneymate.databinding.SheetStatisticsPeriodFilterBinding;
import com.group10.moneymate.ui.statistics.StatisticsViewModel;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.IconProvider;
import com.group10.moneymate.utils.MoneyMateDatePickerHelper;
import com.group10.moneymate.utils.TimeWindowUtils;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportTransactionListFragment extends Fragment {

    private static final String TYPE_EXPENSE = Constants.TYPE_EXPENSE;
    private static final String TYPE_INCOME = Constants.TYPE_INCOME;

    private FragmentReportTransactionListBinding binding;
    private TransactionViewModel viewModel;
    private ReportTransactionListFragmentArgs navArgs;
    private final List<TransactionEntity> allTransactions = new ArrayList<>();
    private final List<TransactionEntity> budgetScopedTransactions = new ArrayList<>();
    private final Map<String, WalletEntity> walletMap = new HashMap<>();
    private final Map<String, CategoryEntity> categoryMap = new HashMap<>();
    private boolean useOtherBudgetSource;
    private boolean statisticsLeafMode;
    private boolean includeChildCategories;
    private long currentStartDate;
    private long currentEndDate;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReportTransactionListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        navArgs = ReportTransactionListFragmentArgs.fromBundle(getArguments() != null ? getArguments() : new Bundle());
        useOtherBudgetSource = Constants.isOtherCategoryId(navArgs.getCategoryId());
        statisticsLeafMode = navArgs.getStatisticsLeafMode();
        includeChildCategories = navArgs.getIncludeChildCategories();
        currentStartDate = navArgs.getStartDate();
        currentEndDate = navArgs.getEndDate();

        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTransactions.setAdapter(new ConcatAdapter());
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.btnDateFilter.setOnClickListener(v -> showDateRangePicker());

        if (navArgs.getReportTitle() != null && !navArgs.getReportTitle().trim().isEmpty()) {
            binding.tvToolbarTitle.setText(navArgs.getReportTitle());
        }
        configureMode();

        applyWindowInsets();
        observeReferenceData();
        observeBudgetScopedTransactions();
        observeTransactions();
    }

    private void observeBudgetScopedTransactions() {
        if (!useOtherBudgetSource) {
            return;
        }
        viewModel.getTransactionsForBudget(
                navArgs.getCategoryId(),
                navArgs.getWalletId(),
                navArgs.getStartDate(),
                navArgs.getEndDate()
        ).observe(getViewLifecycleOwner(), transactions -> {
            budgetScopedTransactions.clear();
            if (transactions != null) {
                budgetScopedTransactions.addAll(transactions);
            }
            renderScreen();
        });
    }

    private void observeReferenceData() {
        viewModel.getWallets().observe(getViewLifecycleOwner(), wallets -> {
            walletMap.clear();
            if (wallets != null) {
                for (WalletEntity wallet : wallets) {
                    walletMap.put(wallet.getId(), wallet);
                }
            }
            renderScreen();
        });
        viewModel.getExpenseCategoriesIncludingDeleted().observe(getViewLifecycleOwner(), this::mergeCategories);
        viewModel.getIncomeCategoriesIncludingDeleted().observe(getViewLifecycleOwner(), this::mergeCategories);
    }

    private void mergeCategories(@Nullable List<CategoryEntity> categories) {
        if (categories != null) {
            for (CategoryEntity category : categories) {
                categoryMap.put(category.getId(), category);
            }
        }
        renderScreen();
    }

    private void observeTransactions() {
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), transactions -> {
            allTransactions.clear();
            if (transactions != null) {
                allTransactions.addAll(transactions);
            }
            renderScreen();
        });
    }

    private void renderScreen() {
        if (binding == null) {
            return;
        }
        List<TransactionEntity> filtered = filterTransactions();
        binding.cardSummary.setVisibility(statisticsLeafMode ? View.GONE : View.VISIBLE);
        if (!statisticsLeafMode) {
            renderSummary(filtered);
        }
        renderSections(filtered);
    }

    @NonNull
    private List<TransactionEntity> filterTransactions() {
        List<TransactionEntity> source = useOtherBudgetSource ? budgetScopedTransactions : allTransactions;
        List<TransactionEntity> filtered = new ArrayList<>();
        for (TransactionEntity transaction : source) {
            if (matchesFilters(transaction)) {
                filtered.add(transaction);
            }
        }
        filtered.sort(Comparator.comparingLong(TransactionEntity::getTimestamp).reversed());
        return filtered;
    }

    private void renderSummary(@NonNull List<TransactionEntity> filtered) {
        binding.tvResultCount.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvResultCount.setText(getResources().getQuantityString(
                R.plurals.report_transaction_list_result_count,
                filtered.size(),
                filtered.size()
        ));

        double income = 0d;
        double expense = 0d;
        for (TransactionEntity transaction : filtered) {
            if (TYPE_INCOME.equals(transaction.getType())) {
                income += transaction.getAmount();
            } else if (TYPE_EXPENSE.equals(transaction.getType())) {
                expense += transaction.getAmount();
            }
        }
        double net = income - expense;
        binding.tvIncomeValue.setText(CurrencyFormatter.format(income, "VND"));
        binding.tvExpenseValue.setText(CurrencyFormatter.format(expense, "VND"));
        binding.tvNetValue.setText(formatNetAmount(net));
        binding.tvNetValue.setTextColor(ContextCompat.getColor(
                requireContext(),
                net < 0d ? R.color.expense_red : R.color.statistics_text_primary
        ));
    }

    private void renderSections(@NonNull List<TransactionEntity> filtered) {
        if (filtered.isEmpty()) {
            binding.rvTransactions.setVisibility(View.GONE);
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.rvTransactions.setAdapter(new ConcatAdapter());
            return;
        }
        binding.rvTransactions.setVisibility(View.VISIBLE);
        binding.tvEmpty.setVisibility(View.GONE);

        Map<LocalDate, DaySection> grouped = new LinkedHashMap<>();
        for (TransactionEntity transaction : filtered) {
            LocalDate date = TimeWindowUtils.toDeviceLocalDate(transaction.getTimestamp());
            DaySection section = grouped.get(date);
            if (section == null) {
                section = new DaySection(date);
                grouped.put(date, section);
            }
            section.transactions.add(transaction);
            section.netTotal += TYPE_EXPENSE.equals(transaction.getType())
                    ? -transaction.getAmount()
                    : transaction.getAmount();
        }

        List<RecyclerView.Adapter<?>> adapters = new ArrayList<>();
        for (DaySection section : grouped.values()) {
            adapters.add(new ReportTransactionDayHeaderAdapter(section.toHeaderItem()));
            ReportTransactionAdapter adapter = new ReportTransactionAdapter();
            adapter.setPresentationMap(buildPresentationMap(section.transactions));
            adapter.setOnTransactionClickListener(this::openTransactionDetail);
            adapter.submitList(new ArrayList<>(section.transactions));
            adapters.add(adapter);
        }
        binding.rvTransactions.setAdapter(new ConcatAdapter(adapters));
    }

    @NonNull
    private Map<String, ReportTransactionAdapter.TransactionPresentation> buildPresentationMap(
            @NonNull List<TransactionEntity> transactions
    ) {
        Map<String, ReportTransactionAdapter.TransactionPresentation> presentations = new HashMap<>();
        for (TransactionEntity transaction : transactions) {
            CategoryEntity category = transaction.getCategoryId() != null
                    ? categoryMap.get(transaction.getCategoryId())
                    : null;
            String type = transaction.getType();
            int accentColor = resolveAccentColor(type, category);
            String title = category != null
                    ? category.getName()
                    : getFallbackCategoryName(type);
            String subtitle = transaction.getNote() != null && !transaction.getNote().trim().isEmpty()
                    ? transaction.getNote()
                    : getString(R.string.transaction_detail_no_note);

            presentations.put(transaction.getId(), new ReportTransactionAdapter.TransactionPresentation(
                    resolveIconRes(category, type),
                    accentColor,
                    title,
                    subtitle,
                    formatItemAmount(transaction.getAmount(), type),
                    accentColor
            ));
        }
        return presentations;
    }

    private void openTransactionDetail(@NonNull TransactionEntity transaction) {
        ReportTransactionListFragmentDirections.ActionReportTransactionListFragmentToTransactionDetailFragment action =
                ReportTransactionListFragmentDirections.actionReportTransactionListFragmentToTransactionDetailFragment();
        action.setTransactionId(transaction.getId());
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void configureMode() {
        binding.btnDateFilter.setVisibility(statisticsLeafMode ? View.VISIBLE : View.GONE);
    }

    private boolean matchesFilters(@NonNull TransactionEntity transaction) {
        long timestamp = transaction.getTimestamp();
        if (timestamp < currentStartDate || timestamp > currentEndDate) {
            return false;
        }
        if (navArgs.getWalletId() != null && !navArgs.getWalletId().equals(transaction.getWalletId())) {
            return false;
        }
        if (!matchesCategoryFilter(transaction)) {
            return false;
        }
        return matchesTransactionType(transaction);
    }

    private boolean matchesCategoryFilter(@NonNull TransactionEntity transaction) {
        if (useOtherBudgetSource || navArgs.getCategoryId() == null) {
            return true;
        }
        if (includeChildCategories) {
            return belongsToCategoryBranch(transaction, navArgs.getCategoryId());
        }
        return navArgs.getCategoryId().equals(transaction.getCategoryId());
    }

    private boolean matchesTransactionType(@NonNull TransactionEntity transaction) {
        return navArgs.getTransactionType() == null
                || navArgs.getTransactionType().trim().isEmpty()
                || navArgs.getTransactionType().equals(transaction.getType());
    }

    private boolean belongsToCategoryBranch(@NonNull TransactionEntity transaction,
                                            @NonNull String rootCategoryId) {
        if (rootCategoryId.equals(transaction.getCategoryId())) {
            return true;
        }
        if (transaction.getCategoryId() == null) {
            return false;
        }
        CategoryEntity category = categoryMap.get(transaction.getCategoryId());
        return category != null && rootCategoryId.equals(category.getParentId());
    }

    private void showDateRangePicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        SheetStatisticsPeriodFilterBinding sheetBinding = SheetStatisticsPeriodFilterBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheetBinding.getRoot());

        StatisticsViewModel.PeriodType selectedType = resolveCurrentPeriodType();
        updatePeriodSheetSelection(sheetBinding, selectedType);

        bindPeriodRow(sheetBinding.rowDay, dialog, () -> applyPresetPeriod(StatisticsViewModel.PeriodType.DAY));
        bindPeriodRow(sheetBinding.rowWeek, dialog, () -> applyPresetPeriod(StatisticsViewModel.PeriodType.WEEK));
        bindPeriodRow(sheetBinding.rowMonth, dialog, () -> applyPresetPeriod(StatisticsViewModel.PeriodType.MONTH));
        bindPeriodRow(sheetBinding.rowQuarter, dialog, () -> applyPresetPeriod(StatisticsViewModel.PeriodType.QUARTER));
        bindPeriodRow(sheetBinding.rowYear, dialog, () -> applyPresetPeriod(StatisticsViewModel.PeriodType.YEAR));
        bindPeriodRow(sheetBinding.rowAll, dialog, () -> applyPresetPeriod(StatisticsViewModel.PeriodType.ALL));
        sheetBinding.rowCustom.setOnClickListener(v -> {
            dialog.dismiss();
            showCustomRangeDialog();
        });

        dialog.show();
    }

    private void bindPeriodRow(@NonNull View row,
                               @NonNull Dialog dialog,
                               @NonNull Runnable action) {
        row.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
    }

    private void updatePeriodSheetSelection(@NonNull SheetStatisticsPeriodFilterBinding sheetBinding,
                                            @NonNull StatisticsViewModel.PeriodType periodType) {
        sheetBinding.ivCheckDay.setVisibility(periodType == StatisticsViewModel.PeriodType.DAY ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckWeek.setVisibility(periodType == StatisticsViewModel.PeriodType.WEEK ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckMonth.setVisibility(periodType == StatisticsViewModel.PeriodType.MONTH ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckQuarter.setVisibility(periodType == StatisticsViewModel.PeriodType.QUARTER ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckYear.setVisibility(periodType == StatisticsViewModel.PeriodType.YEAR ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckAll.setVisibility(periodType == StatisticsViewModel.PeriodType.ALL ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckCustom.setVisibility(periodType == StatisticsViewModel.PeriodType.CUSTOM ? View.VISIBLE : View.GONE);
    }

    private void applyPresetPeriod(@NonNull StatisticsViewModel.PeriodType periodType) {
        StatisticsViewModel.FilterState state = StatisticsViewModel.FilterState.createForPeriodType(
                periodType,
                navArgs.getWalletId()
        );
        currentStartDate = state.getStartDate();
        currentEndDate = state.getEndDate();
        renderScreen();
    }

    private void showCustomRangeDialog() {
        Dialog dialog = new Dialog(requireContext());
        DialogStatisticsCustomRangeBinding dialogBinding =
                DialogStatisticsCustomRangeBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        final LocalDate[] startDate = {toLocalDate(currentStartDate)};
        final LocalDate[] endDate = {toLocalDate(currentEndDate)};

        renderDateButton(dialogBinding.btnStartDate, startDate[0]);
        renderDateButton(dialogBinding.btnEndDate, endDate[0]);

        dialogBinding.btnStartDate.setOnClickListener(v ->
                showSingleDatePicker(startDate[0], pickedDate -> {
                    startDate[0] = pickedDate;
                    renderDateButton(dialogBinding.btnStartDate, pickedDate);
                }));
        dialogBinding.btnEndDate.setOnClickListener(v ->
                showSingleDatePicker(endDate[0], pickedDate -> {
                    endDate[0] = pickedDate;
                    renderDateButton(dialogBinding.btnEndDate, pickedDate);
                }));
        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnApply.setOnClickListener(v -> {
            if (endDate[0].isBefore(startDate[0])) {
                Snackbar.make(binding.getRoot(),
                        getString(R.string.statistics_custom_range_invalid),
                        Snackbar.LENGTH_SHORT).show();
                return;
            }
            currentStartDate = TimeWindowUtils.startOfDayLocalDateUtc(startDate[0]);
            currentEndDate = TimeWindowUtils.endOfDayLocalDateUtc(endDate[0]);
            renderScreen();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showSingleDatePicker(@NonNull LocalDate initialDate,
                                      @NonNull DateSelectedCallback callback) {
        MoneyMateDatePickerHelper.showSingleDatePicker(
                this,
                initialDate,
                "report_transaction_single_date",
                callback::onDateSelected
        );
    }

    private void renderDateButton(@NonNull android.widget.TextView textView, @NonNull LocalDate date) {
        if (date.equals(LocalDate.now())) {
            textView.setText(R.string.statistics_today);
            return;
        }
        textView.setText(String.format(Locale.getDefault(), "%02d/%02d/%d",
                date.getDayOfMonth(),
                date.getMonthValue(),
                date.getYear()));
    }

    @NonNull
    private StatisticsViewModel.PeriodType resolveCurrentPeriodType() {
        if (currentStartDate <= 0L || currentEndDate <= 0L || currentEndDate == Long.MAX_VALUE) {
            return StatisticsViewModel.PeriodType.ALL;
        }
        return StatisticsViewModel.PeriodType.CUSTOM;
    }

    @NonNull
    private LocalDate toLocalDate(long epochMillis) {
        if (epochMillis <= 0L || epochMillis == Long.MAX_VALUE) {
            return LocalDate.now();
        }
        return TimeWindowUtils.toDeviceLocalDate(epochMillis);
    }

    @NonNull
    private String formatNetAmount(double amount) {
        if (amount < 0d) {
            return "-" + CurrencyFormatter.format(Math.abs(amount), "VND");
        }
        return CurrencyFormatter.format(amount, "VND");
    }

    @NonNull
    private String formatItemAmount(double amount, @Nullable String type) {
        if (TYPE_EXPENSE.equals(type)) {
            return "-" + CurrencyFormatter.format(amount, "VND");
        }
        return CurrencyFormatter.format(amount, "VND");
    }

    private int resolveIconRes(@Nullable CategoryEntity category, @Nullable String type) {
        return IconProvider.resolveCategoryIconByType(
                requireContext(),
                category != null ? category.getIconName() : null,
                type
        );
    }

    @ColorInt
    private int resolveAccentColor(@Nullable String type, @Nullable CategoryEntity category) {
        if (TYPE_INCOME.equals(type)) {
            return ContextCompat.getColor(requireContext(), R.color.transfer_blue);
        }
        if (TYPE_EXPENSE.equals(type)) {
            return ContextCompat.getColor(requireContext(), R.color.expense_red);
        }
        return ContextCompat.getColor(requireContext(), R.color.statistics_text_primary);
    }

    @NonNull
    private String getFallbackCategoryName(@Nullable String type) {
        return getString("TRANSFER".equals(type)
                ? R.string.ledger_section_transfer
                : R.string.ledger_section_unknown);
    }

    private void applyWindowInsets() {
        final int initialTopPadding = binding.layoutTopBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTopBar, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    view.getPaddingLeft(),
                    initialTopPadding + systemBars.top,
                    view.getPaddingRight(),
                    view.getPaddingBottom()
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.layoutTopBar);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private class DaySection {
        @NonNull
        private final LocalDate date;
        @NonNull
        private final List<TransactionEntity> transactions = new ArrayList<>();
        private double netTotal;

        DaySection(@NonNull LocalDate date) {
            this.date = date;
        }

        @NonNull
        ReportTransactionDayHeaderAdapter.DayHeaderItem toHeaderItem() {
            return new ReportTransactionDayHeaderAdapter.DayHeaderItem(
                    String.format(Locale.getDefault(), "%02d", date.getDayOfMonth()),
                    capitalize(date.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("vi", "VN"))),
                    String.format(Locale.getDefault(), "tháng %d %d", date.getMonthValue(), date.getYear()),
                    CurrencyFormatter.format(Math.abs(netTotal), "VND")
            );
        }
    }

    @NonNull
    private String capitalize(@NonNull String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(new Locale("vi", "VN")) + value.substring(1);
    }

    private interface DateSelectedCallback {
        void onDateSelected(@NonNull LocalDate date);
    }
}
