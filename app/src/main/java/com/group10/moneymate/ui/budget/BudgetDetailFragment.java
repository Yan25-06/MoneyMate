package com.group10.moneymate.ui.budget;

import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.databinding.FragmentBudgetDetailBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.ui.main.HomeActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TreeMap;

public class BudgetDetailFragment extends Fragment {

    private FragmentBudgetDetailBinding binding;
    private BudgetViewModel viewModel;
    private BudgetBreakdownAdapter breakdownAdapter;
    private BudgetUIModel currentItem;
    private List<BudgetUIModel> currentActiveBudgets = Collections.emptyList();
    private BudgetViewModel.BudgetSummaryUIModel currentSummary;
    private boolean isAggregate;
    @Nullable
    private String aggregateWalletFilterLabel;
    @NonNull
    private BudgetViewModel.BudgetTab budgetTab = BudgetViewModel.BudgetTab.THIS_MONTH;
    private final Map<String, LiveData<List<TransactionEntity>>> chartSources = new HashMap<>();
    private final Map<String, List<TransactionEntity>> chartTransactions = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBudgetDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupViewModel();
        setupInsets();
        setupRecyclerView();
        setupArgs();
        setupActions();
        observeData();
    }

    private void setupViewModel() {
        MoneyMateApplication app = (MoneyMateApplication) requireActivity().getApplication();
        AppContainer container = app.getAppContainer();
        BudgetViewModel.Factory factory = new BudgetViewModel.Factory(
                container.budgetRepository,
                container.categoryRepository,
                container.transactionRepository,
                container.walletRepository,
                container.authRepository.getCurrentUserId(),
                new BudgetViewModel.Labels(
                        getString(R.string.budget_all_categories),
                        getString(R.string.budget_other_categories),
                        getString(R.string.budget_wallet_scope_total),
                        getString(R.string.budget_unknown_wallet),
                        getString(R.string.budget_unknown_category)
                )
        );
        viewModel = new ViewModelProvider(this, factory).get(BudgetViewModel.class);
        BudgetStatisticsCalculator.logExampleOnce();
    }

    private void setupInsets() {
        final int topPadding = binding.topBar.getPaddingTop();
        final int bottomPadding = binding.scrollContent.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.topBar.setPadding(
                    binding.topBar.getPaddingLeft(),
                    topPadding + systemBars.top,
                    binding.topBar.getPaddingRight(),
                    binding.topBar.getPaddingBottom()
            );
            binding.scrollContent.setPadding(
                    binding.scrollContent.getPaddingLeft(),
                    binding.scrollContent.getPaddingTop(),
                    binding.scrollContent.getPaddingRight(),
                    bottomPadding + systemBars.bottom
            );
            return insets;
        });
    }

    private void setupRecyclerView() {
        breakdownAdapter = new BudgetBreakdownAdapter();
        breakdownAdapter.setOnItemClickListener(item -> {
            BudgetDetailFragmentDirections.ActionBudgetDetailToBudgetDetail action =
                    BudgetDetailFragmentDirections.actionBudgetDetailToBudgetDetail();
            action.setBudgetId(item.getBudgetEntity().getId());
            Navigation.findNavController(binding.getRoot()).navigate(action);
        });
        binding.rvBreakdown.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvBreakdown.setAdapter(breakdownAdapter);
        binding.rvBreakdown.setNestedScrollingEnabled(false);
    }

    private void setupArgs() {
        BudgetDetailFragmentArgs args = BudgetDetailFragmentArgs.fromBundle(getArguments() != null
                ? getArguments()
                : new Bundle());
        isAggregate = args.getIsAggregate();
        aggregateWalletFilterLabel = args.getWalletFilterLabel();
        try {
            budgetTab = BudgetViewModel.BudgetTab.valueOf(args.getBudgetTab());
        } catch (IllegalArgumentException | NullPointerException exception) {
            budgetTab = BudgetViewModel.BudgetTab.THIS_MONTH;
        }
        viewModel.setSelectedTab(budgetTab);
        viewModel.setSelectedWalletFilter(args.getWalletFilterId());
    }

    private void setupActions() {
        binding.btnClose.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.btnTransactions.setOnClickListener(v -> openTransactionsTab());
        binding.btnEdit.setOnClickListener(v -> {
            if (isAggregate || currentItem == null) {
                return;
            }
            BudgetDetailFragmentDirections.ActionBudgetDetailToAddEdit action =
                    BudgetDetailFragmentDirections.actionBudgetDetailToAddEdit();
            action.setBudgetId(currentItem.getBudgetEntity().getId());
            Navigation.findNavController(v).navigate(action);
        });
        binding.btnDelete.setOnClickListener(v -> confirmDelete());
    }

    private void observeData() {
        if (isAggregate) {
            binding.btnEdit.setVisibility(View.GONE);
            binding.btnDelete.setVisibility(View.GONE);
            viewModel.getActiveBudgets().observe(getViewLifecycleOwner(), budgets -> {
                currentActiveBudgets = budgets != null ? budgets : Collections.emptyList();
                syncAggregateChartSources();
                renderAggregateIfReady();
            });
            viewModel.getSummary().observe(getViewLifecycleOwner(), summary -> {
                currentSummary = summary;
                renderAggregateIfReady();
            });
            return;
        }

        BudgetDetailFragmentArgs args = BudgetDetailFragmentArgs.fromBundle(getArguments() != null
                ? getArguments()
                : new Bundle());
        if (args.getBudgetId() == null) {
            return;
        }
        viewModel.getBudgetUiModel(args.getBudgetId()).observe(getViewLifecycleOwner(), item -> {
            currentItem = item;
            if (item == null) {
                return;
            }
            syncSingleChartSource(item);
            binding.breakdownSection.setVisibility(View.GONE);
            renderDetail(buildSingleDetailModel(item));
        });
    }

    private void renderAggregateIfReady() {
        if (currentSummary == null) {
            return;
        }
        if (currentActiveBudgets.isEmpty()) {
            binding.breakdownSection.setVisibility(View.GONE);
            return;
        }

        List<BudgetUIModel> sortedItems = new ArrayList<>(currentActiveBudgets);
        sortedItems.sort(Comparator.comparingDouble(BudgetUIModel::getSpentAmount).reversed());

        long earliestStart = Long.MAX_VALUE;
        long latestEnd = 0L;
        for (BudgetUIModel item : currentActiveBudgets) {
            earliestStart = Math.min(earliestStart, item.getBudgetEntity().getStartDate());
            latestEnd = Math.max(latestEnd, item.getBudgetEntity().getEndDate());
        }

        BudgetEntity aggregateBudget = new BudgetEntity();
        aggregateBudget.setCategoryId(null);
        aggregateBudget.setAmount(currentSummary.getTotalBudget());
        aggregateBudget.setStartDate(earliestStart);
        aggregateBudget.setEndDate(latestEnd);

        renderDetail(new DetailRenderModel(
                getString(R.string.budget_all_categories),
                "",
                "#4CAF50",
                aggregateWalletFilterLabel != null
                        ? aggregateWalletFilterLabel
                        : getString(R.string.budget_total_scope),
                aggregateBudget,
                currentSummary.getTotalSpent(),
                buildChartPoints(aggregateBudget, buildAggregateTransactions()),
                sortedItems,
                true
        ));
    }

    private void renderDetail(@NonNull DetailRenderModel model) {
        double remaining = model.entity.getAmount() - model.spentAmount;
        BudgetStatisticsCalculator.BudgetStatistics statistics =
                BudgetStatisticsCalculator.calculate(
                        model.entity.getStartDate(),
                        model.entity.getEndDate(),
                        model.entity.getAmount(),
                        model.spentAmount
                );
        int daysLeft = statistics.getDaysRemaining();
        double recommendedDaily = statistics.getRecommendedDailySpend();
        double actualDaily = statistics.getActualDailyAverage();
        double projectedSpending = statistics.getProjectedTotalSpend();
        float percent = model.entity.getAmount() <= 0d
                ? 0f
                : (float) ((model.spentAmount / model.entity.getAmount()) * 100f);
        int progress = Math.max(0, Math.min(100, Math.round(percent)));
        int iconTint = BudgetUiUtils.parseColorOrDefault(
                model.colorHex,
                ContextCompat.getColor(requireContext(), R.color.budget_safe_green)
        );

        binding.ivCategoryIcon.setImageResource(BudgetUiUtils.resolveCategoryIcon(
                requireContext(),
                model.iconName,
                model.title
        ));
        binding.ivCategoryIcon.setImageTintList(ColorStateList.valueOf(iconTint));
        binding.iconContainer.setBackgroundTintList(ColorStateList.valueOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(iconTint, 32)
        ));
        binding.tvCategoryName.setText(model.title);
        binding.tvAmount.setText(BudgetUiUtils.formatCurrency(model.entity.getAmount()));
        binding.tvSpentValue.setText(BudgetUiUtils.formatCurrency(model.spentAmount));
        binding.tvLeftValue.setText(BudgetUiUtils.formatCurrency(Math.max(remaining, 0d)));
        binding.progressBudget.setProgressCompat(progress, false);
        binding.progressBudget.setIndicatorColor(resolveProgressColor(percent, remaining));
        binding.tvPeriod.setText(BudgetUiUtils.formatDateRange(model.entity.getStartDate(), model.entity.getEndDate()));
        binding.tvDaysLeft.setText(getResources().getQuantityString(
                R.plurals.budget_days_left,
                Math.max(daysLeft, 1),
                Math.max(daysLeft, 1)
        ));
        binding.tvWalletScope.setText(model.walletScopeLabel);
        binding.tvRecommendedDaily.setText(BudgetUiUtils.formatDecimalCurrency(recommendedDaily));
        binding.tvProjectedSpending.setText(BudgetUiUtils.formatDecimalCurrency(projectedSpending));
        binding.tvProjectedSpending.setTextColor(projectedSpending > model.entity.getAmount()
                ? ContextCompat.getColor(requireContext(), R.color.budget_danger_red)
                : ContextCompat.getColor(requireContext(), android.R.color.black));
        binding.tvActualDaily.setText(BudgetUiUtils.formatDecimalCurrency(actualDaily));
        binding.chartView.setBudgetData(
                model.entity.getAmount(),
                projectedSpending,
                model.entity.getStartDate(),
                model.entity.getEndDate(),
                model.chartPoints
        );

        binding.progressMarkerContainer.post(() -> {
            int availableWidth = binding.progressMarkerContainer.getWidth();
            if (availableWidth <= 0) {
                return;
            }
            int markerX = Math.round(availableWidth * BudgetUiUtils.getTimelineFraction(model.entity));
            FrameLayout.LayoutParams markerParams =
                    (FrameLayout.LayoutParams) binding.vTodayMarker.getLayoutParams();
            markerParams.leftMargin = Math.max(0, Math.min(availableWidth, markerX));
            binding.vTodayMarker.setLayoutParams(markerParams);

            binding.tvToday.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            int chipWidth = binding.tvToday.getMeasuredWidth();
            FrameLayout.LayoutParams chipParams =
                    (FrameLayout.LayoutParams) binding.tvToday.getLayoutParams();
            chipParams.leftMargin = Math.max(
                    0,
                    Math.min(availableWidth - chipWidth, markerX - (chipWidth / 2))
            );
            binding.tvToday.setLayoutParams(chipParams);
        });

        binding.breakdownSection.setVisibility(model.showBreakdown ? View.VISIBLE : View.GONE);
        if (model.showBreakdown) {
            breakdownAdapter.submitList(new ArrayList<>(model.breakdownItems));
        } else {
            breakdownAdapter.submitList(Collections.emptyList());
        }
    }

    @NonNull
    private DetailRenderModel buildSingleDetailModel(@NonNull BudgetUIModel item) {
        return new DetailRenderModel(
                item.getCategoryName(),
                item.getCategoryIcon(),
                item.getCategoryColorHex(),
                item.getWalletName(),
                item.getBudgetEntity(),
                item.getSpentAmount(),
                buildChartPoints(
                        item.getBudgetEntity(),
                        chartTransactions.get(item.getBudgetEntity().getId())
                ),
                Collections.emptyList(),
                false
        );
    }

    private void syncSingleChartSource(@NonNull BudgetUIModel item) {
        clearChartSources();
        String budgetId = item.getBudgetEntity().getId();
        LiveData<List<TransactionEntity>> source = viewModel.getBudgetTransactions(item.getBudgetEntity());
        chartSources.put(budgetId, source);
        source.observe(getViewLifecycleOwner(), transactions -> {
            chartTransactions.put(
                    budgetId,
                    transactions != null ? new ArrayList<>(transactions) : Collections.emptyList()
            );
            if (currentItem != null
                    && budgetId.equals(currentItem.getBudgetEntity().getId())
                    && !isAggregate) {
                renderDetail(buildSingleDetailModel(currentItem));
            }
        });
    }

    private void syncAggregateChartSources() {
        clearChartSources();
        if (!isAggregate) {
            return;
        }
        for (BudgetUIModel item : currentActiveBudgets) {
            String budgetId = item.getBudgetEntity().getId();
            LiveData<List<TransactionEntity>> source = viewModel.getBudgetTransactions(item.getBudgetEntity());
            chartSources.put(budgetId, source);
            source.observe(getViewLifecycleOwner(), transactions -> {
                chartTransactions.put(
                        budgetId,
                        transactions != null ? new ArrayList<>(transactions) : Collections.emptyList()
                );
                renderAggregateIfReady();
            });
        }
    }

    private void clearChartSources() {
        for (LiveData<List<TransactionEntity>> source : chartSources.values()) {
            source.removeObservers(getViewLifecycleOwner());
        }
        chartSources.clear();
        chartTransactions.clear();
    }

    @NonNull
    private List<TransactionEntity> buildAggregateTransactions() {
        Map<String, TransactionEntity> uniqueTransactions = new HashMap<>();
        for (BudgetUIModel item : currentActiveBudgets) {
            List<TransactionEntity> transactions =
                    chartTransactions.get(item.getBudgetEntity().getId());
            if (transactions == null) {
                continue;
            }
            for (TransactionEntity transaction : transactions) {
                uniqueTransactions.put(transaction.getId(), transaction);
            }
        }
        return new ArrayList<>(uniqueTransactions.values());
    }

    @NonNull
    private List<BudgetProjectionChartView.ChartPoint> buildChartPoints(@NonNull BudgetEntity entity,
                                                                        @Nullable List<TransactionEntity> transactions) {
        long start = BudgetUiUtils.startOfDay(entity.getStartDate());
        long end = BudgetUiUtils.startOfDay(entity.getEndDate());
        TreeMap<Long, Double> dailyTotals = new TreeMap<>();
        dailyTotals.put(start, 0d);

        if (transactions != null) {
            for (TransactionEntity transaction : transactions) {
                long day = BudgetUiUtils.startOfDay(transaction.getTimestamp());
                if (day < start || day > end) {
                    continue;
                }
                double currentTotal = dailyTotals.containsKey(day) ? dailyTotals.get(day) : 0d;
                dailyTotals.put(day, currentTotal + transaction.getAmount());
            }
        }

        List<BudgetProjectionChartView.ChartPoint> points = new ArrayList<>();
        double runningTotal = 0d;
        points.add(new BudgetProjectionChartView.ChartPoint(start, 0d));
        for (Map.Entry<Long, Double> entry : dailyTotals.entrySet()) {
            if (entry.getKey() == start) {
                runningTotal += entry.getValue();
                points.set(0, new BudgetProjectionChartView.ChartPoint(start, runningTotal));
                continue;
            }
            runningTotal += entry.getValue();
            points.add(new BudgetProjectionChartView.ChartPoint(entry.getKey(), runningTotal));
        }
        return points;
    }

    @ColorInt
    private int resolveProgressColor(float percent, double remainingAmount) {
        if (percent > 90f || remainingAmount < 0d) {
            return ContextCompat.getColor(requireContext(), R.color.budget_danger_red);
        }
        if (percent >= 70f) {
            return ContextCompat.getColor(requireContext(), R.color.budget_warning_orange);
        }
        return ContextCompat.getColor(requireContext(), R.color.budget_safe_green);
    }

    private void confirmDelete() {
        if (isAggregate || currentItem == null) {
            return;
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_delete_action)
                .setMessage(R.string.budget_delete_confirm)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.btn_delete, this::deleteBudget)
                .show();
    }

    private void deleteBudget(DialogInterface dialog, int which) {
        if (currentItem == null) {
            return;
        }
        viewModel.deleteBudget(currentItem.getBudgetEntity());
        Toast.makeText(requireContext(), R.string.budget_delete_success, Toast.LENGTH_SHORT).show();
        Navigation.findNavController(binding.getRoot()).navigateUp();
    }

    private void openTransactionsTab() {
        Bundle args = new Bundle();
        if (!isAggregate && currentItem != null) {
            args = new Bundle();
            args.putString("budgetCategoryId", currentItem.getBudgetEntity().getCategoryId());
            args.putString("budgetWalletId", currentItem.getBudgetEntity().getWalletId());
            args.putLong("budgetStartDate", currentItem.getBudgetEntity().getStartDate());
            args.putLong("budgetEndDate", currentItem.getBudgetEntity().getEndDate());
        } else if (isAggregate && currentActiveBudgets != null && !currentActiveBudgets.isEmpty()) {
            long earliestStart = Long.MAX_VALUE;
            long latestEnd = 0L;
            for (BudgetUIModel item : currentActiveBudgets) {
                earliestStart = Math.min(earliestStart, item.getBudgetEntity().getStartDate());
                latestEnd = Math.max(latestEnd, item.getBudgetEntity().getEndDate());
            }
            if (earliestStart != Long.MAX_VALUE && latestEnd > 0L) {
                args.putLong("budgetStartDate", earliestStart);
                args.putLong("budgetEndDate", latestEnd);
                args.putString("budgetAggregateFilters", buildAggregateBudgetFilterSpec(currentActiveBudgets));
            }
        }

        if (requireActivity() instanceof HomeActivity) {
            ((HomeActivity) requireActivity()).navigateToBottomDestination(
                    R.id.transactionListFragment,
                    args
            );
            return;
        }

        NavController navController = Navigation.findNavController(binding.getRoot());
        navController.navigate(R.id.transactionListFragment, args);
    }

    @NonNull
    private String buildAggregateBudgetFilterSpec(@NonNull List<BudgetUIModel> items) {
        StringJoiner joiner = new StringJoiner(";");
        for (BudgetUIModel item : items) {
            BudgetEntity budgetEntity = item.getBudgetEntity();
            String categoryId = budgetEntity.getCategoryId() != null ? budgetEntity.getCategoryId() : "";
            String walletId = budgetEntity.getWalletId() != null ? budgetEntity.getWalletId() : "";
            joiner.add(
                    categoryId
                            + "|"
                            + walletId
                            + "|"
                            + budgetEntity.getStartDate()
                            + "|"
                            + budgetEntity.getEndDate()
            );
        }
        return joiner.toString();
    }

    private static final class DetailRenderModel {
        @NonNull
        private final String title;
        @NonNull
        private final String iconName;
        @NonNull
        private final String colorHex;
        @NonNull
        private final String walletScopeLabel;
        @NonNull
        private final BudgetEntity entity;
        private final double spentAmount;
        @NonNull
        private final List<BudgetProjectionChartView.ChartPoint> chartPoints;
        @NonNull
        private final List<BudgetUIModel> breakdownItems;
        private final boolean showBreakdown;

        private DetailRenderModel(@NonNull String title,
                                  @NonNull String iconName,
                                  @NonNull String colorHex,
                                  @NonNull String walletScopeLabel,
                                  @NonNull BudgetEntity entity,
                                  double spentAmount,
                                  @NonNull List<BudgetProjectionChartView.ChartPoint> chartPoints,
                                  @NonNull List<BudgetUIModel> breakdownItems,
                                  boolean showBreakdown) {
            this.title = title;
            this.iconName = iconName;
            this.colorHex = colorHex;
            this.walletScopeLabel = walletScopeLabel;
            this.entity = entity;
            this.spentAmount = spentAmount;
            this.chartPoints = chartPoints;
            this.breakdownItems = breakdownItems;
            this.showBreakdown = showBreakdown;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        clearChartSources();
        binding = null;
    }
}
