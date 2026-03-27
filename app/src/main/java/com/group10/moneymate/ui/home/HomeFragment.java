package com.group10.moneymate.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentHomeBinding;
import com.group10.moneymate.ui.transaction.TransactionAdapter;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.util.List;

public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private TransactionAdapter transactionAdapter;

    private double currentTotalBalance = 0.0;
    private double currentIncome = 0.0;
    private double currentExpense = 0.0;
    private boolean isTotalBalanceVisible = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        setupRecyclerView();
        setupListeners();
        observeData();
    }

    private void setupRecyclerView() {
        transactionAdapter = new TransactionAdapter();
        binding.rvRecentTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvRecentTransactions.setAdapter(transactionAdapter);
    }

    private void setupListeners() {
        binding.tvViewAllWallets.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(HomeFragmentDirections.actionHomeToWallets()));

        binding.btnToggleBalanceVisibility.setOnClickListener(v -> {
            isTotalBalanceVisible = !isTotalBalanceVisible;
            renderTotalBalanceText();
        });

        // Bắt sự kiện nút FAB mở Add Transaction
        binding.fabAdd.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(HomeFragmentDirections.actionHomeToAddTransaction()));
        binding.btnViewAllTransactions.setOnClickListener(v -> {
            // Lấy thanh BottomNav từ Activity chứa Fragment này
            BottomNavigationView bottomNav = requireActivity().findViewById(R.id.bottom_navigation);
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.transactionListFragment);
            }
        });
    }

    private void observeData() {
        viewModel.getTotalBalance().observe(getViewLifecycleOwner(), total -> {
            currentTotalBalance = total != null ? total : 0.0;
            renderTotalBalanceText();
        });

        viewModel.getWallets().observe(getViewLifecycleOwner(), this::renderWalletPreview);

        viewModel.getRecentTransactions().observe(getViewLifecycleOwner(), transactions -> {
            transactionAdapter.submitList(transactions);
            // Empty state rule
            boolean isEmpty = transactions == null || transactions.isEmpty();
            binding.tvTransactionsEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.rvRecentTransactions.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });

        viewModel.getMonthlyIncome().observe(getViewLifecycleOwner(), income -> {
            currentIncome = income != null ? income : 0.0;
            renderTotalBalanceText();
        });

        viewModel.getMonthlyExpense().observe(getViewLifecycleOwner(), expense -> {
            currentExpense = expense != null ? expense : 0.0;
            renderTotalBalanceText();
        });
    }

    private void renderTotalBalanceText() {
        if (isTotalBalanceVisible) {
            // Hiện số tiền
            binding.tvTotalBalance.setText(CurrencyFormatter.format(currentTotalBalance, "VND"));
            binding.tvIncome.setText(CurrencyFormatter.format(currentIncome, "VND"));
            binding.tvExpense.setText(CurrencyFormatter.format(currentExpense, "VND"));
            binding.btnToggleBalanceVisibility.setImageResource(R.drawable.outline_visibility_off_24);
        } else {
            // Ẩn số tiền bằng dấu sao
            binding.tvTotalBalance.setText("*********");
            binding.tvIncome.setText("*********");
            binding.tvExpense.setText("*********");
            binding.btnToggleBalanceVisibility.setImageResource(R.drawable.outline_visibility_24);
        }
    }

    private void renderWalletPreview(List<WalletEntity> wallets) {
        binding.llWalletPreview.removeAllViews();
        if (wallets == null || wallets.isEmpty()) {
            binding.tvWalletEmpty.setVisibility(View.VISIBLE);
            binding.llWalletPreview.setVisibility(View.GONE);
            return;
        }

        binding.tvWalletEmpty.setVisibility(View.GONE);
        binding.llWalletPreview.setVisibility(View.VISIBLE);

        int itemCount = Math.min(wallets.size(), 3);
        for (int i = 0; i < itemCount; i++) {
            WalletEntity wallet = wallets.get(i);
            TextView textView = new TextView(requireContext());
            textView.setPadding(0, 8, 0, 8);
            textView.setTextSize(14);
            textView.setText(String.format("%s - %s", wallet.getName(), CurrencyFormatter.format(wallet.getBalance(), "VND")));
            binding.llWalletPreview.addView(textView);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}