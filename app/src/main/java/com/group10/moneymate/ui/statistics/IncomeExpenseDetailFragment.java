package com.group10.moneymate.ui.statistics;

import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
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
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.databinding.DialogStatisticsCustomRangeBinding;
import com.group10.moneymate.databinding.FragmentIncomeExpenseDetailBinding;
import com.group10.moneymate.databinding.SheetStatisticsPeriodFilterBinding;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.MoneyMateDatePickerHelper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IncomeExpenseDetailFragment extends Fragment {

    private static final String RESULT_SELECTED_WALLET_ID = "result_selected_wallet_id";
    private static final String RESULT_SELECTED_WALLET_LABEL = "result_selected_wallet_label";

    private FragmentIncomeExpenseDetailBinding binding;
    private IncomeExpenseDetailViewModel viewModel;
    private StatisticsCategoryBreakdownAdapter categoryAdapter;
    private StatisticsPeriodSummaryAdapter netPeriodsAdapter;
    private StatisticsPeriodSummaryAdapter trendPeriodsAdapter;
    private AbsoluteCurrencyValueFormatter absoluteCurrencyValueFormatter;
    private CategoryContentMode categoryContentMode = CategoryContentMode.DETAIL;
    private boolean isComparisonVisible;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentIncomeExpenseDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        IncomeExpenseDetailFragmentArgs args = IncomeExpenseDetailFragmentArgs.fromBundle(getArguments());
        AppContainer container = ((MoneyMateApplication) requireActivity().getApplication()).getAppContainer();
        IncomeExpenseDetailViewModel.Factory factory = new IncomeExpenseDetailViewModel.Factory(
                container.transactionRepository,
                container.walletRepository,
                container.authRepository.getCurrentUserId(),
                args.getWalletId(),
                args.getStartDate(),
                args.getEndDate(),
                args.getTransactionType()
        );
        viewModel = new ViewModelProvider(this, factory).get(IncomeExpenseDetailViewModel.class);
        absoluteCurrencyValueFormatter = new AbsoluteCurrencyValueFormatter();

        categoryAdapter = new StatisticsCategoryBreakdownAdapter();
        netPeriodsAdapter = new StatisticsPeriodSummaryAdapter(
                StatisticsPeriodSummaryAdapter.DisplayMode.NET,
                null
        );
        trendPeriodsAdapter = new StatisticsPeriodSummaryAdapter(
                StatisticsPeriodSummaryAdapter.DisplayMode.SINGLE,
                viewModel.getSelectedTransactionType()
        );

        applyWindowInsets();
        configureHeader();
        configureRecyclerViews();
        configureCharts();
        configureModeVisibility();
        bindActions();
        observeViewModel();
    }

    private void configureHeader() {
        binding.statisticsHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.statisticsHeader.tvHeaderSummaryLabel.setText(viewModel.getHeaderSummaryLabel());
        binding.statisticsHeader.tvHeaderTotalAmount.setText(R.string.default_currency_zero);
        binding.statisticsHeader.btnWalletSelector.setText(R.string.statistics_wallet_selector_all);
        binding.tvNetSectionTitle.setText(R.string.statistics_net_income_title);
        binding.btnToggleComparison.setText(R.string.statistics_detail_compare_show);
        binding.layoutComparisonSummary.getRoot().setVisibility(View.GONE);
    }

    private void configureRecyclerViews() {
        binding.recyclerCategoryBreakdown.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerCategoryBreakdown.setAdapter(categoryAdapter);
        binding.recyclerCategoryBreakdown.setNestedScrollingEnabled(false);

        binding.recyclerNetPeriods.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerNetPeriods.setAdapter(netPeriodsAdapter);
        binding.recyclerNetPeriods.setNestedScrollingEnabled(false);

        binding.recyclerTrendPeriods.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerTrendPeriods.setAdapter(trendPeriodsAdapter);
        binding.recyclerTrendPeriods.setNestedScrollingEnabled(false);
    }

    private void configureCharts() {
        configureBidirectionalBarChart(binding.chartNetOverview);
        configureBidirectionalBarChart(binding.chartTrend);
        configureLineChart(binding.chartComparison);
    }

    private void configureModeVisibility() {
        boolean netMode = viewModel.isNetMode();
        binding.cardNetOverview.setVisibility(netMode ? View.VISIBLE : View.GONE);
        binding.cardHighlights.setVisibility(netMode ? View.GONE : View.VISIBLE);
        binding.cardComparison.setVisibility(netMode ? View.GONE : View.VISIBLE);
        binding.cardReportDetail.setVisibility(netMode ? View.GONE : View.VISIBLE);
        switchCategoryContentMode(CategoryContentMode.DETAIL);
    }

    private void bindActions() {
        binding.statisticsHeader.btnHeaderBack.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
        binding.statisticsHeader.btnPreviousPeriod.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-1));
        binding.statisticsHeader.btnNextPeriod.setOnClickListener(v -> viewModel.shiftCurrentPeriod(1));
        binding.statisticsHeader.tvPeriodPrevious.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-2));
        binding.statisticsHeader.tvPeriodCurrent.setOnClickListener(v -> viewModel.shiftCurrentPeriod(-1));
        binding.statisticsHeader.tvPeriodNext.setOnClickListener(v -> {
        });
        binding.statisticsHeader.btnDateFilter.setOnClickListener(v -> showDateRangePicker());
        binding.statisticsHeader.btnWalletSelector.setOnClickListener(v -> openWalletPicker());

        binding.btnToggleComparison.setOnClickListener(v -> {
            isComparisonVisible = !isComparisonVisible;
            updateComparisonVisibility();
        });
        binding.btnModeDetail.setOnClickListener(v -> switchCategoryContentMode(CategoryContentMode.DETAIL));
        binding.btnModeTrend.setOnClickListener(v -> switchCategoryContentMode(CategoryContentMode.TREND));

        categoryAdapter.setOnItemClickListener(this::openLevelThreeDetail);
        netPeriodsAdapter.setOnItemClickListener(this::openPeriodTransactions);
        trendPeriodsAdapter.setOnItemClickListener(this::openPeriodTransactions);
    }

    private void observeViewModel() {
        observeWalletPickerResult();

        viewModel.getWalletLabel().observe(getViewLifecycleOwner(),
                label -> binding.statisticsHeader.btnWalletSelector.setText(label));

        viewModel.getHeaderAmount().observe(getViewLifecycleOwner(), amount -> {
            double safeAmount = amount != null ? amount : 0d;
            String formatted = CurrencyFormatter.format(Math.abs(safeAmount), "VND");
            if (viewModel.isNetMode()) {
                renderNetHeaderAmount(safeAmount, formatted);
            } else {
                binding.statisticsHeader.tvHeaderTotalAmount.setText(formatted);
                int accentColor = ContextCompat.getColor(
                        requireContext(),
                        viewModel.getSelectedTransactionType() == TransactionType.INCOME
                                ? R.color.transfer_blue
                                : R.color.expense_red
                );
                binding.statisticsHeader.tvHeaderTotalAmount.setTextColor(accentColor);
                binding.tvHighlightTotalValue.setText(formatted);
                binding.tvHighlightTotalValue.setTextColor(accentColor);
            }
        });

        viewModel.getAveragePerDay().observe(getViewLifecycleOwner(), amount -> {
            binding.tvHighlightAverageValue.setText(CurrencyFormatter.format(amount != null ? amount : 0d, "VND"));
            binding.tvHighlightAverageValue.setTextColor(ContextCompat.getColor(
                    requireContext(),
                    viewModel.getSelectedTransactionType() == TransactionType.INCOME
                            ? R.color.transfer_blue
                            : R.color.expense_red
            ));
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

        viewModel.getPeriodSummaries().observe(getViewLifecycleOwner(), items -> {
            List<IncomeExpenseDetailViewModel.PeriodSummaryUiModel> safeItems =
                    items != null ? items : new ArrayList<>();
            if (viewModel.isNetMode()) {
                renderNetOverview(safeItems);
            } else {
                renderTrendOverview(safeItems);
            }
        });

        viewModel.getCategoryItems().observe(getViewLifecycleOwner(), items -> {
            List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel> safeItems =
                    items != null ? items : new ArrayList<>();
            categoryAdapter.submitList(safeItems);
            renderDonutBreakdown(safeItems);
            updateEmptyState();
        });

        viewModel.getComparisonPoints().observe(getViewLifecycleOwner(), this::renderComparisonChart);
    }

    private void renderNetHeaderAmount(double netAmount, @NonNull String formattedAbsAmount) {
        binding.statisticsHeader.tvHeaderTotalAmount.setText(formattedAbsAmount);
        binding.tvNetSectionValue.setText(formattedAbsAmount);
        if (netAmount >= 0d) {
            binding.tvNetSectionValue.setTextColor(ContextCompat.getColor(requireContext(), R.color.income_green));
            binding.statisticsHeader.tvHeaderTotalAmount.setTextColor(ContextCompat.getColor(requireContext(), R.color.income_green));
            binding.ivNetSectionSign.setImageResource(R.drawable.outline_add_24);
            binding.ivNetSectionSign.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(requireContext(), R.color.income_green)
            ));
        } else {
            int muted = ContextCompat.getColor(requireContext(), R.color.statistics_text_muted);
            binding.tvNetSectionValue.setTextColor(muted);
            binding.statisticsHeader.tvHeaderTotalAmount.setTextColor(muted);
            binding.ivNetSectionSign.setImageResource(R.drawable.outline_remove_24);
            binding.ivNetSectionSign.setImageTintList(ColorStateList.valueOf(muted));
        }
    }

    private void renderNetOverview(@NonNull List<IncomeExpenseDetailViewModel.PeriodSummaryUiModel> items) {
        netPeriodsAdapter.submitList(items);
        renderNetOverviewChart(items);
    }

    private void renderTrendOverview(@NonNull List<IncomeExpenseDetailViewModel.PeriodSummaryUiModel> items) {
        trendPeriodsAdapter.submitList(items);
        renderTrendChart(items);
        updateEmptyState();
    }

    private void renderDonutBreakdown(@NonNull List<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel> items) {
        List<StatisticsDonutBreakdownView.Segment> segments = new ArrayList<>();
        double totalAmount = 0d;
        int fallbackColor = ContextCompat.getColor(
                requireContext(),
                viewModel.getSelectedTransactionType() == TransactionType.INCOME
                        ? R.color.transfer_blue
                        : R.color.expense_red
        );
        for (IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel item : items) {
            totalAmount += item.getTotalAmount();
            segments.add(new StatisticsDonutBreakdownView.Segment(
                    resolveIconRes(item.getIconResId()),
                    parseColorOrDefault(item.getColorHex(), fallbackColor),
                    item.getTotalAmount()
            ));
        }
        binding.donutCategoryBreakdown.setData(segments, totalAmount);
    }

    private void renderNetOverviewChart(@NonNull List<IncomeExpenseDetailViewModel.PeriodSummaryUiModel> items) {
        if (items.isEmpty()) {
            binding.chartNetOverview.clear();
            binding.chartNetOverview.invalidate();
            return;
        }
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        float positiveMax = 0f;
        float negativeMax = 0f;
        for (int index = 0; index < items.size(); index++) {
            IncomeExpenseDetailViewModel.PeriodSummaryUiModel item = items.get(index);
            entries.add(new BarEntry(index, new float[]{
                    (float) item.getIncomeAmount(),
                    (float) -item.getExpenseAmount()
            }));
            labels.add(item.getLabel());
            positiveMax = Math.max(positiveMax, (float) item.getIncomeAmount());
            negativeMax = Math.min(negativeMax, (float) -item.getExpenseAmount());
        }

        BarDataSet dataSet = new BarDataSet(entries, getString(R.string.statistics_net_income_title));
        dataSet.setColors(
                ContextCompat.getColor(requireContext(), R.color.transfer_blue),
                ContextCompat.getColor(requireContext(), R.color.expense_red)
        );
        dataSet.setDrawValues(false);
        dataSet.setHighLightAlpha(0);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.56f);
        binding.chartNetOverview.setData(data);
        binding.chartNetOverview.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.chartNetOverview.getXAxis().setLabelCount(Math.min(labels.size(), 5), false);
        binding.chartNetOverview.getAxisLeft().setAxisMaximum(Math.max(positiveMax * 1.15f, 1f));
        binding.chartNetOverview.getAxisLeft().setAxisMinimum(Math.min(negativeMax * 1.15f, -1f));
        binding.chartNetOverview.invalidate();
        binding.chartNetOverview.animateY(700, Easing.EaseInOutQuad);
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
            float plotted = incomeMode ? amount : -amount;
            entries.add(new BarEntry(index, plotted));
            labels.add(item.getLabel());
            maxValue = Math.max(maxValue, plotted);
            minValue = Math.min(minValue, plotted);
        }

        BarDataSet dataSet = new BarDataSet(entries, getString(R.string.statistics_detail_mode_trend));
        dataSet.setColor(ContextCompat.getColor(
                requireContext(),
                incomeMode ? R.color.transfer_blue : R.color.expense_red
        ));
        dataSet.setDrawValues(false);
        dataSet.setHighLightAlpha(0);

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

        LineDataSet currentDataSet = new LineDataSet(currentEntries, getString(R.string.statistics_detail_compare_current));
        int primaryColor = ContextCompat.getColor(requireContext(),
                viewModel.getSelectedTransactionType() == TransactionType.INCOME
                        ? R.color.transfer_blue
                        : R.color.expense_red);
        currentDataSet.setColor(primaryColor);
        currentDataSet.setDrawCircles(false);
        currentDataSet.setLineWidth(2.8f);
        currentDataSet.setDrawValues(false);
        currentDataSet.setDrawFilled(true);
        currentDataSet.setFillColor(primaryColor);
        currentDataSet.setFillAlpha(28);

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
        boolean showCard = !viewModel.isNetMode() && viewModel.shouldShowComparisonCard();
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

    private void switchCategoryContentMode(@NonNull CategoryContentMode mode) {
        categoryContentMode = mode;
        binding.layoutDetailContent.setVisibility(mode == CategoryContentMode.DETAIL ? View.VISIBLE : View.GONE);
        binding.layoutTrendContent.setVisibility(mode == CategoryContentMode.TREND ? View.VISIBLE : View.GONE);
        styleModeButton(binding.btnModeDetail, mode == CategoryContentMode.DETAIL);
        styleModeButton(binding.btnModeTrend, mode == CategoryContentMode.TREND);
        updateEmptyState();
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
        if (viewModel.isNetMode()) {
            binding.tvEmptyState.setVisibility(View.GONE);
            return;
        }

        boolean showEmpty = false;
        if (categoryContentMode == CategoryContentMode.DETAIL) {
            showEmpty = categoryAdapter.getItemCount() == 0;
        } else {
            showEmpty = trendPeriodsAdapter.getItemCount() == 0;
        }
        binding.tvEmptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
    }

    private void openLevelThreeDetail(@NonNull IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel item) {
        IncomeExpenseDetailFragmentDirections.ActionStatisticsDetailFragmentToStatisticsCategoryDetailFragment action =
                IncomeExpenseDetailFragmentDirections.actionStatisticsDetailFragmentToStatisticsCategoryDetailFragment();
        StatisticsViewModel.FilterState filterState = viewModel.getCurrentFilterState();
        action.setWalletId(filterState.getWalletId());
        action.setStartDate(filterState.getStartDate());
        action.setEndDate(filterState.getEndDate());
        action.setTransactionType(viewModel.getSelectedTransactionType().name());
        action.setCategoryId(item.getCategoryId());
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void openPeriodTransactions(@NonNull IncomeExpenseDetailViewModel.PeriodSummaryUiModel item) {
        IncomeExpenseDetailFragmentDirections.ActionStatisticsDetailFragmentToReportTransactionListFragment action =
                IncomeExpenseDetailFragmentDirections.actionStatisticsDetailFragmentToReportTransactionListFragment();
        action.setWalletId(viewModel.getCurrentFilterState().getWalletId());
        action.setStartDate(item.getStartDate());
        action.setEndDate(item.getEndDate());
        action.setTransactionType(viewModel.isNetMode() ? null : viewModel.getSelectedTransactionType().name());
        action.setCategoryId(null);
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void configureBidirectionalBarChart(@NonNull BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText(getString(R.string.statistics_no_data));
        chart.setDrawGridBackground(false);
        chart.setDrawValueAboveBar(false);
        chart.setFitBars(true);
        chart.setExtraTopOffset(12f);
        chart.setExtraBottomOffset(8f);
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
        leftAxis.setZeroLineWidth(1f);

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
        chart.setExtraBottomOffset(8f);

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
        IncomeExpenseDetailFragmentDirections.ActionStatisticsDetailFragmentToBudgetWalletPickerFragment action =
                IncomeExpenseDetailFragmentDirections.actionStatisticsDetailFragmentToBudgetWalletPickerFragment();
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
                "statistics_detail_single_date",
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
            if (absoluteValue >= 1_000_000_000f) {
                return String.format(Locale.getDefault(), "%.0f T đ", absoluteValue / 1_000_000_000f);
            }
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
