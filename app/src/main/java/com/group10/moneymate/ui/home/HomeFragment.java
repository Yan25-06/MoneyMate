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

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.FragmentHomeBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.util.List;

/**
 * Home/Dashboard fragment showing balance overview and recent transactions.
 */
public class HomeFragment extends Fragment {

    private FragmentHomeBinding binding;
    private HomeViewModel viewModel;
    private double totalBalance;
    private boolean isTotalBalanceVisible = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(HomeViewModel.class);

        binding.tvViewAllWallets.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                HomeFragmentDirections.actionHomeToWallets()));

        binding.btnToggleBalanceVisibility.setOnClickListener(v -> {
            isTotalBalanceVisible = !isTotalBalanceVisible;
            renderTotalBalance();
        });

        renderTotalBalance();

        viewModel.getWallets().observe(getViewLifecycleOwner(), this::renderWalletPreview);
    }

    private void renderWalletPreview(List<WalletEntity> wallets) {
        totalBalance = 0.0;
        binding.llWalletPreview.removeAllViews();

        if (wallets == null || wallets.isEmpty()) {
            binding.tvWalletEmpty.setVisibility(View.VISIBLE);
            binding.llWalletPreview.setVisibility(View.GONE);
            renderTotalBalance();
            return;
        }

        binding.tvWalletEmpty.setVisibility(View.GONE);
        binding.llWalletPreview.setVisibility(View.VISIBLE);

        for (WalletEntity wallet : wallets) {
            if (!wallet.isExcluded()) {
                totalBalance += wallet.getBalance();
            }
        }

        int itemCount = Math.min(wallets.size(), 3);
        for (int i = 0; i < itemCount; i++) {
            WalletEntity wallet = wallets.get(i);
            TextView textView = new TextView(requireContext());
            textView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            textView.setPadding(0, 8, 0, 8);
            textView.setTextSize(14);
            textView.setText(wallet.getName() + " - " + CurrencyFormatter.format(wallet.getBalance(), "VND"));
            binding.llWalletPreview.addView(textView);
        }

        renderTotalBalance();
    }

    private void renderTotalBalance() {
        if (isTotalBalanceVisible) {
            binding.tvTotalBalance.setText(CurrencyFormatter.format(totalBalance, "VND"));
            binding.btnToggleBalanceVisibility.setImageResource(R.drawable.outline_visibility_off_24);
        } else {
            binding.tvTotalBalance.setText("*********");
            binding.btnToggleBalanceVisibility.setImageResource(R.drawable.outline_visibility_24);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
