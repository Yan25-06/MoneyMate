package com.group10.moneymate.ui.transaction;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentTransactionDetailBinding;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.IconProvider;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TransactionDetailFragment extends Fragment {

    private FragmentTransactionDetailBinding binding;
    private TransactionViewModel viewModel;
    private TransactionDetailFragmentArgs navArgs;
    @Nullable private TransactionEntity currentTransaction;
    private final Map<String, WalletEntity> walletMap = new HashMap<>();
    private final Map<String, CategoryEntity> categoryMap = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTransactionDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        navArgs = TransactionDetailFragmentArgs.fromBundle(getArguments() != null ? getArguments() : new Bundle());

        bindActions();
        observeReferenceData();
        observeTransaction();
    }

    private void bindActions() {
        binding.btnClose.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
        binding.btnDuplicate.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.transaction_detail_duplicate_coming_soon, Toast.LENGTH_SHORT).show());
        binding.btnShare.setOnClickListener(v -> shareTransaction());
        binding.btnEdit.setOnClickListener(v -> {
            if (currentTransaction == null) {
                return;
            }
            TransactionDetailFragmentDirections.ActionTransactionDetailFragmentToAddEditTransactionFragment action =
                    TransactionDetailFragmentDirections.actionTransactionDetailFragmentToAddEditTransactionFragment();
            action.setTransactionId(currentTransaction.getId());
            Navigation.findNavController(v).navigate(action);
        });
        binding.btnDelete.setOnClickListener(v -> {
            if (currentTransaction == null) {
                return;
            }
            AlertDialog dialog = new MaterialAlertDialogBuilder(
                    requireContext(),
                    R.style.ThemeOverlay_MoneyMate_MaterialAlertDialog
            )
                    .setTitle(R.string.delete_transaction)
                    .setMessage(R.string.delete_transaction_confirm)
                    .setNegativeButton(R.string.btn_cancel, null)
                    .setPositiveButton(R.string.btn_delete, this::confirmDelete)
                    .show();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(requireContext().getColor(R.color.budget_danger_red));
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                    .setTextColor(requireContext().getColor(R.color.statistics_text_secondary));
        });
    }

    private void confirmDelete(@NonNull DialogInterface dialogInterface, int which) {
        if (currentTransaction == null) {
            return;
        }
        viewModel.deleteTransaction(currentTransaction);
        Navigation.findNavController(binding.getRoot()).navigateUp();
    }

    private void shareTransaction() {
        if (currentTransaction == null) {
            return;
        }
        String shareText = binding.tvCategoryName.getText() + "\n"
                + binding.tvAmount.getText() + "\n"
                + binding.tvDateValue.getText() + "\n"
                + binding.tvWalletValue.getText();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(intent, getString(R.string.transaction_detail_share)));
    }

    private void observeReferenceData() {
        viewModel.getWallets().observe(getViewLifecycleOwner(), wallets -> {
            walletMap.clear();
            if (wallets != null) {
                for (WalletEntity wallet : wallets) {
                    walletMap.put(wallet.getId(), wallet);
                }
            }
            renderTransaction();
        });
        viewModel.getExpenseCategoriesIncludingDeleted().observe(getViewLifecycleOwner(), this::mergeCategories);
        viewModel.getIncomeCategoriesIncludingDeleted().observe(getViewLifecycleOwner(), this::mergeCategories);
    }

    private void mergeCategories(@Nullable List<CategoryEntity> categories) {
        if (categories != null) {
            for (CategoryEntity category : categories) {
                categoryMap.put(category.getId(), category);
            }
        }
        renderTransaction();
    }

    private void observeTransaction() {
        if (navArgs.getTransactionId() == null || navArgs.getTransactionId().trim().isEmpty()) {
            return;
        }
        viewModel.getTransactionById(navArgs.getTransactionId()).observe(getViewLifecycleOwner(), transaction -> {
            currentTransaction = transaction;
            renderTransaction();
        });
    }

    private void renderTransaction() {
        if (binding == null || currentTransaction == null) {
            return;
        }
        TransactionEntity transaction = currentTransaction;
        CategoryEntity category = transaction.getCategoryId() != null
                ? categoryMap.get(transaction.getCategoryId())
                : null;
        WalletEntity wallet = transaction.getWalletId() != null
                ? walletMap.get(transaction.getWalletId())
                : null;

        String type = transaction.getType();
        int iconRes = resolveIconRes(category, type);
        String categoryName = category != null
                ? category.getName()
                : getString("TRANSFER".equals(type) ? R.string.ledger_section_transfer : R.string.ledger_section_unknown);

        binding.ivCategoryIcon.setImageResource(iconRes);
        binding.ivCategoryIcon.setImageTintList(null);
        binding.cvCategoryIconContainer.setCardBackgroundColor(
                ContextCompat.getColor(requireContext(), android.R.color.white)
        );
        binding.tvCategoryName.setText(categoryName);
        binding.tvAmount.setText(formatAmount(transaction.getAmount(), type));
        binding.tvAmount.setTextColor(resolveAmountColor(type));
        binding.tvNoteValue.setText(TextUtils.isEmpty(transaction.getNote())
                ? getString(R.string.transaction_detail_no_note)
                : transaction.getNote());
        binding.tvDateValue.setText(formatDisplayDate(transaction.getTimestamp()));
        binding.tvWalletValue.setText(wallet != null ? wallet.getName() : getString(R.string.wallet));
    }

    @NonNull
    private String formatAmount(double amount, @Nullable String type) {
        if ("INCOME".equals(type)) {
            return "+" + CurrencyFormatter.format(amount, "VND");
        }
        if ("EXPENSE".equals(type)) {
            return "-" + CurrencyFormatter.format(amount, "VND");
        }
        return CurrencyFormatter.format(amount, "VND");
    }

    @NonNull
    private String formatDisplayDate(long timestamp) {
        return new SimpleDateFormat("EEEE, dd/MM/yyyy", new Locale("vi", "VN")).format(timestamp);
    }

    private int resolveIconRes(@Nullable CategoryEntity category, @Nullable String type) {
        return IconProvider.resolveCategoryIconByType(
                requireContext(),
                category != null ? category.getIconName() : null,
                type
        );
    }

    private int resolveAmountColor(@Nullable String type) {
        if ("INCOME".equals(type)) {
            return ContextCompat.getColor(requireContext(), R.color.income_green);
        }
        if ("EXPENSE".equals(type)) {
            return ContextCompat.getColor(requireContext(), R.color.expense_red);
        }
        return ContextCompat.getColor(requireContext(), R.color.statistics_text_primary);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
