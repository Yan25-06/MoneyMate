package com.group10.moneymate.ui.budget;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.Navigation;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.repository.BudgetRepository;
import com.group10.moneymate.databinding.FragmentAddEditBudgetBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.utils.LoadingHelper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class AddEditBudgetFragment extends Fragment {

    private FragmentAddEditBudgetBinding binding;
    private AddEditBudgetViewModel viewModel;

    private final List<CategoryEntity> expenseCategories = new ArrayList<>();
    private final List<WalletOptionItem> walletOptions = new ArrayList<>();
    private final List<WalletWithBalance> allWallets = new ArrayList<>();
    private final List<WalletWithBalance> activeWallets = new ArrayList<>();
    private CategoryEntity selectedCategory;
    private boolean selectedAllCategories;
    private BudgetEntity editingBudget;
    private String pendingCategoryId;
    private String pendingWalletId;
    private String selectedWalletId;
    private long selectedStartDate;
    private long selectedEndDate;
    private boolean isFormattingAmount;
    private boolean shouldApplyPendingWalletSelection = true;
    private boolean isEditMode;
    private double totalWalletBalance;
    private String userId;
    private AppContainer appContainer;
    private boolean isSaving;
    private final LoadingHelper loadingHelper = new LoadingHelper();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAddEditBudgetBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupViewModel();
        setupDefaultPeriod();
        setupInsets();
        setupUi();
        observePickerResults();
        observeData();
    }

    private void setupViewModel() {
        MoneyMateApplication app = (MoneyMateApplication) requireActivity().getApplication();
        appContainer = app.getAppContainer();
        userId = appContainer.authRepository.getCurrentUserId();
        appContainer.categoryRepository.ensureVirtualOtherCategoryExists();
        AddEditBudgetViewModel.Factory factory = new AddEditBudgetViewModel.Factory(
                appContainer.budgetRepository,
                appContainer.categoryRepository,
                appContainer.walletRepository,
                userId
        );
        viewModel = new ViewModelProvider(this, factory).get(AddEditBudgetViewModel.class);
    }

    private void setupDefaultPeriod() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        selectedStartDate = calendar.getTimeInMillis();

        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        selectedEndDate = calendar.getTimeInMillis();
    }

    private void setupInsets() {
        final int topPadding = binding.topBar.getPaddingTop();
        final int bottomPadding = binding.bottomBar.getPaddingBottom();
        final int scrollPaddingBottom = binding.scrollContent.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.topBar.setPadding(
                    binding.topBar.getPaddingLeft(),
                    topPadding + systemBars.top,
                    binding.topBar.getPaddingRight(),
                    binding.topBar.getPaddingBottom()
            );
            binding.bottomBar.setPadding(
                    binding.bottomBar.getPaddingLeft(),
                    binding.bottomBar.getPaddingTop(),
                    binding.bottomBar.getPaddingRight(),
                    bottomPadding + systemBars.bottom
            );
            binding.scrollContent.setPadding(
                    binding.scrollContent.getPaddingLeft(),
                    binding.scrollContent.getPaddingTop(),
                    binding.scrollContent.getPaddingRight(),
                    scrollPaddingBottom
            );
            return insets;
        });
    }

    private void setupUi() {
        AddEditBudgetFragmentArgs args = AddEditBudgetFragmentArgs.fromBundle(getArguments() != null
                ? getArguments()
                : new Bundle());
        isEditMode = args.getBudgetId() != null;
        binding.tvScreenTitle.setText(isEditMode
                ? R.string.edit_budget_sheet_title
                : R.string.add_budget_sheet_title);
        updatePeriodLabel();
        binding.dropdownWallet.setKeyListener(null);

        binding.tvCancel.setOnClickListener(v -> {
            if (isSaving) {
                return;
            }
            Navigation.findNavController(v).navigateUp();
        });
        binding.rowCategory.setOnClickListener(v -> showCategoryPicker());
        binding.rowPeriod.setOnClickListener(v -> showDateRangePicker());
        binding.btnSave.setOnClickListener(v -> saveBudget());
        binding.dropdownWallet.setOnItemClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= walletOptions.size()) {
                return;
            }
            WalletOptionItem selectedItem = walletOptions.get(position);
            selectedWalletId = selectedItem.walletId;
            binding.dropdownWallet.setText(selectedItem.label, false);
            updateSaveState();
        });

        binding.etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No-op: amount formatting is applied once the final text is available.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // No-op: we only normalize the formatted amount after the edit is complete.
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormattingAmount) {
                    return;
                }
                formatAmountInput(s);
                updateSaveState();
            }
        });
    }

    private void observeData() {
        viewModel.getExpenseCategories().observe(getViewLifecycleOwner(), categories -> {
            expenseCategories.clear();
            if (categories != null) {
                expenseCategories.addAll(categories);
            }
            applyPendingCategorySelection();
            updateSaveState();
        });

        viewModel.getAllWallets().observe(getViewLifecycleOwner(), wallets -> {
            allWallets.clear();
            if (wallets != null) {
                allWallets.addAll(wallets);
            }
            refreshWalletDropdown();
            updateSaveState();
        });

        viewModel.getActiveWallets().observe(getViewLifecycleOwner(), wallets -> {
            activeWallets.clear();
            if (wallets != null) {
                activeWallets.addAll(wallets);
            }
            refreshWalletDropdown();
            updateSaveState();
        });

        appContainer.walletRepository.getTotalBalance(userId).observe(getViewLifecycleOwner(), balance -> {
            totalWalletBalance = balance != null ? balance : 0d;
            updateSaveState();
        });

        AddEditBudgetFragmentArgs args = AddEditBudgetFragmentArgs.fromBundle(getArguments() != null
                ? getArguments()
                : new Bundle());
        if (args.getBudgetId() != null) {
            viewModel.getBudgetById(args.getBudgetId()).observe(getViewLifecycleOwner(), budget -> {
                if (budget == null) {
                    return;
                }
                editingBudget = budget;
                pendingCategoryId = budget.getCategoryId();
                selectedAllCategories = budget.getCategoryId() == null;
                pendingWalletId = budget.getWalletId();
                shouldApplyPendingWalletSelection = true;
                selectedStartDate = budget.getStartDate();
                selectedEndDate = budget.getEndDate();
                binding.etAmount.setText(BudgetUiUtils.formatInputAmount((long) budget.getAmount()));
                updatePeriodLabel();
                applyPendingCategorySelection();
                applyPendingWalletSelection();
                updateSaveState();
            });
        }
    }

    private void refreshWalletDropdown() {
        List<WalletWithBalance> displayedWallets = new ArrayList<>(activeWallets);
        if (isEditMode && pendingWalletId != null) {
            boolean containsPending = false;
            for (WalletWithBalance wallet : displayedWallets) {
                if (pendingWalletId.equals(wallet.getWallet().getId())) {
                    containsPending = true;
                    break;
                }
            }
            if (!containsPending) {
                for (WalletWithBalance wallet : allWallets) {
                    if (pendingWalletId.equals(wallet.getWallet().getId())) {
                        displayedWallets.add(wallet);
                        break;
                    }
                }
            }
        }

        walletOptions.clear();
        walletOptions.add(new WalletOptionItem(
                null,
                getString(R.string.budget_wallet_scope_total),
                totalWalletBalance
        ));
        for (WalletWithBalance wallet : displayedWallets) {
            walletOptions.add(new WalletOptionItem(
                    wallet.getWallet().getId(),
                    wallet.getWallet().getName(),
                    wallet.getCurrentBalance()
            ));
        }

        List<String> labels = new ArrayList<>();
        for (WalletOptionItem item : walletOptions) {
            labels.add(item.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_moneymate_dropdown_option,
                labels
        );
        binding.dropdownWallet.setAdapter(adapter);
        applyPendingWalletSelection();
    }

    private void applyPendingWalletSelection() {
        if (!shouldApplyPendingWalletSelection || walletOptions.isEmpty()) {
            return;
        }
        bindSelectedWallet(pendingWalletId);
        shouldApplyPendingWalletSelection = false;
    }

    private void bindSelectedWallet(@Nullable String walletId) {
        for (WalletOptionItem item : walletOptions) {
            boolean matches = item.walletId == null
                    ? walletId == null
                    : item.walletId.equals(walletId);
            if (matches) {
                selectedWalletId = item.walletId;
                binding.dropdownWallet.setText(item.label, false);
                return;
            }
        }
        WalletOptionItem allWallets = walletOptions.get(0);
        selectedWalletId = allWallets.walletId;
        binding.dropdownWallet.setText(allWallets.label, false);
    }

    private void applyPendingCategorySelection() {
        if (pendingCategoryId == null) {
            if (selectedAllCategories) {
                selectedCategory = null;
                bindSelectedCategory(null);
            }
            return;
        }
        if (Constants.isOtherCategoryId(pendingCategoryId)) {
            selectedAllCategories = false;
            selectedCategory = createOtherCategoriesOption();
            bindSelectedCategory(selectedCategory);
            pendingCategoryId = null;
            return;
        }
        for (CategoryEntity category : expenseCategories) {
            if (pendingCategoryId.equals(category.getId())) {
                selectedAllCategories = false;
                selectedCategory = category;
                bindSelectedCategory(category);
                pendingCategoryId = null;
                return;
            }
        }
    }

    private void bindSelectedCategory(@Nullable CategoryEntity category) {
        String categoryName = category != null
                ? category.getName()
                : getString(R.string.budget_all_categories);
        String iconName = category != null ? category.getIconName() : "";
        binding.tvCategoryName.setText(categoryName);
        binding.tvCategoryName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
        binding.ivCategoryIcon.setImageResource(BudgetUiUtils.resolveCategoryIcon(
                requireContext(),
                iconName,
                categoryName
        ));
        binding.ivCategoryIcon.setImageTintList(null);
        binding.categoryIconContainer.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), android.R.color.white)
                )
        );
    }

    private void observePickerResults() {
        NavBackStackEntry backStackEntry = Navigation.findNavController(requireView()).getCurrentBackStackEntry();
        if (backStackEntry == null) {
            return;
        }
        backStackEntry.getSavedStateHandle()
                .getLiveData(com.group10.moneymate.ui.transaction.TransactionCategoryPickerFragment.RESULT_ALL_CATEGORIES)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    if ((Boolean) value) {
                        selectedAllCategories = true;
                        selectedCategory = null;
                        pendingCategoryId = null;
                        bindSelectedCategory(null);
                    }
                    updateSaveState();
                    backStackEntry.getSavedStateHandle()
                            .set(com.group10.moneymate.ui.transaction.TransactionCategoryPickerFragment.RESULT_ALL_CATEGORIES, null);
                });

        backStackEntry.getSavedStateHandle()
                .getLiveData(com.group10.moneymate.ui.transaction.TransactionCategoryPickerFragment.RESULT_CATEGORY_ID)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    selectedAllCategories = false;
                    pendingCategoryId = value.toString();
                    applyPendingCategorySelection();
                    updateSaveState();
                    backStackEntry.getSavedStateHandle()
                            .set(com.group10.moneymate.ui.transaction.TransactionCategoryPickerFragment.RESULT_CATEGORY_ID, null);
                });
    }

    private void showCategoryPicker() {
        AddEditBudgetFragmentDirections.ActionAddEditBudgetFragmentToTransactionCategoryPickerFragment action =
                AddEditBudgetFragmentDirections.actionAddEditBudgetFragmentToTransactionCategoryPickerFragment();
        action.setSelectedCategoryId(selectedCategory != null ? selectedCategory.getId() : null);
        action.setTransactionType(Constants.TYPE_EXPENSE);
        action.setLockToExpense(true);
        action.setShowAllCategories(true);
        Navigation.findNavController(requireView()).navigate(action);
    }

    private void showDateRangePicker() {
        MaterialDatePicker.Builder<androidx.core.util.Pair<Long, Long>> builder =
                MaterialDatePicker.Builder.dateRangePicker();
        builder.setTitleText(R.string.date);
        builder.setTheme(R.style.ThemeOverlay_MoneyMate_MaterialDatePicker);
        builder.setSelection(new androidx.core.util.Pair<>(selectedStartDate, selectedEndDate));
        MaterialDatePicker<androidx.core.util.Pair<Long, Long>> picker = builder.build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) {
                return;
            }
            selectedStartDate = selection.first;
            selectedEndDate = selection.second;
            updatePeriodLabel();
            updateSaveState();
        });
        picker.show(getChildFragmentManager(), "budget_date_range");
    }

    private void updatePeriodLabel() {
        String dateRange = BudgetUiUtils.formatDateRange(selectedStartDate, selectedEndDate);
        if (isCurrentMonthPeriod(selectedStartDate, selectedEndDate)) {
            binding.tvPeriod.setText(getString(R.string.budget_period_template, dateRange));
            return;
        }
        binding.tvPeriod.setText(dateRange);
    }

    private void updateSaveState() {
        boolean hasCategory = selectedAllCategories || selectedCategory != null;
        boolean isOtherCategorySelected = selectedCategory != null
                && Constants.isOtherCategoryId(selectedCategory.getId());
        double amount = parseAmount();
        boolean hasAmount = amount > 0d;
        WalletOptionItem selectedWallet = getSelectedWalletOption();
        double scopeBalance = resolveScopeBalance(selectedWallet);
        String scopeLabel = resolveScopeLabel(selectedWallet);
        boolean exceedsWalletBalance = amount > scopeBalance;
        if (hasAmount && exceedsWalletBalance) {
            binding.tvAmountError.setText(getString(
                    R.string.budget_amount_exceeds_scope,
                    scopeLabel,
                    BudgetUiUtils.formatCurrency(scopeBalance)
            ));
            binding.tvAmountError.setVisibility(View.VISIBLE);
        } else {
            binding.tvAmountError.setVisibility(View.GONE);
        }
        binding.btnSave.setEnabled(hasCategory && hasAmount && !isOtherCategorySelected);
    }

    private double resolveScopeBalance(@Nullable WalletOptionItem selectedWallet) {
        if (selectedWalletId == null || selectedWallet == null) {
            return totalWalletBalance;
        }
        return selectedWallet.balance;
    }

    @NonNull
    private String resolveScopeLabel(@Nullable WalletOptionItem selectedWallet) {
        if (selectedWallet == null) {
            return getString(R.string.budget_wallet_scope_total);
        }
        return selectedWallet.label;
    }

    private void saveBudget() {
        if (isSaving) {
            return;
        }
        if (!selectedAllCategories && selectedCategory == null) {
            Toast.makeText(requireContext(), R.string.error_category_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedCategory != null && Constants.isOtherCategoryId(selectedCategory.getId())) {
            Toast.makeText(requireContext(), R.string.budget_other_categories_auto_only, Toast.LENGTH_SHORT).show();
            return;
        }
        double amount = parseAmount();
        if (amount <= 0d) {
            Toast.makeText(requireContext(), R.string.budget_amount_required, Toast.LENGTH_SHORT).show();
            return;
        }

        startSavingUi();
        performSaveBudget();
    }

    private void performSaveBudget() {
        if (binding == null || !isAdded()) {
            return;
        }
        double amount = parseAmount();
        if (editingBudget == null) {
            viewModel.addBudget(
                    selectedAllCategories ? null : selectedCategory.getId(),
                    selectedWalletId,
                    amount,
                    selectedStartDate,
                    selectedEndDate,
                    new BudgetSaveCallback()
            );
        } else {
            viewModel.updateBudget(
                    editingBudget,
                    selectedAllCategories ? null : selectedCategory.getId(),
                    selectedWalletId,
                    amount,
                    selectedStartDate,
                    selectedEndDate,
                    new BudgetSaveCallback()
            );
        }
    }

    private class BudgetSaveCallback implements com.group10.moneymate.data.repository.BudgetRepository.WriteCallback {
        @Override
        public void onSuccess() {
            if (binding == null || !isAdded()) {
                loadingHelper.dismiss();
                return;
            }
            stopSavingUi();
            Toast.makeText(requireContext(), R.string.budget_save_success, Toast.LENGTH_SHORT).show();
            Navigation.findNavController(binding.getRoot()).navigateUp();
        }

        @Override
        public void onError(Throwable throwable) {
            if (binding == null || !isAdded()) {
                loadingHelper.dismiss();
                return;
            }
            stopSavingUi();
            if (throwable instanceof BudgetRepository.BudgetRuleException) {
                BudgetRepository.BudgetRuleException ruleException =
                        (BudgetRepository.BudgetRuleException) throwable;
                if (ruleException.getReason() == BudgetRepository.BudgetRuleException.Reason.ALL_CATEGORIES_ALREADY_EXISTS) {
                    Toast.makeText(requireContext(), R.string.budget_all_categories_exists, Toast.LENGTH_LONG).show();
                    return;
                }
                if (ruleException.getReason() == BudgetRepository.BudgetRuleException.Reason.OTHER_CATEGORY_MANUAL_NOT_ALLOWED) {
                    Toast.makeText(requireContext(), R.string.budget_other_categories_auto_only, Toast.LENGTH_LONG).show();
                    return;
                }
            }
            Toast.makeText(requireContext(), R.string.budget_save_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void startSavingUi() {
        isSaving = true;
        binding.btnSave.setEnabled(false);
        binding.tvCancel.setEnabled(false);
        loadingHelper.show(this, R.string.common_saving);
    }

    private void stopSavingUi() {
        isSaving = false;
        if (binding != null) {
            binding.btnSave.setEnabled(true);
            binding.tvCancel.setEnabled(true);
        }
        loadingHelper.dismiss();
    }

    private void formatAmountInput(@NonNull Editable editable) {
        String digits = editable.toString().replaceAll("[^\\d]", "");
        if (digits.isEmpty()) {
            return;
        }
        isFormattingAmount = true;
        String formatted = BudgetUiUtils.formatInputAmount(Long.parseLong(digits));
        binding.etAmount.setText(formatted);
        binding.etAmount.setSelection(formatted.length());
        isFormattingAmount = false;
    }

    private double parseAmount() {
        String digits = binding.etAmount.getText() != null
                ? binding.etAmount.getText().toString().replaceAll("[^\\d]", "")
                : "";
        if (digits.isEmpty()) {
            return 0d;
        }
        return Double.parseDouble(digits);
    }

    @Nullable
    private WalletOptionItem getSelectedWalletOption() {
        for (WalletOptionItem item : walletOptions) {
            boolean matches = item.walletId == null
                    ? selectedWalletId == null
                    : item.walletId.equals(selectedWalletId);
            if (matches) {
                return item;
            }
        }
        return null;
    }

    private boolean isCurrentMonthPeriod(long startDate, long endDate) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate monthEnd = today.withDayOfMonth(today.lengthOfMonth());
        LocalDate selectedStart = Instant.ofEpochMilli(startDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        LocalDate selectedEnd = Instant.ofEpochMilli(endDate)
                .atZone(ZoneId.systemDefault())
                .toLocalDate();
        return selectedStart.isEqual(monthStart) && selectedEnd.isEqual(monthEnd);
    }

    @NonNull
    private CategoryEntity createOtherCategoriesOption() {
        CategoryEntity categoryEntity = new CategoryEntity();
        categoryEntity.setId(Constants.CATEGORY_ID_OTHER);
        categoryEntity.setName(getString(R.string.budget_other_categories));
        categoryEntity.setIconName("ic_category_other");
        categoryEntity.setType(Constants.TYPE_EXPENSE);
        categoryEntity.setDefault(true);
        return categoryEntity;
    }

    @Override
    public void onDestroyView() {
        loadingHelper.dismiss();
        super.onDestroyView();
        binding = null;
    }

    private static class WalletOptionItem {
        @Nullable
        private final String walletId;
        @NonNull
        private final String label;
        private final double balance;

        private WalletOptionItem(@Nullable String walletId,
                                 @NonNull String label,
                                 double balance) {
            this.walletId = walletId;
            this.label = label;
            this.balance = balance;
        }
    }
}
