package com.group10.moneymate.ui.debt;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.databinding.FragmentDebtListBinding;
import com.group10.moneymate.models.DebtType;

import java.util.List;

public class DebtListFragment extends Fragment {

    private FragmentDebtListBinding binding;
    private DebtViewModel viewModel;
    private DebtListAdapter adapter;

    // Observer field to avoid multi-observe when switching tabs
    private Observer<List<DebtEntity>> debtObserver;
    private androidx.lifecycle.LiveData<List<DebtEntity>> currentLiveData;

    // Current tab: false = Cần trả (BORROW), true = Cần thu (LEND)
    private boolean isCollectTab = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentDebtListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(DebtViewModel.class);

        setupToolbar();
        setupTabs();
        setupRecyclerView();
        setupFab();

        // Load initial tab
        loadDebts();
    }

    private void setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
    }

    private void setupTabs() {
        binding.tlDebtType.addTab(binding.tlDebtType.newTab().setText(R.string.debt_tab_need_to_pay));
        binding.tlDebtType.addTab(binding.tlDebtType.newTab().setText(R.string.debt_tab_need_to_collect));

        if (isCollectTab) {
            TabLayout.Tab tab = binding.tlDebtType.getTabAt(1);
            if (tab != null) tab.select();
        }

        binding.tlDebtType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                isCollectTab = tab.getPosition() == 1;
                adapter.setCollectTab(isCollectTab);
                loadDebts();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) { }

            @Override
            public void onTabReselected(TabLayout.Tab tab) { }
        });
    }

    private void setupRecyclerView() {
        adapter = new DebtListAdapter();
        adapter.setCollectTab(isCollectTab);
        adapter.setOnDebtClickListener(debt -> {
            Bundle args = new Bundle();
            args.putString("debtId", debt.getId());
            Navigation.findNavController(requireView())
                    .navigate(R.id.action_debtList_to_debtDetail, args);
        });
        binding.rvDebts.setAdapter(adapter);
        binding.rvDebts.setLayoutManager(new LinearLayoutManager(requireContext()));
    }

    private void setupFab() {
        binding.fabAdd.setOnClickListener(v -> {
            // Navigate to AddEditTransaction with debt tab pre-selected
            Bundle args = new Bundle();
            args.putString("preselectedTab", "DEBT");
            Navigation.findNavController(v)
                    .navigate(R.id.action_debtList_to_addEditTransaction, args);
        });
    }

    private void loadDebts() {
        // Remove previous observer if any
        if (currentLiveData != null && debtObserver != null) {
            currentLiveData.removeObserver(debtObserver);
        }

        String type = isCollectTab ? DebtType.LEND.name() : DebtType.BORROW.name();
        currentLiveData = viewModel.getDebtsByType(type);

        debtObserver = debts -> {
            if (binding == null) return;
            adapter.submitDebtList(debts);
            boolean isEmpty = debts == null || debts.isEmpty();
            binding.tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.rvDebts.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        };

        currentLiveData.observe(getViewLifecycleOwner(), debtObserver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}