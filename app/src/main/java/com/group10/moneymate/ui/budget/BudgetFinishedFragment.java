package com.group10.moneymate.ui.budget;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentBudgetFinishedBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BudgetFinishedFragment extends Fragment {

    private static final String RESULT_SELECTED_WALLET_ID = "result_selected_wallet_id";
    private static final String RESULT_SELECTED_WALLET_LABEL = "result_selected_wallet_label";

    private FragmentBudgetFinishedBinding binding;
    private BudgetViewModel viewModel;
    private BudgetAdapter finishedAdapter;
    private List<WalletEntity> wallets = Collections.emptyList();
    @Nullable
    private String selectedWalletId;
    @NonNull
    private String selectedWalletLabel = "";
    private OnBackPressedCallback backPressedCallback;
    private boolean hasWallets;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable pendingWalletRedirect;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBudgetFinishedBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupArgs();
        setupViewModel();
        setupInsets();
        setupRecyclerView();
        setupActions();
        observeData();
    }

    private void setupArgs() {
        BudgetFinishedFragmentArgs args = BudgetFinishedFragmentArgs.fromBundle(
                getArguments() != null ? getArguments() : new Bundle()
        );
        selectedWalletId = args.getSelectedWalletId();
        selectedWalletLabel = args.getSelectedWalletLabel() != null
                ? args.getSelectedWalletLabel()
                : getString(R.string.budget_wallet_scope_total);
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
        viewModel.setSelectedWalletFilter(selectedWalletId);
    }

    private void setupInsets() {
        final int topPadding = binding.appBarContent.getPaddingTop();
        final int bottomPadding = binding.recyclerContainer.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appBarContent.setPadding(
                    binding.appBarContent.getPaddingLeft(),
                    topPadding + systemBars.top,
                    binding.appBarContent.getPaddingRight(),
                    binding.appBarContent.getPaddingBottom()
            );
            binding.recyclerContainer.setPadding(
                    binding.recyclerContainer.getPaddingLeft(),
                    binding.recyclerContainer.getPaddingTop(),
                    binding.recyclerContainer.getPaddingRight(),
                    bottomPadding + systemBars.bottom
            );
            return insets;
        });
    }

    private void setupRecyclerView() {
        finishedAdapter = new BudgetAdapter();
        finishedAdapter.setOnBudgetClickListener(item -> {
            BudgetFinishedFragmentDirections.ActionBudgetFinishedToDetail action =
                    BudgetFinishedFragmentDirections.actionBudgetFinishedToDetail();
            action.setBudgetId(item.getBudgetEntity().getId());
            action.setBudgetTab(BudgetViewModel.BudgetTab.THIS_MONTH.name());
            Navigation.findNavController(requireView()).navigate(action);
        });
        binding.rvFinishedBudgets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvFinishedBudgets.setAdapter(finishedAdapter);
    }

    private void setupActions() {
        binding.btnBack.setOnClickListener(v -> navigateBackWithFilter());
        binding.btnFinishedEmptyAction.setOnClickListener(v -> {
            if (hasWallets) {
                Navigation.findNavController(v).navigate(R.id.addEditBudgetFragment);
            } else {
                Navigation.findNavController(v).navigate(R.id.addEditWalletFragment);
            }
        });
        binding.btnWalletFilter.setOnClickListener(v -> {
            BudgetFinishedFragmentDirections.ActionBudgetFinishedToWalletPicker action =
                    BudgetFinishedFragmentDirections.actionBudgetFinishedToWalletPicker();
            action.setSelectedWalletId(selectedWalletId);
            Navigation.findNavController(v).navigate(action);
        });

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

        backPressedCallback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateBackWithFilter();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(
                getViewLifecycleOwner(),
                backPressedCallback
        );
    }

    private void observeData() {
        viewModel.getWallets().observe(getViewLifecycleOwner(), walletEntities -> {
            wallets = walletEntities != null ? walletEntities : new ArrayList<>();
            hasWallets = !wallets.isEmpty();
            updateWalletFilterLabel();
            renderEmptyState(finishedAdapter.getCurrentList().isEmpty());
        });
        viewModel.getFinishedBudgets().observe(getViewLifecycleOwner(), budgets -> {
            List<BudgetUIModel> items = budgets != null ? budgets : Collections.emptyList();
            finishedAdapter.submitList(items);
            renderEmptyState(items.isEmpty());
        });
        updateWalletFilterLabel();
    }

    private void renderEmptyState(boolean isEmpty) {
        binding.rvFinishedBudgets.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.layoutEmptyFinished.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (!isEmpty) {
            cancelWalletRedirect();
            return;
        }
        if (hasWallets) {
            binding.tvEmptyFinished.setText(R.string.budget_finished_empty);
            binding.btnFinishedEmptyAction.setText(R.string.budget_empty_inline_action);
            cancelWalletRedirect();
            return;
        }
        binding.tvEmptyFinished.setText(R.string.budget_finished_empty_no_wallet);
        binding.btnFinishedEmptyAction.setText(R.string.budget_empty_no_wallet_action);
        scheduleWalletRedirect();
    }

    private void updateWalletFilterLabel() {
        if (binding == null) {
            return;
        }
        if (selectedWalletId == null) {
            binding.tvWalletFilterLabel.setText(compactWalletLabel(
                    getString(R.string.budget_wallet_scope_total)
            ));
            return;
        }
        for (WalletEntity wallet : wallets) {
            if (selectedWalletId.equals(wallet.getId())) {
                binding.tvWalletFilterLabel.setText(compactWalletLabel(wallet.getName()));
                return;
            }
        }
        binding.tvWalletFilterLabel.setText(compactWalletLabel(selectedWalletLabel));
    }

    private void navigateBackWithFilter() {
        if (binding == null) {
            return;
        }
        NavController navController = Navigation.findNavController(binding.getRoot());
        NavBackStackEntry previous = navController.getPreviousBackStackEntry();
        if (previous != null) {
            previous.getSavedStateHandle().set(RESULT_SELECTED_WALLET_ID, selectedWalletId);
            previous.getSavedStateHandle().set(RESULT_SELECTED_WALLET_LABEL, selectedWalletLabel);
        }
        navController.navigateUp();
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

    @Override
    public void onDestroyView() {
        cancelWalletRedirect();
        super.onDestroyView();
        binding = null;
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
}
