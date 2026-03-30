package com.group10.moneymate.ui.home;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

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
import com.google.android.material.button.MaterialButton;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.dto.CategorySumDTO;
import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.databinding.FragmentHomeBinding;
import com.group10.moneymate.ui.main.HomeActivity;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.IconProvider;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class HomeFragment extends Fragment {

    private static final String HIDDEN_BALANCE_MASK = "*********";
    private static final ZoneId APP_ZONE = ZoneId.systemDefault();
    private static final String TYPE_EXPENSE = Constants.TYPE_EXPENSE;
    private static final String TYPE_INCOME = Constants.TYPE_INCOME;

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private HomeWalletAdapter walletAdapter;
    private HomeTopSpendingAdapter topSpendingAdapter;
    private HomeRecentTransactionAdapter recentTransactionAdapter;

    private final Map<String, CategoryEntity> categoryMap = new HashMap<>();
    private List<CategorySumDTO> monthlyTopCategories = new ArrayList<>();
    private List<CategorySumDTO> weeklyTopCategories = new ArrayList<>();
    private List<TransactionEntity> recentTransactions = new ArrayList<>();
    private List<WalletWithBalance> walletItems = new ArrayList<>();
    private List<HomeViewModel.TrendPointUiModel> expenseTrendPoints = new ArrayList<>();
    private List<HomeViewModel.TrendPointUiModel> incomeTrendPoints = new ArrayList<>();

    private double currentTotalBalance = 0.0;
    private double currentMonthExpense = 0.0;
    private double previousMonthExpense = 0.0;
    private double currentWeekExpense = 0.0;
    private double previousWeekExpense = 0.0;
    private double currentMonthIncome = 0.0;
    private boolean isTotalBalanceVisible = false;
    private boolean areWalletBalancesVisible = false;
    @NonNull
    private ExpenseRangeMode reportRangeMode = ExpenseRangeMode.MONTH;
    @NonNull
    private ExpenseRangeMode topSpendingMode = ExpenseRangeMode.MONTH;
    @NonNull
    private ReportCardType reportCardType = ReportCardType.EXPENSE;
    @NonNull
    private TrendMetric trendMetric = TrendMetric.EXPENSE;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        setupInsets();
        setupRecyclerViews();
        configureCharts();
        bindActions();
        observeData();
        updateReportCardTypeUi();
        updateExpenseRangeModeUi();
        updateTopSpendingModeUi();
        updateTrendMetricUi();
    }

    private void setupInsets() {
        final int initialTopPadding = binding.layoutBalanceHeader.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollContent, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.layoutBalanceHeader.setPadding(
                    binding.layoutBalanceHeader.getPaddingLeft(),
                    initialTopPadding + systemBars.top,
                    binding.layoutBalanceHeader.getPaddingRight(),
                    binding.layoutBalanceHeader.getPaddingBottom()
            );
            return insets;
        });
    }

    private void setupRecyclerViews() {
        walletAdapter = new HomeWalletAdapter();
        binding.rvWalletPreview.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvWalletPreview.setNestedScrollingEnabled(false);
        binding.rvWalletPreview.setAdapter(walletAdapter);

        topSpendingAdapter = new HomeTopSpendingAdapter();
        binding.rvTopSpending.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTopSpending.setNestedScrollingEnabled(false);
        binding.rvTopSpending.setAdapter(topSpendingAdapter);

        recentTransactionAdapter = new HomeRecentTransactionAdapter();
        binding.rvRecentTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRecentTransactions.setNestedScrollingEnabled(false);
        binding.rvRecentTransactions.setAdapter(recentTransactionAdapter);
    }

    private void configureCharts() {
        configureExpenseChart(binding.chartMonthlyReport);
        configureTrendChart(binding.chartTrendReport);
    }

    private void bindActions() {
        binding.tvViewAllWallets.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(HomeFragmentDirections.actionHomeToWallets()));

        binding.btnToggleBalanceVisibility.setOnClickListener(v -> {
            isTotalBalanceVisible = !isTotalBalanceVisible;
            renderTotalBalanceText();
        });
        binding.btnToggleWalletBalances.setOnClickListener(v -> {
            areWalletBalancesVisible = !areWalletBalancesVisible;
            renderWalletBalanceVisibility();
        });

        binding.btnSearchHome.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.home_feature_soon, Toast.LENGTH_SHORT).show());
        binding.btnNotificationsHome.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.home_feature_soon, Toast.LENGTH_SHORT).show());

        binding.btnReportExpense.setOnClickListener(v -> {
            reportCardType = ReportCardType.EXPENSE;
            updateReportCardTypeUi();
        });
        binding.btnReportTrend.setOnClickListener(v -> {
            reportCardType = ReportCardType.TREND;
            updateReportCardTypeUi();
        });

        binding.btnReportWeek.setOnClickListener(v -> {
            reportRangeMode = ExpenseRangeMode.WEEK;
            updateExpenseRangeModeUi();
        });
        binding.btnReportMonth.setOnClickListener(v -> {
            reportRangeMode = ExpenseRangeMode.MONTH;
            updateExpenseRangeModeUi();
        });

        binding.layoutTrendExpenseMetric.setOnClickListener(v -> {
            trendMetric = TrendMetric.EXPENSE;
            updateTrendMetricUi();
        });
        binding.layoutTrendIncomeMetric.setOnClickListener(v -> {
            trendMetric = TrendMetric.INCOME;
            updateTrendMetricUi();
        });

        binding.btnTopWeek.setOnClickListener(v -> {
            topSpendingMode = ExpenseRangeMode.WEEK;
            updateTopSpendingModeUi();
        });
        binding.btnTopMonth.setOnClickListener(v -> {
            topSpendingMode = ExpenseRangeMode.MONTH;
            updateTopSpendingModeUi();
        });

        binding.btnViewMonthlyReport.setOnClickListener(v -> navigateToStatisticsForCurrentReport());
        binding.btnReportDetailCta.setOnClickListener(v -> navigateToStatisticsForCurrentReport());
        binding.btnViewTopSpendingDetail.setOnClickListener(v -> navigateToStatistics(topSpendingMode, null));

        binding.btnViewAllTransactions.setOnClickListener(v -> {
            if (requireActivity() instanceof HomeActivity) {
                ((HomeActivity) requireActivity()).navigateToBottomDestination(
                        R.id.transactionListFragment,
                        null
                );
            }
        });

        walletAdapter.setOnWalletClickListener(wallet ->
                Navigation.findNavController(binding.getRoot()).navigate(HomeFragmentDirections.actionHomeToWallets()));
        recentTransactionAdapter.setOnItemClickListener(item -> {
            Bundle args = new Bundle();
            args.putString("transactionId", item.getTransactionId());
            Navigation.findNavController(binding.getRoot()).navigate(R.id.transactionDetailFragment, args);
        });
    }

    private void observeData() {
        observeBalanceData();
        observeWalletData();
        observeCategoryData();
        observeTransactionData();
        observeExpenseData();
        observeTrendData();
        observeTopSpendingData();
    }

    private void observeBalanceData() {
        viewModel.getTotalBalance().observe(getViewLifecycleOwner(), total -> {
            currentTotalBalance = total != null ? total : 0.0;
            renderTotalBalanceText();
        });
    }

    private void observeWalletData() {
        viewModel.getWallets().observe(getViewLifecycleOwner(), wallets -> {
            walletItems = wallets != null ? wallets : new ArrayList<>();
            renderWallets();
        });
    }

    private void observeCategoryData() {
        viewModel.getExpenseCategories().observe(getViewLifecycleOwner(), this::mergeCategories);
        viewModel.getIncomeCategories().observe(getViewLifecycleOwner(), this::mergeCategories);
    }

    private void observeTransactionData() {
        viewModel.getRecentTransactions().observe(getViewLifecycleOwner(), transactions -> {
            recentTransactions = transactions != null ? transactions : new ArrayList<>();
            renderRecentTransactions();
        });
    }

    private void observeExpenseData() {
        viewModel.getMonthlyExpense().observe(getViewLifecycleOwner(), amount -> {
            currentMonthExpense = amount != null ? amount : 0.0;
            renderExpenseReportCard();
            renderTrendMetrics();
        });
        viewModel.getPreviousMonthlyExpense().observe(getViewLifecycleOwner(), amount -> {
            previousMonthExpense = amount != null ? amount : 0.0;
            renderExpenseReportCard();
        });
        viewModel.getWeeklyExpense().observe(getViewLifecycleOwner(), amount -> {
            currentWeekExpense = amount != null ? amount : 0.0;
            renderExpenseReportCard();
        });
        viewModel.getPreviousWeeklyExpense().observe(getViewLifecycleOwner(), amount -> {
            previousWeekExpense = amount != null ? amount : 0.0;
            renderExpenseReportCard();
        });
        viewModel.getMonthlyIncome().observe(getViewLifecycleOwner(), amount -> {
            currentMonthIncome = amount != null ? amount : 0.0;
            renderTrendMetrics();
        });
    }

    private void observeTrendData() {
        viewModel.getExpenseComparisonPoints().observe(getViewLifecycleOwner(), items -> {
            expenseTrendPoints = items != null ? items : new ArrayList<>();
            renderTrendChart();
        });
        viewModel.getIncomeComparisonPoints().observe(getViewLifecycleOwner(), items -> {
            incomeTrendPoints = items != null ? items : new ArrayList<>();
            renderTrendChart();
        });
    }

    private void observeTopSpendingData() {
        viewModel.getMonthlyTopExpenseCategories().observe(getViewLifecycleOwner(), items -> {
            monthlyTopCategories = items != null ? items : new ArrayList<>();
            renderTopSpending();
        });
        viewModel.getWeeklyTopExpenseCategories().observe(getViewLifecycleOwner(), items -> {
            weeklyTopCategories = items != null ? items : new ArrayList<>();
            renderTopSpending();
        });
    }

    private void mergeCategories(@Nullable List<CategoryEntity> categories) {
        if (categories != null) {
            for (CategoryEntity category : categories) {
                categoryMap.put(category.getId(), category);
            }
        }
        renderRecentTransactions();
    }

    private void renderWallets() {
        boolean empty = walletItems.isEmpty();
        binding.tvWalletEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvWalletPreview.setVisibility(empty ? View.GONE : View.VISIBLE);
        renderWalletBalanceVisibility();
        if (empty) {
            walletAdapter.submitList(new ArrayList<>());
            return;
        }
        walletAdapter.submitList(new ArrayList<>(walletItems.subList(0, Math.min(3, walletItems.size()))));
    }

    private void renderRecentTransactions() {
        boolean empty = recentTransactions.isEmpty();
        binding.tvTransactionsEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvRecentTransactions.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            recentTransactionAdapter.submitList(new ArrayList<>());
            return;
        }
        List<HomeRecentTransactionAdapter.ItemUiModel> items = new ArrayList<>();
        for (TransactionEntity transaction : recentTransactions) {
            CategoryEntity category = transaction.getCategoryId() != null
                    ? categoryMap.get(transaction.getCategoryId())
                    : null;
            String type = transaction.getType();
            items.add(new HomeRecentTransactionAdapter.ItemUiModel(
                    transaction.getId(),
                    resolveCategoryIcon(category, type),
                    0,
                    category != null ? category.getName() : getString(R.string.ledger_section_unknown),
                    formatShortDate(transaction.getTimestamp()),
                    formatAmountLabel(transaction.getAmount(), type),
                    ContextCompat.getColor(requireContext(), resolveRecentTransactionAmountColor(type))
            ));
        }
        recentTransactionAdapter.submitList(items);
    }

    private void renderTotalBalanceText() {
        if (isTotalBalanceVisible) {
            binding.tvTotalBalance.setText(CurrencyFormatter.format(currentTotalBalance, "VND"));
            binding.btnToggleBalanceVisibility.setImageResource(R.drawable.outline_visibility_24);
        } else {
            binding.tvTotalBalance.setText(HIDDEN_BALANCE_MASK);
            binding.btnToggleBalanceVisibility.setImageResource(R.drawable.outline_visibility_off_24);
        }
        binding.tvTotalBalance.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
    }

    private void renderWalletBalanceVisibility() {
        walletAdapter.setBalancesVisible(areWalletBalancesVisible);
        binding.btnToggleWalletBalances.setImageResource(
                areWalletBalancesVisible
                        ? R.drawable.outline_visibility_24
                        : R.drawable.outline_visibility_off_24
        );
    }

    private void updateReportCardTypeUi() {
        styleSegmentButton(binding.btnReportExpense, reportCardType == ReportCardType.EXPENSE);
        styleSegmentButton(binding.btnReportTrend, reportCardType == ReportCardType.TREND);
        binding.layoutExpenseReportContent.setVisibility(reportCardType == ReportCardType.EXPENSE ? View.VISIBLE : View.GONE);
        binding.layoutTrendReportContent.setVisibility(reportCardType == ReportCardType.TREND ? View.VISIBLE : View.GONE);
        binding.btnReportDetailCta.setText(reportCardType == ReportCardType.EXPENSE
                ? R.string.home_expense_report_cta
                : R.string.home_trend_report_cta);
        if (reportCardType == ReportCardType.EXPENSE) {
            renderExpenseReportCard();
        } else {
            renderTrendMetrics();
            renderTrendChart();
        }
    }

    private void updateExpenseRangeModeUi() {
        styleSegmentButton(binding.btnReportWeek, reportRangeMode == ExpenseRangeMode.WEEK);
        styleSegmentButton(binding.btnReportMonth, reportRangeMode == ExpenseRangeMode.MONTH);
        renderExpenseReportCard();
    }

    private void updateTopSpendingModeUi() {
        styleSegmentButton(binding.btnTopWeek, topSpendingMode == ExpenseRangeMode.WEEK);
        styleSegmentButton(binding.btnTopMonth, topSpendingMode == ExpenseRangeMode.MONTH);
        renderTopSpending();
    }

    private void updateTrendMetricUi() {
        int activeExpenseColor = ContextCompat.getColor(requireContext(), R.color.expense_red);
        int activeIncomeColor = ContextCompat.getColor(requireContext(), R.color.transfer_blue);
        int inactiveTextColor = ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary);
        int inactiveIndicatorColor = ContextCompat.getColor(requireContext(), R.color.statistics_card_stroke);

        boolean expenseSelected = trendMetric == TrendMetric.EXPENSE;
        boolean incomeSelected = trendMetric == TrendMetric.INCOME;
        binding.tvTrendExpenseAmount.setTextColor(expenseSelected ? activeExpenseColor : inactiveTextColor);
        binding.tvTrendIncomeAmount.setTextColor(incomeSelected ? activeIncomeColor : inactiveTextColor);
        binding.viewTrendExpenseIndicator.setBackgroundTintList(ColorStateList.valueOf(
                expenseSelected ? activeExpenseColor : inactiveIndicatorColor
        ));
        binding.viewTrendIncomeIndicator.setBackgroundTintList(ColorStateList.valueOf(
                incomeSelected ? activeIncomeColor : inactiveIndicatorColor
        ));
        binding.viewTrendLegendCurrent.setBackgroundTintList(ColorStateList.valueOf(
                expenseSelected ? activeExpenseColor : activeIncomeColor
        ));
        binding.tvTrendLegendCurrent.setTextColor(expenseSelected ? activeExpenseColor : activeIncomeColor);
        renderTrendChart();
    }

    private void renderExpenseReportCard() {
        double currentValue = reportRangeMode == ExpenseRangeMode.MONTH ? currentMonthExpense : currentWeekExpense;
        double previousValue = reportRangeMode == ExpenseRangeMode.MONTH ? previousMonthExpense : previousWeekExpense;

        binding.tvReportAmount.setText(CurrencyFormatter.format(currentValue, "VND"));
        binding.tvReportSubtitle.setText(reportRangeMode == ExpenseRangeMode.MONTH
                ? R.string.home_report_subtitle_month
                : R.string.home_report_subtitle_week);
        String changeText = formatPercentChange(previousValue, currentValue);
        binding.tvReportChange.setText(changeText);
        binding.tvReportChange.setTextColor(ContextCompat.getColor(
                requireContext(),
                resolveReportChangeColor(currentValue, previousValue)
        ));
        renderExpenseChart(previousValue, currentValue);
    }

    private void renderTrendMetrics() {
        binding.tvTrendExpenseAmount.setText(CurrencyFormatter.format(currentMonthExpense, "VND"));
        binding.tvTrendIncomeAmount.setText(CurrencyFormatter.format(currentMonthIncome, "VND"));
    }

    private void renderExpenseChart(double previousValue, double currentValue) {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0f, (float) previousValue));
        entries.add(new BarEntry(1f, (float) currentValue));

        BarDataSet dataSet = new BarDataSet(entries, "");
        dataSet.setColors(
                ContextCompat.getColor(requireContext(), R.color.transaction_expense_soft),
                ContextCompat.getColor(requireContext(), R.color.expense_red)
        );
        dataSet.setDrawValues(false);
        dataSet.setHighLightAlpha(0);

        BarData data = new BarData(dataSet);
        data.setBarWidth(0.48f);
        binding.chartMonthlyReport.setData(data);
        binding.chartMonthlyReport.getXAxis().setValueFormatter(new IndexAxisValueFormatter(new String[]{
                getString(reportRangeMode == ExpenseRangeMode.MONTH
                        ? R.string.home_previous_month_label
                        : R.string.home_previous_week_label),
                getString(reportRangeMode == ExpenseRangeMode.MONTH
                        ? R.string.home_current_month_label
                        : R.string.home_current_week_label)
        }));
        binding.chartMonthlyReport.getAxisLeft().setAxisMaximum((float) Math.max(Math.max(previousValue, currentValue) * 1.15f, 1f));
        binding.chartMonthlyReport.invalidate();
        binding.chartMonthlyReport.animateY(550, Easing.EaseOutCubic);
    }

    private void renderTrendChart() {
        if (binding == null) {
            return;
        }
        List<HomeViewModel.TrendPointUiModel> points = trendMetric == TrendMetric.EXPENSE
                ? expenseTrendPoints
                : incomeTrendPoints;
        if (points == null || points.isEmpty()) {
            binding.chartTrendReport.clear();
            binding.chartTrendReport.invalidate();
            binding.layoutTrendSummary.getRoot().setVisibility(View.GONE);
            return;
        }

        List<Entry> currentEntries = new ArrayList<>();
        List<Entry> averageEntries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        float maxValue = 0f;

        for (int index = 0; index < points.size(); index++) {
            HomeViewModel.TrendPointUiModel point = points.get(index);
            currentEntries.add(new Entry(index, (float) point.getCurrentAmount()));
            averageEntries.add(new Entry(index, (float) point.getAverageAmount()));
            labels.add(point.getLabel());
            maxValue = Math.max(maxValue, (float) Math.max(point.getCurrentAmount(), point.getAverageAmount()));
        }

        HomeViewModel.TrendPointUiModel latestPoint = points.get(points.size() - 1);
        @ColorInt int primaryColor = ContextCompat.getColor(requireContext(),
                trendMetric == TrendMetric.EXPENSE ? R.color.expense_red : R.color.transfer_blue);
        @ColorInt int averageColor = ContextCompat.getColor(requireContext(), R.color.statistics_text_muted);

        LineDataSet currentDataSet = new LineDataSet(currentEntries, getString(R.string.home_trend_legend_current));
        currentDataSet.setColor(primaryColor);
        currentDataSet.setLineWidth(3f);
        currentDataSet.setDrawCircles(false);
        currentDataSet.setDrawValues(false);
        currentDataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);
        currentDataSet.setDrawFilled(true);
        currentDataSet.setFillDrawable(ContextCompat.getDrawable(requireContext(),
                trendMetric == TrendMetric.EXPENSE
                        ? R.drawable.bg_statistics_compare_fill_red
                        : R.drawable.bg_statistics_compare_fill_blue));

        LineDataSet averageDataSet = new LineDataSet(averageEntries, getString(R.string.home_trend_legend_average));
        averageDataSet.setColor(averageColor);
        averageDataSet.setLineWidth(2.4f);
        averageDataSet.setDrawCircles(false);
        averageDataSet.setDrawValues(false);
        averageDataSet.setMode(LineDataSet.Mode.HORIZONTAL_BEZIER);

        List<Entry> focusEntries = new ArrayList<>();
        focusEntries.add(new Entry(points.size() - 1, (float) latestPoint.getCurrentAmount()));
        LineDataSet focusDataSet = new LineDataSet(focusEntries, "");
        focusDataSet.setColor(primaryColor);
        focusDataSet.setCircleColor(primaryColor);
        focusDataSet.setCircleHoleColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        focusDataSet.setCircleRadius(7f);
        focusDataSet.setCircleHoleRadius(3.5f);
        focusDataSet.setDrawValues(false);
        focusDataSet.setLineWidth(0f);

        binding.chartTrendReport.setData(new LineData(averageDataSet, currentDataSet, focusDataSet));
        binding.chartTrendReport.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        binding.chartTrendReport.getXAxis().setLabelCount(Math.min(labels.size(), 6), false);
        binding.chartTrendReport.getAxisLeft().setAxisMaximum(Math.max(maxValue * 1.12f, 1f));
        binding.chartTrendReport.invalidate();
        binding.chartTrendReport.animateX(700, Easing.EaseInOutQuad);
        renderTrendSummary(latestPoint, primaryColor);
    }

    private void renderTrendSummary(@NonNull HomeViewModel.TrendPointUiModel point, @ColorInt int accentColor) {
        binding.layoutTrendSummary.getRoot().setVisibility(View.VISIBLE);
        binding.layoutTrendSummary.tvComparisonSummaryDate.setText(formatComparisonDate(point.getDateMillis()));
        binding.layoutTrendSummary.tvComparisonSummaryCurrentValue.setText(
                CurrencyFormatter.format(point.getCurrentAmount(), "VND")
        );
        binding.layoutTrendSummary.tvComparisonSummaryCurrentValue.setTextColor(accentColor);
        binding.layoutTrendSummary.tvComparisonSummaryAverageValue.setText(
                getString(
                        R.string.statistics_detail_compare_average_value,
                        CurrencyFormatter.format(point.getAverageAmount(), "VND")
                )
        );
    }

    private void renderTopSpending() {
        List<CategorySumDTO> source = topSpendingMode == ExpenseRangeMode.MONTH
                ? monthlyTopCategories
                : weeklyTopCategories;
        boolean empty = source == null || source.isEmpty();
        binding.tvTopSpendingEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.rvTopSpending.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            topSpendingAdapter.submitList(new ArrayList<>());
            return;
        }

        double total = 0d;
        for (CategorySumDTO item : source) {
            total += item.getTotalAmount();
        }

        List<HomeTopSpendingAdapter.ItemUiModel> items = new ArrayList<>();
        int limit = Math.min(3, source.size());
        for (int index = 0; index < limit; index++) {
            CategorySumDTO item = source.get(index);
            int accent = IconProvider.getCategoryColor(
                    requireContext(),
                    item.getCategoryId(),
                    true
            );
            double percent = total > 0d ? (item.getTotalAmount() / total) * 100d : 0d;
            items.add(new HomeTopSpendingAdapter.ItemUiModel(
                    item.getCategoryId() != null ? item.getCategoryId() : String.valueOf(index),
                    resolveIcon(item.getIconName()),
                    accent,
                    item.getCategoryName() != null ? item.getCategoryName() : getString(R.string.ledger_section_unknown),
                    item.getTotalAmount(),
                    String.format(Locale.getDefault(), "%.0f%%", percent)
            ));
        }
        topSpendingAdapter.submitList(items);
    }

    private void navigateToStatisticsForCurrentReport() {
        if (reportCardType == ReportCardType.TREND) {
            navigateToStatistics(ExpenseRangeMode.MONTH, trendMetric == TrendMetric.EXPENSE ? TYPE_EXPENSE : TYPE_INCOME);
            return;
        }
        navigateToStatistics(reportRangeMode, TYPE_EXPENSE);
    }

    private int resolveRecentTransactionAmountColor(@Nullable String type) {
        return TYPE_EXPENSE.equals(type) ? R.color.expense_red : R.color.transfer_blue;
    }

    private int resolveReportChangeColor(double currentValue, double previousValue) {
        if (currentValue > previousValue) {
            return R.color.expense_red;
        }
        if (currentValue < previousValue) {
            return R.color.income_green;
        }
        return R.color.statistics_text_secondary;
    }

    private void navigateToStatistics(@NonNull ExpenseRangeMode mode, @Nullable String transactionType) {
        Bundle args = new Bundle();
        long[] bounds = mode == ExpenseRangeMode.MONTH ? getCurrentMonthBounds() : getCurrentWeekBounds();
        args.putLong("filterStartDate", bounds[0]);
        args.putLong("filterEndDate", bounds[1]);
        args.putString("filterPeriodType", mode == ExpenseRangeMode.MONTH ? "MONTH" : "WEEK");
        if (transactionType != null) {
            args.putString("filterTransactionType", transactionType);
        }
        Navigation.findNavController(binding.getRoot()).navigate(R.id.statisticsFragment, args);
    }

    private void styleSegmentButton(@NonNull MaterialButton button, boolean selected) {
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(
                requireContext(),
                selected ? R.color.statistics_text_primary : R.color.statistics_card_inner_bg
        )));
        button.setTextColor(ContextCompat.getColor(
                requireContext(),
                selected ? android.R.color.white : R.color.statistics_text_secondary
        ));
        button.setStrokeWidth(0);
    }

    private void configureExpenseChart(@NonNull BarChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setDrawGridBackground(false);
        chart.setTouchEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setExtraTopOffset(8f);
        chart.setExtraBottomOffset(8f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary));
        xAxis.setTextSize(13f);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary));
        leftAxis.setDrawGridLines(true);
        leftAxis.setGridColor(ContextCompat.getColor(requireContext(), R.color.statistics_card_stroke));
        leftAxis.setAxisMinimum(0f);
        leftAxis.setValueFormatter(new CompactMoneyValueFormatter());

        chart.getAxisRight().setEnabled(false);
    }

    private void configureTrendChart(@NonNull LineChart chart) {
        chart.getDescription().setEnabled(false);
        chart.getLegend().setEnabled(false);
        chart.setNoDataText(getString(R.string.statistics_no_data));
        chart.setTouchEnabled(false);
        chart.setScaleEnabled(false);
        chart.setPinchZoom(false);
        chart.setDrawGridBackground(false);
        chart.setExtraTopOffset(12f);
        chart.setExtraBottomOffset(8f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary));
        xAxis.setTextSize(12f);
        xAxis.setGranularity(1f);
        xAxis.setAvoidFirstLastClipping(true);

        YAxis leftAxis = chart.getAxisLeft();
        leftAxis.setAxisMinimum(0f);
        leftAxis.setTextColor(ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary));
        leftAxis.setGridColor(ContextCompat.getColor(requireContext(), R.color.statistics_card_stroke));
        leftAxis.enableGridDashedLine(10f, 10f, 0f);
        leftAxis.setValueFormatter(new CompactMoneyValueFormatter());

        chart.getAxisRight().setEnabled(false);
    }

    private int resolveCategoryIcon(@Nullable CategoryEntity category, @Nullable String type) {
        return IconProvider.resolveCategoryIconByType(
                requireContext(),
                category != null ? category.getIconName() : null,
                type
        );
    }

    private int resolveIcon(@Nullable String iconName) {
        return IconProvider.resolveCategoryIcon(requireContext(), iconName);
    }


    @NonNull
    private String formatShortDate(long timestamp) {
        return new SimpleDateFormat("dd 'tháng' M yyyy", new Locale("vi", "VN")).format(timestamp);
    }

    @NonNull
    private String formatAmountLabel(double amount, @Nullable String type) {
        if ("EXPENSE".equals(type)) {
            return "-" + CurrencyFormatter.format(amount, "VND");
        }
        return CurrencyFormatter.format(amount, "VND");
    }

    @NonNull
    private String formatPercentChange(double previousValue, double currentValue) {
        if (previousValue <= 0d) {
            return currentValue > 0d ? "+100%" : getString(R.string.home_report_no_change);
        }
        double change = ((currentValue - previousValue) / previousValue) * 100d;
        if (Math.abs(change) < 0.5d) {
            return getString(R.string.home_report_no_change);
        }
        return String.format(Locale.getDefault(), "%s%.0f%%", change > 0d ? "+" : "", change);
    }

    @NonNull
    private String formatComparisonDate(long dateMillis) {
        LocalDate date = Instant.ofEpochMilli(dateMillis).atZone(APP_ZONE).toLocalDate();
        return String.format(Locale.getDefault(), "%02d/%02d/%d:",
                date.getDayOfMonth(),
                date.getMonthValue(),
                date.getYear());
    }

    @NonNull
    private long[] getCurrentMonthBounds() {
        Calendar calendar = Calendar.getInstance();
        normalizeToStartOfDay(calendar);
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        long start = calendar.getTimeInMillis();
        calendar.add(Calendar.MONTH, 1);
        calendar.add(Calendar.MILLISECOND, -1);
        long end = calendar.getTimeInMillis();
        return new long[]{start, end};
    }

    @NonNull
    private long[] getCurrentWeekBounds() {
        Calendar calendar = Calendar.getInstance();
        calendar.setFirstDayOfWeek(Calendar.MONDAY);
        normalizeToStartOfDay(calendar);
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, -1);
        }
        long start = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_MONTH, 7);
        calendar.add(Calendar.MILLISECOND, -1);
        long end = calendar.getTimeInMillis();
        return new long[]{start, end};
    }

    private void normalizeToStartOfDay(@NonNull Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private enum ExpenseRangeMode {
        WEEK,
        MONTH
    }

    private enum ReportCardType {
        EXPENSE,
        TREND
    }

    private enum TrendMetric {
        EXPENSE,
        INCOME
    }

    private static class CompactMoneyValueFormatter extends ValueFormatter {
        @NonNull
        @Override
        public String getFormattedValue(float value) {
            if (value >= 1_000_000f) {
                return String.format(Locale.getDefault(), "%.0f M", value / 1_000_000f);
            }
            if (value >= 1_000f) {
                return String.format(Locale.getDefault(), "%.0f K", value / 1_000f);
            }
            return String.format(Locale.getDefault(), "%.0f", value);
        }
    }
}
