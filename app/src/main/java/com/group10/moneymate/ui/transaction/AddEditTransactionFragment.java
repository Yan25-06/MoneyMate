package com.group10.moneymate.ui.transaction;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.group10.moneymate.utils.PrefsManager;
import com.group10.moneymate.di.MoneyMateApplication;

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
    private long selectedTimestamp = System.currentTimeMillis();
    private List<WalletEntity> walletList = new ArrayList<>();
    private List<CategoryEntity> currentCategoryList = new ArrayList<>();

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

        // Đọc transactionId từ arguments (Edit mode)
        if (getArguments() != null) {
            transactionId = getArguments().getString("transactionId");
        }

        setupTypeToggle();
        setupDatePicker();
        setupSaveButton();
        observeWallets();

        if (transactionId != null) {
            loadExistingTransaction();
        } else {
            // Add mode: chọn EXPENSE mặc định, ngày hôm nay
            binding.toggleType.check(R.id.btn_expense);
            binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
            observeCategories("EXPENSE");
        }
    }

    // ─── Type Toggle ──────────────────────────────────────────────────────────

    private void setupTypeToggle() {
        binding.toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_expense) {
                currentType = "EXPENSE";
            } else if (checkedId == R.id.btn_income) {
                currentType = "INCOME";
            }
            selectedCategoryId = null;
            observeCategories(currentType);
        });
    }

    // ─── Date Picker ──────────────────────────────────────────────────────────

    private void setupDatePicker() {
        binding.etDate.setOnClickListener(v -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(selectedTimestamp);
            new DatePickerDialog(
                    requireContext(),
                    (picker, year, month, day) -> {
                        cal.set(year, month, day);
                        selectedTimestamp = cal.getTimeInMillis();
                        binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
            ).show();
        });
    }

    // ─── Wallet Dropdown ──────────────────────────────────────────────────────

    private void observeWallets() {
        viewModel.getWallets().observe(getViewLifecycleOwner(), wallets -> {
            walletList = wallets != null ? wallets : new ArrayList<>();
            List<String> walletNames = new ArrayList<>();
            for (WalletEntity w : walletList) walletNames.add(w.getName());
            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    walletNames);
            binding.dropdownWallet.setAdapter(adapter);
        });
    }

    // ─── Category Chips ───────────────────────────────────────────────────────

    private void observeCategories(String type) {
        // Xóa chips cũ
        binding.chipGroupCategory.removeAllViews();

        LiveData<List<CategoryEntity>> source = "INCOME".equals(type)
                ? viewModel.getIncomeCategories()
                : viewModel.getExpenseCategories();

        source.observe(getViewLifecycleOwner(), categories -> {
            binding.chipGroupCategory.removeAllViews();
            currentCategoryList = categories != null ? categories : new ArrayList<>();
            for (CategoryEntity cat : currentCategoryList) {
                Chip chip = new Chip(requireContext());
                chip.setText(cat.getName());
                chip.setCheckable(true);
                chip.setTag(cat.getId());
                if (cat.getId().equals(selectedCategoryId)) {
                    chip.setChecked(true);
                }
                chip.setOnCheckedChangeListener((btn, checked) -> {
                    if (checked) selectedCategoryId = cat.getId();
                });
                binding.chipGroupCategory.addView(chip);
            }
        });
    }

    // ─── Load existing transaction (Edit mode) ────────────────────────────────

    private void loadExistingTransaction() {
        viewModel.getTransactionById(transactionId).observe(getViewLifecycleOwner(), transaction -> {
            if (transaction == null) return;
            // Chỉ populate lần đầu
            if (originalTransaction != null) return;
            originalTransaction = transaction;

            binding.etAmount.setText(String.valueOf(transaction.getAmount()));
            binding.etNote.setText(transaction.getNote());
            selectedTimestamp = transaction.getTimestamp();
            binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
            selectedCategoryId = transaction.getCategoryId();
            currentType = transaction.getType();

            if ("INCOME".equals(currentType)) {
                binding.toggleType.check(R.id.btn_income);
            } else {
                binding.toggleType.check(R.id.btn_expense);
            }

            // Chọn ví đúng trong dropdown
            for (int i = 0; i < walletList.size(); i++) {
                if (walletList.get(i).getId().equals(transaction.getWalletId())) {
                    binding.dropdownWallet.setText(walletList.get(i).getName(), false);
                    break;
                }
            }

            observeCategories(currentType);
        });
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    private void setupSaveButton() {
        binding.btnSave.setOnClickListener(v -> {
            if (!validateForm()) return;

            String amountStr = Objects.requireNonNull(binding.etAmount.getText()).toString().trim();
            double amount = Double.parseDouble(amountStr);
            String note = Objects.requireNonNull(binding.etNote.getText()).toString().trim();

            // Tìm walletId từ tên đã chọn
            String walletName = binding.dropdownWallet.getText().toString().trim();
            String walletId = null;
            for (WalletEntity w : walletList) {
                if (w.getName().equals(walletName)) {
                    walletId = w.getId();
                    break;
                }
            }

            PrefsManager prefs = ((MoneyMateApplication) requireActivity().getApplication())
                    .getAppContainer().prefsManager;
            String uid = prefs.getUid();

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
            amount = Double.parseDouble(amountStr);
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