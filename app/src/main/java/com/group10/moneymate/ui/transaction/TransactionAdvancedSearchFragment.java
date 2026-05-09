package com.group10.moneymate.ui.transaction;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentAdvancedSearchBinding;
import com.group10.moneymate.ui.budget.BudgetWalletPickerFragment;
import com.group10.moneymate.utils.MoneyMateDatePickerHelper;
import com.group10.moneymate.utils.TimeWindowUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Stand-alone Advanced Filter screen.
 * Shares the same TransactionSearchViewModel (scoped to nav back-stack entry of
 * TransactionSearchFragment) so changes here update the search results
 * instantly.
 */
public class TransactionAdvancedSearchFragment extends Fragment {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private FragmentAdvancedSearchBinding binding;
    private TransactionSearchViewModel viewModel;

    // guard against recursive observer → setText → listener loops
    private boolean suppressAmountListeners = false;
    private boolean suppressModeListeners = false;

    private List<String> amountModeLabels;
    private List<String> timeModeLabels;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAdvancedSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Scope ViewModel to the *parent* TransactionSearchFragment back-stack entry
        // so both screens share the same filter state.
        NavController nav = Navigation.findNavController(view);
        NavBackStackEntry searchEntry = nav.getBackStackEntry(R.id.transactionSearchFragment);
        viewModel = new ViewModelProvider(searchEntry).get(TransactionSearchViewModel.class);

        setupModeDropdowns();
        setupAmountFormatting();
        bindActions();
        restoreAmountsFromFilter();
        observeFilter();
        observePickersResult();
    }

    private void restoreAmountsFromFilter() {
        TransactionSearchFilter f = viewModel.getFilter().getValue();
        if (f == null) return;

        suppressAmountListeners = true;
        if (f.amountMin > 0) {
            binding.etAmountMin.setText(String.format("%,d", (long) f.amountMin));
        }
        if (f.amountMax > 0) {
            binding.etAmountMax.setText(String.format("%,d", (long) f.amountMax));
        }
        if (f.amountValue > 0) {
            binding.etAmountValue.setText(String.format("%,d", (long) f.amountValue));
        }
        suppressAmountListeners = false;
    }

    // ─── Dropdowns ────────────────────────────────────────────────────────────

    private void setupModeDropdowns() {
        amountModeLabels = new ArrayList<>();
        amountModeLabels.add(getString(R.string.search_all));
        amountModeLabels.add(getString(R.string.search_amount_gt));
        amountModeLabels.add(getString(R.string.search_amount_lt));
        amountModeLabels.add(getString(R.string.search_amount_eq));
        amountModeLabels.add(getString(R.string.search_amount_between));

        ArrayAdapter<String> amountAdapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_list_item_1, amountModeLabels) {
            @NonNull
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = amountModeLabels;
                        results.count = amountModeLabels.size();
                        return results;
                    }
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
        binding.actvAmountMode.setAdapter(amountAdapter);
        binding.actvAmountMode.setOnItemClickListener((parent, view, position, id) -> {
            if (suppressModeListeners)
                return;
            applyAmountModeSelection(position, true);
        });

        timeModeLabels = new ArrayList<>();
        timeModeLabels.add(getString(R.string.search_all));
        timeModeLabels.add(getString(R.string.search_time_after));
        timeModeLabels.add(getString(R.string.search_time_before));
        timeModeLabels.add(getString(R.string.search_time_on));
        timeModeLabels.add(getString(R.string.search_time_between));

        ArrayAdapter<String> timeAdapter = new ArrayAdapter<String>(requireContext(),
                android.R.layout.simple_list_item_1, timeModeLabels) {
            @NonNull
            @Override
            public android.widget.Filter getFilter() {
                return new android.widget.Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        results.values = timeModeLabels;
                        results.count = timeModeLabels.size();
                        return results;
                    }
                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        notifyDataSetChanged();
                    }
                };
            }
        };
        binding.actvTimeMode.setAdapter(timeAdapter);
        binding.actvTimeMode.setOnItemClickListener((parent, view, position, id) -> {
            if (suppressModeListeners)
                return;
            applyTimeModeSelection(position, true);
        });
    }

    private void applyAmountModeSelection(int position, boolean updateFilter) {
        binding.layoutAmountSingle.setVisibility(
                (position >= 1 && position <= 3) ? View.VISIBLE : View.GONE);
        binding.layoutAmountRange.setVisibility(position == 4 ? View.VISIBLE : View.GONE);
        if (updateFilter) {
            TransactionSearchFilter f = viewModel.getFilter().getValue();
            if (f != null) {
                f.amountMode = TransactionSearchFilter.AmountMode.values()[position];
                viewModel.updateFilter(f);
            }
        }
    }

    private void applyTimeModeSelection(int position, boolean updateFilter) {
        binding.layoutTimeSingle.setVisibility(
                (position >= 1 && position <= 3) ? View.VISIBLE : View.GONE);
        binding.layoutTimeRange.setVisibility(position == 4 ? View.VISIBLE : View.GONE);
        if (updateFilter) {
            TransactionSearchFilter f = viewModel.getFilter().getValue();
            if (f != null) {
                f.timeMode = TransactionSearchFilter.TimeMode.values()[position];
                viewModel.updateFilter(f);
            }
        }
    }

    // ─── Amount formatting (thousand separator) ────────────────────────────────

    private void setupAmountFormatting() {
        addThousandSeparatorWatcher(binding.etAmountValue);
        addThousandSeparatorWatcher(binding.etAmountMin);
        addThousandSeparatorWatcher(binding.etAmountMax);
    }

    private void addThousandSeparatorWatcher(
            @NonNull com.google.android.material.textfield.TextInputEditText editText) {
        editText.addTextChangedListener(new TextWatcher() {
            boolean selfChange = false;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (selfChange || suppressAmountListeners)
                    return;
                selfChange = true;

                String raw = s.toString().replace(",", "").replace(".", "");
                if (!raw.isEmpty()) {
                    try {
                        long value = Long.parseLong(raw);
                        String formatted = String.format("%,d", value);
                        editText.setText(formatted);
                        editText.setSelection(formatted.length());
                    } catch (NumberFormatException ignored) {
                    }
                }
                selfChange = false;
            }
        });
    }

    /** Strip thousand-separator commas and parse to double. */
    private double parseAmount(String text) {
        if (text == null || text.trim().isEmpty())
            return 0d;
        try {
            return Double.parseDouble(text.replace(",", "").replace(".", ""));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    // ─── Actions ───────────────────────────────────────────────────────────────

    private void bindActions() {
        Runnable handleBack = () -> {
            clearFilter();
            Navigation.findNavController(binding.getRoot()).navigateUp();
        };

        binding.btnBackAdvanced.setOnClickListener(v -> handleBack.run());

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(),
                new androidx.activity.OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        handleBack.run();
                    }
                });

        binding.actvWallet.setOnClickListener(v -> {
            Bundle args = new Bundle();
            TransactionSearchFilter f = viewModel.getFilter().getValue();
            if (f != null && f.walletId != null) {
                args.putString("selectedWalletId", f.walletId);
            }
            Navigation.findNavController(v).navigate(R.id.budgetWalletPickerFragment, args);
        });

        binding.actvCategory.setOnClickListener(v -> {
            Bundle args = new Bundle();
            TransactionSearchFilter f = viewModel.getFilter().getValue();
            if (f != null && f.categoryId != null) {
                args.putString(TransactionCategoryPickerFragment.RESULT_CATEGORY_ID, f.categoryId);
            }
            Navigation.findNavController(v).navigate(R.id.transactionCategoryPickerFragment, args);
        });

        binding.btnTimeValue.setOnClickListener(v -> showDatePicker(date -> {
            TransactionSearchFilter f = viewModel.getFilter().getValue();
            if (f != null) {
                f.timeValue = TimeWindowUtils.startOfDayLocalDateUtc(date);
                viewModel.updateFilter(f);
                binding.btnTimeValue.setText(date.format(DATE_FMT));
            }
        }));

        binding.btnTimeStart.setOnClickListener(v -> showDatePicker(date -> {
            TransactionSearchFilter f = viewModel.getFilter().getValue();
            if (f != null) {
                f.timeStart = TimeWindowUtils.startOfDayLocalDateUtc(date);
                viewModel.updateFilter(f);
                binding.btnTimeStart.setText(date.format(DATE_FMT));
            }
        }));

        binding.btnTimeEnd.setOnClickListener(v -> showDatePicker(date -> {
            TransactionSearchFilter f = viewModel.getFilter().getValue();
            if (f != null) {
                f.timeEnd = TimeWindowUtils.endOfDayLocalDateUtc(date);
                viewModel.updateFilter(f);
                binding.btnTimeEnd.setText(date.format(DATE_FMT));
            }
        }));

        binding.btnApplyFilter.setOnClickListener(v -> applyAndClose());
        binding.btnClearFilter.setOnClickListener(v -> clearFilter());
    }

    private void saveCurrentInputsToFilter() {
        TransactionSearchFilter f = viewModel.getFilter().getValue();
        if (f != null) {
            suppressAmountListeners = true;
            if (f.amountMode == TransactionSearchFilter.AmountMode.BETWEEN) {
                f.amountMin = parseAmount(binding.etAmountMin.getText() != null
                        ? binding.etAmountMin.getText().toString()
                        : "");
                f.amountMax = parseAmount(binding.etAmountMax.getText() != null
                        ? binding.etAmountMax.getText().toString()
                        : "");
            } else if (f.amountMode != TransactionSearchFilter.AmountMode.ALL) {
                f.amountValue = parseAmount(binding.etAmountValue.getText() != null
                        ? binding.etAmountValue.getText().toString()
                        : "");
            }
            suppressAmountListeners = false;
            viewModel.updateFilter(f);
        }
    }

    private void applyAndClose() {
        saveCurrentInputsToFilter();
        Navigation.findNavController(binding.getRoot()).navigateUp();
    }

    private void clearFilter() {
        suppressAmountListeners = true;
        binding.etAmountValue.setText("");
        binding.etAmountMin.setText("");
        binding.etAmountMax.setText("");
        binding.btnTimeValue.setText(R.string.search_select_date);
        binding.btnTimeStart.setText(R.string.search_date_from);
        binding.btnTimeEnd.setText(R.string.search_date_to);
        suppressModeListeners = true;
        if (amountModeLabels != null && !amountModeLabels.isEmpty()) {
            binding.actvAmountMode.setText(amountModeLabels.get(0), false);
        }
        if (timeModeLabels != null && !timeModeLabels.isEmpty()) {
            binding.actvTimeMode.setText(timeModeLabels.get(0), false);
        }
        applyAmountModeSelection(0, false);
        applyTimeModeSelection(0, false);
        suppressModeListeners = false;
        suppressAmountListeners = false;

        // Keep existing keyword, reset everything else
        TransactionSearchFilter current = viewModel.getFilter().getValue();
        String existingKeyword = current != null ? current.keyword : null;
        TransactionSearchFilter newFilter = new TransactionSearchFilter();
        newFilter.keyword = existingKeyword;
        binding.actvWallet.setText(getString(R.string.search_all_wallets), false);
        binding.actvCategory.setText(getString(R.string.search_all_categories), false);
        viewModel.updateFilter(newFilter);
    }

    // ─── Observe filter to sync UI when returning to this screen ─────────────

    private void observeFilter() {
        viewModel.getFilter().observe(getViewLifecycleOwner(), f -> {
            if (f == null)
                return;

            // Wallet / Category labels
            binding.actvWallet.setText(
                    f.walletLabel != null ? f.walletLabel : getString(R.string.search_all_wallets), false);
            binding.actvCategory.setText(
                    f.categoryLabel != null ? f.categoryLabel : getString(R.string.search_all_categories), false);

            // Amount mode dropdown (prevent looping)
            int amountPos = f.amountMode.ordinal();
            String amountLabel = amountModeLabels.get(amountPos);
            suppressModeListeners = true;
            if (!amountLabel.equals(binding.actvAmountMode.getText().toString())) {
                binding.actvAmountMode.setText(amountLabel, false);
            }
            applyAmountModeSelection(amountPos, false);
            suppressModeListeners = false;

            // Time mode dropdown
            int timePos = f.timeMode.ordinal();
            String timeLabel = timeModeLabels.get(timePos);
            suppressModeListeners = true;
            if (!timeLabel.equals(binding.actvTimeMode.getText().toString())) {
                binding.actvTimeMode.setText(timeLabel, false);
            }
            applyTimeModeSelection(timePos, false);
            suppressModeListeners = false;

            // Date buttons
            if (f.timeValue > 0) {
                LocalDate d = TimeWindowUtils.toDeviceLocalDate(f.timeValue);
                binding.btnTimeValue.setText(d.format(DATE_FMT));
            }
            if (f.timeStart > 0) {
                LocalDate d = TimeWindowUtils.toDeviceLocalDate(f.timeStart);
                binding.btnTimeStart.setText(d.format(DATE_FMT));
            }
            if (f.timeEnd > 0) {
                LocalDate d = TimeWindowUtils.toDeviceLocalDate(f.timeEnd);
                binding.btnTimeEnd.setText(d.format(DATE_FMT));
            }
        });
    }

    // ─── Observe result from pickers ──────────────────────────────────────────

    private void observePickersResult() {
        NavController nav = Navigation.findNavController(binding.getRoot());
        NavBackStackEntry currentEntry = nav.getCurrentBackStackEntry();
        if (currentEntry == null)
            return;

        // Category picker sets RESULT_CATEGORY_ID on the previous back stack entry
        // (this fragment).
        currentEntry.getSavedStateHandle()
                .<String>getLiveData(TransactionCategoryPickerFragment.RESULT_CATEGORY_ID)
                .observe(getViewLifecycleOwner(), categoryId -> {
                    if (categoryId == null)
                        return;

                    // Ask ViewModel to resolve + store category label
                    viewModel.setCategoryId(categoryId);

                    // Clear the result to avoid re-triggering on View recreation
                    currentEntry.getSavedStateHandle()
                            .set(TransactionCategoryPickerFragment.RESULT_CATEGORY_ID, null);
                });

        // Once the ViewModel has resolved the label (from categoryMap), update the
        // button
        viewModel.getResolvedCategoryLabel().observe(getViewLifecycleOwner(), label -> {
            if (label != null) {
                binding.actvCategory.setText(label, false);
            } else {
                // null means no category selected or just cleared
                TransactionSearchFilter f = viewModel.getFilter().getValue();
                if (f == null || f.categoryId == null) {
                    binding.actvCategory.setText(getString(R.string.search_all_categories), false);
                }
            }
        });

        // Wallet picker result
        currentEntry.getSavedStateHandle()
                .<String>getLiveData(BudgetWalletPickerFragment.RESULT_SELECTED_WALLET_LABEL)
                .observe(getViewLifecycleOwner(), walletLabel -> {
                    if (walletLabel == null)
                        return;
                    String walletId = currentEntry.getSavedStateHandle().get(BudgetWalletPickerFragment.RESULT_SELECTED_WALLET_ID);
                    TransactionSearchFilter f = viewModel.getFilter().getValue();
                    if (f != null) {
                        f.walletId = walletId;
                        f.walletLabel = walletLabel;
                        viewModel.updateFilter(f);
                    }
                    currentEntry.getSavedStateHandle().set(BudgetWalletPickerFragment.RESULT_SELECTED_WALLET_ID, null);
                    currentEntry.getSavedStateHandle().set(BudgetWalletPickerFragment.RESULT_SELECTED_WALLET_LABEL, null);
                });
    }

    private void showDatePicker(DateSelectedCallback callback) {
        MoneyMateDatePickerHelper.showSingleDatePicker(
                this,
                LocalDate.now(),
                "adv_search_date_picker",
                callback::onDateSelected);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private interface DateSelectedCallback {
        void onDateSelected(@NonNull LocalDate date);
    }
}
