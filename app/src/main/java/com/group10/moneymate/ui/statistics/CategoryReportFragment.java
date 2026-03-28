package com.group10.moneymate.ui.statistics;

import android.app.DatePickerDialog;
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
import androidx.appcompat.app.AlertDialog;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.group10.moneymate.R;
import com.group10.moneymate.databinding.DialogStatisticsCustomRangeBinding;
import com.group10.moneymate.databinding.FragmentCategoryReportBinding;
import com.group10.moneymate.databinding.SheetStatisticsPeriodFilterBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CategoryReportFragment extends Fragment {

    private FragmentCategoryReportBinding binding;
    private CategoryReportViewModel viewModel;
    private StatisticsPeriodSummaryAdapter dailyGroupsAdapter;
    private AbsoluteCurrencyValueFormatter absoluteCurrencyValueFormatter;
    private CategoryContentMode categoryContentMode = CategoryContentMode.DETAIL;
    private boolean isComparisonVisible;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoryReportBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CategoryReportFragmentArgs args =
                CategoryReportFragmentArgs.fromBundle(getArguments());
        AppContainer container = ((MoneyMateApplication) requireActivity().getApplication()).getAppContainer();
        CategoryReportViewModel.Factory factory = new CategoryReportViewModel.Factory(
                container.transactionRepository,
                container.categoryRepository,
                container.authRepository.getCurrentUserId(),
                args.getWalletId(),
                args.getStartDate(),
                args.getEndDate(),
                args.getTransactionType(),
                args.getCategoryId()
        );
        viewModel = new ViewModelProvider(this, factory).get(CategoryReportViewModel.class);
        absoluteCurrencyValueFormatter = new AbsoluteCurrencyValueFormatter();
        dailyGroupsAdapter = new StatisticsPeriodSummaryAdapter(
                StatisticsPeriodSummaryAdapter.DisplayMode.SINGLE,
                viewModel.getSelectedTransactionType()
        );

        applyWindowInsets();
        configureHeader();
        configureRecyclerView();
        configureCharts();
        bindActions();
        observeViewModel();
    }

    private void configureHeader() {
        binding.statisticsHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.statisticsHeader.tvHeaderSummaryLabel.setVisibility(View.GONE);
        binding.statisticsHeader.tvHeaderTotalAmount.setVisibility(View.GONE);
        binding.statisticsHeader.btnWalletSelector.setText(R.string.statistics_category_report_title);
        binding.btnToggleComparison.setText(R.string.statistics_detail_compare_show);
        binding.layoutComparisonSummary.getRoot().setVisibility(View.GONE);
        switchContentMode(CategoryContentMode.DETAIL);
    }

    private void configureRecyclerView() {
        binding.recyclerDailyGroups.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerDailyGroups.setAdapter(dailyGroupsAdapter);
        binding.recyclerDailyGroups.setNestedScrollingEnabled(false);
    }

    private void configureCharts() {
        configureBidirectionalBarChart(binding.chartTrend);
        configureLineChart(binding.chartComparison);
    }

    private void bindActions() {
        binding.statisticsHeader.btnHeaderBack.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        binding.statisticsHeader.btnWalletSelector.setOnClickListener(v -> showCategorySelectorDialog());
        binding.statisticsHeader.btnPreviousPeriod.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-1));
        binding.statisticsHeader.btnNextPeriod.setOnClickListener(v -> viewModel.shiftCurrentPeriod(1));
        binding.statisticsHeader.tvPeriodPrevious.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-2));
        binding.statisticsHeader.tvPeriodCurrent.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-1));
        binding.statisticsHeader.btnDateFilter.setOnClickListener(v -> showDateRangePicker());

        binding.btnToggleComparison.setOnClickListener(v -> {
            isComparisonVisible = !isComparisonVisible;
            updateComparisonVisibility();
        });
        binding.btnModeDetail.setOnClickListener(v -> switchContentMode(CategoryContentMode.DETAIL));
        binding.btnModeTrend.setOnClickListener(v -> switchContentMode(CategoryContentMode.TREND));
        dailyGroupsAdapter.setOnItemClickListener(this::openLevelFourDetail);
    }

    private void observeViewModel() {
        viewModel.getCategoryOptions().observe(getViewLifecycleOwner(), items -> {
            if (items == null || items.isEmpty()) {
                binding.statisticsHeader.btnWalletSelector.setEnabled(false);
                binding.statisticsHeader.btnWalletSelector.setAlpha(0.55f);
                return;
            }
            binding.statisticsHeader.btnWalletSelector.setEnabled(true);
            binding.statisticsHeader.btnWalletSelector.setAlpha(1f);
        });

        viewModel.getSelectedCategory().observe(getViewLifecycleOwner(), category -> {
            if (category == null) {
                return;
            }
            binding.statisticsHeader.btnWalletSelector.setText(category.getCategoryName());
            binding.statisticsHeader.btnWalletSelector.setIconResource(resolveIconRes(category.getIconResId()));
            binding.statisticsHeader.btnWalletSelector.setIconTint(ColorStateList.valueOf(
                    parseColorOrDefault(category.getColorHex(), ContextCompat.getColor(requireContext(), R.color.transfer_blue))
            ));
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

        viewModel.getTrendSummaries().observe(getViewLifecycleOwner(), items -> {
            renderTrendChart(items != null ? items : new ArrayList<>());
            updateEmptyState();
        });

        viewModel.getDailyGroups().observe(getViewLifecycleOwner(), items -> {
            List<IncomeExpenseDetailViewModel.PeriodSummaryUiModel> safeItems =
                    items != null ? items : new ArrayList<>();
            dailyGroupsAdapter.submitList(safeItems);
            renderDayComposition(safeItems);
            updateEmptyState();
        });

        viewModel.getComparisonPoints().observe(getViewLifecycleOwner(), this::renderComparisonChart);
    }

    private void renderDayComposition(@NonNull List<IncomeExpenseDetailViewModel.PeriodSummaryUiModel> items) {
        List<StatisticsDonutBreakdownView.Segment> segments = new ArrayList<>();
        int[] palette = new int[]{
                ContextCompat.getColor(requireContext(), R.color.transfer_blue),
                ContextCompat.getColor(requireContext(), R.color.income_green),
                ContextCompat.getColor(requireContext(), R.color.expense_red),
                ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary)
        };
        for (int index = 0; index < items.size(); index++) {
            double amount = items.get(index).getPrimaryAmount(viewModel.getSelectedTransactionType());
            segments.add(new StatisticsDonutBreakdownView.Segment(
                    R.drawable.outline_calendar_today_24,
                    palette[index % palette.length],
                    amount
            ));
        }
        binding.donutDayBreakdown.setData(
                segments,
                viewModel.getTotalAmount().getValue() != null ? viewModel.getTotalAmount().getValue() : 0d
        );
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
            binding.layoutComparisonSummary.getRoot().setVisibility(View.GONE);
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
        renderComparisonSummary(latestPoint, primaryColor);
    }

    private void updateComparisonVisibility() {
        boolean showCard = viewModel.shouldShowComparisonCard();
        binding.cardComparison.setVisibility(showCard ? View.VISIBLE : View.GONE);
        binding.layoutComparisonChartContainer.setVisibility(showCard && isComparisonVisible ? View.VISIBLE : View.GONE);
        binding.layoutComparisonSummary.getRoot().setVisibility(showCard && isComparisonVisible
                && binding.layoutComparisonSummary.tvComparisonSummaryDate.getText().length() > 0
                ? View.VISIBLE
                : View.GONE);
        binding.btnToggleComparison.setText(isComparisonVisible
                ? R.string.statistics_detail_compare_hide
                : R.string.statistics_detail_compare_show);
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

    private void switchContentMode(@NonNull CategoryContentMode mode) {
        categoryContentMode = mode;
        binding.layoutDetailContent.setVisibility(mode == CategoryContentMode.DETAIL ? View.VISIBLE : View.GONE);
        binding.chartTrend.setVisibility(mode == CategoryContentMode.TREND ? View.VISIBLE : View.GONE);
        styleModeButton(binding.btnModeDetail, mode == CategoryContentMode.DETAIL);
        styleModeButton(binding.btnModeTrend, mode == CategoryContentMode.TREND);
    }

    private void styleModeButton(@NonNull MaterialButton button, boolean selected) {
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                requireContext(),
                selected ? R.color.statistics_text_primary : R.color.statistics_card_inner_bg
        )));
        button.setTextColor(ContextCompat.getColor(
                requireContext(),
                selected ? android.R.color.white : R.color.statistics_text_secondary
        ));
    }

    private void updateEmptyState() {
        binding.tvEmptyState.setVisibility(dailyGroupsAdapter.getItemCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void openLevelFourDetail(@NonNull IncomeExpenseDetailViewModel.PeriodSummaryUiModel item) {
        CategoryReportFragmentDirections.ActionStatisticsCategoryDetailFragmentToStatisticsCategoryDayDetailFragment action =
                CategoryReportFragmentDirections.actionStatisticsCategoryDetailFragmentToStatisticsCategoryDayDetailFragment();
        action.setWalletId(viewModel.getCurrentFilterState().getWalletId());
        action.setStartDate(item.getStartDate());
        action.setEndDate(item.getEndDate());
        action.setTransactionType(viewModel.getSelectedTransactionType().name());
        action.setCategoryId(viewModel.getSelectedCategoryId());
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void showCategorySelectorDialog() {
        List<CategoryReportViewModel.CategoryOptionUiModel> items = viewModel.getCategoryOptions().getValue();
        if (items == null || items.isEmpty()) {
            Snackbar.make(binding.getRoot(), R.string.statistics_no_data, Snackbar.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] labels = new CharSequence[items.size()];
        int checkedIndex = 0;
        for (int index = 0; index < items.size(); index++) {
            labels[index] = items.get(index).getCategoryName();
            if (items.get(index).getCategoryId().equals(viewModel.getSelectedCategoryId())) {
                checkedIndex = index;
            }
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.statistics_category_report_title)
                .setSingleChoiceItems(labels, checkedIndex, (dialog, which) -> {
                    viewModel.updateSelectedCategory(items.get(which).getCategoryId());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
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
        chart.getLegend().setEnabled(false);
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
                Snackbar.make(binding.getRoot(), R.string.statistics_custom_range_invalid, Snackbar.LENGTH_SHORT).show();
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
        new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) ->
                        callback.onDateSelected(LocalDate.of(year, month + 1, dayOfMonth)),
                initialDate.getYear(),
                initialDate.getMonthValue() - 1,
                initialDate.getDayOfMonth()
        ).show();
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
        return LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli() - 1L;
    }

    @ColorInt
    private int parseColorOrDefault(@Nullable String colorHex, @ColorInt int defaultColor) {
        if (colorHex == null || colorHex.trim().isEmpty()) {
            return defaultColor;
        }
        try {
            return Color.parseColor(colorHex);
        } catch (IllegalArgumentException ignored) {
            return defaultColor;
        }
    }

    private int resolveIconRes(@Nullable String iconResId) {
        if (iconResId == null || iconResId.trim().isEmpty()) {
            return R.drawable.ic_category_other;
        }
        int resolved = requireContext().getResources().getIdentifier(
                iconResId,
                "drawable",
                requireContext().getPackageName()
        );
        return resolved != 0 ? resolved : R.drawable.ic_category_other;
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

    private enum CategoryContentMode {
        DETAIL,
        TREND
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
