package com.group10.moneymate.ui.statistics;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemStatisticsCategoryRowBinding;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.IconProvider;

import java.util.ArrayList;
import java.util.Locale;

public class StatisticsCategoryBreakdownAdapter extends ListAdapter<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel, StatisticsCategoryBreakdownAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onClick(@NonNull IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel item);
    }

    private static final DiffUtil.ItemCallback<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel oldItem,
                                               @NonNull IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel newItem) {
                    String oldId = oldItem.getCategoryId();
                    String newId = newItem.getCategoryId();
                    if (oldId == null || newId == null) {
                        return oldItem.getCategoryName().equals(newItem.getCategoryName());
                    }
                    return oldId.equals(newId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel oldItem,
                                                  @NonNull IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel newItem) {
                    return oldItem.getTotalAmount() == newItem.getTotalAmount()
                            && oldItem.getSharePercent() == newItem.getSharePercent()
                            && oldItem.getTransactionCount() == newItem.getTransactionCount()
                            && oldItem.getCategoryName().equals(newItem.getCategoryName())
                            && oldItem.isCategoryDeleted() == newItem.isCategoryDeleted();
                }
            };

    @Nullable
    private OnItemClickListener onItemClickListener;
    @ColorInt
    private int amountAccentColor;

    public StatisticsCategoryBreakdownAdapter() {
        super(DIFF_CALLBACK);
        amountAccentColor = 0;
    }

    public void setOnItemClickListener(@Nullable OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    public void setAmountAccentColor(@ColorInt int amountAccentColor) {
        this.amountAccentColor = amountAccentColor;
        submitList(new ArrayList<>(getCurrentList()));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemStatisticsCategoryRowBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        ));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), onItemClickListener, amountAccentColor);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemStatisticsCategoryRowBinding binding;

        ViewHolder(@NonNull ItemStatisticsCategoryRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel item,
                  @Nullable OnItemClickListener clickListener,
                  @ColorInt int amountAccentColor) {
            Context context = binding.getRoot().getContext();

            String categoryLabel = item.getCategoryName();
            if (item.isCategoryDeleted()) {
                categoryLabel = categoryLabel + " " + context.getString(R.string.statistics_category_deleted_suffix);
            }
            binding.tvCategoryName.setText(categoryLabel);
            binding.tvCategoryAmount.setText(CurrencyFormatter.format(item.getTotalAmount(), "VND"));
            binding.tvCategoryPercent.setText(String.format(Locale.getDefault(), "%.0f%%", item.getSharePercent()));
            binding.tvCategoryMeta.setText(context.getString(
                    R.string.statistics_detail_category_meta,
                    item.getTransactionCount()
            ));

            binding.cardIconContainer.setCardBackgroundColor(ColorStateList.valueOf(
                    ContextCompat.getColor(context, android.R.color.white)
            ));
            binding.cardIconContainer.setStrokeColor(ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.transaction_border)
            ));
            binding.ivCategoryIcon.setImageResource(IconProvider.resolveCategoryIcon(context, item.getIconName()));
            if (amountAccentColor != 0) {
                binding.tvCategoryAmount.setTextColor(amountAccentColor);
                binding.tvCategoryPercent.setTextColor(amountAccentColor);
            }

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onClick(item);
                }
            });
        }

    }
}
