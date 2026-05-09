package com.group10.moneymate.ui.transaction;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import androidx.activity.OnBackPressedCallback;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentTransactionSearchBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

public class TransactionSearchFragment extends Fragment {

    private FragmentTransactionSearchBinding binding;
    private TransactionSearchViewModel viewModel;
    private TransactionTimeGroupAdapter groupAdapter;

    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTransactionSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Scope ViewModel to this fragment's own back-stack entry so that
        // TransactionAdvancedSearchFragment can retrieve the SAME instance.
        NavController nav = Navigation.findNavController(view);
        androidx.navigation.NavBackStackEntry selfEntry =
                nav.getBackStackEntry(R.id.transactionSearchFragment);
        viewModel = new ViewModelProvider(selfEntry).get(TransactionSearchViewModel.class);

        setupRecyclerView();
        bindActions();
        observeViewModel();

        if (savedInstanceState == null && !isAdvancedFilterActive(viewModel.getFilter().getValue())) {
            showKeyboard();
        }
        
        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isAdvancedFilterActive(viewModel.getFilter().getValue())) {
                    Navigation.findNavController(binding.getRoot())
                            .navigate(R.id.action_transactionSearchFragment_to_advancedSearchFragment);
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private boolean isAdvancedFilterActive(TransactionSearchFilter f) {
        if (f == null) return false;
        return f.amountMode != TransactionSearchFilter.AmountMode.ALL ||
               f.timeMode != TransactionSearchFilter.TimeMode.ALL ||
               f.walletId != null ||
               f.categoryId != null;
    }

    private void setupRecyclerView() {
        groupAdapter = new TransactionTimeGroupAdapter();
        groupAdapter.setOnTransactionClickListener(transaction -> {
            Bundle args = new Bundle();
            args.putString("transactionId", transaction.getId());
            Navigation.findNavController(binding.getRoot())
                    .navigate(R.id.action_transactionSearchFragment_to_transactionDetailFragment, args);
        });
        binding.rvSearchResults.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvSearchResults.setAdapter(groupAdapter);
    }

    private void bindActions() {
        binding.btnBack.setOnClickListener(v -> {
            if (isAdvancedFilterActive(viewModel.getFilter().getValue())) {
                Navigation.findNavController(v)
                        .navigate(R.id.action_transactionSearchFragment_to_advancedSearchFragment);
            } else {
                Navigation.findNavController(v).navigateUp();
            }
        });

        binding.btnAdvancedSearch.setOnClickListener(v ->
                Navigation.findNavController(v)
                        .navigate(R.id.action_transactionSearchFragment_to_advancedSearchFragment));

        binding.etSearchKeyword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = () -> {
                    TransactionSearchFilter f = viewModel.getFilter().getValue();
                    if (f != null) {
                        f.keyword = s.toString();
                        viewModel.updateFilter(f);
                    }
                };
                searchHandler.postDelayed(searchRunnable, 300);
            }
        });

        binding.etSearchKeyword.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard();
                return true;
            }
            return false;
        });
    }

    private void observeViewModel() {
        viewModel.getGroupedResults().observe(getViewLifecycleOwner(), items -> {
            groupAdapter.submitList(items);
            boolean hasResults = items != null && !items.isEmpty();
            binding.rvSearchResults.setVisibility(hasResults ? View.VISIBLE : View.GONE);
            binding.tvEmptyResults.setVisibility(hasResults ? View.GONE : View.VISIBLE);
        });

        viewModel.getSummary().observe(getViewLifecycleOwner(), summary -> {
            boolean hasData = summary.count > 0;
            binding.layoutSummaryStats.setVisibility(hasData ? View.VISIBLE : View.GONE);

            if (hasData) {
                binding.tvSummaryCount.setText(
                        getString(R.string.search_transaction_count, summary.count));
                binding.tvSummaryIncome.setText(CurrencyFormatter.format(summary.totalIncome, "VND"));
                binding.tvSummaryExpense.setText(CurrencyFormatter.format(summary.totalExpense, "VND"));
                binding.tvSummaryNet.setText(CurrencyFormatter.format(summary.net, "VND"));
            } else {
                binding.tvSummaryCount.setText(R.string.search_empty_hint);
            }
        });

        viewModel.getFilter().observe(getViewLifecycleOwner(), f -> {
            boolean isAdvanced = isAdvancedFilterActive(f);
            binding.etSearchKeyword.setVisibility(isAdvanced ? View.GONE : View.VISIBLE);
            binding.btnAdvancedSearch.setVisibility(isAdvanced ? View.GONE : View.VISIBLE);
            binding.tvAdvancedFilterTitle.setVisibility(isAdvanced ? View.VISIBLE : View.GONE);
            
            if (isAdvanced) {
                hideKeyboard();
            }
        });
    }

    private void showKeyboard() {
        binding.etSearchKeyword.requestFocus();
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(binding.etSearchKeyword, InputMethodManager.SHOW_IMPLICIT);
        }
    }

    private void hideKeyboard() {
        View focus = requireView().findFocus();
        if (focus == null) focus = binding.getRoot();
        InputMethodManager imm = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
        binding = null;
    }
}
