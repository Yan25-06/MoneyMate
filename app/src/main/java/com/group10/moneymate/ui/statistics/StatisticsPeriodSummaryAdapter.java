package com.group10.moneymate.ui.statistics;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemStatisticsPeriodSummaryBinding;
import com.group10.moneymate.models.TransactionType;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.util.Locale;

public class StatisticsPeriodSummaryAdapter extends ListAdapter<IncomeExpenseDetailViewModel.PeriodSummaryUiModel, StatisticsPeriodSummaryAdapter.ViewHolder> {

    public enum DisplayMode {
        NET,
        SINGLE
    }

    public interface OnItemClickListener {
        void onClick(@NonNull IncomeExpenseDetailViewModel.PeriodSummaryUiModel item);
    }

    private static final DiffUtil.ItemCallback<IncomeExpenseDetailViewModel.PeriodSummaryUiModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<IncomeExpenseDetailViewModel.PeriodSummaryUiModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull IncomeExpenseDetailViewModel.PeriodSummaryUiModel oldItem,
                                               @NonNull IncomeExpenseDetailViewModel.PeriodSummaryUiModel newItem) {
                    return oldItem.getStartDate() == newItem.getStartDate()
                            && oldItem.getEndDate() == newItem.getEndDate();
                }

                @Override
                public boolean areContentsTheSame(@NonNull IncomeExpenseDetailViewModel.PeriodSummaryUiModel oldItem,
                                                  @NonNull IncomeExpenseDetailViewModel.PeriodSummaryUiModel newItem) {
                    return oldItem.getIncomeAmount() == newItem.getIncomeAmount()
                            && oldItem.getExpenseAmount() == newItem.getExpenseAmount()
                            && oldItem.getLabel().equals(newItem.getLabel());
                }
            };

    private final DisplayMode displayMode;
    @Nullable
    private final TransactionType transactionType;
    @Nullable
    private OnItemClickListener onItemClickListener;

    public StatisticsPeriodSummaryAdapter(@NonNull DisplayMode displayMode,
                                          @Nullable TransactionType transactionType) {
        super(DIFF_CALLBACK);
        this.displayMode = displayMode;
        this.transactionType = transactionType;
    }

    public void setOnItemClickListener(@Nullable OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemStatisticsPeriodSummaryBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        ));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), displayMode, transactionType, onItemClickListener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemStatisticsPeriodSummaryBinding binding;

        ViewHolder(@NonNull ItemStatisticsPeriodSummaryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull IncomeExpenseDetailViewModel.PeriodSummaryUiModel item,
                  @NonNull DisplayMode displayMode,
                  @Nullable TransactionType transactionType,
                  @Nullable OnItemClickListener clickListener) {
            binding.tvPeriodLabel.setText(item.getLabel());
            boolean isNetMode = displayMode == DisplayMode.NET;

            binding.layoutNetValues.setVisibility(isNetMode ? View.VISIBLE : View.GONE);
            binding.layoutPrimaryValue.setVisibility(isNetMode ? View.GONE : View.VISIBLE);
            binding.ivChevron.setVisibility(isNetMode ? View.GONE : View.VISIBLE);

            if (isNetMode) {
                binding.tvIncomeValue.setText(CurrencyFormatter.format(item.getIncomeAmount(), "VND"));
                binding.tvExpenseValue.setText(CurrencyFormatter.format(item.getExpenseAmount(), "VND"));
                binding.tvNetValue.setText(CurrencyFormatter.format(item.getNetAmount(), "VND"));
                int netColor = item.getNetAmount() >= 0d
                        ? ContextCompat.getColor(binding.getRoot().getContext(), R.color.statistics_text_primary)
                        : ContextCompat.getColor(binding.getRoot().getContext(), R.color.statistics_text_muted);
                binding.tvNetValue.setTextColor(netColor);
            } else {
                double primaryAmount = item.getPrimaryAmount(transactionType != null ? transactionType : TransactionType.EXPENSE);
                binding.tvPrimaryValue.setText(CurrencyFormatter.format(primaryAmount, "VND"));
                int primaryColor = ContextCompat.getColor(
                        binding.getRoot().getContext(),
                        transactionType == TransactionType.INCOME ? R.color.transfer_blue : R.color.expense_red
                );
                binding.tvPrimaryValue.setTextColor(primaryColor);
                binding.tvPrimaryMeta.setText(String.format(Locale.getDefault(), "%s",
                        transactionType == TransactionType.INCOME
                                ? binding.getRoot().getContext().getString(R.string.statistics_income_label)
                                : binding.getRoot().getContext().getString(R.string.statistics_expense_label)));
            }

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onClick(item);
                }
            });
        }
    }
}
