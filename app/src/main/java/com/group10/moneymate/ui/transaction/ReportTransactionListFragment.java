package com.group10.moneymate.ui.transaction;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentReportTransactionListBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ReportTransactionListFragment extends Fragment {

    private FragmentReportTransactionListBinding binding;
    private TransactionViewModel viewModel;
    private ReportTransactionListFragmentArgs navArgs;
    private final List<TransactionEntity> allTransactions = new ArrayList<>();
    private final Map<String, WalletEntity> walletMap = new HashMap<>();
    private final Map<String, CategoryEntity> categoryMap = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReportTransactionListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        navArgs = ReportTransactionListFragmentArgs.fromBundle(getArguments() != null ? getArguments() : new Bundle());

        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTransactions.setAdapter(new ConcatAdapter());
        binding.btnBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());

        applyWindowInsets();
        observeReferenceData();
        observeTransactions();
    }

    private void observeReferenceData() {
        viewModel.getWallets().observe(getViewLifecycleOwner(), wallets -> {
            walletMap.clear();
            if (wallets != null) {
                for (WalletEntity wallet : wallets) {
                    walletMap.put(wallet.getId(), wallet);
                }
            }
            renderScreen();
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
        renderScreen();
    }

    private void observeTransactions() {
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), transactions -> {
            allTransactions.clear();
            if (transactions != null) {
                allTransactions.addAll(transactions);
            }
            renderScreen();
        });
    }

    private void renderScreen() {
        if (binding == null) {
            return;
        }
        List<TransactionEntity> filtered = filterTransactions();
        renderSummary(filtered);
        renderSections(filtered);
    }

    @NonNull
    private List<TransactionEntity> filterTransactions() {
        List<TransactionEntity> filtered = new ArrayList<>();
        for (TransactionEntity transaction : allTransactions) {
            long timestamp = transaction.getTimestamp();
            if (timestamp < navArgs.getStartDate() || timestamp > navArgs.getEndDate()) {
                continue;
            }
            if (navArgs.getWalletId() != null && !navArgs.getWalletId().equals(transaction.getWalletId())) {
                continue;
            }
            if (navArgs.getCategoryId() != null && !navArgs.getCategoryId().equals(transaction.getCategoryId())) {
                continue;
            }
            if (navArgs.getTransactionType() != null
                    && !navArgs.getTransactionType().trim().isEmpty()
                    && !navArgs.getTransactionType().equals(transaction.getType())) {
                continue;
            }
            filtered.add(transaction);
        }
        filtered.sort(Comparator.comparingLong(TransactionEntity::getTimestamp).reversed());
        return filtered;
    }

    private void renderSummary(@NonNull List<TransactionEntity> filtered) {
        binding.tvResultCount.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        binding.tvResultCount.setText(getResources().getQuantityString(
                R.plurals.report_transaction_list_result_count,
                filtered.size(),
                filtered.size()
        ));

        double income = 0d;
        double expense = 0d;
        for (TransactionEntity transaction : filtered) {
            if ("INCOME".equals(transaction.getType())) {
                income += transaction.getAmount();
            } else if ("EXPENSE".equals(transaction.getType())) {
                expense += transaction.getAmount();
            }
        }
        double net = income - expense;
        binding.tvIncomeValue.setText(CurrencyFormatter.format(income, "VND"));
        binding.tvExpenseValue.setText(CurrencyFormatter.format(expense, "VND"));
        binding.tvNetValue.setText(formatNetAmount(net));
        binding.tvNetValue.setTextColor(ContextCompat.getColor(
                requireContext(),
                net < 0d ? R.color.expense_red : R.color.statistics_text_primary
        ));
    }

    private void renderSections(@NonNull List<TransactionEntity> filtered) {
        if (filtered.isEmpty()) {
            binding.rvTransactions.setVisibility(View.GONE);
            binding.tvEmpty.setVisibility(View.VISIBLE);
            binding.rvTransactions.setAdapter(new ConcatAdapter());
            return;
        }
        binding.rvTransactions.setVisibility(View.VISIBLE);
        binding.tvEmpty.setVisibility(View.GONE);

        Map<LocalDate, DaySection> grouped = new LinkedHashMap<>();
        for (TransactionEntity transaction : filtered) {
            LocalDate date = Instant.ofEpochMilli(transaction.getTimestamp())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();
            DaySection section = grouped.get(date);
            if (section == null) {
                section = new DaySection(date);
                grouped.put(date, section);
            }
            section.transactions.add(transaction);
            section.netTotal += "EXPENSE".equals(transaction.getType())
                    ? -transaction.getAmount()
                    : transaction.getAmount();
        }

        List<RecyclerView.Adapter<?>> adapters = new ArrayList<>();
        for (DaySection section : grouped.values()) {
            adapters.add(new ReportTransactionDayHeaderAdapter(section.toHeaderItem()));
            ReportTransactionAdapter adapter = new ReportTransactionAdapter();
            adapter.setPresentationMap(buildPresentationMap(section.transactions));
            adapter.setOnTransactionClickListener(this::openTransactionDetail);
            adapter.submitList(new ArrayList<>(section.transactions));
            adapters.add(adapter);
        }
        binding.rvTransactions.setAdapter(new ConcatAdapter(adapters));
    }

    @NonNull
    private Map<String, ReportTransactionAdapter.TransactionPresentation> buildPresentationMap(
            @NonNull List<TransactionEntity> transactions
    ) {
        Map<String, ReportTransactionAdapter.TransactionPresentation> presentations = new HashMap<>();
        for (TransactionEntity transaction : transactions) {
            CategoryEntity category = transaction.getCategoryId() != null
                    ? categoryMap.get(transaction.getCategoryId())
                    : null;
            String type = transaction.getType();
            int accentColor = resolveAccentColor(type, category);
            String title = category != null
                    ? category.getName()
                    : getString("TRANSFER".equals(type) ? R.string.ledger_section_transfer : R.string.ledger_section_unknown);
            String subtitle = transaction.getNote() != null && !transaction.getNote().trim().isEmpty()
                    ? transaction.getNote()
                    : getString(R.string.transaction_detail_no_note);

            presentations.put(transaction.getId(), new ReportTransactionAdapter.TransactionPresentation(
                    resolveIconRes(category, type),
                    accentColor,
                    title,
                    subtitle,
                    formatItemAmount(transaction.getAmount(), type),
                    accentColor
            ));
        }
        return presentations;
    }

    private void openTransactionDetail(@NonNull TransactionEntity transaction) {
        ReportTransactionListFragmentDirections.ActionReportTransactionListFragmentToTransactionDetailFragment action =
                ReportTransactionListFragmentDirections.actionReportTransactionListFragmentToTransactionDetailFragment();
        action.setTransactionId(transaction.getId());
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    @NonNull
    private String formatNetAmount(double amount) {
        if (amount < 0d) {
            return "-" + CurrencyFormatter.format(Math.abs(amount), "VND");
        }
        return CurrencyFormatter.format(amount, "VND");
    }

    @NonNull
    private String formatItemAmount(double amount, @Nullable String type) {
        if ("EXPENSE".equals(type)) {
            return "-" + CurrencyFormatter.format(amount, "VND");
        }
        return CurrencyFormatter.format(amount, "VND");
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

    @ColorInt
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

    private void applyWindowInsets() {
        final int initialTopPadding = binding.layoutTopBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.layoutTopBar, (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(
                    view.getPaddingLeft(),
                    initialTopPadding + systemBars.top,
                    view.getPaddingRight(),
                    view.getPaddingBottom()
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.layoutTopBar);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private class DaySection {
        @NonNull
        private final LocalDate date;
        @NonNull
        private final List<TransactionEntity> transactions = new ArrayList<>();
        private double netTotal;

        DaySection(@NonNull LocalDate date) {
            this.date = date;
        }

        @NonNull
        ReportTransactionDayHeaderAdapter.DayHeaderItem toHeaderItem() {
            return new ReportTransactionDayHeaderAdapter.DayHeaderItem(
                    String.format(Locale.getDefault(), "%02d", date.getDayOfMonth()),
                    capitalize(date.getDayOfWeek().getDisplayName(TextStyle.FULL, new Locale("vi", "VN"))),
                    String.format(Locale.getDefault(), "tháng %d %d", date.getMonthValue(), date.getYear()),
                    CurrencyFormatter.format(Math.abs(netTotal), "VND")
            );
        }
    }

    @NonNull
    private String capitalize(@NonNull String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(new Locale("vi", "VN")) + value.substring(1);
    }
}
