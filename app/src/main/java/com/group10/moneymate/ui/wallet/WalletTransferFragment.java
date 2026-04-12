package com.group10.moneymate.ui.wallet;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentWalletTransferBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

import android.text.Editable;
import android.text.TextWatcher;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * Fragment cho màn hình chuyển tiền giữa 2 ví.
 * Nhận argument fromWalletId (nullable) qua Safe Args.
 */
public class WalletTransferFragment extends Fragment {

    private FragmentWalletTransferBinding binding;
    private WalletTransferViewModel viewModel;

    private final List<WalletEntity> walletList = new ArrayList<>();
    private WalletEntity selectedFromWallet;
    private WalletEntity selectedToWallet;
    private final Calendar selectedDate = Calendar.getInstance();

    private boolean isFormattingAmount;
    private boolean isFormattingFeeAmount;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWalletTransferBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(WalletTransferViewModel.class);

        String fromWalletId = WalletTransferFragmentArgs.fromBundle(getArguments()).getFromWalletId();

        setupToolbar();
        setupDateControls();
        setupFeeToggle();
        setupTextFormatters();

        // Observe categories ready
        viewModel.getCategoriesReady().observe(getViewLifecycleOwner(), ready -> {
            if (Boolean.FALSE.equals(ready)) {
                Toast.makeText(requireContext(), R.string.transfer_error_category_not_found, Toast.LENGTH_LONG).show();
            }
        });

        // Observe wallets
        viewModel.getActiveWallets().observe(getViewLifecycleOwner(), wallets -> {
            walletList.clear();
            if (wallets != null) {
                walletList.addAll(wallets);
            }

            if (walletList.size() < 2) {
                Toast.makeText(requireContext(), R.string.transfer_error_not_enough_wallets, Toast.LENGTH_LONG).show();
            }

            // Pre-select from wallet
            if (fromWalletId != null && selectedFromWallet == null) {
                for (int i = 0; i < walletList.size(); i++) {
                    if (walletList.get(i).getId().equals(fromWalletId)) {
                        selectedFromWallet = walletList.get(i);
                        binding.dropdownFromWallet.setText(selectedFromWallet.getName(), false);
                        break;
                    }
                }
            }

            setupWalletDropdowns();
            updateNotesHints();
        });

        // Confirm button
        binding.btnConfirm.setOnClickListener(v -> doTransfer());
    }

    private void setupToolbar() {
        binding.btnCloseScreen.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
    }

    private void setupDateControls() {
        binding.tvDate.setText(dateFormat.format(selectedDate.getTime()));

        binding.tvDate.setOnClickListener(v -> showDatePicker());

        binding.btnPrevDate.setOnClickListener(v -> {
            selectedDate.add(Calendar.DAY_OF_MONTH, -1);
            binding.tvDate.setText(dateFormat.format(selectedDate.getTime()));
        });

        binding.btnNextDate.setOnClickListener(v -> {
            selectedDate.add(Calendar.DAY_OF_MONTH, 1);
            binding.tvDate.setText(dateFormat.format(selectedDate.getTime()));
        });
    }

    private void showDatePicker() {
        new DatePickerDialog(
                requireContext(),
                (datePicker, year, month, dayOfMonth) -> {
                    selectedDate.set(year, month, dayOfMonth);
                    binding.tvDate.setText(dateFormat.format(selectedDate.getTime()));
                },
                selectedDate.get(Calendar.YEAR),
                selectedDate.get(Calendar.MONTH),
                selectedDate.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private void setupFeeToggle() {
        binding.switchFee.setOnCheckedChangeListener((buttonView, isChecked) ->
                binding.layoutFee.setVisibility(isChecked ? View.VISIBLE : View.GONE));
    }

    private void setupTextFormatters() {
        binding.etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable editable) {
                if (isFormattingAmount) return;
                String digits = CurrencyFormatter.extractDigits(editable.toString());
                if (digits.isEmpty()) return;
                isFormattingAmount = true;
                String formatted = CurrencyFormatter.formatInputAmount(Long.parseLong(digits));
                binding.etAmount.setText(formatted);
                binding.etAmount.setSelection(formatted.length());
                isFormattingAmount = false;
            }
        });

        binding.etFeeAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable editable) {
                if (isFormattingFeeAmount) return;
                String digits = CurrencyFormatter.extractDigits(editable.toString());
                if (digits.isEmpty()) return;
                isFormattingFeeAmount = true;
                String formatted = CurrencyFormatter.formatInputAmount(Long.parseLong(digits));
                binding.etFeeAmount.setText(formatted);
                binding.etFeeAmount.setSelection(formatted.length());
                isFormattingFeeAmount = false;
            }
        });
    }

    private void setupWalletDropdowns() {
        List<String> walletNames = new ArrayList<>();
        for (WalletEntity wallet : walletList) {
            walletNames.add(wallet.getName());
        }

        ArrayAdapter<String> fromAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                walletNames);
        binding.dropdownFromWallet.setAdapter(fromAdapter);

        updateToWalletDropdown();

        binding.dropdownFromWallet.setOnItemClickListener((parent, v, position, id) -> {
            WalletEntity newSelected = null;
            String selectedName = parent.getItemAtPosition(position).toString();
            for (WalletEntity wallet : walletList) {
                if (wallet.getName().equals(selectedName)) {
                    newSelected = wallet;
                    break;
                }
            }
            if (newSelected != null) {
                selectedFromWallet = newSelected;
                if (selectedToWallet != null && selectedToWallet.getId().equals(selectedFromWallet.getId())) {
                    selectedToWallet = null;
                    binding.dropdownToWallet.setText("", false);
                }
                updateToWalletDropdown();
                updateNotesHints();
            }
        });

        binding.dropdownToWallet.setOnItemClickListener((parent, v, position, id) -> {
            String selectedName = parent.getItemAtPosition(position).toString();
            for (WalletEntity wallet : walletList) {
                if (wallet.getName().equals(selectedName)) {
                    selectedToWallet = wallet;
                    break;
                }
            }
            updateNotesHints();
        });
    }

    private void updateToWalletDropdown() {
        List<String> toWalletNames = new ArrayList<>();
        for (WalletEntity wallet : walletList) {
            if (selectedFromWallet == null || !wallet.getId().equals(selectedFromWallet.getId())) {
                toWalletNames.add(wallet.getName());
            }
        }
        ArrayAdapter<String> toAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                toWalletNames);
        binding.dropdownToWallet.setAdapter(toAdapter);
    }

    private void updateNotesHints() {
        String fromName = selectedFromWallet != null ? selectedFromWallet.getName() : "";
        String toName = selectedToWallet != null ? selectedToWallet.getName() : "";

        if (!toName.isEmpty()) {
            String fromHint = getString(R.string.transfer_note_from_hint, toName);
            if (binding.etNoteFrom.getText() == null || binding.etNoteFrom.getText().toString().isEmpty()
                    || isAutoGeneratedFromNote(binding.etNoteFrom.getText().toString())) {
                binding.etNoteFrom.setText(fromHint);
            }
            binding.etNoteFrom.setHint(fromHint);
        }

        if (!fromName.isEmpty()) {
            String toHint = getString(R.string.transfer_note_to_hint, fromName);
            if (binding.etNoteTo.getText() == null || binding.etNoteTo.getText().toString().isEmpty()
                    || isAutoGeneratedToNote(binding.etNoteTo.getText().toString())) {
                binding.etNoteTo.setText(toHint);
            }
            binding.etNoteTo.setHint(toHint);
        }

        // Fee note
        if (!fromName.isEmpty() && !toName.isEmpty()) {
            String feeHint = getString(R.string.transfer_fee_note_hint, fromName, toName);
            if (binding.etFeeNote.getText() == null || binding.etFeeNote.getText().toString().isEmpty()
                    || isAutoGeneratedFeeNote(binding.etFeeNote.getText().toString())) {
                binding.etFeeNote.setText(feeHint);
            }
            binding.etFeeNote.setHint(feeHint);
        }
    }

    private boolean isAutoGeneratedFromNote(String text) {
        // Check if starts with the common prefix pattern
        return text.startsWith("Chuyển tiền đến ");
    }

    private boolean isAutoGeneratedToNote(String text) {
        return text.startsWith("Nhận tiền từ ");
    }

    private boolean isAutoGeneratedFeeNote(String text) {
        return text.startsWith("Phí chuyển khoản khi chuyển tiền từ ");
    }

    private void doTransfer() {
        // Validate amount
        String amountStr = binding.etAmount.getText() != null
                ? CurrencyFormatter.extractDigits(binding.etAmount.getText().toString().trim()) : "";
        if (amountStr.isEmpty()) {
            Toast.makeText(requireContext(), R.string.transfer_error_no_amount, Toast.LENGTH_SHORT).show();
            binding.etAmount.requestFocus();
            return;
        }
        double amount;
        try {
            amount = Double.parseDouble(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), R.string.error_amount_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        if (amount <= 0) {
            Toast.makeText(requireContext(), R.string.error_amount_positive, Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate wallets
        if (selectedFromWallet == null) {
            Toast.makeText(requireContext(), R.string.error_wallet_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedToWallet == null) {
            Toast.makeText(requireContext(), R.string.transfer_error_no_dest_wallet, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedFromWallet.getId().equals(selectedToWallet.getId())) {
            Toast.makeText(requireContext(), R.string.transfer_error_same_wallet, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fee validation
        boolean hasFee = binding.switchFee.isChecked();
        double feeAmount = 0;
        if (hasFee) {
            String feeStr = binding.etFeeAmount.getText() != null
                    ? CurrencyFormatter.extractDigits(binding.etFeeAmount.getText().toString().trim()) : "";
            if (feeStr.isEmpty()) {
                Toast.makeText(requireContext(), R.string.transfer_error_no_fee_amount, Toast.LENGTH_SHORT).show();
                binding.etFeeAmount.requestFocus();
                return;
            }
            try {
                feeAmount = Double.parseDouble(feeStr);
            } catch (NumberFormatException e) {
                Toast.makeText(requireContext(), R.string.error_amount_invalid, Toast.LENGTH_SHORT).show();
                return;
            }
            if (feeAmount <= 0) {
                Toast.makeText(requireContext(), R.string.error_amount_positive, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        // Get notes
        String fromNote = binding.etNoteFrom.getText() != null
                ? binding.etNoteFrom.getText().toString().trim() : "";
        String toNote = binding.etNoteTo.getText() != null
                ? binding.etNoteTo.getText().toString().trim() : "";
        String feeNote = binding.etFeeNote.getText() != null
                ? binding.etFeeNote.getText().toString().trim() : "";

        long timestamp = selectedDate.getTimeInMillis();

        // Disable button to prevent double-click
        binding.btnConfirm.setEnabled(false);

        viewModel.executeTransfer(
                amount,
                selectedFromWallet.getId(),
                selectedToWallet.getId(),
                fromNote,
                toNote,
                timestamp,
                hasFee,
                feeAmount,
                feeNote,
                new WalletTransferViewModel.TransferCallback() {
                    @Override
                    public void onSuccess() {
                        if (!isAdded()) return;
                        Toast.makeText(requireContext(), R.string.transfer_success, Toast.LENGTH_SHORT).show();
                        Navigation.findNavController(requireView()).navigateUp();
                    }

                    @Override
                    public void onError(@NonNull String message) {
                        if (!isAdded()) return;
                        binding.btnConfirm.setEnabled(true);
                        Toast.makeText(requireContext(), R.string.common_save_failed, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
