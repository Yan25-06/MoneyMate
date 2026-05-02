package com.group10.moneymate.ui.debt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.databinding.FragmentDebtTransactionListBinding;
import com.group10.moneymate.ui.transaction.TransactionTimeGroupAdapter;
import com.group10.moneymate.ui.transaction.TransactionViewModel;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.IconProvider;
import com.group10.moneymate.utils.TimeWindowUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DebtTransactionListFragment extends Fragment {

    private static final String TYPE_INCOME = Constants.TYPE_INCOME;
    private static final String TYPE_EXPENSE = Constants.TYPE_EXPENSE;

    private FragmentDebtTransactionListBinding binding;
    private DebtViewModel viewModel;
    private TransactionViewModel transactionViewModel;
    private TransactionTimeGroupAdapter groupAdapter;

    private final List<TransactionEntity> currentTransactions = new ArrayList<>();
    private final Map<String, CategoryEntity> categoryMap = new HashMap<>();

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDebtTransactionListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DebtViewModel.class);
        transactionViewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        binding.topAppBar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        String debtId = null;
        if (getArguments() != null) {
            debtId = getArguments().getString("debtId");
        }

        if (debtId == null || debtId.isEmpty()) {
            Navigation.findNavController(requireView()).navigateUp();
            return;
        }

        groupAdapter = new TransactionTimeGroupAdapter();
        groupAdapter.setOnTransactionClickListener(transaction -> {
            Bundle detailArgs = new Bundle();
            detailArgs.putString("transactionId", transaction.getId());
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_debtTransactionList_to_transactionDetail, detailArgs);
        });
        binding.rvTransactions.setAdapter(groupAdapter);
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));

        // Observe reference data (categories) to map the transaction titles and icons
        transactionViewModel.getExpenseCategoriesIncludingDeleted().observe(getViewLifecycleOwner(), categories -> {
            mergeCategories(categories);
            renderList();
        });
        transactionViewModel.getIncomeCategoriesIncludingDeleted().observe(getViewLifecycleOwner(), categories -> {
            mergeCategories(categories);
            renderList();
        });

        viewModel.getTransactionsByDebtId(debtId).observe(getViewLifecycleOwner(), transactions -> {
            currentTransactions.clear();
            if (transactions != null) {
                currentTransactions.addAll(transactions);
            }
            renderList();
        });
    }

    private void mergeCategories(@Nullable List<CategoryEntity> categories) {
        if (categories != null) {
            for (CategoryEntity category : categories) {
                categoryMap.put(category.getId(), category);
            }
        }
    }

    private void renderList() {
        if (binding == null) return;

        boolean isEmpty = currentTransactions.isEmpty();
        binding.tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvTransactions.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

        if (isEmpty) {
            groupAdapter.submitList(new ArrayList<>());
            return;
        }

        List<TransactionTimeGroupAdapter.GroupItem> items = buildGroupItems(currentTransactions);
        groupAdapter.submitList(items);
    }

    @NonNull
    private List<TransactionTimeGroupAdapter.GroupItem> buildGroupItems(@NonNull List<TransactionEntity> transactions) {
        Map<String, DayBucket> grouped = new LinkedHashMap<>();
        for (TransactionEntity transaction : transactions) {
            LocalDate date = TimeWindowUtils.toDeviceLocalDate(transaction.getTimestamp());
            String key = date.toString();
            DayBucket bucket = grouped.get(key);
            if (bucket == null) {
                bucket = new DayBucket(key, date);
                grouped.put(key, bucket);
            }
            bucket.add(transaction);
        }

        List<DayBucket> buckets = new ArrayList<>(grouped.values());
        buckets.sort((a, b) -> Long.compare(
                TimeWindowUtils.startOfDayUtc(b.anchorDate),
                TimeWindowUtils.startOfDayUtc(a.anchorDate)));

        List<TransactionTimeGroupAdapter.GroupItem> items = new ArrayList<>();
        for (DayBucket bucket : buckets) {
            bucket.sortTransactions();
            items.add(bucket.toGroupItem());
        }
        return items;
    }

    @NonNull
    private String formatNetAmount(double amount) {
        if (amount < 0d) {
            return "-" + CurrencyFormatter.format(Math.abs(amount), "VND");
        }
        return CurrencyFormatter.format(amount, "VND");
    }

    @NonNull
    private String capitalize(@NonNull String value) {
        if (value.isEmpty()) {
            return value;
        }
        return value.substring(0, 1).toUpperCase(new Locale("vi", "VN")) + value.substring(1);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ─── Inner class: DayBucket ──────────────────────────────────────────────

    private class DayBucket {
        @NonNull final String key;
        @NonNull final LocalDate anchorDate;
        @NonNull final List<TransactionEntity> transactions = new ArrayList<>();
        double totalIncome;
        double totalExpense;

        DayBucket(@NonNull String key, @NonNull LocalDate anchorDate) {
            this.key = key;
            this.anchorDate = anchorDate;
        }

        void add(@NonNull TransactionEntity transaction) {
            transactions.add(transaction);
            if (TYPE_INCOME.equals(transaction.getType())) {
                totalIncome += transaction.getAmount();
            } else if (TYPE_EXPENSE.equals(transaction.getType())) {
                totalExpense += transaction.getAmount();
            }
        }

        void sortTransactions() {
            transactions.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        }

        @NonNull
        TransactionTimeGroupAdapter.GroupItem toGroupItem() {
            List<TransactionTimeGroupAdapter.RowItem> rowItems = new ArrayList<>();
            for (TransactionEntity transaction : transactions) {
                rowItems.add(toRowItem(transaction));
            }

            LocalDate today = LocalDate.now(java.time.ZoneId.systemDefault());
            String title;
            if (anchorDate.equals(today)) {
                title = getString(R.string.statistics_today);
            } else if (anchorDate.equals(today.minusDays(1))) {
                title = "Hôm qua";
            } else {
                title = capitalize(anchorDate.getDayOfWeek().getDisplayName(
                        java.time.format.TextStyle.FULL, new Locale("vi", "VN")));
            }
            String subtitle = String.format(Locale.getDefault(), "tháng %d %d",
                    anchorDate.getMonthValue(), anchorDate.getYear());

            return new TransactionTimeGroupAdapter.GroupItem(
                    key,
                    String.valueOf(anchorDate.getDayOfMonth()),
                    title,
                    subtitle,
                    formatNetAmount(totalIncome - totalExpense),
                    rowItems
            );
        }

        @NonNull
        private TransactionTimeGroupAdapter.RowItem toRowItem(@NonNull TransactionEntity transaction) {
            CategoryEntity category = transaction.getCategoryId() != null
                    ? categoryMap.get(transaction.getCategoryId())
                    : null;
            String type = transaction.getType();

            int iconRes = IconProvider.resolveCategoryIconByType(
                    requireContext(),
                    category != null ? category.getIconName() : null,
                    type
            );

            String categoryName = category != null
                    ? category.getName()
                    : getString(R.string.ledger_section_unknown);

            String rowSubtitle = transaction.getNote() != null && !transaction.getNote().trim().isEmpty()
                    ? transaction.getNote()
                    : getString(R.string.transaction_detail_no_note);

            String amountLabel = resolveAmountLabel(transaction.getAmount(), type);
            int amountColor = ContextCompat.getColor(requireContext(), resolveAmountColor(type));

            return new TransactionTimeGroupAdapter.RowItem(
                    transaction,
                    iconRes,
                    categoryName,
                    rowSubtitle,
                    amountLabel,
                    amountColor
            );
        }

        @NonNull
        private String resolveAmountLabel(double amount, @Nullable String type) {
            if (TYPE_INCOME.equals(type)) {
                return "+" + CurrencyFormatter.format(amount, "VND");
            }
            if (TYPE_EXPENSE.equals(type)) {
                return "-" + CurrencyFormatter.format(amount, "VND");
            }
            return CurrencyFormatter.format(amount, "VND");
        }

        private int resolveAmountColor(@Nullable String type) {
            if (TYPE_INCOME.equals(type)) {
                return R.color.transfer_blue;
            }
            if (TYPE_EXPENSE.equals(type)) {
                return R.color.expense_red;
            }
            return R.color.statistics_text_primary;
        }
    }
}
