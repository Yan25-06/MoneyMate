package com.group10.moneymate.ui.transaction;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.snackbar.Snackbar;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.DialogStatisticsCustomRangeBinding;
import com.group10.moneymate.databinding.FragmentTransactionListBinding;
import com.group10.moneymate.databinding.SheetStatisticsPeriodFilterBinding;
import com.group10.moneymate.ui.statistics.StatisticsViewModel;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.MoneyMateDatePickerHelper;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TransactionListFragment extends Fragment {

    private static final String RESULT_SELECTED_WALLET_ID = "result_selected_wallet_id";
    private static final String RESULT_SELECTED_WALLET_LABEL = "result_selected_wallet_label";

    private FragmentTransactionListBinding binding;
    private TransactionViewModel viewModel;
    private TransactionListFragmentArgs navArgs;
    private StatisticsViewModel.FilterState currentFilterState;
    private final List<TransactionEntity> allTransactions = new ArrayList<>();
    private final Map<String, WalletEntity> walletMap = new HashMap<>();
    private final Map<String, CategoryEntity> categoryMap = new HashMap<>();
    private List<AggregateBudgetFilter> aggregateBudgetFilters = new ArrayList<>();
    @Nullable private String forcedCategoryId;
    @Nullable private String forcedTransactionType;
    @Nullable private String selectedWalletLabel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTransactionListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        navArgs = TransactionListFragmentArgs.fromBundle(getArguments() != null ? getArguments() : new Bundle());
        aggregateBudgetFilters = parseAggregateBudgetFilters(navArgs.getBudgetAggregateFilters());
        currentFilterState = buildInitialFilterState(navArgs);
        forcedCategoryId = resolveForcedCategoryId(navArgs);
        forcedTransactionType = resolveForcedTransactionType(navArgs);

        binding.rvTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvTransactions.setAdapter(new ConcatAdapter());

        applyWindowInsets();
        configureHeader();
        bindActions();
        observeWalletPickerResult();
        observeReferenceData();
        observeTransactions();
    }

    private void configureHeader() {
        binding.statisticsHeader.btnHeaderBack.setVisibility(View.GONE);
        binding.statisticsHeader.tvHeaderSummaryLabel.setText(R.string.total_balance);
        binding.statisticsHeader.tvHeaderTotalAmount.setText(R.string.default_currency_zero);
        binding.statisticsHeader.btnWalletSelector.setText(R.string.statistics_wallet_selector_all);
        renderHeaderTabs(currentFilterState);
        binding.btnViewPeriodReport.setOnClickListener(v -> openPeriodReport());
    }

    private void bindActions() {
        binding.statisticsHeader.btnWalletSelector.setOnClickListener(v -> openWalletPicker());
        binding.statisticsHeader.btnDateFilter.setOnClickListener(v -> showDateRangePicker());
        binding.statisticsHeader.btnPreviousPeriod.setOnClickListener(v -> shiftCurrentPeriod(-1));
        binding.statisticsHeader.btnNextPeriod.setOnClickListener(v -> shiftCurrentPeriod(1));
        binding.statisticsHeader.tvPeriodPrevious.setOnClickListener(v -> shiftCurrentPeriod(-2));
        binding.statisticsHeader.tvPeriodCurrent.setOnClickListener(v -> shiftCurrentPeriod(-1));
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

    private void observeTransactions() {
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), transactions -> {
            allTransactions.clear();
            if (transactions != null) {
                allTransactions.addAll(transactions);
            }
            renderScreen();
        });
    }

    private void mergeCategories(@Nullable List<CategoryEntity> categories) {
        if (categories != null) {
            for (CategoryEntity category : categories) {
                categoryMap.put(category.getId(), category);
            }
        }
        renderScreen();
    }

    private void renderScreen() {
        renderWalletSelector();
        renderBalance();
        List<TransactionEntity> filtered = filterTransactions();
        renderSummary(filtered);
        renderSections(filtered);
    }

    private void renderWalletSelector() {
        if (selectedWalletLabel != null && !selectedWalletLabel.trim().isEmpty()) {
            binding.statisticsHeader.btnWalletSelector.setText(selectedWalletLabel);
            return;
        }
        String walletId = currentFilterState.getWalletId();
        if (walletId == null) {
            binding.statisticsHeader.btnWalletSelector.setText(R.string.statistics_wallet_selector_all);
            return;
        }
        WalletEntity wallet = walletMap.get(walletId);
        binding.statisticsHeader.btnWalletSelector.setText(
                wallet != null ? wallet.getName() : getString(R.string.statistics_wallet_selector_all)
        );
    }

    private void renderBalance() {
        double balance = 0d;
        if (currentFilterState.getWalletId() == null) {
            for (WalletEntity wallet : walletMap.values()) {
                balance += wallet.getBalance();
            }
        } else {
            WalletEntity wallet = walletMap.get(currentFilterState.getWalletId());
            if (wallet != null) {
                balance = wallet.getBalance();
            }
        }
        binding.statisticsHeader.tvHeaderTotalAmount.setText(CurrencyFormatter.format(balance, "VND"));
    }

    @NonNull
    private List<TransactionEntity> filterTransactions() {
        List<TransactionEntity> matches = new ArrayList<>();
        for (TransactionEntity transaction : allTransactions) {
            if (!matchesDate(transaction)) {
                continue;
            }
            if (currentFilterState.getWalletId() != null
                    && !currentFilterState.getWalletId().equals(transaction.getWalletId())) {
                continue;
            }
            if (forcedTransactionType != null && !forcedTransactionType.equals(transaction.getType())) {
                continue;
            }
            if (forcedCategoryId != null && !forcedCategoryId.equals(transaction.getCategoryId())) {
                continue;
            }
            if (!aggregateBudgetFilters.isEmpty() && !matchesAnyAggregateFilter(transaction)) {
                continue;
            }
            matches.add(transaction);
        }
        return matches;
    }

    private boolean matchesDate(@NonNull TransactionEntity transaction) {
        if (currentFilterState.getPeriodType() == StatisticsViewModel.PeriodType.ALL) {
            return true;
        }
        long timestamp = transaction.getTimestamp();
        return timestamp >= currentFilterState.getStartDate() && timestamp <= currentFilterState.getEndDate();
    }

    private boolean matchesAnyAggregateFilter(@NonNull TransactionEntity transaction) {
        for (AggregateBudgetFilter filter : aggregateBudgetFilters) {
            if (filter.matches(transaction)) {
                return true;
            }
        }
        return false;
    }

    private void renderSummary(@NonNull List<TransactionEntity> filtered) {
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
        binding.tvNetValue.setText(formatSignedAmount(net));
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

        Map<String, LedgerSection> grouped = new LinkedHashMap<>();
        for (TransactionEntity transaction : filtered) {
            SectionMeta meta = resolveSectionMeta(transaction);
            LedgerSection section = grouped.get(meta.key);
            if (section == null) {
                section = new LedgerSection(meta);
                grouped.put(meta.key, section);
            }
            section.add(transaction);
        }

        List<LedgerSection> sections = new ArrayList<>(grouped.values());
        sections.sort(Comparator.comparingLong(LedgerSection::getLatestTimestamp).reversed());

        List<RecyclerView.Adapter<?>> adapters = new ArrayList<>();
        for (LedgerSection section : sections) {
            section.transactions.sort((left, right) -> {
                String leftWallet = resolveWalletName(left.getWalletId());
                String rightWallet = resolveWalletName(right.getWalletId());
                int walletCompare = leftWallet.compareToIgnoreCase(rightWallet);
                if (walletCompare != 0) {
                    return walletCompare;
                }
                return Long.compare(right.getTimestamp(), left.getTimestamp());
            });
            adapters.add(new LedgerSectionHeaderAdapter(section.toHeaderItem()));
            TransactionAdapter adapter = new TransactionAdapter();
            adapter.setWalletPresentationMap(buildWalletPresentationMap());
            adapter.setOnTransactionClickListener(this::openTransactionDetail);
            adapter.submitList(new ArrayList<>(section.transactions));
            adapters.add(adapter);
        }
        binding.rvTransactions.setAdapter(new ConcatAdapter(adapters));
    }

    @NonNull
    private Map<String, TransactionAdapter.WalletPresentation> buildWalletPresentationMap() {
        Map<String, TransactionAdapter.WalletPresentation> map = new HashMap<>();
        for (WalletEntity wallet : walletMap.values()) {
            int accent = parseColorOrDefault(
                    wallet.getColorHex(),
                    ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary)
            );
            map.put(wallet.getId(), new TransactionAdapter.WalletPresentation(wallet.getType(), accent));
        }
        return map;
    }

    private void openTransactionDetail(@NonNull TransactionEntity transaction) {
        TransactionListFragmentDirections.ActionTransactionListFragmentToTransactionDetailFragment action =
                TransactionListFragmentDirections.actionTransactionListFragmentToTransactionDetailFragment();
        action.setTransactionId(transaction.getId());
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void openPeriodReport() {
        TransactionListFragmentDirections.ActionTransactionListFragmentToStatisticsFragment action =
                TransactionListFragmentDirections.actionTransactionListFragmentToStatisticsFragment();
        action.setFilterWalletId(currentFilterState.getWalletId());
        action.setFilterWalletLabel(binding.statisticsHeader.btnWalletSelector.getText() != null
                ? binding.statisticsHeader.btnWalletSelector.getText().toString()
                : null);
        action.setFilterStartDate(currentFilterState.getStartDate());
        action.setFilterEndDate(currentFilterState.getEndDate());
        action.setFilterPeriodType(currentFilterState.getPeriodType().name());
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void openWalletPicker() {
        TransactionListFragmentDirections.ActionTransactionListFragmentToBudgetWalletPickerFragment action =
                TransactionListFragmentDirections.actionTransactionListFragmentToBudgetWalletPickerFragment();
        action.setSelectedWalletId(currentFilterState.getWalletId());
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void observeWalletPickerResult() {
        NavController navController = Navigation.findNavController(binding.getRoot());
        NavBackStackEntry currentBackStackEntry = navController.getCurrentBackStackEntry();
        if (currentBackStackEntry == null) {
            return;
        }
        currentBackStackEntry.getSavedStateHandle()
                .<String>getLiveData(RESULT_SELECTED_WALLET_ID)
                .observe(getViewLifecycleOwner(), walletId -> {
                    selectedWalletLabel = currentBackStackEntry.getSavedStateHandle().get(RESULT_SELECTED_WALLET_LABEL);
                    currentFilterState = currentFilterState.withWalletId(walletId);
                    renderScreen();
                    currentBackStackEntry.getSavedStateHandle().remove(RESULT_SELECTED_WALLET_ID);
                    currentBackStackEntry.getSavedStateHandle().remove(RESULT_SELECTED_WALLET_LABEL);
                });
    }

    private void showDateRangePicker() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        SheetStatisticsPeriodFilterBinding sheetBinding = SheetStatisticsPeriodFilterBinding.inflate(getLayoutInflater());
        dialog.setContentView(sheetBinding.getRoot());
        updatePeriodSheetSelection(sheetBinding, currentFilterState.getPeriodType());
        bindPeriodRow(sheetBinding.rowDay, dialog, () -> updatePresetPeriod(StatisticsViewModel.PeriodType.DAY));
        bindPeriodRow(sheetBinding.rowWeek, dialog, () -> updatePresetPeriod(StatisticsViewModel.PeriodType.WEEK));
        bindPeriodRow(sheetBinding.rowMonth, dialog, () -> updatePresetPeriod(StatisticsViewModel.PeriodType.MONTH));
        bindPeriodRow(sheetBinding.rowQuarter, dialog, () -> updatePresetPeriod(StatisticsViewModel.PeriodType.QUARTER));
        bindPeriodRow(sheetBinding.rowYear, dialog, () -> updatePresetPeriod(StatisticsViewModel.PeriodType.YEAR));
        bindPeriodRow(sheetBinding.rowAll, dialog, () -> updatePresetPeriod(StatisticsViewModel.PeriodType.ALL));
        sheetBinding.rowCustom.setOnClickListener(v -> {
            dialog.dismiss();
            showCustomRangeDialog();
        });
        dialog.show();
    }

    private void bindPeriodRow(@NonNull View row, @NonNull Dialog dialog, @NonNull Runnable action) {
        row.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
    }

    private void updatePresetPeriod(@NonNull StatisticsViewModel.PeriodType periodType) {
        currentFilterState = StatisticsViewModel.FilterState.createForPeriodType(periodType, currentFilterState.getWalletId());
        renderHeaderTabs(currentFilterState);
        renderScreen();
    }

    private void shiftCurrentPeriod(int direction) {
        if (direction > 0 && !canMoveForward(currentFilterState)) {
            return;
        }
        currentFilterState = currentFilterState.shift(direction);
        renderHeaderTabs(currentFilterState);
        renderScreen();
    }

    private void showCustomRangeDialog() {
        Dialog dialog = new Dialog(requireContext());
        DialogStatisticsCustomRangeBinding dialogBinding = DialogStatisticsCustomRangeBinding.inflate(getLayoutInflater());
        dialog.setContentView(dialogBinding.getRoot());
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        final LocalDate[] startDate = {toLocalDate(currentFilterState.getStartDate())};
        final LocalDate[] endDate = {toLocalDate(currentFilterState.getEndDate())};
        renderDateButton(dialogBinding.btnStartDate, startDate[0]);
        renderDateButton(dialogBinding.btnEndDate, endDate[0]);
        dialogBinding.btnStartDate.setOnClickListener(v -> showSingleDatePicker(startDate[0], date -> {
            startDate[0] = date;
            renderDateButton(dialogBinding.btnStartDate, date);
        }));
        dialogBinding.btnEndDate.setOnClickListener(v -> showSingleDatePicker(endDate[0], date -> {
            endDate[0] = date;
            renderDateButton(dialogBinding.btnEndDate, date);
        }));
        dialogBinding.btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialogBinding.btnApply.setOnClickListener(v -> {
            if (endDate[0].isBefore(startDate[0])) {
                Snackbar.make(binding.getRoot(), R.string.statistics_custom_range_invalid, Snackbar.LENGTH_SHORT).show();
                return;
            }
            currentFilterState = StatisticsViewModel.FilterState.createRange(
                    currentFilterState.getWalletId(),
                    startDate[0].atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                    endDate[0].plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1L
            );
            dialog.dismiss();
            renderHeaderTabs(currentFilterState);
            renderScreen();
        });
        dialog.show();
    }

    private void showSingleDatePicker(@NonNull LocalDate initialDate, @NonNull DateSelectedCallback callback) {
        MoneyMateDatePickerHelper.showSingleDatePicker(
                this,
                initialDate,
                "transaction_list_single_date",
                callback::onDateSelected
        );
    }

    private void renderDateButton(@NonNull TextView textView, @NonNull LocalDate date) {
        if (date.equals(LocalDate.now())) {
            textView.setText(R.string.statistics_today);
            return;
        }
        textView.setText(String.format(Locale.getDefault(), "%02d/%02d/%d", date.getDayOfMonth(), date.getMonthValue(), date.getYear()));
    }

    private void renderHeaderTabs(@NonNull StatisticsViewModel.FilterState filterState) {
        if (filterState.getPeriodType() == StatisticsViewModel.PeriodType.ALL) {
            binding.statisticsHeader.tvPeriodPrevious.setVisibility(View.GONE);
            binding.statisticsHeader.tvPeriodCurrent.setVisibility(View.GONE);
            binding.statisticsHeader.tvPeriodNext.setVisibility(View.VISIBLE);
            binding.statisticsHeader.tvPeriodNext.setText(filterState.getDisplayLabel());
            applyTabStyle(binding.statisticsHeader.tvPeriodNext, true);
            binding.statisticsHeader.btnPreviousPeriod.setVisibility(View.GONE);
            binding.statisticsHeader.btnNextPeriod.setVisibility(View.GONE);
            return;
        }
        binding.statisticsHeader.tvPeriodPrevious.setVisibility(View.VISIBLE);
        binding.statisticsHeader.tvPeriodCurrent.setVisibility(View.VISIBLE);
        binding.statisticsHeader.tvPeriodNext.setVisibility(View.VISIBLE);
        binding.statisticsHeader.btnPreviousPeriod.setVisibility(View.VISIBLE);
        binding.statisticsHeader.btnNextPeriod.setVisibility(View.VISIBLE);
        binding.statisticsHeader.tvPeriodPrevious.setText(filterState.shift(-2).getDisplayLabel());
        binding.statisticsHeader.tvPeriodCurrent.setText(filterState.shift(-1).getDisplayLabel());
        binding.statisticsHeader.tvPeriodNext.setText(filterState.getDisplayLabel());
        applyTabStyle(binding.statisticsHeader.tvPeriodPrevious, false);
        applyTabStyle(binding.statisticsHeader.tvPeriodCurrent, false);
        applyTabStyle(binding.statisticsHeader.tvPeriodNext, true);
        boolean canMoveForward = canMoveForward(filterState);
        binding.statisticsHeader.btnNextPeriod.setEnabled(canMoveForward);
        binding.statisticsHeader.btnNextPeriod.setAlpha(canMoveForward ? 1f : 0.35f);
    }

    private void applyTabStyle(@NonNull TextView tabView, boolean selected) {
        tabView.setBackgroundResource(selected ? R.drawable.bg_statistics_period_nav_item_selected : R.drawable.bg_statistics_period_nav_item);
        tabView.setTextColor(ContextCompat.getColor(requireContext(), selected ? R.color.statistics_text_primary : R.color.statistics_text_muted));
    }

    private boolean canMoveForward(@NonNull StatisticsViewModel.FilterState filterState) {
        StatisticsViewModel.FilterState latest = StatisticsViewModel.FilterState.createForPeriodType(filterState.getPeriodType(), filterState.getWalletId());
        if (filterState.getPeriodType() == StatisticsViewModel.PeriodType.CUSTOM) {
            return filterState.getEndDate() < endOfToday();
        }
        return filterState.getStartDate() < latest.getStartDate() || filterState.getEndDate() < latest.getEndDate();
    }

    private void updatePeriodSheetSelection(@NonNull SheetStatisticsPeriodFilterBinding sheetBinding,
                                            @NonNull StatisticsViewModel.PeriodType periodType) {
        sheetBinding.ivCheckDay.setVisibility(periodType == StatisticsViewModel.PeriodType.DAY ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckWeek.setVisibility(periodType == StatisticsViewModel.PeriodType.WEEK ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckMonth.setVisibility(periodType == StatisticsViewModel.PeriodType.MONTH ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckQuarter.setVisibility(periodType == StatisticsViewModel.PeriodType.QUARTER ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckYear.setVisibility(periodType == StatisticsViewModel.PeriodType.YEAR ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckAll.setVisibility(periodType == StatisticsViewModel.PeriodType.ALL ? View.VISIBLE : View.GONE);
        sheetBinding.ivCheckCustom.setVisibility(periodType == StatisticsViewModel.PeriodType.CUSTOM ? View.VISIBLE : View.GONE);
    }

    @NonNull
    private StatisticsViewModel.FilterState buildInitialFilterState(@NonNull TransactionListFragmentArgs args) {
        if (shouldUseStatisticsFilter(args)) {
            return createFilterState(args.getStatisticsWalletId(), args.getStatisticsStartDate(), args.getStatisticsEndDate());
        }
        if (args.getBudgetStartDate() > 0L && args.getBudgetEndDate() > 0L) {
            return createFilterState(args.getBudgetWalletId(), args.getBudgetStartDate(), args.getBudgetEndDate());
        }
        return StatisticsViewModel.FilterState.createCurrentMonth(null);
    }

    @NonNull
    private StatisticsViewModel.FilterState createFilterState(@Nullable String walletId, long startDate, long endDate) {
        if (startDate <= 0L && endDate == Long.MAX_VALUE) {
            return StatisticsViewModel.FilterState.createAll(walletId);
        }
        if (startDate <= 0L || endDate <= 0L) {
            return StatisticsViewModel.FilterState.createCurrentMonth(walletId);
        }
        LocalDate start = toLocalDate(startDate);
        LocalDate end = toLocalDate(endDate);
        long daySpan = ChronoUnit.DAYS.between(start, end) + 1L;
        if (daySpan == 1L) return StatisticsViewModel.FilterState.createDay(walletId, start);
        if (daySpan == 7L && start.plusDays(6L).equals(end)) return StatisticsViewModel.FilterState.createWeek(walletId, start);
        if (start.getDayOfMonth() == 1 && end.getDayOfMonth() == end.lengthOfMonth()
                && start.getMonthValue() == end.getMonthValue() && start.getYear() == end.getYear()) {
            return StatisticsViewModel.FilterState.createMonth(walletId, start);
        }
        if (start.getDayOfMonth() == 1 && start.getMonthValue() == 1
                && end.getMonthValue() == 12 && end.getDayOfMonth() == 31 && start.getYear() == end.getYear()) {
            return StatisticsViewModel.FilterState.createYear(walletId, start);
        }
        return StatisticsViewModel.FilterState.createRange(walletId, startDate, endDate);
    }

    @Nullable
    private String resolveForcedCategoryId(@NonNull TransactionListFragmentArgs args) {
        return args.getStatisticsCategoryId() != null ? args.getStatisticsCategoryId() : args.getBudgetCategoryId();
    }

    @Nullable
    private String resolveForcedTransactionType(@NonNull TransactionListFragmentArgs args) {
        if (args.getStatisticsTransactionType() != null) {
            return args.getStatisticsTransactionType();
        }
        return (args.getBudgetStartDate() > 0L && args.getBudgetEndDate() > 0L) ? "EXPENSE" : null;
    }

    private boolean shouldUseStatisticsFilter(@NonNull TransactionListFragmentArgs args) {
        return args.getStatisticsStartDate() > 0L && args.getStatisticsEndDate() > 0L;
    }

    @NonNull
    private List<AggregateBudgetFilter> parseAggregateBudgetFilters(@Nullable String filterSpec) {
        List<AggregateBudgetFilter> filters = new ArrayList<>();
        if (filterSpec == null || filterSpec.trim().isEmpty()) {
            return filters;
        }
        String[] segments = filterSpec.split(";");
        for (String segment : segments) {
            String[] parts = segment.split("\\|", -1);
            if (parts.length != 4) continue;
            try {
                filters.add(new AggregateBudgetFilter(parts[0], parts[1].isEmpty() ? null : parts[1], Long.parseLong(parts[2]), Long.parseLong(parts[3])));
            } catch (NumberFormatException ignored) {
            }
        }
        return filters;
    }

    private int resolveIconRes(@Nullable String iconResId) {
        if (iconResId == null || iconResId.trim().isEmpty()) return R.drawable.ic_category_other;
        int resolved = requireContext().getResources().getIdentifier(iconResId, "drawable", requireContext().getPackageName());
        return resolved != 0 ? resolved : R.drawable.ic_category_other;
    }

    @ColorInt
    private int parseColorOrDefault(@Nullable String colorHex, @ColorInt int defaultColor) {
        if (colorHex == null || colorHex.trim().isEmpty()) return defaultColor;
        try {
            return Color.parseColor(colorHex);
        } catch (IllegalArgumentException ignored) {
            return defaultColor;
        }
    }

    @NonNull
    private SectionMeta resolveSectionMeta(@NonNull TransactionEntity transaction) {
        if ("TRANSFER".equals(transaction.getType())) {
            int accent = ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary);
            return new SectionMeta("transfer", getString(R.string.ledger_section_transfer), R.drawable.outline_payments_24, accent, applyAlpha(accent, 0.14f));
        }
        CategoryEntity category = transaction.getCategoryId() != null ? categoryMap.get(transaction.getCategoryId()) : null;
        int fallback = ContextCompat.getColor(requireContext(), "INCOME".equals(transaction.getType()) ? R.color.transfer_blue : R.color.expense_red);
        int accent = category != null ? parseColorOrDefault(category.getColorHex(), fallback) : fallback;
        int iconRes = category != null ? resolveIconRes(category.getIconResId()) : ("INCOME".equals(transaction.getType()) ? R.drawable.outline_attach_money_24 : R.drawable.ic_spending);
        String title = category != null ? category.getName() : getString(R.string.ledger_section_unknown);
        return new SectionMeta(transaction.getCategoryId() != null ? transaction.getCategoryId() : transaction.getType(), title, iconRes, accent, applyAlpha(accent, 0.14f));
    }

    private int applyAlpha(@ColorInt int color, float alphaFraction) {
        int alpha = Math.min(255, Math.max(0, Math.round(alphaFraction * 255f)));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    @NonNull
    private Map<String, String> buildWalletNameMap() {
        Map<String, String> walletNames = new HashMap<>();
        for (Map.Entry<String, WalletEntity> entry : walletMap.entrySet()) {
            WalletEntity wallet = entry.getValue();
            if (wallet != null) {
                walletNames.put(entry.getKey(), wallet.getName());
            }
        }
        return walletNames;
    }

    @NonNull
    private String resolveWalletName(@Nullable String walletId) {
        if (walletId == null) {
            return "";
        }
        WalletEntity wallet = walletMap.get(walletId);
        return wallet != null ? wallet.getName() : "";
    }

    @NonNull
    private String formatSignedAmount(double amount) {
        if (amount < 0d) {
            return "-" + CurrencyFormatter.format(Math.abs(amount), "VND");
        }
        if (amount > 0d) {
            return "+" + CurrencyFormatter.format(amount, "VND");
        }
        return CurrencyFormatter.format(0d, "VND");
    }

    @NonNull
    private LocalDate toLocalDate(long epochMillis) {
        if (epochMillis <= 0L || epochMillis == Long.MAX_VALUE) return LocalDate.now();
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private long endOfToday() {
        return LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1L;
    }

    private void applyWindowInsets() {
        final int initialTopPadding = binding.statisticsHeader.getRoot().getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.statisticsHeader.getRoot(), (headerView, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            headerView.setPadding(headerView.getPaddingLeft(), initialTopPadding + systemBars.top, headerView.getPaddingRight(), headerView.getPaddingBottom());
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.statisticsHeader.getRoot());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private interface DateSelectedCallback { void onDateSelected(@NonNull LocalDate date); }

    private static class AggregateBudgetFilter {
        @NonNull private final String categoryId;
        @Nullable private final String walletId;
        private final long startDate;
        private final long endDate;

        private AggregateBudgetFilter(@NonNull String categoryId, @Nullable String walletId, long startDate, long endDate) {
            this.categoryId = categoryId;
            this.walletId = walletId;
            this.startDate = startDate;
            this.endDate = endDate;
        }

        private boolean matches(@NonNull TransactionEntity transaction) {
            if (!categoryId.equals(transaction.getCategoryId())) return false;
            if (walletId != null && !walletId.equals(transaction.getWalletId())) return false;
            long timestamp = transaction.getTimestamp();
            return timestamp >= startDate && timestamp <= endDate;
        }
    }

    private static class SectionMeta {
        @NonNull private final String key;
        @NonNull private final String title;
        private final int iconResId;
        private final int accentColor;
        private final int containerColor;

        private SectionMeta(@NonNull String key, @NonNull String title, int iconResId, int accentColor, int containerColor) {
            this.key = key;
            this.title = title;
            this.iconResId = iconResId;
            this.accentColor = accentColor;
            this.containerColor = containerColor;
        }
    }

    private class LedgerSection {
        @NonNull private final SectionMeta meta;
        @NonNull private final List<TransactionEntity> transactions = new ArrayList<>();
        private long latestTimestamp;
        private double total;

        private LedgerSection(@NonNull SectionMeta meta) { this.meta = meta; }

        private void add(@NonNull TransactionEntity transaction) {
            transactions.add(transaction);
            latestTimestamp = Math.max(latestTimestamp, transaction.getTimestamp());
            total += "EXPENSE".equals(transaction.getType()) ? -transaction.getAmount() : transaction.getAmount();
        }

        private long getLatestTimestamp() { return latestTimestamp; }

        @NonNull
        private LedgerSectionHeaderAdapter.SectionHeaderItem toHeaderItem() {
            int amountColor = total < 0d
                    ? ContextCompat.getColor(requireContext(), R.color.expense_red)
                    : (total > 0d ? ContextCompat.getColor(requireContext(), R.color.transfer_blue)
                    : ContextCompat.getColor(requireContext(), R.color.statistics_text_primary));
            return new LedgerSectionHeaderAdapter.SectionHeaderItem(
                    meta.title,
                    getString(R.string.ledger_section_transaction_count, transactions.size()),
                    formatSignedAmount(total),
                    meta.iconResId,
                    meta.accentColor,
                    meta.containerColor,
                    amountColor
            );
        }
    }
}
