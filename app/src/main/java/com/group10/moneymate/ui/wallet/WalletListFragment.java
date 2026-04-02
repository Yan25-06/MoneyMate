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
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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

        setupInsets();

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

        viewModel.getWallets().observe(getViewLifecycleOwner(), adapter::submitList);
        viewModel.getTotalBalance().observe(getViewLifecycleOwner(), total -> {
            double value = total == null ? 0d : total;
            binding.tvTotalWalletBalance.setText(CurrencyFormatter.format(value, "VND"));
            binding.tvTotalWalletBalance.setTextColor(requireContext().getColor(
                    value < 0d ? R.color.expense_red : R.color.statistics_text_primary
            ));
        });

        binding.topAppBar.setNavigationOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.fabAddInline.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                WalletListFragmentDirections.actionWalletListToAddEdit()
        ));
    }

    private void setupInsets() {
        final int initialTopPadding = binding.scrollWallets.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollWallets, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.scrollWallets.setPadding(
                    binding.scrollWallets.getPaddingLeft(),
                    initialTopPadding + systemBars.top,
                    binding.scrollWallets.getPaddingRight(),
                    binding.scrollWallets.getPaddingBottom()
            );
            return insets;
        });
    }

    private void showDeleteConfirmDialog(WalletEntity wallet) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_MoneyMate_MaterialAlertDialog
        )
                .setTitle(R.string.delete_wallet_title)
                .setMessage(getString(R.string.delete_wallet_message, wallet.getName()))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.delete_wallet, (dialogInterface, which) -> {
                    viewModel.deleteWallet(wallet);
                    Toast.makeText(requireContext(), R.string.wallet_deleted, Toast.LENGTH_SHORT).show();
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
                R.style.ThemeOverlay_MoneyMate_MaterialAlertDialog
        )
                .setTitle(R.string.archive_wallet_title)
                .setMessage(getString(R.string.archive_wallet_message, wallet.getName()))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.archive_wallet, (dialogInterface, which) -> {
                    viewModel.archiveWallet(wallet);
                    Toast.makeText(requireContext(), R.string.wallet_archived, Toast.LENGTH_SHORT).show();
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
                R.style.ThemeOverlay_MoneyMate_MaterialAlertDialog
        )
                .setTitle(R.string.restore_wallet_title)
                .setMessage(getString(R.string.restore_wallet_message, wallet.getName()))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.restore_wallet, (dialogInterface, which) -> {
                    viewModel.restoreWallet(wallet);
                    Toast.makeText(requireContext(), R.string.wallet_restored, Toast.LENGTH_SHORT).show();
                })
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(requireContext().getColor(R.color.transfer_blue));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(requireContext().getColor(R.color.statistics_text_secondary));
    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
