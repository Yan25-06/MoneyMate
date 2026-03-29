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

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentTransactionDetailBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

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
            new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.delete_transaction)
                    .setMessage(R.string.delete_transaction_confirm)
                    .setNegativeButton(R.string.btn_cancel, null)
                    .setPositiveButton(R.string.btn_delete, this::confirmDelete)
                    .show();
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
        viewModel.getExpenseCategories().observe(getViewLifecycleOwner(), this::mergeCategories);
        viewModel.getIncomeCategories().observe(getViewLifecycleOwner(), this::mergeCategories);
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
        @ColorInt int accentColor = resolveAccentColor(type, category);
        int iconRes = resolveIconRes(category, type);
        String categoryName = category != null
                ? category.getName()
                : getString("TRANSFER".equals(type) ? R.string.ledger_section_transfer : R.string.ledger_section_unknown);

        binding.ivCategoryIcon.setImageResource(iconRes);
        DrawableCompat.setTint(binding.ivCategoryIcon.getDrawable().mutate(), accentColor);
        binding.cvCategoryIconContainer.setCardBackgroundColor(applyAlpha(accentColor, 0.14f));
        binding.tvCategoryName.setText(categoryName);
        binding.tvAmount.setText(formatAmount(transaction.getAmount(), type));
        binding.tvAmount.setTextColor(accentColor);
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
        if (category != null && category.getIconResId() != null && !category.getIconResId().trim().isEmpty()) {
            int resolved = requireContext().getResources().getIdentifier(
                    category.getIconResId(),
                    "drawable",
                    requireContext().getPackageName()
            );
            if (resolved != 0) {
                return resolved;
            }
        }
        if ("INCOME".equals(type)) {
            return R.drawable.outline_attach_money_24;
        }
        if ("TRANSFER".equals(type)) {
            return R.drawable.outline_payments_24;
        }
        return R.drawable.ic_category_spending;
    }

    private int resolveAccentColor(@Nullable String type, @Nullable CategoryEntity category) {
        if (category != null && category.getColorHex() != null && !category.getColorHex().trim().isEmpty()) {
            try {
                return Color.parseColor(category.getColorHex());
            } catch (IllegalArgumentException ignored) {
            }
        }
        if ("INCOME".equals(type)) {
            return ContextCompat.getColor(requireContext(), R.color.transfer_blue);
        }
        if ("TRANSFER".equals(type)) {
            return ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary);
        }
        return ContextCompat.getColor(requireContext(), R.color.expense_red);
    }

    private int applyAlpha(@ColorInt int color, float alphaFraction) {
        int alpha = Math.min(255, Math.max(0, Math.round(alphaFraction * 255f)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
