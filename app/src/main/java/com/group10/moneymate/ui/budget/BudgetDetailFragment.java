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
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.databinding.FragmentBudgetDetailBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class BudgetDetailFragment extends Fragment {

    private FragmentBudgetDetailBinding binding;
    private BudgetViewModel viewModel;
    private BudgetBreakdownAdapter breakdownAdapter;
    private BudgetUIModel currentItem;
    private List<BudgetUIModel> currentActiveBudgets = Collections.emptyList();
    private BudgetViewModel.BudgetSummaryUIModel currentSummary;
    private boolean isAggregate;

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
                container.authRepository.getCurrentUserId()
        );
        viewModel = new ViewModelProvider(this, factory).get(BudgetViewModel.class);
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
            binding.breakdownSection.setVisibility(View.GONE);
            renderDetail(
                    item.getCategoryName(),
                    item.getCategoryIcon(),
                    item.getCategoryColorHex(),
                    item.getWalletName(),
                    item.getBudgetEntity(),
                    item.getSpentAmount(),
                    Collections.emptyList(),
                    false
            );
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
        aggregateBudget.setCategoryId("");
        aggregateBudget.setAmount(currentSummary.getTotalBudget());
        aggregateBudget.setStartDate(earliestStart);
        aggregateBudget.setEndDate(latestEnd);

        renderDetail(
                getString(R.string.budget_all_categories),
                "",
                "#4CAF50",
                getString(R.string.budget_total_scope),
                aggregateBudget,
                currentSummary.getTotalSpent(),
                sortedItems,
                true
        );
    }

    private void renderDetail(@NonNull String title,
                              @NonNull String iconName,
                              @NonNull String colorHex,
                              @NonNull String walletScopeLabel,
                              @NonNull BudgetEntity entity,
                              double spentAmount,
                              @NonNull List<BudgetUIModel> breakdownItems,
                              boolean showBreakdown) {
        double remaining = entity.getAmount() - spentAmount;
        int daysLeft = BudgetUiUtils.getDaysLeftInclusive(entity.getEndDate());
        int totalDays = BudgetUiUtils.getTotalDaysInclusive(entity);
        int elapsedDays = BudgetUiUtils.getElapsedDays(entity);
        double recommendedDaily = daysLeft > 0 ? Math.max(remaining, 0d) / daysLeft : 0d;
        double actualDaily = elapsedDays > 0 ? spentAmount / elapsedDays : 0d;
        double projectedSpending = actualDaily * totalDays;
        float percent = entity.getAmount() <= 0d ? 0f : (float) ((spentAmount / entity.getAmount()) * 100f);
        int progress = Math.max(0, Math.min(100, Math.round(percent)));
        int iconTint = BudgetUiUtils.parseColorOrDefault(
                colorHex,
                ContextCompat.getColor(requireContext(), R.color.budget_safe_green)
        );

        binding.ivCategoryIcon.setImageResource(BudgetUiUtils.resolveCategoryIcon(
                requireContext(),
                iconName,
                title
        ));
        binding.ivCategoryIcon.setImageTintList(ColorStateList.valueOf(iconTint));
        binding.iconContainer.setBackgroundTintList(ColorStateList.valueOf(
                androidx.core.graphics.ColorUtils.setAlphaComponent(iconTint, 32)
        ));
        binding.tvCategoryName.setText(title);
        binding.tvAmount.setText(BudgetUiUtils.formatCurrency(entity.getAmount()));
        binding.tvSpentValue.setText(BudgetUiUtils.formatCurrency(spentAmount));
        binding.tvLeftValue.setText(BudgetUiUtils.formatCurrency(Math.max(remaining, 0d)));
        binding.progressBudget.setProgressCompat(progress, false);
        binding.progressBudget.setIndicatorColor(resolveProgressColor(percent, remaining));
        binding.tvPeriod.setText(BudgetUiUtils.formatDateRange(entity.getStartDate(), entity.getEndDate()));
        binding.tvDaysLeft.setText(getResources().getQuantityString(
                R.plurals.budget_days_left,
                Math.max(daysLeft, 1),
                Math.max(daysLeft, 1)
        ));
        binding.tvWalletScope.setText(walletScopeLabel);
        binding.tvRecommendedDaily.setText(BudgetUiUtils.formatDecimalNumber(recommendedDaily));
        binding.tvProjectedSpending.setText(BudgetUiUtils.formatDecimalNumber(projectedSpending));
        binding.tvProjectedSpending.setTextColor(projectedSpending > entity.getAmount()
                ? ContextCompat.getColor(requireContext(), R.color.budget_danger_red)
                : ContextCompat.getColor(requireContext(), android.R.color.black));
        binding.tvActualDaily.setText(BudgetUiUtils.formatDecimalNumber(actualDaily));
        binding.chartView.setBudgetData(
                entity.getAmount(),
                spentAmount,
                projectedSpending,
                BudgetUiUtils.getTimelineFraction(entity),
                entity.getStartDate(),
                entity.getEndDate()
        );

        binding.progressMarkerContainer.post(() -> {
            int availableWidth = binding.progressMarkerContainer.getWidth();
            if (availableWidth <= 0) {
                return;
            }
            int markerX = Math.round(availableWidth * BudgetUiUtils.getTimelineFraction(entity));
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

        binding.breakdownSection.setVisibility(showBreakdown ? View.VISIBLE : View.GONE);
        if (showBreakdown) {
            breakdownAdapter.submitList(new ArrayList<>(breakdownItems));
        } else {
            breakdownAdapter.submitList(Collections.emptyList());
        }
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
        BottomNavigationView bottomNavigationView =
                requireActivity().findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.transactionListFragment);
            return;
        }
        Navigation.findNavController(binding.getRoot()).navigate(R.id.transactionListFragment);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
