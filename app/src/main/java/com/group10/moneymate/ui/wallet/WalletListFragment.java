package com.group10.moneymate.ui.wallet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.appbar.MaterialToolbar;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentWalletListBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

public class WalletListFragment extends Fragment {

    private FragmentWalletListBinding binding;
    private WalletViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentWalletListBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(WalletViewModel.class);

        // Make this Fragment's toolbar act as the Activity ActionBar so the title is displayed
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(binding.topAppBar);
        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setTitle(R.string.my_wallets);
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        WalletAdapter adapter = new WalletAdapter();
        adapter.setWalletItemListener(new WalletAdapter.WalletItemListener() {
            @Override
            public void onEdit(WalletEntity wallet) {
                WalletListFragmentDirections.ActionWalletListToAddEdit action =
                        WalletListFragmentDirections.actionWalletListToAddEdit();
                action.setWalletId(wallet.getId());
                Navigation.findNavController(view).navigate(action);
            }

            @Override
            public void onDelete(WalletEntity wallet) {
                showDeleteConfirmDialog(wallet);
            }
        });

        binding.rvWallets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvWallets.setAdapter(adapter);

        viewModel.getWallets().observe(getViewLifecycleOwner(), adapter::submitList);
        viewModel.getTotalBalance().observe(getViewLifecycleOwner(), total -> {
            double value = total == null ? 0d : total;
            binding.tvTotalWalletBalance.setText(CurrencyFormatter.format(value, "VND"));
        });

        // keep navigation click to handle up navigation
        binding.topAppBar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        binding.fabAdd.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                WalletListFragmentDirections.actionWalletListToAddEdit()
        ));
    }

    private void showDeleteConfirmDialog(WalletEntity wallet) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_wallet_title)
                .setMessage(getString(R.string.delete_wallet_message, wallet.getName()))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.delete_wallet, (dialog, which) -> viewModel.deleteWallet(wallet))
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
