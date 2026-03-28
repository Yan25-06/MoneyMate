package com.group10.moneymate.ui.budget;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentBudgetListBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BudgetListFragment extends Fragment {

    private static final String RESULT_SELECTED_WALLET_ID = "result_selected_wallet_id";
    private static final String RESULT_SELECTED_WALLET_LABEL = "result_selected_wallet_label";

    private FragmentBudgetListBinding binding;
    private BudgetViewModel viewModel;
    private BudgetAdapter activeAdapter;

    private List<BudgetUIModel> activeBudgets = Collections.emptyList();
    private List<WalletEntity> wallets = Collections.emptyList();
    @Nullable
    private String selectedWalletId;
    @NonNull
    private String selectedWalletLabel = "";
    @Nullable
    private BudgetViewModel.BudgetSummaryUIModel currentSummary;
    private boolean hasAnyBudgets;
    private boolean hasWallets;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pendingWalletRedirect;

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
        setupTabs();
        observeViewModel();
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
                getString(R.string.budget_all_categories),
                getString(R.string.budget_other_categories),
                getString(R.string.budget_wallet_scope_total),
                getString(R.string.budget_unknown_wallet),
                getString(R.string.budget_unknown_category)
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
        activeAdapter.setOnBudgetClickListener(item -> {
            BudgetListFragmentDirections.ActionBudgetListToDetail action =
                    BudgetListFragmentDirections.actionBudgetListToDetail();
            action.setBudgetId(item.getBudgetEntity().getId());
            action.setBudgetTab(viewModel.getSelectedTab().name());
            Navigation.findNavController(requireView()).navigate(action);
        });

        binding.rvActiveBudgets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvActiveBudgets.setAdapter(activeAdapter);
        binding.rvActiveBudgets.setNestedScrollingEnabled(false);
    }

    private void setupActions() {
        View.OnClickListener createBudgetListener = v -> Navigation.findNavController(v).navigate(
                BudgetListFragmentDirections.actionBudgetListToAddEdit()
        );
        View.OnClickListener createWalletListener = v -> Navigation.findNavController(v)
                .navigate(R.id.addEditWalletFragment);

        binding.btnCreateBudget.setOnClickListener(createBudgetListener);
        binding.btnEmptyCreateBudget.setOnClickListener(v -> {
            if (hasWallets) {
                createBudgetListener.onClick(v);
            } else {
                createWalletListener.onClick(v);
            }
        });
        binding.btnContentEmptyAction.setOnClickListener(v -> {
            if (hasWallets) {
                createBudgetListener.onClick(v);
            } else {
                createWalletListener.onClick(v);
            }
        });
        binding.btnWalletFilter.setOnClickListener(v -> {
            BudgetListFragmentDirections.ActionBudgetListToWalletPicker action =
                    BudgetListFragmentDirections.actionBudgetListToWalletPicker();
            action.setSelectedWalletId(selectedWalletId);
            Navigation.findNavController(v).navigate(action);
        });
        binding.toolbarBudget.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_finished_budgets) {
                openFinishedBudgets(binding.toolbarBudget);
                return true;
            }
            return false;
        });
        binding.rowAllCategories.setOnClickListener(v -> openAllCategoriesDetail(false));
        binding.btnBudgetGapWarning.setOnClickListener(v -> openAllCategoriesDetail(true));

        NavBackStackEntry currentEntry = Navigation.findNavController(requireView()).getCurrentBackStackEntry();
        if (currentEntry != null) {
            SavedStateHandle savedStateHandle = currentEntry.getSavedStateHandle();
            savedStateHandle.getLiveData(RESULT_SELECTED_WALLET_ID, (String) null)
                    .observe(getViewLifecycleOwner(), walletId -> {
                        selectedWalletId = walletId;
                        viewModel.setSelectedWalletFilter(walletId);
                        updateWalletFilterLabel();
                    });
            savedStateHandle.getLiveData(RESULT_SELECTED_WALLET_LABEL, getString(R.string.budget_wallet_scope_total))
                    .observe(getViewLifecycleOwner(), label -> {
                        selectedWalletLabel = label != null ? label : getString(R.string.budget_wallet_scope_total);
                        updateWalletFilterLabel();
                    });
        }
    }

    private void setupTabs() {
        binding.tabBudgetFilter.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab == null) {
                    return;
                }
                int position = tab.getPosition();
                if (position == 1) {
                    viewModel.setSelectedTab(BudgetViewModel.BudgetTab.FUTURE);
                } else if (position == 2) {
                    viewModel.setSelectedTab(BudgetViewModel.BudgetTab.CUSTOM);
                } else {
                    viewModel.setSelectedTab(BudgetViewModel.BudgetTab.THIS_MONTH);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
        selectTab(viewModel.getSelectedTab());
    }

    private void observeViewModel() {
        viewModel.getWallets().observe(getViewLifecycleOwner(), walletEntities -> {
            wallets = walletEntities != null ? walletEntities : new ArrayList<>();
            hasWallets = !wallets.isEmpty();
            updateWalletFilterLabel();
            renderSections();
        });
        viewModel.getSelectedTabLiveData().observe(getViewLifecycleOwner(), this::selectTab);
        viewModel.getHasAnyBudgets().observe(getViewLifecycleOwner(), hasBudgets -> {
            hasAnyBudgets = Boolean.TRUE.equals(hasBudgets);
            renderSections();
        });
        viewModel.getActiveBudgets().observe(getViewLifecycleOwner(), budgets -> {
            activeBudgets = budgets != null ? budgets : Collections.emptyList();
            activeAdapter.submitList(activeBudgets);
            renderSections();
        });
        viewModel.getSummary().observe(getViewLifecycleOwner(), this::renderSummary);

        selectedWalletLabel = getString(R.string.budget_wallet_scope_total);
        updateWalletFilterLabel();
    }

    private void selectTab(@Nullable BudgetViewModel.BudgetTab budgetTab) {
        if (binding == null) {
            return;
        }
        int tabIndex = 0;
        if (budgetTab == BudgetViewModel.BudgetTab.FUTURE) {
            tabIndex = 1;
        } else if (budgetTab == BudgetViewModel.BudgetTab.CUSTOM) {
            tabIndex = 2;
        }
        TabLayout.Tab targetTab = binding.tabBudgetFilter.getTabAt(tabIndex);
        if (targetTab != null && !targetTab.isSelected()) {
            targetTab.select();
        }
    }

    private void renderSummary(@Nullable BudgetViewModel.BudgetSummaryUIModel summary) {
        currentSummary = summary;
        boolean hasVisibleSummary = summary != null && summary.hasActiveBudgets();
        binding.cardSummary.setVisibility(hasVisibleSummary ? View.VISIBLE : View.GONE);
        binding.btnBudgetGapWarning.setVisibility(
                hasVisibleSummary && summary != null && summary.shouldShowGapWarning()
                        ? View.VISIBLE
                        : View.GONE
        );

        if (!hasVisibleSummary || summary == null) {
            renderSections();
            return;
        }

        binding.tvSummaryAvailableAmount.setText(BudgetUiUtils.formatCurrency(summary.getRemainingAmount()));
        binding.tvSummaryTotalBudget.setText(BudgetUiUtils.formatCompactCurrency(summary.getTotalBudget()));
        binding.tvSummarySpent.setText(BudgetUiUtils.formatCompactCurrency(summary.getTotalSpent()));
        binding.tvSummaryEnd.setText(getResources().getQuantityString(
                R.plurals.budget_days_short,
                Math.max(summary.getDaysLeft(), 1),
                Math.max(summary.getDaysLeft(), 1)
        ));
        binding.btnBudgetGapWarning.setText(getString(
                R.string.budget_gap_warning_action,
                BudgetUiUtils.formatCurrency(summary.getShortfallAmount())
        ));

        int color = resolveSummaryColor(summary);
        binding.budgetArcProgress.setProgressFraction(Math.min(1f, summary.getPercent() / 100f));
        binding.budgetArcProgress.setProgressColor(color);
        binding.tvSummaryAvailableAmount.setTextColor(color);

        renderSections();
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
        boolean hasVisibleSummary = currentSummary != null && currentSummary.hasActiveBudgets();
        boolean hasSpecificBudgets = !activeBudgets.isEmpty();
        boolean hasVisibleContent = hasVisibleSummary || hasSpecificBudgets;
        boolean showFullEmpty = !hasAnyBudgets;
        boolean showInlineEmpty = hasAnyBudgets && !hasVisibleContent;

        if (hasWallets) {
            cancelWalletRedirect();
        }

        binding.scrollContent.setVisibility(showFullEmpty ? View.GONE : View.VISIBLE);
        binding.emptyState.setVisibility(showFullEmpty ? View.VISIBLE : View.GONE);

        binding.rvActiveBudgets.setVisibility(hasSpecificBudgets ? View.VISIBLE : View.GONE);
        binding.layoutContentEmpty.setVisibility(showInlineEmpty ? View.VISIBLE : View.GONE);

        if (showFullEmpty) {
            binding.cardSummary.setVisibility(View.GONE);
            if (hasWallets) {
                binding.btnEmptyCreateBudget.setText(R.string.budget_create_empty_cta);
                binding.btnEmptyCreateBudget.setVisibility(View.VISIBLE);
                binding.emptyState.setVisibility(View.VISIBLE);
                updateFullEmptyState(
                        getString(R.string.budget_empty_title),
                        getString(R.string.budget_empty_description),
                        R.string.budget_create_empty_cta
                );
            } else {
                binding.btnEmptyCreateBudget.setVisibility(View.VISIBLE);
                binding.btnEmptyCreateBudget.setText(R.string.budget_empty_no_wallet_action);
                updateFullEmptyState(
                        getString(R.string.budget_empty_no_wallet_title),
                        getString(R.string.budget_empty_no_wallet_description),
                        R.string.budget_empty_no_wallet_action
                );
                scheduleWalletRedirect();
            }
            return;
        }

        cancelWalletRedirect();
        if (showInlineEmpty) {
            binding.tvContentEmpty.setText(hasWallets
                    ? getTabEmptyMessage()
                    : getString(R.string.budget_empty_no_wallet_description));
            binding.btnContentEmptyAction.setText(hasWallets
                    ? R.string.budget_empty_inline_action
                    : R.string.budget_inline_no_wallet_action);
            if (!hasWallets) {
                scheduleWalletRedirect();
            }
        }
    }

    private void updateFullEmptyState(@NonNull String title,
                                      @NonNull String description,
                                      int actionRes) {
        binding.btnEmptyCreateBudget.setText(actionRes);
        binding.tvEmptyTitle.setText(title);
        binding.tvEmptyDescription.setText(description);
    }

    @NonNull
    private String getTabEmptyMessage() {
        switch (viewModel.getSelectedTab()) {
            case FUTURE:
                return getString(R.string.budget_tab_empty_future);
            case CUSTOM:
                return getString(R.string.budget_tab_empty_custom);
            case THIS_MONTH:
            default:
                return getString(R.string.budget_tab_empty_this_month);
        }
    }

    private void scheduleWalletRedirect() {
        if (pendingWalletRedirect != null || !isAdded() || hasWallets) {
            return;
        }
        pendingWalletRedirect = () -> {
            pendingWalletRedirect = null;
            if (!isAdded() || hasWallets || binding == null) {
                return;
            }
            Navigation.findNavController(binding.getRoot()).navigate(R.id.addEditWalletFragment);
        };
        handler.postDelayed(pendingWalletRedirect, 3000L);
    }

    private void cancelWalletRedirect() {
        if (pendingWalletRedirect == null) {
            return;
        }
        handler.removeCallbacks(pendingWalletRedirect);
        pendingWalletRedirect = null;
    }

    private void openFinishedBudgets(@NonNull View anchor) {
        BudgetListFragmentDirections.ActionBudgetListToFinished action =
                BudgetListFragmentDirections.actionBudgetListToFinished();
        action.setSelectedWalletId(selectedWalletId);
        action.setSelectedWalletLabel(selectedWalletLabel);
        Navigation.findNavController(anchor).navigate(action);
    }

    private void openAllCategoriesDetail(boolean preferEdit) {
        if (currentSummary != null && currentSummary.hasAllCategoriesBudget()) {
            BudgetUIModel allCategoriesBudget = currentSummary.getAllCategoriesBudget();
            if (allCategoriesBudget == null) {
                return;
            }
            if (preferEdit) {
                BudgetListFragmentDirections.ActionBudgetListToAddEdit action =
                        BudgetListFragmentDirections.actionBudgetListToAddEdit();
                action.setBudgetId(allCategoriesBudget.getBudgetEntity().getId());
                Navigation.findNavController(requireView()).navigate(action);
                return;
            }
            BudgetListFragmentDirections.ActionBudgetListToDetail action =
                    BudgetListFragmentDirections.actionBudgetListToDetail();
            action.setBudgetId(allCategoriesBudget.getBudgetEntity().getId());
            action.setBudgetTab(viewModel.getSelectedTab().name());
            Navigation.findNavController(requireView()).navigate(action);
            return;
        }

        BudgetListFragmentDirections.ActionBudgetListToDetail action =
                BudgetListFragmentDirections.actionBudgetListToDetail();
        action.setIsAggregate(true);
        action.setWalletFilterId(selectedWalletId);
        action.setWalletFilterLabel(selectedWalletLabel);
        action.setBudgetTab(viewModel.getSelectedTab().name());
        Navigation.findNavController(requireView()).navigate(action);
    }

    private void updateWalletFilterLabel() {
        String rawLabel = selectedWalletId == null
                ? getString(R.string.budget_wallet_scope_total)
                : resolveWalletName(selectedWalletId);
        selectedWalletLabel = rawLabel;
        if (binding != null) {
            binding.tvWalletFilterLabel.setText(compactWalletLabel(rawLabel));
        }
    }

    @NonNull
    private String resolveWalletName(@NonNull String walletId) {
        for (WalletEntity wallet : wallets) {
            if (walletId.equals(wallet.getId())) {
                return wallet.getName();
            }
        }
        return getString(R.string.budget_unknown_wallet);
    }

    @NonNull
    private String compactWalletLabel(@NonNull String label) {
        String normalized = label.trim().replaceAll("\\s+", " ");
        if (normalized.isEmpty() || normalized.length() <= 16) {
            return normalized;
        }
        String[] tokens = normalized.split(" ");
        int startIndex = 0;
        if (tokens.length > 1 && isGenericWalletWord(tokens[0])) {
            startIndex = 1;
        }
        String primary = capitalizeFirst(tokens[startIndex]);
        if (startIndex + 1 < tokens.length) {
            String combined = primary + " " + tokens[startIndex + 1];
            if (combined.length() <= 14) {
                return combined + "…";
            }
        }
        return primary + "…";
    }

    private boolean isGenericWalletWord(@NonNull String token) {
        return "ví".equalsIgnoreCase(token) || "wallet".equalsIgnoreCase(token);
    }

    @NonNull
    private String capitalizeFirst(@NonNull String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase() + value.substring(1);
    }

    @Override
    public void onDestroyView() {
        cancelWalletRedirect();
        super.onDestroyView();
        binding = null;
    }
}
