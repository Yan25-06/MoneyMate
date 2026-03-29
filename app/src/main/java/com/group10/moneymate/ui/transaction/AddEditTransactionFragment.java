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
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.chip.Chip;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentAddEditTransactionBinding;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.DateUtils;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.MoneyMateDatePickerHelper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class AddEditTransactionFragment extends Fragment {

    private FragmentAddEditTransactionBinding binding;
    private TransactionViewModel viewModel;

    // State
    private String currentType = "EXPENSE";
    private String selectedCategoryId = null;
    private String selectedWalletId = null;
    private long selectedTimestamp = System.currentTimeMillis();
    private List<WalletEntity> walletList = new ArrayList<>();
    private List<CategoryEntity> currentCategoryList = new ArrayList<>();
    private LiveData<List<CategoryEntity>> currentCategorySource;
    private boolean isFormattingAmount;
    private boolean isLoadingEdit;

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
        setupDatePicker();
        setupTextInputs();
        setupSaveButton();
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
            observeCategories("EXPENSE");
            updateAmountAccent();
            updateTypeToggleAppearance();
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
                currentType = "EXPENSE";
            } else if (checkedId == R.id.btn_income) {
                currentType = "INCOME";
            }
            selectedCategoryId = null;
            updateAmountAccent();
            updateTypeToggleAppearance();
            observeCategories(currentType);
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
                "INCOME".equals(currentType) ? R.color.transaction_income_accent : R.color.transaction_expense_accent
        );
        binding.etAmount.setTextColor(accentColor);
        binding.viewAmountAccent.setBackgroundColor(accentColor);
        binding.btnSave.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.transaction_income_accent)
        ));
    }

    private void updateTypeToggleAppearance() {
        styleTypeButton(binding.btnExpense, "EXPENSE".equals(currentType), true);
        styleTypeButton(binding.btnIncome, "INCOME".equals(currentType), false);
    }

    private void styleTypeButton(@NonNull com.google.android.material.button.MaterialButton button,
                                 boolean selected,
                                 boolean expenseButton) {
        int backgroundColor = ContextCompat.getColor(
                requireContext(),
                selected
                        ? (expenseButton ? R.color.transaction_expense_soft : R.color.transaction_income_soft)
                        : R.color.white
        );
        int textColor = ContextCompat.getColor(
                requireContext(),
                selected
                        ? (expenseButton ? R.color.transaction_expense_accent : R.color.transaction_income_accent)
                        : R.color.transaction_muted_text
        );
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
            walletList = wallets != null ? wallets : new ArrayList<>();
            List<String> walletNames = new ArrayList<>();
            for (WalletEntity w : walletList) walletNames.add(w.getName());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    requireContext(),
                    R.layout.item_wallet_dropdown,
                    walletNames);
            binding.dropdownWallet.setAdapter(adapter);
            binding.dropdownWallet.setOnItemClickListener((parent, view, position, id) -> {
                if (position >= 0 && position < walletList.size()) {
                    selectedWalletId = walletList.get(position).getId();
                }
            });
            applySelectedWalletSelection();
        });
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

    // ─── Category Chips ───────────────────────────────────────────────────────

    private void observeCategories(String type) {
        if (currentCategorySource != null) {
            currentCategorySource.removeObservers(getViewLifecycleOwner());
        }

        currentCategorySource = "INCOME".equals(type)
                ? viewModel.getIncomeCategories()
                : viewModel.getExpenseCategories();

        currentCategorySource.observe(getViewLifecycleOwner(), categories -> {
            binding.chipGroupCategory.removeAllViews();
            currentCategoryList = categories != null ? categories : new ArrayList<>();
            for (CategoryEntity cat : currentCategoryList) {
                Chip chip = new Chip(requireContext());
                chip.setText(cat.getName());
                chip.setCheckable(true);
                chip.setTag(cat.getId());
                chip.setChipBackgroundColor(ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.transaction_chip_bg)
                ));
                chip.setChipStrokeWidth(1f);
                chip.setChipStrokeColor(ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.transaction_border)
                ));
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.transaction_muted_text));
                if (cat.getId().equals(selectedCategoryId)) {
                    chip.setChecked(true);
                }
                chip.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) {
                        selectedCategoryId = cat.getId();
                    }
                    updateCategoryChipAppearance(chip, checked);
                });
                updateCategoryChipAppearance(chip, cat.getId().equals(selectedCategoryId));
                binding.chipGroupCategory.addView(chip);
            }
        });
    }

    private void updateCategoryChipAppearance(@NonNull Chip chip, boolean selected) {
        chip.setChipBackgroundColor(ColorStateList.valueOf(ContextCompat.getColor(
                requireContext(),
                selected ? R.color.transaction_chip_bg_selected : R.color.transaction_chip_bg
        )));
        chip.setTextColor(ContextCompat.getColor(
                requireContext(),
                selected ? R.color.transaction_chip_text_selected : R.color.transaction_muted_text
        ));
        chip.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        chip.setChipStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.transaction_border)
        ));
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

            if ("INCOME".equals(currentType)) {
                binding.toggleType.check(R.id.btn_income);
            } else {
                binding.toggleType.check(R.id.btn_expense);
            }
            updateAmountAccent();
            updateTypeToggleAppearance();
            observeCategories(currentType);
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

            if (originalTransaction != null) {
                // Edit mode
                TransactionEntity updated = new TransactionEntity();
                updated.setId(originalTransaction.getId());
                updated.setUserId(uid);
                updated.setWalletId(walletId);
                updated.setCategoryId(selectedCategoryId);
                updated.setAmount(amount);
                updated.setType(currentType);
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
                transaction.setType(currentType);
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

        if (selectedCategoryId == null) {
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
        super.onDestroyView();
        binding = null;
    }
}
