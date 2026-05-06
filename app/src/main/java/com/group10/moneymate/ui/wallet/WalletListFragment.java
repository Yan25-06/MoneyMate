package com.group10.moneymate.ui.wallet;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentWalletListBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.util.ArrayList;

public class WalletListFragment extends Fragment {

    private FragmentWalletListBinding binding;
    private WalletViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentWalletListBinding.inflate(inflater, container, false);
        View view = binding.getRoot();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(WalletViewModel.class);

        setupInsets();

        WalletAdapter adapter = new WalletAdapter();
        adapter.setWalletItemListener(new WalletAdapter.WalletItemListener() {
            @Override
            public void onEdit(WalletEntity wallet) {
                WalletListFragmentDirections.ActionWalletListToAddEdit action = WalletListFragmentDirections
                        .actionWalletListToAddEdit();
                action.setWalletId(wallet.getId());
                Navigation.findNavController(view).navigate(action);
            }

            @Override
            public void onTransfer(WalletEntity wallet) {
                WalletListFragmentDirections.ActionWalletListToTransfer action = WalletListFragmentDirections
                        .actionWalletListToTransfer();
                action.setFromWalletId(wallet.getId());
                Navigation.findNavController(view).navigate(action);
            }

            @Override
            public void onArchive(WalletEntity wallet) {
                showArchiveConfirmDialog(wallet);
            }

            @Override
            public void onRestore(WalletEntity wallet) {
                showRestoreConfirmDialog(wallet);
            }

            @Override
            public void onDelete(WalletEntity wallet) {
                showDeleteConfirmDialog(wallet);
            }
        });

        binding.rvWallets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvWallets.setAdapter(adapter);

        viewModel.getWallets().observe(getViewLifecycleOwner(),
                wallets -> adapter.submitList(wallets != null ? new ArrayList<>(wallets) : new ArrayList<>()));
        viewModel.getTotalBalance().observe(getViewLifecycleOwner(), total -> {
            double value = total == null ? 0d : total;
            binding.tvTotalWalletBalance.setText(CurrencyFormatter.format(value, "VND"));
            binding.tvTotalWalletBalance.setTextColor(requireContext().getColor(
                    value < 0d ? R.color.expense_red : R.color.statistics_text_primary));
        });
        observeWalletChangedResult();

        binding.topAppBar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.fabAddInline.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                WalletListFragmentDirections.actionWalletListToAddEdit()));
    }

    private void observeWalletChangedResult() {
        NavController navController = Navigation.findNavController(binding.getRoot());
        NavBackStackEntry currentBackStackEntry = navController.getCurrentBackStackEntry();
        if (currentBackStackEntry == null) {
            return;
        }
        currentBackStackEntry.getSavedStateHandle()
                .<Boolean>getLiveData(AddEditWalletFragment.RESULT_WALLET_CHANGED)
                .observe(getViewLifecycleOwner(), changed -> {
                    if (!Boolean.TRUE.equals(changed)) {
                        return;
                    }
                    binding.rvWallets.scrollToPosition(0);
                    currentBackStackEntry.getSavedStateHandle()
                            .remove(AddEditWalletFragment.RESULT_WALLET_CHANGED);
                    currentBackStackEntry.getSavedStateHandle()
                            .remove(AddEditWalletFragment.RESULT_WALLET_CHANGED_ID);
                    currentBackStackEntry.getSavedStateHandle()
                            .remove(AddEditWalletFragment.RESULT_WALLET_CHANGE_TYPE);
                });
    }

    private void setupInsets() {
        final int initialAppBarTopPadding = binding.appBarLayout.getPaddingTop();
        final int initialScrollBottomPadding = binding.scrollWallets.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appBarLayout.setPadding(
                    binding.appBarLayout.getPaddingLeft(),
                    initialAppBarTopPadding + systemBars.top,
                    binding.appBarLayout.getPaddingRight(),
                    binding.appBarLayout.getPaddingBottom());
            binding.scrollWallets.setPadding(
                    binding.scrollWallets.getPaddingLeft(),
                    binding.scrollWallets.getPaddingTop(),
                    binding.scrollWallets.getPaddingRight(),
                    initialScrollBottomPadding + systemBars.bottom);
            return insets;
        });
    }

    private void showDeleteConfirmDialog(WalletEntity wallet) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_MoneyMate_MaterialAlertDialog)
                .setTitle(R.string.delete_wallet_title)
                .setMessage(getString(R.string.delete_wallet_message, wallet.getName()))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.delete_wallet, (dialogInterface, which) -> {
                    viewModel.deleteWallet(wallet, new com.group10.moneymate.data.repository.WalletRepository.WriteCallback() {
                        @Override
                        public void onSuccess() {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), R.string.wallet_deleted, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(@NonNull Throwable throwable) {
                            showWalletWriteFailed();
                        }
                    });
                })
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(requireContext().getColor(R.color.budget_danger_red));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(requireContext().getColor(R.color.statistics_text_secondary));
    }

    private void showArchiveConfirmDialog(WalletEntity wallet) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_MoneyMate_MaterialAlertDialog)
                .setTitle(R.string.archive_wallet_title)
                .setMessage(getString(R.string.archive_wallet_message, wallet.getName()))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.archive_wallet, (dialogInterface, which) -> {
                    viewModel.archiveWallet(wallet, new com.group10.moneymate.data.repository.WalletRepository.WriteCallback() {
                        @Override
                        public void onSuccess() {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), R.string.wallet_archived, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(@NonNull Throwable throwable) {
                            showWalletWriteFailed();
                        }
                    });
                })
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(requireContext().getColor(R.color.moneymate_picker_accent));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(requireContext().getColor(R.color.statistics_text_secondary));
    }

    private void showRestoreConfirmDialog(WalletEntity wallet) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_MoneyMate_MaterialAlertDialog)
                .setTitle(R.string.restore_wallet_title)
                .setMessage(getString(R.string.restore_wallet_message, wallet.getName()))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.restore_wallet, (dialogInterface, which) -> {
                    viewModel.restoreWallet(wallet, new com.group10.moneymate.data.repository.WalletRepository.WriteCallback() {
                        @Override
                        public void onSuccess() {
                            if (isAdded()) {
                                Toast.makeText(requireContext(), R.string.wallet_restored, Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onError(@NonNull Throwable throwable) {
                            showWalletWriteFailed();
                        }
                    });
                })
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(requireContext().getColor(R.color.transfer_blue));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(requireContext().getColor(R.color.statistics_text_secondary));
    }

    private void showWalletWriteFailed() {
        if (!isAdded()) {
            return;
        }
        Toast.makeText(requireContext(), R.string.common_save_failed, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
