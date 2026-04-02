package com.group10.moneymate.ui.statistics;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
import androidx.recyclerview.widget.LinearLayoutManager;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.github.mikephil.charting.formatter.ValueFormatter;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.group10.moneymate.R;
import com.group10.moneymate.databinding.DialogStatisticsCustomRangeBinding;
import com.group10.moneymate.databinding.FragmentStatisticsLeafCategoryReportBinding;
import com.group10.moneymate.databinding.SheetStatisticsPeriodFilterBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.MoneyMateDatePickerHelper;
import com.group10.moneymate.utils.TimeWindowUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StatisticsCategoryDayDetailFragment extends Fragment {

    private FragmentStatisticsLeafCategoryReportBinding binding;
    private StatisticsCategoryDayDetailViewModel viewModel;
    private StatisticsPeriodSummaryAdapter trendPeriodsAdapter;
    private AbsoluteCurrencyValueFormatter absoluteCurrencyValueFormatter;
    private boolean isComparisonVisible;
    private boolean hasTrendData;
    @Nullable
    private IncomeExpenseDetailViewModel.ComparisonPointUiModel latestComparisonPoint;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatisticsLeafCategoryReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        StatisticsCategoryDayDetailFragmentArgs args = StatisticsCategoryDayDetailFragmentArgs.fromBundle(
                getArguments() == null ? new Bundle() : getArguments()
        );
        AppContainer container = ((MoneyMateApplication) requireActivity().getApplication()).getAppContainer();
        StatisticsCategoryDayDetailViewModel.Factory factory =
                new StatisticsCategoryDayDetailViewModel.Factory(
                        container.transactionRepository,
                        container.authRepository.getCurrentUserId(),
                        args.getWalletId(),
                        args.getStartDate(),
                        args.getEndDate(),
                        args.getTransactionType(),
                        args.getCategoryId()
        );
        viewModel = new ViewModelProvider(this, factory).get(StatisticsCategoryDayDetailViewModel.class);
        absoluteCurrencyValueFormatter = new AbsoluteCurrencyValueFormatter();
        trendPeriodsAdapter = new StatisticsPeriodSummaryAdapter(
                StatisticsPeriodSummaryAdapter.DisplayMode.SINGLE,
                viewModel.getSelectedTransactionType()
        );

        applyWindowInsets();
        configureHeader(args);
        configureRecyclerViews();
        configureCharts();
        bindActions(args);
        observeViewModel();
    }

    private void configureHeader(@NonNull StatisticsCategoryDayDetailFragmentArgs args) {
        binding.statisticsHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.statisticsHeader.tvHeaderTitle.setVisibility(View.VISIBLE);
        binding.statisticsHeader.tvHeaderTitle.setText(
                args.getCategoryName() == null || args.getCategoryName().trim().isEmpty()
                        ? getString(R.string.statistics_leaf_detail_title)
                        : args.getCategoryName()
        );
        binding.statisticsHeader.btnWalletSelector.setVisibility(View.GONE);
        binding.statisticsHeader.btnHeaderSecondarySelector.setVisibility(View.GONE);
        binding.statisticsHeader.layoutHeaderSummary.setVisibility(View.GONE);
        binding.statisticsHeader.layoutPeriodNavigator.setVisibility(View.VISIBLE);
        binding.layoutDetailContent.setVisibility(View.GONE);
        binding.layoutTrendContent.setVisibility(View.VISIBLE);
    }

    private void configureRecyclerViews() {
        binding.recyclerTrendPeriods.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerTrendPeriods.setAdapter(trendPeriodsAdapter);
        binding.recyclerTrendPeriods.setNestedScrollingEnabled(false);
    }

    private void configureCharts() {
        configureBidirectionalBarChart(binding.chartTrend);
        configureLineChart(binding.chartComparison);
    }

    private void bindActions(@NonNull StatisticsCategoryDayDetailFragmentArgs args) {
        binding.statisticsHeader.btnHeaderBack.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        binding.statisticsHeader.btnDateFilter.setOnClickListener(v -> showDateRangePicker());
        binding.statisticsHeader.btnPreviousPeriod.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-1));
        binding.statisticsHeader.btnNextPeriod.setOnClickListener(v -> viewModel.shiftCurrentPeriod(1));
        binding.statisticsHeader.tvPeriodPrevious.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-2));
        binding.statisticsHeader.tvPeriodCurrent.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-1));
        binding.statisticsHeader.tvPeriodNext.setOnClickListener(v -> showDateRangePicker());
        binding.btnToggleComparison.setOnClickListener(v -> {
            isComparisonVisible = !isComparisonVisible;
            updateComparisonVisibility();
        });
        trendPeriodsAdapter.setOnItemClickListener(item -> openTransactionList(item, args.getCategoryName()));
    }

    private void observeViewModel() {
        viewModel.getFilterStateLiveData().observe(getViewLifecycleOwner(), filterState -> {
            if (filterState == null) {
                return;
            }
            renderHeaderTabs(filterState);
            if (!viewModel.shouldShowComparisonCard()) {
                isComparisonVisible = false;
            }
            updateComparisonVisibility();
        });

        viewModel.getTotalAmount().observe(getViewLifecycleOwner(), amount -> {
            int accentColor = ContextCompat.getColor(
                    requireContext(),
                    viewModel.getSelectedTransactionType() == TransactionType.INCOME
                            ? R.color.transfer_blue
                            : R.color.expense_red
            );
            binding.tvHighlightTotalValue.setText(CurrencyFormatter.format(amount != null ? amount : 0d, "VND"));
            binding.tvHighlightTotalValue.setTextColor(accentColor);
        });

        viewModel.getAveragePerDay().observe(getViewLifecycleOwner(), amount -> {
            int accentColor = ContextCompat.getColor(
                    requireContext(),
                    viewModel.getSelectedTransactionType() == TransactionType.INCOME
                            ? R.color.transfer_blue
                            : R.color.expense_red
            );
            binding.tvHighlightAverageValue.setText(CurrencyFormatter.format(amount != null ? amount : 0d, "VND"));
            binding.tvHighlightAverageValue.setTextColor(accentColor);
        });

        viewModel.getTrendSummaries().observe(getViewLifecycleOwner(), items -> {
            List<IncomeExpenseDetailViewModel.PeriodSummaryUiModel> safeItems =
                    items != null ? items : new ArrayList<>();
            hasTrendData = !safeItems.isEmpty();
            trendPeriodsAdapter.submitList(safeItems, this::updateEmptyState);
            renderTrendChart(safeItems);
        });

        viewModel.getComparisonPoints().observe(getViewLifecycleOwner(), this::renderComparisonChart);
    }

    private void openTransactionList(@NonNull IncomeExpenseDetailViewModel.PeriodSummaryUiModel item,
                                     @Nullable String categoryName) {
        StatisticsCategoryDayDetailFragmentDirections.ActionStatisticsCategoryDayDetailFragmentToReportTransactionListFragment action =
                StatisticsCategoryDayDetailFragmentDirections.actionStatisticsCategoryDayDetailFragmentToReportTransactionListFragment();
        action.setWalletId(viewModel.getCurrentFilterState().getWalletId());
        action.setStartDate(item.getStartDate());
        action.setEndDate(item.getEndDate());
        action.setTransactionType(viewModel.getSelectedTransactionType().name());
        action.setCategoryId(StatisticsCategoryDayDetailFragmentArgs.fromBundle(
                getArguments() == null ? new Bundle() : getArguments()
        ).getCategoryId());
        action.setReportTitle(categoryName);
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void renderTrendChart(@NonNull List<IncomeExpenseDetailViewModel.PeriodSummaryUiModel> items) {
        if (items.isEmpty()) {
            binding.chartTrend.clear();
            binding.chartTrend.invalidate();
            return;
        }

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        float maxValue = 0f;
        float minValue = 0f;
        boolean incomeMode = viewModel.getSelectedTransactionType() == TransactionType.INCOME;
        for (int index = 0; index < items.size(); index++) {
            IncomeExpenseDetailViewModel.PeriodSummaryUiModel item = items.get(index);
            float amount = (float) item.getPrimaryAmount(viewModel.getSelectedTransactionType());
            float plottedValue = incomeMode ? amount : -amount;
            entries.add(new BarEntry(index, plottedValue));
            labels.add(item.getLabel());
            maxValue = Math.max(maxValue, plottedValue);
            minValue = Math.min(minValue, plottedValue);
        }

        BarDataSet dataSet = new BarDataSet(entries, getString(R.string.statistics_detail_mode_trend));
        dataSet.setColor(ContextCompat.getColor(
                requireContext(),
                incomeMode ? R.color.transfer_blue : R.color.expense_red
        ));
        dataSet.setDrawValues(false);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.56f);

        binding.chartTrend.setData(data);
        binding.chartTrend.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.chartTrend.getXAxis().setLabelCount(Math.min(labels.size(), 5), false);
        binding.chartTrend.getAxisLeft().setAxisMaximum(Math.max(maxValue * 1.15f, incomeMode ? 1f : 0f));
        binding.chartTrend.getAxisLeft().setAxisMinimum(Math.min(minValue * 1.15f, incomeMode ? 0f : -1f));
        binding.chartTrend.invalidate();
        binding.chartTrend.animateY(700, Easing.EaseInOutQuad);
    }

    private void renderComparisonChart(@Nullable List<IncomeExpenseDetailViewModel.ComparisonPointUiModel> items) {
        if (items == null || items.isEmpty()) {
            binding.chartComparison.clear();
            binding.chartComparison.invalidate();
            latestComparisonPoint = null;
            binding.layoutComparisonSummary.getRoot().setVisibility(View.GONE);
            binding.tvComparisonCollapsedHint.setVisibility(View.GONE);
            return;
        }

        List<Entry> currentEntries = new ArrayList<>();
        List<Entry> averageEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        float maxValue = 0f;
        for (int index = 0; index < items.size(); index++) {
            IncomeExpenseDetailViewModel.ComparisonPointUiModel item = items.get(index);
            currentEntries.add(new Entry(index, (float) item.getCurrentAmount()));
            averageEntries.add(new Entry(index, (float) item.getAverageAmount()));
            labels.add(item.getLabel());
            maxValue = Math.max(maxValue, (float) Math.max(item.getCurrentAmount(), item.getAverageAmount()));
        }

        IncomeExpenseDetailViewModel.ComparisonPointUiModel latestPoint = items.get(items.size() - 1);
        int primaryColor = ContextCompat.getColor(
                requireContext(),
                viewModel.getSelectedTransactionType() == TransactionType.INCOME
                        ? R.color.transfer_blue
                        : R.color.expense_red
        );
        LineDataSet currentDataSet = new LineDataSet(currentEntries, getString(R.string.statistics_detail_compare_current));
        currentDataSet.setColor(primaryColor);
        currentDataSet.setDrawCircles(false);
        currentDataSet.setLineWidth(2.8f);
        currentDataSet.setDrawFilled(true);
        currentDataSet.setFillColor(primaryColor);
        currentDataSet.setFillAlpha(24);
        currentDataSet.setDrawValues(false);

        LineDataSet averageDataSet = new LineDataSet(averageEntries, getString(R.string.statistics_detail_compare_average));
        averageDataSet.setColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_muted));
        averageDataSet.setDrawCircles(false);
        averageDataSet.setLineWidth(2f);
        averageDataSet.setDrawValues(false);

        List<Entry> focusEntries = new ArrayList<>();
        focusEntries.add(new Entry(items.size() - 1, (float) latestPoint.getCurrentAmount()));
        LineDataSet focusDataSet = new LineDataSet(focusEntries, "");
        focusDataSet.setColor(Color.TRANSPARENT);
        focusDataSet.setLineWidth(0f);
        focusDataSet.setDrawValues(false);
        focusDataSet.setForm(Legend.LegendForm.NONE);
        focusDataSet.setCircleColor(primaryColor);
        focusDataSet.setCircleRadius(5.5f);
        focusDataSet.setDrawCircleHole(true);
        focusDataSet.setCircleHoleColor(Color.WHITE);
        focusDataSet.setCircleHoleRadius(2.8f);

        binding.chartComparison.setData(new LineData(averageDataSet, currentDataSet, focusDataSet));
        binding.chartComparison.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.chartComparison.getXAxis().setLabelCount(Math.min(labels.size(), 6), false);
        binding.chartComparison.getAxisLeft().setAxisMaximum(Math.max(maxValue * 1.12f, 1f));
        binding.chartComparison.invalidate();
        binding.chartComparison.animateX(700, Easing.EaseInOutQuad);
        latestComparisonPoint = latestPoint;
        renderComparisonSummary(latestPoint, primaryColor);
        updateComparisonVisibility();
    }

    private void updateComparisonVisibility() {
        boolean showCard = viewModel.shouldShowComparisonCard();
        binding.cardComparison.setVisibility(showCard ? View.VISIBLE : View.GONE);
        binding.layoutComparisonChartContainer.setVisibility(showCard && isComparisonVisible ? View.VISIBLE : View.GONE);
        binding.layoutComparisonSummary.getRoot().setVisibility(showCard && isComparisonVisible
                && binding.layoutComparisonSummary.tvComparisonSummaryDate.getText().length() > 0
                ? View.VISIBLE
                : View.GONE);
        boolean showCollapsedHint = showCard && !isComparisonVisible && latestComparisonPoint != null;
        binding.tvComparisonCollapsedHint.setVisibility(showCollapsedHint ? View.VISIBLE : View.GONE);
        if (showCollapsedHint && latestComparisonPoint != null) {
            binding.tvComparisonCollapsedHint.setText(getString(
                    R.string.statistics_detail_compare_average_value,
                    CurrencyFormatter.format(latestComparisonPoint.getAverageAmount(), "VND")
            ));
        }
        binding.btnToggleComparison.setIconResource(isComparisonVisible
                ? R.drawable.outline_visibility_off_24
                : R.drawable.outline_visibility_24);
        binding.btnToggleComparison.setContentDescription(getString(isComparisonVisible
                ? R.string.statistics_detail_compare_hide
                : R.string.statistics_detail_compare_show));
    }

    private void renderComparisonSummary(@NonNull IncomeExpenseDetailViewModel.ComparisonPointUiModel point,
                                         @ColorInt int accentColor) {
        binding.layoutComparisonSummary.getRoot().setVisibility(View.VISIBLE);
        binding.layoutComparisonSummary.tvComparisonSummaryDate.setText(formatComparisonDate(point.getDateMillis()));
        binding.layoutComparisonSummary.tvComparisonSummaryCurrentValue.setText(
                CurrencyFormatter.format(point.getCurrentAmount(), "VND")
        );
        binding.layoutComparisonSummary.tvComparisonSummaryCurrentValue.setTextColor(accentColor);
        binding.layoutComparisonSummary.tvComparisonSummaryAverageValue.setText(
                getString(
                        R.string.statistics_detail_compare_average_value,
                        CurrencyFormatter.format(point.getAverageAmount(), "VND")
                )
        );
    }

    private void updateEmptyState() {
        boolean showEmpty = !hasTrendData && trendPeriodsAdapter.getItemCount() == 0;
        binding.tvEmptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        binding.recyclerTrendPeriods.setVisibility(trendPeriodsAdapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
    }

    private void configureBidirectionalBarChart(@NonNull BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText(getString(R.string.statistics_no_data));
        chart.setDrawGridBackground(false);
        chart.setDrawValueAboveBar(false);
        chart.setFitBars(true);
        chart.setTouchEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setLabelRotationAngle(-35f);
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_muted));
        xAxis.setTextSize(11f);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(ContextCompat.getColor(requireContext(), R.color.statistics_card_stroke));
        leftAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_muted));
        leftAxis.setValueFormatter(absoluteCurrencyValueFormatter);
        leftAxis.setAxisLineColor(Color.TRANSPARENT);
        leftAxis.setDrawZeroLine(true);
        leftAxis.setZeroLineColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_muted));

        chart.getAxisRight().setEnabled(false);
    }

    private void configureLineChart(@NonNull LineChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(true);
        chart.getLegend().setTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_muted));
        chart.getLegend().setTextSize(11f);
        chart.getLegend().setForm(Legend.LegendForm.LINE);
        chart.setNoDataText(getString(R.string.statistics_no_data));
        chart.setTouchEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.setExtraTopOffset(12f);
        chart.setExtraRightOffset(12f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_muted));
        xAxis.setTextSize(11f);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(ContextCompat.getColor(requireContext(), R.color.statistics_card_stroke));
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_muted));
        leftAxis.setValueFormatter(absoluteCurrencyValueFormatter);

        chart.getAxisRight().setEnabled(false);
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

    private void bindPeriodRow(@NonNull View row, @NonNull Dialog dialog, @NonNull Runnable action) {
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
        DialogStatisticsCustomRangeBinding dialogBinding = DialogStatisticsCustomRangeBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        StatisticsViewModel.FilterState current = viewModel.getCurrentFilterState();
        final LocalDate[] selectedStart = {toLocalDate(current.getStartDate())};
        final LocalDate[] selectedEnd = {toLocalDate(current.getEndDate())};
        renderDateButton(dialogBinding.btnStartDate, selectedStart[0]);
        renderDateButton(dialogBinding.btnEndDate, selectedEnd[0]);

        dialogBinding.btnStartDate.setOnClickListener(v ->
                showSingleDatePicker(selectedStart[0], pickedDate -> {
                    selectedStart[0] = pickedDate;
                    renderDateButton(dialogBinding.btnStartDate, pickedDate);
                }));
        dialogBinding.btnEndDate.setOnClickListener(v ->
                showSingleDatePicker(selectedEnd[0], pickedDate -> {
                    selectedEnd[0] = pickedDate;
                    renderDateButton(dialogBinding.btnEndDate, pickedDate);
                }));
        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnApply.setOnClickListener(v -> {
            if (selectedEnd[0].isBefore(selectedStart[0])) {
                Snackbar.make(binding.getRoot(),
                        getString(R.string.statistics_custom_range_invalid),
                        Snackbar.LENGTH_SHORT).show();
                return;
            }
            viewModel.updateCustomRange(
                    TimeWindowUtils.startOfDayLocalDateUtc(selectedStart[0]),
                    TimeWindowUtils.endOfDayLocalDateUtc(selectedEnd[0])
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
                "statistics_leaf_single_date",
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

    @NonNull
    private String formatComparisonDate(long dateMillis) {
        LocalDate date = toLocalDate(dateMillis);
        return String.format(Locale.getDefault(), "%02d/%02d/%d:",
                date.getDayOfMonth(),
                date.getMonthValue(),
                date.getYear());
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
        return TimeWindowUtils.endOfTodayUtc();
    }

    @NonNull
    private LocalDate toLocalDate(long epochMillis) {
        if (epochMillis <= 0L || epochMillis == Long.MAX_VALUE) {
            return LocalDate.now();
        }
        return TimeWindowUtils.toDeviceLocalDate(epochMillis);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private static class AbsoluteCurrencyValueFormatter extends ValueFormatter {
        @NonNull
        @Override
        public String getFormattedValue(float value) {
            float absoluteValue = Math.abs(value);
            if (absoluteValue >= 1_000_000f) {
                return String.format(Locale.getDefault(), "%.0f M đ", absoluteValue / 1_000_000f);
            }
            if (absoluteValue >= 1_000f) {
                return String.format(Locale.getDefault(), "%.0f K đ", absoluteValue / 1_000f);
            }
            return String.format(Locale.getDefault(), "%.0f đ", absoluteValue);
        }
    }

    private interface DateSelectedCallback {
        void onDateSelected(@NonNull LocalDate date);
    }
}
