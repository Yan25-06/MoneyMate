package com.group10.moneymate.ui.transaction;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentTransactionListBinding;

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
        viewModel.getFilteredTransactions().observe(getViewLifecycleOwner(), transactions -> {
            adapter.submitList(transactions);
            // Empty state
            if (transactions == null || transactions.isEmpty()) {
                binding.rvTransactions.setVisibility(View.GONE);
                binding.tvEmpty.setVisibility(View.VISIBLE);
            } else {
                binding.rvTransactions.setVisibility(View.VISIBLE);
                binding.tvEmpty.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}