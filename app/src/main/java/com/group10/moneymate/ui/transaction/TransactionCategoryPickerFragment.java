package com.group10.moneymate.ui.transaction;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentTransactionCategoryPickerBinding;
import com.group10.moneymate.models.DebtType;
import com.group10.moneymate.utils.Constants;

public class TransactionCategoryPickerFragment extends Fragment {

    public static final String RESULT_CATEGORY_ID = "selectedCategoryId";
    public static final String RESULT_CATEGORY_TYPE = "transactionType";
    public static final String RESULT_DEBT_TYPE = "selectedDebtType";
    public static final String RESULT_ALL_CATEGORIES = "selectedAllCategories";

    private FragmentTransactionCategoryPickerBinding binding;
    private TransactionCategoryPickerViewModel viewModel;
    private TransactionCategoryPickerAdapter adapter;

    private String selectedCategoryId;
    private String selectedType = Constants.TYPE_EXPENSE;
    private DebtType selectedDebtType;
    private boolean lockToExpense;
    private boolean showAllCategories;
    private boolean hideDebtTab;
    private boolean onlyParentCategoriesWithChildren;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTransactionCategoryPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionCategoryPickerViewModel.class);

        TransactionCategoryPickerFragmentArgs args =
                TransactionCategoryPickerFragmentArgs.fromBundle(getArguments() == null ? new Bundle() : getArguments());
        selectedCategoryId = args.getSelectedCategoryId();
        selectedType = resolveSelectedType(args.getTransactionType());
        lockToExpense = args.getLockToExpense();
        showAllCategories = args.getShowAllCategories();
        hideDebtTab = args.getHideDebtTab();
        onlyParentCategoriesWithChildren = args.getOnlyParentCategoriesWithChildren();
        if (hideDebtTab && TransactionCategoryPickerViewModel.TYPE_DEBT.equals(selectedType)) {
            selectedType = Constants.TYPE_EXPENSE;
        }

        setupToolbar();
        setupTabs();
        setupList();
        setupAddNewRow();
        setupAllCategoriesRow();

        applyTabSelection();
        viewModel.setSelectedType(selectedType);
    }

    private void setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
    }

    private void setupTabs() {
        if (lockToExpense) {
            binding.tabCategoryType.setVisibility(View.GONE);
            selectedType = Constants.TYPE_EXPENSE;
            return;
        }
        binding.tabCategoryType.addTab(binding.tabCategoryType.newTab()
                .setText(R.string.tab_expense));
        binding.tabCategoryType.addTab(binding.tabCategoryType.newTab()
                .setText(R.string.tab_income));
        if (!hideDebtTab) {
            binding.tabCategoryType.addTab(binding.tabCategoryType.newTab()
                    .setText(R.string.tab_debt));
        }

        binding.tabCategoryType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab == null) {
                    return;
                }
                int position = tab.getPosition();
                if (position == 1) {
                    selectedType = Constants.TYPE_INCOME;
                } else if (!hideDebtTab && position == 2) {
                    selectedType = TransactionCategoryPickerViewModel.TYPE_DEBT;
                } else {
                    selectedType = Constants.TYPE_EXPENSE;
                }
                selectedCategoryId = null;
                selectedDebtType = null;
                adapter.setSelectedCategoryId(null);
                adapter.setSelectedDebtType(null);
                viewModel.setSelectedType(selectedType);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                // No-op
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
                // No-op
            }
        });
    }

    private void setupAllCategoriesRow() {
        if (!showAllCategories) {
            binding.cardAllCategories.setVisibility(View.GONE);
            return;
        }
        binding.cardAllCategories.setVisibility(View.VISIBLE);
        binding.cardAllCategories.setOnClickListener(v -> {
            NavController navController = Navigation.findNavController(requireView());
            NavBackStackEntry previous = navController.getPreviousBackStackEntry();
            if (previous != null) {
                previous.getSavedStateHandle().set(RESULT_ALL_CATEGORIES, true);
                previous.getSavedStateHandle().set(RESULT_CATEGORY_ID, null);
                previous.getSavedStateHandle().set(RESULT_CATEGORY_TYPE, Constants.TYPE_EXPENSE);
                previous.getSavedStateHandle().set(RESULT_DEBT_TYPE, null);
            }
            navController.navigateUp();
        });
    }

    private void setupList() {
        adapter = new TransactionCategoryPickerAdapter();
        adapter.setOnItemClickListener(this::handleItemSelection);
        adapter.setSelectedCategoryId(selectedCategoryId);
        adapter.setSelectedDebtType(selectedDebtType);

        binding.rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategories.setAdapter(adapter);

        viewModel.getItems().observe(getViewLifecycleOwner(), items -> {
            java.util.List<TransactionCategoryPickerItem> displayItems = items;
            if (onlyParentCategoriesWithChildren && items != null) {
                displayItems = new java.util.ArrayList<>();
                for (TransactionCategoryPickerItem item : items) {
                    if (item == null || item.getGroup() == null) {
                        continue;
                    }
                    if (!item.getGroup().getChildren().isEmpty()) {
                        displayItems.add(item);
                    }
                }
            }
            adapter.submitList(displayItems);
            boolean isEmpty = displayItems == null || displayItems.isEmpty();
            binding.tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.rvCategories.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });
    }

    private void setupAddNewRow() {
        if (lockToExpense && !showAllCategories) {
            binding.cardAddNewCategory.setVisibility(View.GONE);
            return;
        }
        binding.cardAddNewCategory.setOnClickListener(v -> {
            TransactionCategoryPickerFragmentDirections.ActionTransactionCategoryPickerFragmentToAddEditCategoryFragment
                    action = TransactionCategoryPickerFragmentDirections
                    .actionTransactionCategoryPickerFragmentToAddEditCategoryFragment();
            action.setCategoryId(null);
            action.setInitialType(selectedType);
            Navigation.findNavController(v).navigate(action);
        });
    }

    private void applyTabSelection() {
        if (lockToExpense) {
            return;
        }
        int tabIndex = resolveTabIndex();
        TabLayout.Tab tab = binding.tabCategoryType.getTabAt(tabIndex);
        if (tab != null) {
            tab.select();
        }
    }

    private void handleItemSelection(@NonNull TransactionCategoryPickerItem item) {
        NavController navController = Navigation.findNavController(requireView());
        NavBackStackEntry previous = navController.getPreviousBackStackEntry();
        if (previous == null) {
            navController.navigateUp();
            return;
        }

        if (item.isDebt()) {
            handleDebtSelection(previous, item.getDebtType());
        } else if (item.getGroup() != null) {
            handleCategorySelection(previous, item.getGroup().getRoot().getId());
        }

        navController.navigateUp();
    }

    @NonNull
    private String resolveSelectedType(@Nullable String transactionType) {
        return transactionType == null ? Constants.TYPE_EXPENSE : transactionType;
    }

    private int resolveTabIndex() {
        if (Constants.TYPE_INCOME.equals(selectedType)) {
            return 1;
        }
        if (TransactionCategoryPickerViewModel.TYPE_DEBT.equals(selectedType) && !hideDebtTab) {
            return 2;
        }
        return 0;
    }

    private void handleDebtSelection(@NonNull NavBackStackEntry previous,
                                     @Nullable DebtType debtType) {
        selectedDebtType = debtType;
        adapter.setSelectedDebtType(selectedDebtType);
        previous.getSavedStateHandle().set(
                RESULT_DEBT_TYPE,
                selectedDebtType == null ? null : selectedDebtType.name()
        );
        previous.getSavedStateHandle().set(RESULT_CATEGORY_ID, null);
        previous.getSavedStateHandle().set(RESULT_ALL_CATEGORIES, false);
        previous.getSavedStateHandle().set(RESULT_CATEGORY_TYPE, resolveDebtTransactionType(debtType));
    }

    private void handleCategorySelection(@NonNull NavBackStackEntry previous,
                                         @NonNull String categoryId) {
        selectedCategoryId = categoryId;
        adapter.setSelectedCategoryId(selectedCategoryId);
        previous.getSavedStateHandle().set(RESULT_CATEGORY_ID, selectedCategoryId);
        previous.getSavedStateHandle().set(RESULT_CATEGORY_TYPE, selectedType);
        previous.getSavedStateHandle().set(RESULT_DEBT_TYPE, null);
        previous.getSavedStateHandle().set(RESULT_ALL_CATEGORIES, false);
    }

    @NonNull
    private String resolveDebtTransactionType(@Nullable DebtType debtType) {
        return debtType == DebtType.BORROW ? Constants.TYPE_INCOME : Constants.TYPE_EXPENSE;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
