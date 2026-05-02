package com.group10.moneymate.ui.debt;

import androidx.appcompat.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.repository.DebtRepository;
import com.group10.moneymate.databinding.FragmentDebtDetailBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.models.DebtStatus;
import com.group10.moneymate.models.DebtType;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.DateUtils;
import com.group10.moneymate.utils.MoneyMateDatePickerHelper;

import java.time.LocalDate;
import java.time.ZoneId;

public class DebtDetailFragment extends Fragment {

    private FragmentDebtDetailBinding binding;
    private DebtViewModel viewModel;
    private DebtEntity currentDebt;
    private String defaultWalletId;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDebtDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DebtViewModel.class);

        // Get default wallet id from AppContainer for cashback transactions
        MoneyMateApplication app = (MoneyMateApplication) requireActivity().getApplication();
        AppContainer container = app.getAppContainer();

        binding.topAppBar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        // Handle toolbar menu clicks (Edit / Delete)
        binding.topAppBar.setOnMenuItemClickListener(item -> {
            if (currentDebt == null) return false;
            int itemId = item.getItemId();
            if (itemId == R.id.action_edit) {
                showEditDialog();
                return true;
            } else if (itemId == R.id.action_delete) {
                showDeleteConfirmDialog();
                return true;
            }
            return false;
        });

        String debtId = null;
        if (getArguments() != null) {
            debtId = getArguments().getString("debtId");
        }

        if (debtId == null || debtId.isEmpty()) {
            Navigation.findNavController(requireView()).navigateUp();
            return;
        }

        viewModel.getDebtById(debtId).observe(getViewLifecycleOwner(), debt -> {
            if (binding == null || debt == null) return;
            currentDebt = debt;
            populateUI(debt);
        });

        // Observe linked transactions to capture the wallet used in the original debt creation
        viewModel.getTransactionsByDebtId(debtId).observe(getViewLifecycleOwner(), transactions -> {
            if (transactions != null && !transactions.isEmpty()) {
                // List is ordered DESC by timestamp — last item = original creation transaction
                String walletId = transactions.get(transactions.size() - 1).getWalletId();
                if (walletId != null && !walletId.isEmpty()) {
                    defaultWalletId = walletId;
                }
            }
        });

        binding.btnViewTransactions.setOnClickListener(v -> {
            if (currentDebt == null) return;
            Bundle args = new Bundle();
            args.putString("debtId", currentDebt.getId());
            Navigation.findNavController(v)
                    .navigate(R.id.action_debtDetail_to_debtTransactionList, args);
        });

        binding.btnCashback.setOnClickListener(v -> {
            if (currentDebt == null) return;
            showCashbackDialog();
        });
    }

    private void populateUI(DebtEntity debt) {
        boolean isLend = DebtType.LEND.name().equals(debt.getType());
        boolean isSettled = DebtStatus.SETTLED.name().equals(debt.getStatus());

        // Type badge
        binding.tvDebtType.setText(isLend ? R.string.debt_type_lend : R.string.debt_type_borrow);
        int badgeColor = ContextCompat.getColor(
                requireContext(),
                isLend ? R.color.transaction_expense_accent : R.color.transfer_blue);
        binding.tvDebtType.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(badgeColor));

        // Amount
        binding.tvAmount.setText(CurrencyFormatter.format(debt.getAmount(), "VND"));

        // Progress
        int progress = 0;
        if (debt.getAmount() > 0) {
            double paid = debt.getAmount() - debt.getRemainingAmount();
            progress = (int) ((paid / debt.getAmount()) * 100);
        }
        binding.progressBar.setProgress(progress);

        binding.tvProgressLabel.setText(getString(R.string.debt_progress_label,
                CurrencyFormatter.format(debt.getAmount() - debt.getRemainingAmount(), "VND"),
                CurrencyFormatter.format(debt.getAmount(), "VND")));

        // Person
        binding.tvPersonLabel.setText(isLend ? R.string.debt_person_borrower : R.string.debt_person_lender);
        binding.tvPersonName.setText(debt.getPersonName());

        // Due date
        Long dueDate = debt.getDueDate();
        if (dueDate != null && dueDate > 0) {
            binding.tvDueDate.setText(DateUtils.formatDate(dueDate));
        } else {
            binding.tvDueDate.setText(R.string.debt_no_due_date);
        }

        // Note
        if (debt.getNote() != null && !debt.getNote().isEmpty()) {
            binding.layoutNote.setVisibility(View.VISIBLE);
            binding.tvNote.setText(debt.getNote());
        } else {
            binding.layoutNote.setVisibility(View.GONE);
        }

        // Cashback button
        if (isSettled) {
            binding.btnCashback.setEnabled(false);
            binding.btnCashback.setText(isLend
                    ? R.string.debt_section_settled_collect
                    : R.string.debt_section_settled);
        } else {
            binding.btnCashback.setEnabled(true);
            binding.btnCashback.setText(isLend
                    ? R.string.debt_action_collect
                    : R.string.debt_action_repay);
        }
    }

    // ─── Delete ───────────────────────────────────────────────────────────────

    private void showDeleteConfirmDialog() {
        if (currentDebt == null) return;

        AlertDialog dialog = new MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_MoneyMate_MaterialAlertDialog
        )
                .setTitle(R.string.debt_delete_title)
                .setMessage(R.string.debt_delete_confirm)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_delete, (d, which) -> confirmDelete())
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(requireContext().getColor(R.color.budget_danger_red));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(requireContext().getColor(R.color.statistics_text_secondary));
    }

    private void confirmDelete() {
        if (currentDebt == null) return;

        viewModel.deleteDebtWithTransactions(currentDebt, new DebtRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                if (getContext() != null) {
                    Toast.makeText(getContext(), R.string.debt_delete_success, Toast.LENGTH_SHORT).show();
                }
                if (binding != null) {
                    Navigation.findNavController(binding.getRoot()).navigateUp();
                }
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                if (getContext() != null) {
                    Toast.makeText(getContext(), R.string.common_save_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ─── Edit ─────────────────────────────────────────────────────────────────

    private void showEditDialog() {
        if (currentDebt == null) return;

        // Build custom dialog layout
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 24, 48, 0);

        // Person name input
        TextInputLayout personLayout = new TextInputLayout(requireContext());
        personLayout.setHint(getString(R.string.debt_edit_person_name_hint));
        personLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        TextInputEditText personEdit = new TextInputEditText(personLayout.getContext());
        personEdit.setText(currentDebt.getPersonName());
        personLayout.addView(personEdit);
        container.addView(personLayout);

        // Amount input
        TextInputLayout amountLayout = new TextInputLayout(requireContext());
        amountLayout.setHint(getString(R.string.debt_edit_amount_hint));
        amountLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        LinearLayout.LayoutParams amountLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        amountLayoutParams.topMargin = 24;
        amountLayout.setLayoutParams(amountLayoutParams);
        TextInputEditText amountEdit = new TextInputEditText(amountLayout.getContext());
        amountEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        amountEdit.setText(CurrencyFormatter.formatInputAmount((long) currentDebt.getAmount()));
        amountEdit.selectAll();
        amountLayout.addView(amountEdit);
        container.addView(amountLayout);

        // Format amount with thousand separators
        amountEdit.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                amountLayout.setError(null);
            }
            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting) return;
                isFormatting = true;
                String digits = CurrencyFormatter.extractDigits(s.toString());
                String formatted = digits.isEmpty() ? "" : CurrencyFormatter.formatInputAmount(Long.parseLong(digits));
                amountEdit.setText(formatted);
                amountEdit.setSelection(formatted.length());
                isFormatting = false;
            }
        });

        // Due date input
        TextInputLayout dueDateLayout = new TextInputLayout(requireContext());
        dueDateLayout.setHint(getString(R.string.hint_due_date));
        dueDateLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        LinearLayout.LayoutParams dueDateLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        dueDateLayoutParams.topMargin = 24;
        dueDateLayout.setLayoutParams(dueDateLayoutParams);
        TextInputEditText dueDateEdit = new TextInputEditText(dueDateLayout.getContext());
        dueDateEdit.setFocusable(false);
        dueDateEdit.setClickable(true);
        final long[] selectedDueDate = {currentDebt.getDueDate() != null ? currentDebt.getDueDate() : 0L};
        if (selectedDueDate[0] > 0) {
            dueDateEdit.setText(DateUtils.formatDate(selectedDueDate[0]));
        } else {
            dueDateEdit.setText(getString(R.string.debt_no_due_date));
        }
        dueDateLayout.addView(dueDateEdit);
        container.addView(dueDateLayout);

        dueDateEdit.setOnClickListener(v -> {
            LocalDate initialDate = selectedDueDate[0] > 0
                ? java.time.Instant.ofEpochMilli(selectedDueDate[0])
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()
                : LocalDate.now().plusMonths(1);
            MoneyMateDatePickerHelper.showSingleDatePicker(
                this,
                initialDate,
                "edit_debt_due_date",
                date -> {
                selectedDueDate[0] = date.atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
                dueDateEdit.setText(DateUtils.formatDate(selectedDueDate[0]));
                });
        });

        // Note input
        TextInputLayout noteLayout = new TextInputLayout(requireContext());
        noteLayout.setHint(getString(R.string.debt_edit_note_hint));
        noteLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        LinearLayout.LayoutParams noteLayoutParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        noteLayoutParams.topMargin = 24;
        noteLayout.setLayoutParams(noteLayoutParams);
        TextInputEditText noteEdit = new TextInputEditText(noteLayout.getContext());
        noteEdit.setText(currentDebt.getNote());
        noteLayout.addView(noteEdit);
        container.addView(noteLayout);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.debt_edit_title)
                .setView(container)
                .setPositiveButton(R.string.btn_save, null)
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            // Validate person name
            String personName = personEdit.getText() != null ? personEdit.getText().toString().trim() : "";
            if (personName.isEmpty()) {
                personLayout.setError(getString(R.string.debt_error_person_required));
                return;
            }

            // Validate amount
            String rawAmount = amountEdit.getText() != null ? amountEdit.getText().toString().trim() : "";
            String digits = CurrencyFormatter.extractDigits(rawAmount);
            if (digits.isEmpty()) {
                amountLayout.setError(getString(R.string.error_amount_required));
                return;
            }
            double amount;
            try {
                amount = CurrencyFormatter.parseFormattedAmount(rawAmount);
            } catch (NumberFormatException e) {
                amountLayout.setError(getString(R.string.error_amount_invalid));
                return;
            }
            if (amount <= 0) {
                amountLayout.setError(getString(R.string.error_amount_positive));
                return;
            }

            String note = noteEdit.getText() != null ? noteEdit.getText().toString().trim() : "";

            // Create updated debt entity
            DebtEntity updatedDebt = new DebtEntity();
            updatedDebt.setId(currentDebt.getId());
            updatedDebt.setPersonName(personName);
            updatedDebt.setAmount(amount);
            updatedDebt.setDueDate(selectedDueDate[0] > 0 ? selectedDueDate[0] : null);
            updatedDebt.setNote(note.isEmpty() ? null : note);

            viewModel.updateDebtDetails(updatedDebt, new DebtRepository.WriteCallback() {
                @Override
                public void onSuccess() {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), R.string.debt_edit_success, Toast.LENGTH_SHORT).show();
                    }
                    dialog.dismiss();
                }

                @Override
                public void onError(@NonNull Throwable throwable) {
                    if (getContext() != null) {
                        Toast.makeText(getContext(), R.string.common_save_failed, Toast.LENGTH_SHORT).show();
                    }
                }
            });
        });
    }

    // ─── Cashback ─────────────────────────────────────────────────────────────

    private void showCashbackDialog() {
        if (currentDebt == null) return;

        double maxAmount = currentDebt.getRemainingAmount();
        boolean isLend = DebtType.LEND.name().equals(currentDebt.getType());

        // Create dialog with amount input
        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        inputLayout.setHint(getString(R.string.debt_cashback_hint));
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setPadding(48, 24, 48, 0);

        TextInputEditText editText = new TextInputEditText(inputLayout.getContext());
        editText.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        inputLayout.addView(editText);

        // Pre-fill with remaining amount (formatted)
        editText.setText(CurrencyFormatter.formatInputAmount((long) maxAmount));
        editText.selectAll();

        // Format with thousand separators as user types
        editText.addTextChangedListener(new android.text.TextWatcher() {
            private boolean isFormatting;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                inputLayout.setError(null);
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {
                if (isFormatting) return;
                isFormatting = true;
                String digits = CurrencyFormatter.extractDigits(s.toString());
                String formatted = digits.isEmpty() ? "" : CurrencyFormatter.formatInputAmount(Long.parseLong(digits));
                editText.setText(formatted);
                editText.setSelection(formatted.length());
                isFormatting = false;
            }
        });

        String title = isLend
                ? getString(R.string.debt_action_collect)
                : getString(R.string.debt_action_repay);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(title)
                .setView(inputLayout)
                .setPositiveButton(R.string.debt_cashback_confirm, null) // Set null first, override later
                .setNegativeButton(R.string.btn_cancel, null)
                .create();

        dialog.show();

        // Override positive button to add validation
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String raw = editText.getText() != null ? editText.getText().toString().trim() : "";
            String text = CurrencyFormatter.extractDigits(raw);
            if (text.isEmpty()) {
                inputLayout.setError(getString(R.string.error_amount_required));
                return;
            }

            double amount;
            try {
                amount = CurrencyFormatter.parseFormattedAmount(raw);
            } catch (NumberFormatException e) {
                inputLayout.setError(getString(R.string.error_amount_invalid));
                return;
            }

            if (amount <= 0) {
                inputLayout.setError(getString(R.string.error_amount_positive));
                return;
            }

            if (amount > maxAmount) {
                inputLayout.setError(getString(R.string.debt_cashback_max_exceeded,
                        CurrencyFormatter.format(maxAmount, "VND")));
                return;
            }

            // Create cashback transaction
            executeCashback(amount, isLend);
            dialog.dismiss();
        });

        // Error is already cleared inside the formatting TextWatcher above
    }

    private void executeCashback(double amount, boolean isLend) {
        TransactionEntity transaction = new TransactionEntity();
        transaction.setAmount(amount);
        transaction.setTimestamp(System.currentTimeMillis());

        // Thu nợ (LEND → INCOME: tiền vào ví)
        // Trả nợ (BORROW → EXPENSE: tiền ra ví)
        if (isLend) {
            transaction.setType("INCOME");
            transaction.setNote(getString(R.string.debt_type_collection) + " - " + currentDebt.getPersonName());
        } else {
            transaction.setType("EXPENSE");
            transaction.setNote(getString(R.string.debt_type_repayment) + " - " + currentDebt.getPersonName());
        }

        // Debt transactions do not require a category (it would violate foreign keys if invalid)
        transaction.setCategoryId(null);

        // Use wallet from original debt transaction (captured in onViewCreated)
        if (defaultWalletId == null || defaultWalletId.isEmpty()) {
            Toast.makeText(requireContext(), R.string.error_wallet_required, Toast.LENGTH_SHORT).show();
            return;
        }
        transaction.setWalletId(defaultWalletId);

        viewModel.createCashbackTransaction(currentDebt.getId(), amount, transaction,
                new DebtRepository.WriteCallback() {
                    @Override
                    public void onSuccess() {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), R.string.debt_cashback_success, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), R.string.common_save_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
