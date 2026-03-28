package com.group10.moneymate.ui.transaction;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.databinding.FragmentTransactionListBinding;

import java.util.ArrayList;
import java.util.List;

public class TransactionListFragment extends Fragment {

    private FragmentTransactionListBinding binding;
    private TransactionViewModel viewModel;
    private TransactionAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTransactionListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);

        setupRecyclerView();
        setupFab();
        observeTransactions();
    }

    private void setupRecyclerView() {
        adapter = new TransactionAdapter();
        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTransactions.setAdapter(adapter);

        // Click → Edit
        adapter.setOnTransactionClickListener(transaction -> {
            Bundle args = new Bundle();
            args.putString("transactionId", transaction.getId());
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_transactionList_to_addEdit, args);
        });

        // Long click → Delete confirm
        adapter.setOnTransactionLongClickListener(transaction ->
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.delete_transaction)
                        .setMessage(R.string.delete_transaction_confirm)
                        .setPositiveButton(R.string.btn_delete, (d, w) ->
                                viewModel.deleteTransaction(transaction))
                        .setNegativeButton(R.string.btn_cancel, null)
                        .show()
        );
    }

    private void setupFab() {
        binding.fabAdd.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_transactionList_to_addEdit)
        );
    }

    private void observeTransactions() {
        TransactionListFragmentArgs args = TransactionListFragmentArgs.fromBundle(
                getArguments() != null ? getArguments() : new Bundle()
        );
        List<AggregateBudgetFilter> aggregateFilters =
                parseAggregateBudgetFilters(args.getBudgetAggregateFilters());
        LiveData<List<TransactionEntity>> source = resolveTransactionSource(args);

        source.observe(getViewLifecycleOwner(), transactions -> {
            List<TransactionEntity> displayTransactions = transactions;
            if (!aggregateFilters.isEmpty()) {
                displayTransactions = filterTransactionsForAggregateBudget(transactions, aggregateFilters);
            }

            adapter.submitList(displayTransactions);
            // Empty state
            if (displayTransactions == null || displayTransactions.isEmpty()) {
                binding.rvTransactions.setVisibility(View.GONE);
                binding.tvEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.rvTransactions.setVisibility(View.VISIBLE);
                binding.tvEmpty.setVisibility(View.GONE);
            }
        });
    }

    private boolean shouldUseSingleBudgetFilter(@NonNull TransactionListFragmentArgs args) {
        return args.getBudgetStartDate() > 0L
                && args.getBudgetEndDate() > 0L
                && (args.getBudgetCategoryId() != null
                || args.getBudgetWalletId() != null
                || args.getBudgetAggregateFilters() == null);
    }

    private boolean shouldUseAggregateBudgetFilter(@NonNull TransactionListFragmentArgs args) {
        return args.getBudgetCategoryId() == null
                && args.getBudgetStartDate() > 0L
                && args.getBudgetEndDate() > 0L;
    }

    @NonNull
    private LiveData<List<TransactionEntity>> resolveTransactionSource(
            @NonNull TransactionListFragmentArgs args
    ) {
        if (shouldUseSingleBudgetFilter(args)) {
            return viewModel.getTransactionsForBudget(
                    args.getBudgetCategoryId(),
                    args.getBudgetWalletId(),
                    args.getBudgetStartDate(),
                    args.getBudgetEndDate()
            );
        }
        if (shouldUseAggregateBudgetFilter(args)) {
            return viewModel.getExpenseTransactionsByRange(
                    args.getBudgetStartDate(),
                    args.getBudgetEndDate()
            );
        }
        return viewModel.getFilteredTransactions();
    }

    @NonNull
    private List<AggregateBudgetFilter> parseAggregateBudgetFilters(@Nullable String filterSpec) {
        List<AggregateBudgetFilter> filters = new ArrayList<>();
        if (filterSpec == null || filterSpec.trim().isEmpty()) {
            return filters;
        }

        String[] segments = filterSpec.split(";");
        for (String segment : segments) {
            AggregateBudgetFilter filter = parseAggregateBudgetFilter(segment);
            if (filter != null) {
                filters.add(filter);
            }
        }
        return filters;
    }

    @Nullable
    private AggregateBudgetFilter parseAggregateBudgetFilter(@Nullable String segment) {
        if (segment == null || segment.trim().isEmpty()) {
            return null;
        }
        String[] parts = segment.split("\\|", -1);
        if (parts.length != 4) {
            return null;
        }
        try {
            return new AggregateBudgetFilter(
                    parts[0],
                    parts[1].isEmpty() ? null : parts[1],
                    Long.parseLong(parts[2]),
                    Long.parseLong(parts[3])
            );
        } catch (NumberFormatException ignored) {
            // Skip malformed filters to keep the transaction screen usable.
            return null;
        }
    }

    @NonNull
    private List<TransactionEntity> filterTransactionsForAggregateBudget(
            @Nullable List<TransactionEntity> transactions,
            @NonNull List<AggregateBudgetFilter> filters
    ) {
        if (transactions == null || transactions.isEmpty()) {
            return new ArrayList<>();
        }
        List<TransactionEntity> matches = new ArrayList<>();
        for (TransactionEntity transaction : transactions) {
            if (matchesAnyBudgetFilter(transaction, filters)) {
                matches.add(transaction);
            }
        }
        return matches;
    }

    private boolean matchesAnyBudgetFilter(@NonNull TransactionEntity transaction,
                                           @NonNull List<AggregateBudgetFilter> filters) {
        for (AggregateBudgetFilter filter : filters) {
            if (filter.matches(transaction)) {
                return true;
            }
        }
        return false;
    }

    private static class AggregateBudgetFilter {
        private final String categoryId;
        @Nullable
        private final String walletId;
        private final long startDate;
        private final long endDate;

        private AggregateBudgetFilter(@NonNull String categoryId,
                                      @Nullable String walletId,
                                      long startDate,
                                      long endDate) {
            this.categoryId = categoryId;
            this.walletId = walletId;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        private boolean matches(@NonNull TransactionEntity transaction) {
            if (!categoryId.equals(transaction.getCategoryId())) {
                return false;
            }
            if (walletId != null && !walletId.equals(transaction.getWalletId())) {
                return false;
            }
            long timestamp = transaction.getTimestamp();
            return timestamp >= startDate && timestamp <= endDate;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
