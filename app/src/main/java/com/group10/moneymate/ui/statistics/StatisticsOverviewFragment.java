package com.group10.moneymate.ui.statistics;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.databinding.DialogStatisticsCustomRangeBinding;
import com.group10.moneymate.databinding.FragmentStatisticsBinding;
import com.group10.moneymate.databinding.LayoutStatisticsGroupPreviewBinding;
import com.group10.moneymate.databinding.SheetStatisticsPeriodFilterBinding;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.IconProvider;
import com.group10.moneymate.utils.MoneyMateDatePickerHelper;
import com.group10.moneymate.utils.WalletSelectorButtonHelper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StatisticsOverviewFragment extends Fragment {

    private static final String RESULT_SELECTED_WALLET_ID = "result_selected_wallet_id";
    private static final String RESULT_SELECTED_WALLET_LABEL = "result_selected_wallet_label";

    private FragmentStatisticsBinding binding;
    private StatisticsViewModel viewModel;
    private MoneyChartValueFormatter moneyChartValueFormatter;
    private StatisticsOverviewFragmentArgs navArgs;
    @Nullable
    private WalletEntity selectedWallet;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatisticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        AppContainer container = ((MoneyMateApplication) requireActivity().getApplication()).getAppContainer();
        StatisticsViewModel.Factory factory = new StatisticsViewModel.Factory(
                container.transactionRepository,
                container.walletRepository,
                container.authRepository.getCurrentUserId()
        );
        viewModel = new ViewModelProvider(this, factory).get(StatisticsViewModel.class);
        navArgs = StatisticsOverviewFragmentArgs.fromBundle(getArguments() != null ? getArguments() : new Bundle());
        moneyChartValueFormatter = new MoneyChartValueFormatter();

        applyWindowInsets();
        configureHeader();
        configureCharts();
        applyInitialFilterIfPresent();
        bindActions();
        observeViewModel();
    }

    private void applyInitialFilterIfPresent() {
        if (navArgs.getFilterPeriodType() == null
                && navArgs.getFilterStartDate() <= 0L
                && navArgs.getFilterEndDate() <= 0L
                && navArgs.getFilterWalletId() == null) {
            return;
        }
        viewModel.applyExternalFilter(
                navArgs.getFilterWalletId(),
                navArgs.getFilterWalletLabel(),
                navArgs.getFilterStartDate(),
                navArgs.getFilterEndDate(),
                navArgs.getFilterPeriodType()
        );
    }

    private void configureHeader() {
        binding.statisticsHeader.tvHeaderSummaryLabel.setText(R.string.total_balance);
        binding.statisticsHeader.tvHeaderTotalAmount.setText(R.string.default_currency_zero);
        binding.statisticsHeader.tvPeriodPrevious.setText(R.string.statistics_period_previous);
        binding.statisticsHeader.tvPeriodCurrent.setText(R.string.statistics_period_current);
        binding.statisticsHeader.tvPeriodNext.setText(R.string.statistics_period_next);
        updateWalletSelectorButton();
    }

    private void configureCharts() {
        configureBarChart(binding.chartNetIncome);
    }

    private void bindActions() {
        binding.cardNetIncomeAction.setOnClickListener(v -> openLevelTwoDetail("NET"));
        binding.cardReportIncome.setOnClickListener(v -> openLevelTwoDetail(TransactionType.INCOME.name()));
        binding.cardReportExpense.setOnClickListener(v -> openLevelTwoDetail(TransactionType.EXPENSE.name()));
        binding.statisticsHeader.btnHeaderBack.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        binding.statisticsHeader.btnPreviousPeriod.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-1));
        binding.statisticsHeader.btnNextPeriod.setOnClickListener(v -> viewModel.shiftCurrentPeriod(1));
        binding.statisticsHeader.tvPeriodPrevious.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-2));
        binding.statisticsHeader.tvPeriodCurrent.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-1));
        binding.statisticsHeader.tvPeriodNext.setOnClickListener(v -> UnitAction.run());
        binding.statisticsHeader.btnDateFilter.setOnClickListener(v -> showDateRangePicker());
        binding.statisticsHeader.btnWalletSelector.setOnClickListener(v -> openWalletPicker());
    }

    private void observeViewModel() {
        observeWalletPickerResult();
        viewModel.getHeaderBalance().observe(getViewLifecycleOwner(), balance -> {
            String formattedBalance = formatCurrency(balance);
            binding.statisticsHeader.tvHeaderTotalAmount.setText(formattedBalance);
            binding.tvClosingBalance.setText(formattedBalance);
        });
        viewModel.getWalletLabel().observe(getViewLifecycleOwner(), label -> updateWalletSelectorButton());
        viewModel.getSelectedWallet().observe(getViewLifecycleOwner(), wallet -> {
            selectedWallet = wallet;
            updateWalletSelectorButton();
        });
        viewModel.getFilterStateLiveData().observe(getViewLifecycleOwner(), filterState -> {
            if (filterState == null) {
                return;
            }
            renderHeaderTabs(filterState);
            binding.statisticsHeader.btnDateFilter.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.statistics_text_primary)
            ));
        });
        viewModel.getNetIncomeSummary().observe(getViewLifecycleOwner(), summary -> {
            if (summary == null) {
                renderNetIncomeCard(0d, 0d, 0d);
                binding.tvOpeningBalance.setText(getString(R.string.default_currency_zero));
                return;
            }
            renderNetIncomeCard(summary.getTotalIncome(), summary.getTotalExpense(), summary.getNetAmount());
            double headerBalance = viewModel.getHeaderBalance().getValue() != null
                    ? viewModel.getHeaderBalance().getValue()
                    : 0d;
            binding.tvOpeningBalance.setText(formatCurrency(headerBalance - summary.getNetAmount()));
        });
        viewModel.getTotalIncomeAmount().observe(getViewLifecycleOwner(),
                amount -> binding.tvReportIncomeAmount.setText(formatCurrency(amount)));
        viewModel.getTotalExpenseAmount().observe(getViewLifecycleOwner(),
                amount -> binding.tvReportExpenseAmount.setText(formatCurrency(amount)));
        viewModel.getIncomeCategorySums().observe(getViewLifecycleOwner(),
                items -> renderCategoryPreview(binding.incomePreview, items, false));
        viewModel.getExpenseCategorySums().observe(getViewLifecycleOwner(),
                items -> renderCategoryPreview(binding.expensePreview, items, true));
    }

    private void updateWalletSelectorButton() {
        if (binding == null) {
            return;
        }
        WalletSelectorButtonHelper.bindStatisticsWalletSelector(
                binding.statisticsHeader.btnWalletSelector,
                requireContext(),
                selectedWallet,
                viewModel != null ? viewModel.getWalletLabel().getValue() : null,
                R.string.statistics_wallet_selector_all
        );
    }

    private void renderNetIncomeCard(double totalIncome, double totalExpense, double netAmount) {
        binding.tvNetIncomeValue.setText(formatCurrency(Math.abs(netAmount)));
        int netColor = ContextCompat.getColor(
                requireContext(),
                netAmount >= 0 ? R.color.income_green : R.color.statistics_text_muted
        );
        binding.tvNetIncomeValue.setTextColor(netColor);
        Drawable signDrawable = ContextCompat.getDrawable(
                requireContext(),
                netAmount >= 0 ? R.drawable.outline_add_24 : R.drawable.outline_remove_24
        );
        if (signDrawable != null) {
            signDrawable = signDrawable.mutate();
            DrawableCompat.setTint(signDrawable, netColor);
        }
        binding.tvNetIncomeValue.setCompoundDrawablesRelativeWithIntrinsicBounds(signDrawable, null, null, null);
        binding.tvNetIncomeValue.setCompoundDrawablePadding((int) (requireContext().getResources().getDisplayMetrics().density * 6));
        binding.tvIncomeAmount.setText(formatCurrency(totalIncome));
        binding.tvExpenseAmount.setText(formatCurrency(totalExpense));

        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, (float) totalIncome));
        entries.add(new BarEntry(1f, (float) totalExpense));

        BarDataSet dataSet = new BarDataSet(entries, getString(R.string.statistics_net_income_title));
        dataSet.setColors(
                ContextCompat.getColor(requireContext(), R.color.transfer_blue),
                ContextCompat.getColor(requireContext(), R.color.expense_red)
        );
        dataSet.setValueFormatter(moneyChartValueFormatter);
        dataSet.setValueTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_primary));
        dataSet.setValueTextSize(11f);
        dataSet.setHighLightAlpha(0);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.42f);
        binding.chartNetIncome.setData(data);
        binding.chartNetIncome.getXAxis().setValueFormatter(
                new IndexAxisValueFormatter(new String[]{
                        getString(R.string.statistics_income_label),
                        getString(R.string.statistics_expense_label)
                })
        );
        binding.chartNetIncome.getAxisLeft().setAxisMaximum((float) Math.max(totalIncome, totalExpense) * 1.2f + 1f);
        binding.chartNetIncome.invalidate();
        binding.chartNetIncome.animateY(800, Easing.EaseInOutQuad);
    }

    private void renderCategoryPreview(@NonNull LayoutStatisticsGroupPreviewBinding previewBinding,
                                       @Nullable List<StatisticsViewModel.CategorySliceUiModel> categoryItems,
                                       boolean isExpense) {
        List<StatisticsDonutBreakdownView.Segment> segments = new ArrayList<>();
        double totalAmount = 0d;
        if (categoryItems != null) {
            for (StatisticsViewModel.CategorySliceUiModel item : categoryItems) {
                totalAmount += item.getTotalAmount();
                segments.add(new StatisticsDonutBreakdownView.Segment(
                        IconProvider.resolveCategoryIcon(requireContext(), item.getIconName()),
                        IconProvider.getCategoryColor(requireContext(), item.getCategoryId(), isExpense),
                        item.getTotalAmount()
                ));
            }
        }
        previewBinding.donutBreakdownView.setData(segments, totalAmount);
    }

    private void configureBarChart(@NonNull BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText(getString(R.string.statistics_no_data));
        chart.setDrawGridBackground(false);
        chart.setDrawValueAboveBar(true);
        chart.setFitBars(true);
        chart.setExtraBottomOffset(8f);
        chart.setExtraTopOffset(12f);
        chart.setTouchEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_muted));
        xAxis.setTextSize(12f);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(false);
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_muted));
        leftAxis.setValueFormatter(moneyChartValueFormatter);

        chart.getAxisRight().setEnabled(false);
    }

    private void openLevelTwoDetail(@Nullable String transactionTypeValue) {
        StatisticsOverviewFragmentDirections.ActionStatisticsFragmentToStatisticsDetailFragment action =
                StatisticsOverviewFragmentDirections.actionStatisticsFragmentToStatisticsDetailFragment();
        StatisticsViewModel.FilterState filterState = viewModel.getCurrentFilterState();
        action.setWalletId(filterState.getWalletId());
        action.setStartDate(filterState.getStartDate());
        action.setEndDate(filterState.getEndDate());
        action.setTransactionType(transactionTypeValue != null ? transactionTypeValue : TransactionType.EXPENSE.name());
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void applyWindowInsets() {
        final int initialTopPadding = binding.statisticsHeader.getRoot().getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.statisticsHeader.getRoot(), (headerView, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            headerView.setPadding(
                    headerView.getPaddingLeft(),
                    initialTopPadding + systemBars.top,
                    headerView.getPaddingRight(),
                    headerView.getPaddingBottom()
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.statisticsHeader.getRoot());
    }

    private void observeWalletPickerResult() {
        NavController navController = Navigation.findNavController(binding.getRoot());
        NavBackStackEntry currentBackStackEntry = navController.getCurrentBackStackEntry();
        if (currentBackStackEntry == null) {
            return;
        }
        currentBackStackEntry.getSavedStateHandle()
                .<String>getLiveData(RESULT_SELECTED_WALLET_ID)
                .observe(getViewLifecycleOwner(), walletId -> {
                    String walletLabel = currentBackStackEntry.getSavedStateHandle().get(RESULT_SELECTED_WALLET_LABEL);
                    viewModel.updateWalletFilter(walletId, walletLabel);
                    currentBackStackEntry.getSavedStateHandle().remove(RESULT_SELECTED_WALLET_ID);
                    currentBackStackEntry.getSavedStateHandle().remove(RESULT_SELECTED_WALLET_LABEL);
                });
    }

    private void openWalletPicker() {
        StatisticsOverviewFragmentDirections.ActionStatisticsFragmentToBudgetWalletPickerFragment action =
                StatisticsOverviewFragmentDirections.actionStatisticsFragmentToBudgetWalletPickerFragment();
        action.setSelectedWalletId(viewModel.getCurrentFilterState().getWalletId());
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void showDateRangePicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        SheetStatisticsPeriodFilterBinding sheetBinding = SheetStatisticsPeriodFilterBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheetBinding.getRoot());

        StatisticsViewModel.PeriodType selectedType = viewModel.getCurrentFilterState().getPeriodType();
        updatePeriodSheetSelection(sheetBinding, selectedType);

        bindPeriodRow(sheetBinding.rowDay, dialog, () -> viewModel.updatePresetPeriod(StatisticsViewModel.PeriodType.DAY));
        bindPeriodRow(sheetBinding.rowWeek, dialog, () -> viewModel.updatePresetPeriod(StatisticsViewModel.PeriodType.WEEK));
        bindPeriodRow(sheetBinding.rowMonth, dialog, () -> viewModel.updatePresetPeriod(StatisticsViewModel.PeriodType.MONTH));
        bindPeriodRow(sheetBinding.rowQuarter, dialog, () -> viewModel.updatePresetPeriod(StatisticsViewModel.PeriodType.QUARTER));
        bindPeriodRow(sheetBinding.rowYear, dialog, () -> viewModel.updatePresetPeriod(StatisticsViewModel.PeriodType.YEAR));
        bindPeriodRow(sheetBinding.rowAll, dialog, () -> viewModel.updatePresetPeriod(StatisticsViewModel.PeriodType.ALL));
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

    private void showCustomRangeDialog() {
        Dialog dialog = new Dialog(requireContext());
        DialogStatisticsCustomRangeBinding dialogBinding =
                DialogStatisticsCustomRangeBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        StatisticsViewModel.FilterState currentFilter = viewModel.getCurrentFilterState();
        final LocalDate[] startDate = {toLocalDate(currentFilter.getStartDate())};
        final LocalDate[] endDate = {toLocalDate(currentFilter.getEndDate())};

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
            viewModel.updateCustomDateRange(
                    startDate[0].atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    endDate[0].plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1L
            );
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showSingleDatePicker(@NonNull LocalDate initialDate,
                                      @NonNull DateSelectedCallback callback) {
        MoneyMateDatePickerHelper.showSingleDatePicker(
                this,
                initialDate,
                "statistics_overview_single_date",
                callback::onDateSelected
        );
    }

    private void renderDateButton(@NonNull TextView textView, @NonNull LocalDate date) {
        if (date.equals(LocalDate.now())) {
            textView.setText(R.string.statistics_today);
            return;
        }
        textView.setText(String.format(Locale.getDefault(), "%02d/%02d/%d",
                date.getDayOfMonth(),
                date.getMonthValue(),
                date.getYear()));
    }

    private void renderHeaderTabs(@NonNull StatisticsViewModel.FilterState filterState) {
        if (filterState.getPeriodType() == StatisticsViewModel.PeriodType.ALL) {
            binding.statisticsHeader.tvPeriodPrevious.setVisibility(View.GONE);
            binding.statisticsHeader.tvPeriodCurrent.setVisibility(View.GONE);
            binding.statisticsHeader.tvPeriodNext.setVisibility(View.VISIBLE);
            binding.statisticsHeader.tvPeriodNext.setText(filterState.getDisplayLabel());
            applyTabStyle(binding.statisticsHeader.tvPeriodNext, true);
            binding.statisticsHeader.btnPreviousPeriod.setVisibility(View.GONE);
            binding.statisticsHeader.btnNextPeriod.setVisibility(View.GONE);
            return;
        }

        binding.statisticsHeader.tvPeriodPrevious.setVisibility(View.VISIBLE);
        binding.statisticsHeader.tvPeriodCurrent.setVisibility(View.VISIBLE);
        binding.statisticsHeader.tvPeriodNext.setVisibility(View.VISIBLE);
        binding.statisticsHeader.btnPreviousPeriod.setVisibility(View.VISIBLE);
        binding.statisticsHeader.btnNextPeriod.setVisibility(View.VISIBLE);

        binding.statisticsHeader.tvPeriodPrevious.setText(filterState.shift(-2).getDisplayLabel());
        binding.statisticsHeader.tvPeriodCurrent.setText(filterState.shift(-1).getDisplayLabel());
        binding.statisticsHeader.tvPeriodNext.setText(filterState.getDisplayLabel());
        applyTabStyle(binding.statisticsHeader.tvPeriodPrevious, false);
        applyTabStyle(binding.statisticsHeader.tvPeriodCurrent, false);
        applyTabStyle(binding.statisticsHeader.tvPeriodNext, true);

        boolean canMoveForward = canMoveForward(filterState);
        binding.statisticsHeader.btnNextPeriod.setEnabled(canMoveForward);
        binding.statisticsHeader.btnNextPeriod.setAlpha(canMoveForward ? 1f : 0.35f);
    }

    private void applyTabStyle(@NonNull TextView tabView, boolean selected) {
        tabView.setBackgroundResource(selected
                ? R.drawable.bg_statistics_period_nav_item_selected
                : R.drawable.bg_statistics_period_nav_item);
        tabView.setTextColor(ContextCompat.getColor(requireContext(),
                selected ? R.color.statistics_text_primary : R.color.statistics_text_muted));
    }

    private boolean canMoveForward(@NonNull StatisticsViewModel.FilterState filterState) {
        StatisticsViewModel.FilterState latest = StatisticsViewModel.FilterState.createForPeriodType(
                filterState.getPeriodType(),
                filterState.getWalletId()
        );
        if (filterState.getPeriodType() == StatisticsViewModel.PeriodType.CUSTOM) {
            return filterState.getEndDate() < endOfToday();
        }
        return filterState.getStartDate() < latest.getStartDate()
                || filterState.getEndDate() < latest.getEndDate();
    }

    private long endOfToday() {
        return LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() - 1L;
    }

    @NonNull
    private String formatCurrency(@Nullable Double amount) {
        return CurrencyFormatter.format(amount != null ? amount : 0d, "VND");
    }


    @NonNull
    private LocalDate toLocalDate(long epochMillis) {
        if (epochMillis <= 0L || epochMillis == Long.MAX_VALUE) {
            return LocalDate.now();
        }
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class MoneyChartValueFormatter extends ValueFormatter {
        @NonNull
        @Override
        public String getFormattedValue(float value) {
            return CurrencyFormatter.format(value, "VND");
        }
    }

    private interface DateSelectedCallback {
        void onDateSelected(@NonNull LocalDate date);
    }

    private static final class UnitAction {
        private UnitAction() {
        }

        private static void run() {
        }
    }
}
