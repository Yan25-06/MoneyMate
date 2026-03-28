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
import androidx.navigation.Navigation;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.BudgetEntity;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentAddEditBudgetBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.Constants;

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
    private double totalWalletBalance;
    private String userId;
    private AppContainer appContainer;

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
        boolean isEditMode = args.getBudgetId() != null;
        binding.tvScreenTitle.setText(isEditMode
                ? R.string.edit_budget_sheet_title
                : R.string.add_budget_sheet_title);
        updatePeriodLabel();
        binding.dropdownWallet.setKeyListener(null);

        binding.tvCancel.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
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
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
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

        viewModel.getWallets().observe(getViewLifecycleOwner(), wallets -> {
            populateWalletDropdown(wallets);
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

    private void populateWalletDropdown(@Nullable List<WalletEntity> wallets) {
        walletOptions.clear();
        walletOptions.add(new WalletOptionItem(
                null,
                getString(R.string.budget_wallet_scope_total),
                totalWalletBalance
        ));
        if (wallets != null) {
            for (WalletEntity wallet : wallets) {
                walletOptions.add(new WalletOptionItem(
                        wallet.getId(),
                        wallet.getName(),
                        wallet.getBalance()
                ));
            }
        }

        List<String> labels = new ArrayList<>();
        for (WalletOptionItem item : walletOptions) {
            labels.add(item.label);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
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
        String iconName = category != null ? category.getIconResId() : "";
        String colorHex = category != null ? category.getColorHex() : "#4CAF50";
        binding.tvCategoryName.setText(categoryName);
        binding.tvCategoryName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
        int iconTint = BudgetUiUtils.parseColorOrDefault(
                colorHex,
                ContextCompat.getColor(requireContext(), R.color.budget_safe_green)
        );
        binding.ivCategoryIcon.setImageResource(BudgetUiUtils.resolveCategoryIcon(
                requireContext(),
                iconName,
                categoryName
        ));
        binding.ivCategoryIcon.setImageTintList(android.content.res.ColorStateList.valueOf(iconTint));
        binding.categoryIconContainer.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        androidx.core.graphics.ColorUtils.setAlphaComponent(iconTint, 32)
                )
        );
    }

    private void showCategoryPicker() {
        List<CategoryEntity> selectableCategories = new ArrayList<>(expenseCategories);
        selectableCategories.add(createOtherCategoriesOption());

        String[] categoryNames = new String[selectableCategories.size() + 1];
        categoryNames[0] = getString(R.string.budget_all_categories);
        int checkedItem = selectedAllCategories ? 0 : -1;
        for (int i = 0; i < selectableCategories.size(); i++) {
            CategoryEntity category = selectableCategories.get(i);
            categoryNames[i + 1] = category.getName();
            if (!selectedAllCategories
                    && selectedCategory != null
                    && category.getId().equals(selectedCategory.getId())) {
                checkedItem = i + 1;
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_category_picker_title)
                .setSingleChoiceItems(categoryNames, checkedItem, (dialog, which) -> {
                    if (which == 0) {
                        selectedAllCategories = true;
                        selectedCategory = null;
                        bindSelectedCategory(null);
                    } else {
                        selectedAllCategories = false;
                        selectedCategory = selectableCategories.get(which - 1);
                        bindSelectedCategory(selectedCategory);
                    }
                    updateSaveState();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.common_cancel, null)
                .show();
    }

    private void showDateRangePicker() {
        MaterialDatePicker.Builder<androidx.core.util.Pair<Long, Long>> builder =
                MaterialDatePicker.Builder.dateRangePicker();
        builder.setTitleText(R.string.date);
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
        double amount = parseAmount();
        boolean hasAmount = amount > 0d;
        WalletOptionItem selectedWallet = getSelectedWalletOption();
        boolean isAllWalletsScope = selectedWalletId == null;
        double scopeBalance = isAllWalletsScope
                ? totalWalletBalance
                : (selectedWallet != null ? selectedWallet.balance : totalWalletBalance);
        String scopeLabel = selectedWallet != null
                ? selectedWallet.label
                : getString(R.string.budget_wallet_scope_total);
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
        binding.btnSave.setEnabled(hasCategory && hasAmount);
    }

    private void saveBudget() {
        if (!selectedAllCategories && selectedCategory == null) {
            Toast.makeText(requireContext(), R.string.error_category_required, Toast.LENGTH_SHORT).show();
            return;
        }
        double amount = parseAmount();
        if (amount <= 0d) {
            Toast.makeText(requireContext(), R.string.budget_amount_required, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnSave.setEnabled(false);
        if (!selectedAllCategories
                && selectedCategory != null
                && Constants.isOtherCategoryId(selectedCategory.getId())) {
            appContainer.categoryRepository.ensureVirtualOtherCategoryExists(this::performSaveBudget);
            return;
        }
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
                return;
            }
            Toast.makeText(requireContext(), R.string.budget_save_success, Toast.LENGTH_SHORT).show();
            Navigation.findNavController(binding.getRoot()).navigateUp();
        }

        @Override
        public void onError(Throwable throwable) {
            if (binding == null || !isAdded()) {
                return;
            }
            binding.btnSave.setEnabled(true);
            Toast.makeText(requireContext(), R.string.budget_save_failed, Toast.LENGTH_LONG).show();
        }
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
        categoryEntity.setIconResId("ic_category_other");
        categoryEntity.setColorHex("#64748B");
        categoryEntity.setType(Constants.TYPE_EXPENSE);
        categoryEntity.setDefault(true);
        return categoryEntity;
    }

    @Override
    public void onDestroyView() {
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
