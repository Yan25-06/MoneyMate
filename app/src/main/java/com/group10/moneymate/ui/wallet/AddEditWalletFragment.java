package com.group10.moneymate.ui.wallet;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentAddEditWalletBinding;
import com.group10.moneymate.models.WalletType;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.util.Locale;

public class AddEditWalletFragment extends Fragment {

    private FragmentAddEditWalletBinding binding;
    private WalletViewModel viewModel;
    private String walletId;
    private WalletEntity editingWallet;
    private BottomNavigationView bottomNavigationView;
    private boolean isFormattingBalance;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAddEditWalletBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(WalletViewModel.class);
        bottomNavigationView = requireActivity().findViewById(R.id.bottom_navigation);
        if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(View.GONE);
        }

        setupTypeDropdown();
        setupBalanceInput();

        AddEditWalletFragmentArgs args = AddEditWalletFragmentArgs
                .fromBundle(getArguments() == null ? new Bundle() : getArguments());
        walletId = args.getWalletId();

        if (!TextUtils.isEmpty(walletId)) {
            loadEditingWallet(walletId);
            binding.btnSave.setText(R.string.btn_update);
            binding.topAppBar.setTitle(R.string.edit_wallet);
        } else {
            binding.topAppBar.setTitle(R.string.add_wallet);
        }

        binding.topAppBar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.btnSave.setOnClickListener(v -> saveWallet());
    }

    private void setupBalanceInput() {
        binding.etBalance.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No-op: balance formatting happens after the text change is applied.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // No-op: we only need the final text to normalize currency formatting.
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (isFormattingBalance) {
                    return;
                }
                String digits = CurrencyFormatter.extractDigits(editable.toString());
                if (digits.isEmpty()) {
                    return;
                }
                isFormattingBalance = true;
                String formatted = CurrencyFormatter.formatInputAmount(Long.parseLong(digits));
                binding.etBalance.setText(formatted);
                binding.etBalance.setSelection(formatted.length());
                isFormattingBalance = false;
            }
        });
    }

    private void setupTypeDropdown() {
        String[] types = new String[] {
                getString(R.string.wallet_type_cash),
                getString(R.string.wallet_type_bank),
                getString(R.string.wallet_type_ewallet)
        };
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, types);
        binding.dropdownType.setAdapter(adapter);
        binding.dropdownType.setText(types[0], false);
    }

    private void loadEditingWallet(String id) {
        viewModel.getWalletById(id).observe(getViewLifecycleOwner(), wallet -> {
            if (wallet == null) {
                return;
            }
            editingWallet = wallet;
            binding.etName.setText(wallet.getName());
            binding.etBalance.setText(CurrencyFormatter.formatInputAmount((long) wallet.getBalance()));
            binding.dropdownType.setText(typeToLabel(wallet.getType()), false);
        });
    }

    private void saveWallet() {
        String name = normalizeSingleLineText(binding.etName.getText() == null
                ? ""
                : binding.etName.getText().toString());
        String balanceText = binding.etBalance.getText() == null ? "" : binding.etBalance.getText().toString().trim();
        String typeLabel = binding.dropdownType.getText() == null ? "" : binding.dropdownType.getText().toString();

        if (name.isEmpty()) {
            binding.etName.setError(getString(R.string.error_wallet_name_required));
            return;
        }
        if (balanceText.isEmpty()) {
            binding.etBalance.setError(getString(R.string.error_wallet_balance_required));
            return;
        }

        double balance;
        try {
            balance = CurrencyFormatter.parseFormattedAmount(balanceText);
        } catch (NumberFormatException e) {
            binding.etBalance.setError(getString(R.string.error_wallet_balance_invalid));
            return;
        }

        WalletType type = labelToType(typeLabel);

        if (editingWallet == null) {
            viewModel.addWallet(name, type, balance);
        } else {
            viewModel.updateWallet(editingWallet, name, type, balance);
        }

        Toast.makeText(requireContext(), R.string.wallet_saved, Toast.LENGTH_SHORT).show();
        NavController navController = Navigation.findNavController(binding.getRoot());
        navController.popBackStack();
    }

    private WalletType labelToType(String label) {
        if (getString(R.string.wallet_type_bank).equals(label)) {
            return WalletType.BANK;
        }
        if (getString(R.string.wallet_type_ewallet).equals(label)) {
            return WalletType.E_WALLET;
        }
        return WalletType.CASH;
    }

    private String typeToLabel(String type) {
        if (WalletType.BANK.name().equals(type)) {
            return getString(R.string.wallet_type_bank);
        }
        if (WalletType.E_WALLET.name().equals(type)) {
            return getString(R.string.wallet_type_ewallet);
        }
        return getString(R.string.wallet_type_cash);
    }

    @NonNull
    private String normalizeSingleLineText(@NonNull String value) {
        return value.trim().replaceAll("\\s{2,}", " ");
    }

    @Override
    public void onDestroyView() {
        if (bottomNavigationView != null) {
            bottomNavigationView.setVisibility(View.VISIBLE);
            bottomNavigationView = null;
        }
        super.onDestroyView();
        binding = null;
    }
}
