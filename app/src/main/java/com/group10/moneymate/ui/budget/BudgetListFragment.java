package com.group10.moneymate.ui.budget;

import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.os.Bundle;
import android.widget.PopupMenu;

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

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentBudgetListBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;

import java.util.Collections;
import java.util.List;

public class BudgetListFragment extends Fragment {

    private FragmentBudgetListBinding binding;
    private BudgetViewModel viewModel;
    private BudgetAdapter activeAdapter;
    private BudgetAdapter finishedAdapter;

    private List<BudgetUIModel> activeBudgets = Collections.emptyList();
    private List<BudgetUIModel> finishedBudgets = Collections.emptyList();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBudgetListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupViewModel();
        setupInsets();
        setupRecyclerViews();
        setupActions();
        observeViewModel();
    }

    private void setupViewModel() {
        MoneyMateApplication app = (MoneyMateApplication) requireActivity().getApplication();
        AppContainer container = app.getAppContainer();
        BudgetViewModel.Factory factory = new BudgetViewModel.Factory(
                container.budgetRepository,
                container.categoryRepository,
                container.transactionRepository,
                container.authRepository.getCurrentUserId()
        );
        viewModel = new ViewModelProvider(this, factory).get(BudgetViewModel.class);
    }

    private void setupInsets() {
        final int topPadding = binding.appBarContent.getPaddingTop();
        final int bottomPadding = binding.scrollContent.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appBarContent.setPadding(
                    binding.appBarContent.getPaddingLeft(),
                    topPadding + systemBars.top,
                    binding.appBarContent.getPaddingRight(),
                    binding.appBarContent.getPaddingBottom()
            );
            binding.scrollContent.setPadding(
                    binding.scrollContent.getPaddingLeft(),
                    binding.scrollContent.getPaddingTop(),
                    binding.scrollContent.getPaddingRight(),
                    bottomPadding + systemBars.bottom
            );
            binding.emptyState.setPadding(
                    binding.emptyState.getPaddingLeft(),
                    binding.emptyState.getPaddingTop() + systemBars.top,
                    binding.emptyState.getPaddingRight(),
                    binding.emptyState.getPaddingBottom() + systemBars.bottom
            );
            return insets;
        });
    }

    private void setupRecyclerViews() {
        activeAdapter = new BudgetAdapter();
        finishedAdapter = new BudgetAdapter();
        BudgetAdapter.OnBudgetClickListener clickListener = item -> {
            BudgetListFragmentDirections.ActionBudgetListToDetail action =
                    BudgetListFragmentDirections.actionBudgetListToDetail();
            action.setBudgetId(item.getBudgetEntity().getId());
            Navigation.findNavController(requireView()).navigate(action);
        };
        activeAdapter.setOnBudgetClickListener(clickListener);
        finishedAdapter.setOnBudgetClickListener(clickListener);

        binding.rvActiveBudgets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvActiveBudgets.setAdapter(activeAdapter);
        binding.rvActiveBudgets.setNestedScrollingEnabled(false);

        binding.rvFinishedBudgets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvFinishedBudgets.setAdapter(finishedAdapter);
        binding.rvFinishedBudgets.setNestedScrollingEnabled(false);
    }

    private void setupActions() {
        View.OnClickListener createBudgetListener = v -> Navigation.findNavController(v).navigate(
                BudgetListFragmentDirections.actionBudgetListToAddEdit()
        );

        binding.btnCreateBudget.setOnClickListener(createBudgetListener);
        binding.btnEmptyCreateBudget.setOnClickListener(createBudgetListener);
        binding.btnBudgetMenu.setOnClickListener(this::showOverflowMenu);
        binding.rowAllCategories.setOnClickListener(v -> {
            BudgetListFragmentDirections.ActionBudgetListToDetail action =
                    BudgetListFragmentDirections.actionBudgetListToDetail();
            action.setIsAggregate(true);
            Navigation.findNavController(v).navigate(action);
        });
    }

    private void observeViewModel() {
        viewModel.getActiveBudgets().observe(getViewLifecycleOwner(), budgets -> {
            activeBudgets = budgets != null ? budgets : Collections.emptyList();
            activeAdapter.submitList(activeBudgets);
            renderSections();
        });

        viewModel.getFinishedBudgets().observe(getViewLifecycleOwner(), budgets -> {
            finishedBudgets = budgets != null ? budgets : Collections.emptyList();
            finishedAdapter.submitList(finishedBudgets);
            renderSections();
        });

        viewModel.getSummary().observe(getViewLifecycleOwner(), this::renderSummary);
    }

    private void renderSummary(@Nullable BudgetViewModel.BudgetSummaryUIModel summary) {
        if (summary == null || !summary.hasActiveBudgets()) {
            binding.cardSummary.setVisibility(View.GONE);
            binding.tvActiveEmpty.setVisibility(View.VISIBLE);
            binding.tvActiveEmpty.setText(R.string.budget_no_active);
            return;
        }

        binding.cardSummary.setVisibility(View.VISIBLE);
        binding.tvActiveEmpty.setVisibility(View.GONE);
        binding.tvSummaryAvailableAmount.setText(BudgetUiUtils.formatCurrency(summary.getRemainingAmount()));
        binding.tvSummaryTotalBudget.setText(BudgetUiUtils.formatCompactCurrency(summary.getTotalBudget()));
        binding.tvSummarySpent.setText(BudgetUiUtils.formatCompactCurrency(summary.getTotalSpent()));
        binding.tvSummaryEnd.setText(getResources().getQuantityString(
                R.plurals.budget_days_short,
                Math.max(summary.getDaysLeft(), 1),
                Math.max(summary.getDaysLeft(), 1)
        ));

        int color = resolveSummaryColor(summary);
        binding.budgetArcProgress.setProgressFraction(Math.min(1f, summary.getPercent() / 100f));
        binding.budgetArcProgress.setProgressColor(color);
        binding.tvSummaryAvailableAmount.setTextColor(color);
    }

    private int resolveSummaryColor(@NonNull BudgetViewModel.BudgetSummaryUIModel summary) {
        if (summary.getPercent() > 90f || summary.getRemainingAmount() < 0d) {
            return ContextCompat.getColor(requireContext(), R.color.budget_danger_red);
        }
        if (summary.getPercent() >= 70f) {
            return ContextCompat.getColor(requireContext(), R.color.budget_warning_orange);
        }
        return ContextCompat.getColor(requireContext(), R.color.budget_safe_green);
    }

    private void renderSections() {
        boolean hasAnyBudgets = !activeBudgets.isEmpty() || !finishedBudgets.isEmpty();

        binding.scrollContent.setVisibility(hasAnyBudgets ? View.VISIBLE : View.GONE);
        binding.emptyState.setVisibility(hasAnyBudgets ? View.GONE : View.VISIBLE);

        binding.tvActiveSection.setVisibility(View.VISIBLE);
        binding.rvActiveBudgets.setVisibility(activeBudgets.isEmpty() ? View.GONE : View.VISIBLE);
        if (activeBudgets.isEmpty() && hasAnyBudgets) {
            binding.tvActiveEmpty.setVisibility(View.VISIBLE);
        } else if (binding.cardSummary.getVisibility() == View.VISIBLE) {
            binding.tvActiveEmpty.setVisibility(View.GONE);
        }

        boolean showFinished = !finishedBudgets.isEmpty();
        binding.tvFinishedSection.setVisibility(showFinished ? View.VISIBLE : View.GONE);
        binding.rvFinishedBudgets.setVisibility(showFinished ? View.VISIBLE : View.GONE);
        binding.dividerFinished.setVisibility(showFinished ? View.VISIBLE : View.GONE);
    }

    private void showOverflowMenu(@NonNull View anchor) {
        PopupMenu popupMenu = new PopupMenu(requireContext(), anchor);
        popupMenu.inflate(R.menu.menu_budget_list);
        MenuItem scrollItem = popupMenu.getMenu().findItem(R.id.action_scroll_finished);
        scrollItem.setVisible(!finishedBudgets.isEmpty());
        popupMenu.setOnMenuItemClickListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.action_create_budget) {
                Navigation.findNavController(anchor).navigate(
                        BudgetListFragmentDirections.actionBudgetListToAddEdit()
                );
                return true;
            }
            if (itemId == R.id.action_scroll_finished) {
                binding.scrollContent.post(() -> binding.scrollContent.smoothScrollTo(
                        0,
                        binding.finishedSectionContainer.getTop()
                ));
                return true;
            }
            return false;
        });
        popupMenu.show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
