package com.group10.moneymate.ui.budget;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentBudgetWalletPickerBinding;
import com.group10.moneymate.ui.wallet.WalletViewModel;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.util.ArrayList;
import java.util.List;

public class BudgetWalletPickerFragment extends Fragment {

    private static final String RESULT_SELECTED_WALLET_ID = "result_selected_wallet_id";
    private static final String RESULT_SELECTED_WALLET_LABEL = "result_selected_wallet_label";

    private FragmentBudgetWalletPickerBinding binding;
    private WalletViewModel viewModel;
    private BudgetWalletPickerAdapter adapter;
    @Nullable
    private String selectedWalletId;
    private List<WalletWithBalance> wallets = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBudgetWalletPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(WalletViewModel.class);
        BudgetWalletPickerFragmentArgs args = BudgetWalletPickerFragmentArgs.fromBundle(
                getArguments() != null ? getArguments() : new Bundle()
        );
        selectedWalletId = args.getSelectedWalletId();

        setupRecyclerView();
        setupActions();
        observeData();
        renderSelectedWallet();
    }

    private void setupRecyclerView() {
        adapter = new BudgetWalletPickerAdapter();
        adapter.setSelectedWalletId(selectedWalletId);
        adapter.setListener(new BudgetWalletPickerAdapter.Listener() {
            @Override
            public void onSelect(@NonNull WalletEntity wallet) {
                deliverSelection(wallet.getId(), wallet.getName());
            }

            @Override
            public void onEdit(@NonNull WalletEntity wallet) {
                BudgetWalletPickerFragmentDirections.ActionBudgetWalletPickerToAddEditWallet action =
                        BudgetWalletPickerFragmentDirections.actionBudgetWalletPickerToAddEditWallet();
                action.setWalletId(wallet.getId());
                Navigation.findNavController(binding.getRoot()).navigate(action);
            }
        });
        binding.rvWallets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvWallets.setAdapter(adapter);
    }

    private void setupActions() {
        binding.btnClose.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.cardAllWallets.setOnClickListener(v -> deliverSelection(
                null,
                getString(R.string.budget_wallet_scope_total)
        ));
        binding.fabAddWallet.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                BudgetWalletPickerFragmentDirections.actionBudgetWalletPickerToAddEditWallet()
        ));
    }

    private void observeData() {
        viewModel.getWallets().observe(getViewLifecycleOwner(), walletEntities -> {
            wallets = walletEntities != null ? walletEntities : new ArrayList<>();
            adapter.submitList(new ArrayList<>(wallets));
            renderSelectedWallet();
        });
        viewModel.getTotalBalance().observe(getViewLifecycleOwner(), total -> {
            double value = total != null ? total : 0d;
            binding.tvAllWalletsBalance.setText(CurrencyFormatter.format(value, "VND"));
            binding.tvAllWalletsBalance.setTextColor(requireContext().getColor(
                    value < 0d ? R.color.expense_red : R.color.statistics_text_primary
            ));
        });
    }

    private void renderSelectedWallet() {
        boolean isAllWallets = selectedWalletId == null;
        binding.vAllWalletSelected.setVisibility(isAllWallets ? View.VISIBLE : View.INVISIBLE);
        binding.cardAllWallets.setStrokeWidth(isAllWallets ? 2 : 1);
        binding.cardAllWallets.setStrokeColor(requireContext().getColor(
                isAllWallets ? R.color.budget_safe_green : R.color.budget_divider
        ));
        binding.cardAllWallets.setCardBackgroundColor(requireContext().getColor(android.R.color.white));
        if (adapter != null) {
            adapter.setSelectedWalletId(selectedWalletId);
        }
    }

    private void deliverSelection(@Nullable String walletId, @NonNull String walletLabel) {
        NavController navController = Navigation.findNavController(binding.getRoot());
        NavBackStackEntry previous = navController.getPreviousBackStackEntry();
        if (previous != null) {
            previous.getSavedStateHandle().set(RESULT_SELECTED_WALLET_ID, walletId);
            previous.getSavedStateHandle().set(RESULT_SELECTED_WALLET_LABEL, walletLabel);
        }
        navController.navigateUp();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
