package com.group10.moneymate.ui.transaction;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.Navigation;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentAddEditTransactionBinding;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.DateUtils;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.IconProvider;
import com.group10.moneymate.utils.MoneyMateDatePickerHelper;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.models.DebtType;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class AddEditTransactionFragment extends Fragment {

    private FragmentAddEditTransactionBinding binding;
    private TransactionViewModel viewModel;

    // State
    private String currentType = Constants.TYPE_EXPENSE;
    private boolean isDebtTabSelected;
    private String selectedCategoryId = null;
    private String selectedCategoryName = null;
    private String selectedIconName = null;
    private DebtType selectedDebtType = null;
    private String selectedWalletId = null;
    private long selectedTimestamp = System.currentTimeMillis();
    private List<WalletEntity> walletList = new ArrayList<>();
    private List<WalletEntity> allWalletList = new ArrayList<>();
    private List<WalletEntity> activeWalletList = new ArrayList<>();
    @Nullable
    private LiveData<CategoryEntity> selectedCategorySource;
    @Nullable
    private Observer<CategoryEntity> selectedCategoryObserver;
    private boolean isFormattingAmount;
    private boolean isLoadingEdit;
    private boolean isManualIcon;

    // Edit mode
    private String transactionId = null;
    private TransactionEntity originalTransaction = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAddEditTransactionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        AddEditTransactionFragmentArgs args = AddEditTransactionFragmentArgs.fromBundle(
                getArguments() != null ? getArguments() : new Bundle()
        );
        transactionId = args.getTransactionId();

        setupToolbar();
        setupTypeToggle();
        setupCategoryPickerRow();
        setupDatePicker();
        setupTextInputs();
        setupSaveButton();
        observePickerResults();
        observeWallets();

        if (transactionId != null) {
            binding.tvToolbarTitle.setText(R.string.transaction_edit_title);
            binding.btnSave.setText(R.string.btn_update);
            loadExistingTransaction();
        } else {
            // Add mode: chọn EXPENSE mặc định, ngày hôm nay
            binding.tvToolbarTitle.setText(R.string.add_transaction);
            binding.btnSave.setText(R.string.btn_save);
            binding.toggleType.check(R.id.btn_expense);
            binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
            updateAmountAccent();
            updateTypeToggleAppearance();
            updateCategorySelectionUi();
        }
    }

    private void setupToolbar() {
        binding.btnCloseScreen.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
    }

    // ─── Type Toggle ──────────────────────────────────────────────────────────

    private void setupTypeToggle() {
        binding.toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || isLoadingEdit) return;
            if (checkedId == R.id.btn_expense) {
                currentType = Constants.TYPE_EXPENSE;
                isDebtTabSelected = false;
            } else if (checkedId == R.id.btn_income) {
                currentType = Constants.TYPE_INCOME;
                isDebtTabSelected = false;
            } else if (checkedId == R.id.btn_debt) {
                currentType = TransactionCategoryPickerViewModel.TYPE_DEBT;
                isDebtTabSelected = true;
            }
            selectedCategoryId = null;
            selectedCategoryName = null;
            selectedDebtType = null;
            isManualIcon = false;
            selectedIconName = null;
            updateAmountAccent();
            updateTypeToggleAppearance();
            updateCategorySelectionUi();
        });
    }

    private void setupCategoryPickerRow() {
        binding.layoutCategoryPicker.setOnClickListener(v -> {
            AddEditTransactionFragmentDirections.ActionAddEditTransactionFragmentToTransactionCategoryPickerFragment action =
                    AddEditTransactionFragmentDirections.actionAddEditTransactionFragmentToTransactionCategoryPickerFragment();
            action.setSelectedCategoryId(selectedCategoryId);
            action.setTransactionType(resolvePickerTransactionType());
            Navigation.findNavController(v).navigate(action);
        });
    }

    // ─── Date Picker ──────────────────────────────────────────────────────────

    private void setupDatePicker() {
        binding.etDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selectedTimestamp);
            MoneyMateDatePickerHelper.showSingleDatePicker(
                    this,
                    java.time.Instant.ofEpochMilli(selectedTimestamp)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate(),
                    "transaction_single_date",
                    date -> {
                        cal.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
                        selectedTimestamp = cal.getTimeInMillis();
                        binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
                    }
            );
        });
        binding.btnPrevDate.setOnClickListener(v -> shiftSelectedDate(-1));
        binding.btnNextDate.setOnClickListener(v -> shiftSelectedDate(1));
    }

    private void shiftSelectedDate(int dayOffset) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(selectedTimestamp);
        cal.add(Calendar.DAY_OF_MONTH, dayOffset);
        selectedTimestamp = cal.getTimeInMillis();
        binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
    }

    private void setupTextInputs() {
        binding.etNote.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        binding.etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No-op: input is reformatted only after the latest user edit is available.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // No-op: the formatter relies on the settled text in afterTextChanged().
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (isFormattingAmount) {
                    return;
                }
                String digits = CurrencyFormatter.extractDigits(editable.toString());
                if (digits.isEmpty()) {
                    return;
                }
                isFormattingAmount = true;
                String formatted = CurrencyFormatter.formatInputAmount(Long.parseLong(digits));
                binding.etAmount.setText(formatted);
                binding.etAmount.setSelection(formatted.length());
                isFormattingAmount = false;
            }
        });
    }

    private void updateAmountAccent() {
        int accentColor = ContextCompat.getColor(
                requireContext(),
                Constants.TYPE_INCOME.equals(getEffectiveTypeForUi())
                        ? R.color.transfer_blue
                        : R.color.transaction_expense_accent
        );
        binding.etAmount.setTextColor(accentColor);
        binding.viewAmountAccent.setBackgroundColor(accentColor);
        binding.btnSave.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.transaction_income_accent)
        ));
    }

    private void updateTypeToggleAppearance() {
        boolean expenseSelected = !isDebtTabSelected && Constants.TYPE_EXPENSE.equals(currentType);
        boolean incomeSelected = !isDebtTabSelected && Constants.TYPE_INCOME.equals(currentType);
        styleTypeButton(binding.btnExpense, expenseSelected, true);
        styleTypeButton(binding.btnIncome, incomeSelected, false);
        styleDebtButton(binding.btnDebt, isDebtTabSelected);
    }

    private void styleTypeButton(@NonNull com.google.android.material.button.MaterialButton button,
                                 boolean selected,
                                 boolean expenseButton) {
        int backgroundColor = ContextCompat.getColor(
                requireContext(),
                selected
                        ? (expenseButton ? R.color.transaction_expense_soft : R.color.statistics_period_selected_background)
                        : R.color.white
        );
        int textColor = ContextCompat.getColor(
                requireContext(),
                selected
                        ? (expenseButton ? R.color.transaction_expense_accent : R.color.transfer_blue)
                        : R.color.transaction_muted_text
        );
        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        button.setStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.transaction_border)
        ));
        button.setTextColor(textColor);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void styleDebtButton(@NonNull com.google.android.material.button.MaterialButton button,
                                 boolean selected) {
        int accentColor = ContextCompat.getColor(requireContext(), R.color.budget_warning_orange);
        int backgroundColor = selected
                ? ColorUtils.setAlphaComponent(accentColor, 28)
                : ContextCompat.getColor(requireContext(), R.color.white);
        int textColor = selected
                ? accentColor
                : ContextCompat.getColor(requireContext(), R.color.transaction_muted_text);
        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        button.setStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.transaction_border)
        ));
        button.setTextColor(textColor);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    // ─── Wallet Dropdown ──────────────────────────────────────────────────────

    private void observeWallets() {
        viewModel.getWallets().observe(getViewLifecycleOwner(), wallets -> {
            allWalletList = wallets != null ? wallets : new ArrayList<>();
            refreshWalletDropdown();
        });
        viewModel.getActiveWallets().observe(getViewLifecycleOwner(), wallets -> {
            activeWalletList = wallets != null ? wallets : new ArrayList<>();
            refreshWalletDropdown();
        });
    }

    private void refreshWalletDropdown() {
        List<WalletEntity> displayWallets = new ArrayList<>(activeWalletList);
        addExistingEditWallet(displayWallets);
        walletList = displayWallets;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_wallet_dropdown,
                buildWalletNames(displayWallets)
        );
        binding.dropdownWallet.setAdapter(adapter);
        binding.dropdownWallet.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < walletList.size()) {
                selectedWalletId = walletList.get(position).getId();
            }
        });
        applySelectedWalletSelection();
    }

    private void applySelectedWalletSelection() {
        if (selectedWalletId == null || walletList.isEmpty()) {
            return;
        }
        for (WalletEntity wallet : walletList) {
            if (selectedWalletId.equals(wallet.getId())) {
                binding.dropdownWallet.setText(wallet.getName(), false);
                return;
            }
        }
    }

    private void observePickerResults() {
        NavBackStackEntry backStackEntry = Navigation.findNavController(requireView()).getCurrentBackStackEntry();
        if (backStackEntry == null) {
            return;
        }
        observeCategoryIdResult(backStackEntry);
        observeCategoryTypeResult(backStackEntry);
        observeDebtTypeResult(backStackEntry);
    }

    private void loadSelectedCategory() {
        if (selectedCategorySource != null && selectedCategoryObserver != null) {
            selectedCategorySource.removeObserver(selectedCategoryObserver);
        }
        if (selectedCategoryId == null) {
            updateCategorySelectionUi();
            return;
        }
        selectedCategorySource = viewModel.getCategoryByIdIncludingDeleted(selectedCategoryId);
        selectedCategoryObserver = category -> {
            if (category == null) {
                return;
            }
            selectedCategoryName = category.getName();
            if (!isManualIcon) {
                selectedIconName = category.getIconName();
            }
            updateCategorySelectionUi();
        };
        selectedCategorySource.observe(getViewLifecycleOwner(), selectedCategoryObserver);
    }

    private void updateCategorySelectionUi() {
        binding.tvCategoryValue.setText(resolveCategorySelectionLabel());

        int iconRes = IconProvider.resolveCategoryIconByType(
                requireContext(),
                selectedIconName,
                getEffectiveTypeForUi()
        );
        binding.ivCategoryIcon.setImageResource(iconRes);
    }

    private String getEffectiveTypeForUi() {
        if (selectedDebtType != null) {
            return selectedDebtType == DebtType.BORROW
                    ? Constants.TYPE_INCOME
                    : Constants.TYPE_EXPENSE;
        }
        return currentType;
    }

    @NonNull
    private String resolvePickerTransactionType() {
        return isDebtTabSelected ? TransactionCategoryPickerViewModel.TYPE_DEBT : currentType;
    }

    private void addExistingEditWallet(@NonNull List<WalletEntity> displayWallets) {
        if (transactionId == null || selectedWalletId == null || containsWallet(displayWallets, selectedWalletId)) {
            return;
        }
        for (WalletEntity wallet : allWalletList) {
            if (selectedWalletId.equals(wallet.getId())) {
                displayWallets.add(wallet);
                return;
            }
        }
    }

    private boolean containsWallet(@NonNull List<WalletEntity> wallets, @NonNull String walletId) {
        for (WalletEntity wallet : wallets) {
            if (walletId.equals(wallet.getId())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private List<String> buildWalletNames(@NonNull List<WalletEntity> wallets) {
        List<String> walletNames = new ArrayList<>();
        for (WalletEntity wallet : wallets) {
            walletNames.add(wallet.getName());
        }
        return walletNames;
    }

    private void observeCategoryIdResult(@NonNull NavBackStackEntry backStackEntry) {
        backStackEntry.getSavedStateHandle()
                .getLiveData(TransactionCategoryPickerFragment.RESULT_CATEGORY_ID)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    selectedCategoryId = value.toString();
                    resetCategoryIdentity();
                    selectedDebtType = null;
                    isDebtTabSelected = false;
                    loadSelectedCategory();
                    backStackEntry.getSavedStateHandle()
                            .set(TransactionCategoryPickerFragment.RESULT_CATEGORY_ID, null);
                });
    }

    private void observeCategoryTypeResult(@NonNull NavBackStackEntry backStackEntry) {
        backStackEntry.getSavedStateHandle()
                .getLiveData(TransactionCategoryPickerFragment.RESULT_CATEGORY_TYPE)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    applySelectedType(value.toString());
                    backStackEntry.getSavedStateHandle()
                            .set(TransactionCategoryPickerFragment.RESULT_CATEGORY_TYPE, null);
                });
    }

    private void observeDebtTypeResult(@NonNull NavBackStackEntry backStackEntry) {
        backStackEntry.getSavedStateHandle()
                .getLiveData(TransactionCategoryPickerFragment.RESULT_DEBT_TYPE)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    applyDebtSelection(DebtType.valueOf(value.toString()));
                    backStackEntry.getSavedStateHandle()
                            .set(TransactionCategoryPickerFragment.RESULT_DEBT_TYPE, null);
                });
    }

    private void resetCategoryIdentity() {
        selectedCategoryName = null;
        isManualIcon = false;
        selectedIconName = null;
    }

    private void applySelectedType(@NonNull String type) {
        isLoadingEdit = true;
        if (TransactionCategoryPickerViewModel.TYPE_DEBT.equals(type)) {
            isDebtTabSelected = true;
            binding.toggleType.check(R.id.btn_debt);
        } else {
            isDebtTabSelected = false;
            currentType = type;
            binding.toggleType.check(resolveTypeToggleButtonId(type));
        }
        updateAmountAccent();
        updateTypeToggleAppearance();
        isLoadingEdit = false;
    }

    private void applyDebtSelection(@NonNull DebtType debtType) {
        selectedDebtType = debtType;
        selectedCategoryId = null;
        selectedCategoryName = null;
        isManualIcon = false;
        selectedIconName = null;
        isDebtTabSelected = true;
        currentType = debtType == DebtType.BORROW ? Constants.TYPE_INCOME : Constants.TYPE_EXPENSE;
        binding.toggleType.check(R.id.btn_debt);
        updateAmountAccent();
        updateTypeToggleAppearance();
        updateCategorySelectionUi();
    }

    private int resolveTypeToggleButtonId(@NonNull String type) {
        return Constants.TYPE_INCOME.equals(type) ? R.id.btn_income : R.id.btn_expense;
    }

    @NonNull
    private String resolveCategorySelectionLabel() {
        if (selectedDebtType != null) {
            return selectedDebtType == DebtType.BORROW
                    ? getString(R.string.debt_type_borrow)
                    : getString(R.string.debt_type_lend);
        }
        if (selectedCategoryName != null) {
            return selectedCategoryName;
        }
        return getString(R.string.category_pick_placeholder);
    }

    // ─── Load existing transaction (Edit mode) ────────────────────────────────

    private void loadExistingTransaction() {
        viewModel.getTransactionById(transactionId).observe(getViewLifecycleOwner(), transaction -> {
            if (transaction == null) return;
            // Chỉ populate lần đầu
            if (originalTransaction != null) return;
            originalTransaction = transaction;
            isLoadingEdit = true;

            binding.etAmount.setText(CurrencyFormatter.formatInputAmount((long) transaction.getAmount()));
            binding.etNote.setText(TextUtils.isEmpty(transaction.getNote()) ? "" : transaction.getNote());
            selectedTimestamp = transaction.getTimestamp();
            binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
            selectedCategoryId = transaction.getCategoryId();
            selectedWalletId = transaction.getWalletId();
            currentType = transaction.getType();
            isDebtTabSelected = false;

            if (Constants.TYPE_INCOME.equals(currentType)) {
                binding.toggleType.check(R.id.btn_income);
            } else {
                binding.toggleType.check(R.id.btn_expense);
            }
            updateAmountAccent();
            updateTypeToggleAppearance();
            loadSelectedCategory();
            refreshWalletDropdown();
            applySelectedWalletSelection();
            isLoadingEdit = false;
        });
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    private void setupSaveButton() {
        binding.btnSave.setOnClickListener(v -> {
            if (!validateForm()) return;

            String amountStr = Objects.requireNonNull(binding.etAmount.getText()).toString().trim();
            double amount = CurrencyFormatter.parseFormattedAmount(amountStr);
            String note = binding.etNote.getText() != null
                    ? binding.etNote.getText().toString().trim()
                    : "";

            String walletId = selectedWalletId;
            if (walletId == null) {
                String walletName = binding.dropdownWallet.getText().toString().trim();
                for (WalletEntity w : walletList) {
                    if (w.getName().equals(walletName)) {
                        walletId = w.getId();
                        break;
                    }
                }
            }

            MoneyMateApplication app = (MoneyMateApplication) requireActivity().getApplication();
            String uid = app.getAppContainer().authRepository.getCurrentUserId();
            if (TextUtils.isEmpty(uid)) {
                Toast.makeText(requireContext(), R.string.error_auth_required, Toast.LENGTH_SHORT).show();
                return;
            }

            String effectiveType = getEffectiveTypeForUi();

            if (originalTransaction != null) {
                // Edit mode
                TransactionEntity updated = new TransactionEntity();
                updated.setId(originalTransaction.getId());
                updated.setUserId(uid);
                updated.setWalletId(walletId);
                updated.setCategoryId(selectedCategoryId);
                updated.setAmount(amount);
                updated.setType(effectiveType);
                updated.setNote(note);
                updated.setTimestamp(selectedTimestamp);
                updated.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                updated.setUpdatedAt(System.currentTimeMillis());
                viewModel.updateTransaction(originalTransaction, updated);
            } else {
                // Add mode
                TransactionEntity transaction = new TransactionEntity();
                transaction.setId(UUID.randomUUID().toString());
                transaction.setUserId(uid);
                transaction.setWalletId(walletId);
                transaction.setCategoryId(selectedCategoryId);
                transaction.setAmount(amount);
                transaction.setType(effectiveType);
                transaction.setNote(note);
                transaction.setTimestamp(selectedTimestamp);
                transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                transaction.setUpdatedAt(System.currentTimeMillis());
                viewModel.insertTransaction(transaction);
            }

            Navigation.findNavController(requireView()).popBackStack();
        });
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    private boolean validateForm() {
        String amountStr = binding.etAmount.getText() != null
                ? binding.etAmount.getText().toString().trim() : "";

        if (TextUtils.isEmpty(amountStr)) {
            Toast.makeText(requireContext(), R.string.error_amount_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        double amount;
        try {
            amount = CurrencyFormatter.parseFormattedAmount(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), R.string.error_amount_invalid, Toast.LENGTH_SHORT).show();
            return false;
        }

        if (amount <= 0) {
            Toast.makeText(requireContext(), R.string.error_amount_positive, Toast.LENGTH_SHORT).show();
            return false;
        }

        if (selectedCategoryId == null && selectedDebtType == null) {
            Toast.makeText(requireContext(), R.string.error_category_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        String walletName = binding.dropdownWallet.getText() != null
                ? binding.dropdownWallet.getText().toString().trim() : "";
        if (TextUtils.isEmpty(walletName)) {
            Toast.makeText(requireContext(), R.string.error_wallet_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    @Override
    public void onDestroyView() {
        if (selectedCategorySource != null && selectedCategoryObserver != null) {
            selectedCategorySource.removeObserver(selectedCategoryObserver);
        }
        super.onDestroyView();
        binding = null;
    }
}
