package com.group10.moneymate.ui.wallet;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.NavBackStackEntry;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentAddEditWalletBinding;
import com.group10.moneymate.models.WalletType;
import com.group10.moneymate.utils.CurrencyFormatter;

public class AddEditWalletFragment extends Fragment {

    private FragmentAddEditWalletBinding binding;
    private WalletViewModel viewModel;
    private String walletId;
    private WalletEntity editingWallet;
    private String selectedIconName = "ic_wallet_default";
    private boolean hasInitializedEdit;
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

        setupInsets();
        setupTypeDropdown();
        setupBalanceInput();
        setupIconPicker();
        observeIconSelection();
        updateIconPreview();

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

    private void setupInsets() {
        final int initialTopPadding = binding.scrollWalletForm.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollWalletForm, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.scrollWalletForm.setPadding(
                    binding.scrollWalletForm.getPaddingLeft(),
                    initialTopPadding + systemBars.top,
                    binding.scrollWalletForm.getPaddingRight(),
                    binding.scrollWalletForm.getPaddingBottom()
            );
            return insets;
        });
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
        ArrayAdapter<String> adapter = new NoFilterArrayAdapter(
                requireContext(),
                R.layout.item_moneymate_dropdown_option,
                types
        );
        binding.dropdownType.setAdapter(adapter);
        binding.dropdownType.setText(types[0], false);
        binding.dropdownType.setOnClickListener(v -> binding.dropdownType.showDropDown());
    }

    private static final class NoFilterArrayAdapter extends ArrayAdapter<String> {
        private final String[] items;

        NoFilterArrayAdapter(@NonNull android.content.Context context,
                             int resource,
                             @NonNull String[] items) {
            super(context, resource, items);
            this.items = items;
        }

        @NonNull
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence constraint) {
                    FilterResults results = new FilterResults();
                    results.values = items;
                    results.count = items.length;
                    return results;
                }

                @Override
                protected void publishResults(CharSequence constraint, FilterResults results) {
                    notifyDataSetChanged();
                }
            };
        }
    }

    private void setupIconPicker() {
        binding.cardWalletIcon.setOnClickListener(v -> openIconPicker());
        binding.ivWalletIcon.setOnClickListener(v -> openIconPicker());
    }

    private void openIconPicker() {
        AddEditWalletFragmentDirections.ActionAddEditWalletFragmentToWalletIconPickerFragment action =
                AddEditWalletFragmentDirections.actionAddEditWalletFragmentToWalletIconPickerFragment();
        action.setSelectedIconName(selectedIconName);
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void observeIconSelection() {
        NavBackStackEntry backStackEntry = Navigation.findNavController(requireView()).getCurrentBackStackEntry();
        if (backStackEntry == null) {
            return;
        }
        backStackEntry.getSavedStateHandle()
                .getLiveData(WalletIconPickerFragment.RESULT_ICON_NAME)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    selectedIconName = value.toString();
                    updateIconPreview();
                    backStackEntry.getSavedStateHandle()
                            .set(WalletIconPickerFragment.RESULT_ICON_NAME, null);
                });
    }

    private void updateIconPreview() {
        binding.ivWalletIcon.setImageResource(
                com.group10.moneymate.utils.IconProvider.resolveWalletIcon(
                        requireContext(),
                        selectedIconName,
                        editingWallet == null ? null : editingWallet.getType()
                )
        );
    }

    private void loadEditingWallet(String id) {
        viewModel.getWalletById(id).observe(getViewLifecycleOwner(), wallet -> {
            if (wallet == null) {
                return;
            }
            if (hasInitializedEdit) {
                return;
            }
            hasInitializedEdit = true;
            editingWallet = wallet;
            binding.etName.setText(wallet.getName());
            binding.etBalance.setText(CurrencyFormatter.formatInputAmount((long) wallet.getBalance()));
            binding.dropdownType.setText(typeToLabel(wallet.getType()), false);
            selectedIconName = wallet.getIconName();
            updateIconPreview();
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
            viewModel.addWallet(name, type, balance, selectedIconName);
        } else {
            viewModel.updateWallet(editingWallet, name, type, balance, selectedIconName);
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

    @Override
    public void onResume() {
        super.onResume();
        applyPickedIconIfPresent();
    }

    private void applyPickedIconIfPresent() {
        NavController navController = Navigation.findNavController(requireView());
        NavBackStackEntry currentEntry = navController.getCurrentBackStackEntry();
        if (currentEntry == null) {
            return;
        }
        Object value = currentEntry.getSavedStateHandle().get(WalletIconPickerFragment.RESULT_ICON_NAME);
        if (value == null) {
            return;
        }
        selectedIconName = value.toString();
        updateIconPreview();
        currentEntry.getSavedStateHandle().set(WalletIconPickerFragment.RESULT_ICON_NAME, null);
    }
}
