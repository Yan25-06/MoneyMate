package com.group10.moneymate.ui.budget;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.group10.moneymate.databinding.FragmentAddEditBudgetBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class AddEditBudgetFragment extends Fragment {

    private FragmentAddEditBudgetBinding binding;
    private BudgetViewModel viewModel;

    private final List<CategoryEntity> expenseCategories = new ArrayList<>();
    private CategoryEntity selectedCategory;
    private BudgetEntity editingBudget;
    private String pendingCategoryId;
    private long selectedStartDate;
    private long selectedEndDate;
    private boolean isFormattingAmount;
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
        BudgetViewModel.Factory factory = new BudgetViewModel.Factory(
                appContainer.budgetRepository,
                appContainer.categoryRepository,
                appContainer.transactionRepository,
                userId
        );
        viewModel = new ViewModelProvider(this, factory).get(BudgetViewModel.class);
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
        binding.tvCancel.setText(R.string.budget_cancel_label);
        binding.tvWalletScope.setText(R.string.budget_wallet_scope_total);
        updatePeriodLabel();

        binding.tvCancel.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.rowCategory.setOnClickListener(v -> showCategoryPicker());
        binding.rowPeriod.setOnClickListener(v -> showDateRangePicker());
        binding.btnSave.setOnClickListener(v -> saveBudget());

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
                selectedStartDate = budget.getStartDate();
                selectedEndDate = budget.getEndDate();
                binding.etAmount.setText(formatAmountValue((long) budget.getAmount()));
                updatePeriodLabel();
                applyPendingCategorySelection();
                updateSaveState();
            });
        }
    }

    private void applyPendingCategorySelection() {
        if (pendingCategoryId == null || expenseCategories.isEmpty()) {
            return;
        }
        for (CategoryEntity category : expenseCategories) {
            if (pendingCategoryId.equals(category.getId())) {
                selectedCategory = category;
                bindSelectedCategory(category);
                pendingCategoryId = null;
                return;
            }
        }
    }

    private void bindSelectedCategory(@NonNull CategoryEntity category) {
        binding.tvCategoryName.setText(category.getName());
        binding.tvCategoryName.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.black));
        int iconTint = BudgetUiUtils.parseColorOrDefault(
                category.getColorHex(),
                ContextCompat.getColor(requireContext(), R.color.budget_safe_green)
        );
        binding.ivCategoryIcon.setImageResource(BudgetUiUtils.resolveCategoryIcon(
                requireContext(),
                category.getIconResId(),
                category.getName()
        ));
        binding.ivCategoryIcon.setImageTintList(android.content.res.ColorStateList.valueOf(iconTint));
        binding.categoryIconContainer.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(
                        androidx.core.graphics.ColorUtils.setAlphaComponent(iconTint, 32)
                )
        );
    }

    private void showCategoryPicker() {
        if (expenseCategories.isEmpty()) {
            Toast.makeText(requireContext(), R.string.empty_categories, Toast.LENGTH_SHORT).show();
            return;
        }

        String[] categoryNames = new String[expenseCategories.size()];
        int checkedItem = -1;
        for (int i = 0; i < expenseCategories.size(); i++) {
            CategoryEntity category = expenseCategories.get(i);
            categoryNames[i] = category.getName();
            if (selectedCategory != null && category.getId().equals(selectedCategory.getId())) {
                checkedItem = i;
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.budget_category_picker_title)
                .setSingleChoiceItems(categoryNames, checkedItem, (dialog, which) -> {
                    selectedCategory = expenseCategories.get(which);
                    bindSelectedCategory(selectedCategory);
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
        binding.tvPeriod.setText(getString(
                R.string.budget_period_template,
                BudgetUiUtils.formatDateRange(selectedStartDate, selectedEndDate)
        ));
    }

    private void updateSaveState() {
        boolean hasCategory = selectedCategory != null;
        double amount = parseAmount();
        boolean hasAmount = amount > 0d;
        boolean exceedsWalletBalance = amount > totalWalletBalance;
        if (hasAmount && exceedsWalletBalance) {
            binding.tvAmountError.setText(getString(
                    R.string.budget_amount_exceeds_wallets,
                    BudgetUiUtils.formatCurrency(totalWalletBalance)
            ));
            binding.tvAmountError.setVisibility(View.VISIBLE);
        } else {
            binding.tvAmountError.setVisibility(View.GONE);
        }
        binding.btnSave.setEnabled(hasCategory && hasAmount);
    }

    private void saveBudget() {
        if (selectedCategory == null) {
            Toast.makeText(requireContext(), R.string.error_category_required, Toast.LENGTH_SHORT).show();
            return;
        }
        double amount = parseAmount();
        if (amount <= 0d) {
            Toast.makeText(requireContext(), R.string.budget_amount_required, Toast.LENGTH_SHORT).show();
            return;
        }

        binding.btnSave.setEnabled(false);
        if (editingBudget == null) {
            viewModel.addBudget(
                    selectedCategory.getId(),
                    amount,
                    selectedStartDate,
                    selectedEndDate,
                    new BudgetSaveCallback()
            );
        } else {
            viewModel.updateBudget(
                    editingBudget,
                    selectedCategory.getId(),
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
        String formatted = formatAmountValue(Long.parseLong(digits));
        binding.etAmount.setText(formatted);
        binding.etAmount.setSelection(formatted.length());
        isFormattingAmount = false;
    }

    @NonNull
    private String formatAmountValue(long value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        symbols.setGroupingSeparator(',');
        return new DecimalFormat("#,###", symbols).format(value);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
