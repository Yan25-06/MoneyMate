package com.group10.moneymate.ui.statistics;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemStatisticsCategoryRowBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

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
                            && oldItem.getCategoryName().equals(newItem.getCategoryName());
                }
            };

    @Nullable
    private OnItemClickListener onItemClickListener;

    public StatisticsCategoryBreakdownAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(@Nullable OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
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
        holder.bind(getItem(position), onItemClickListener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemStatisticsCategoryRowBinding binding;

        ViewHolder(@NonNull ItemStatisticsCategoryRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull IncomeExpenseDetailViewModel.CategoryBreakdownItemUiModel item,
                  @Nullable OnItemClickListener clickListener) {
            Context context = binding.getRoot().getContext();
            int accentColor = parseColorOrDefault(
                    item.getColorHex(),
                    ContextCompat.getColor(context, R.color.transfer_blue)
            );

            binding.tvCategoryName.setText(item.getCategoryName());
            binding.tvCategoryAmount.setText(CurrencyFormatter.format(item.getTotalAmount(), "VND"));
            binding.tvCategoryPercent.setText(String.format(Locale.getDefault(), "%.0f%%", item.getSharePercent()));
            binding.tvCategoryMeta.setText(context.getString(
                    R.string.statistics_detail_category_meta,
                    item.getTransactionCount()
            ));

            binding.cardIconContainer.setCardBackgroundColor(ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(accentColor, 26)
            ));
            binding.cardIconContainer.setStrokeColor(ColorStateList.valueOf(
                    ColorUtils.setAlphaComponent(accentColor, 54)
            ));
            binding.ivCategoryIcon.setImageResource(resolveIconRes(context, item.getIconResId()));
            binding.ivCategoryIcon.setImageTintList(ColorStateList.valueOf(accentColor));

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onClick(item);
                }
            });
        }

        private int resolveIconRes(@NonNull Context context, @Nullable String iconResId) {
            if (iconResId == null || iconResId.trim().isEmpty()) {
                return R.drawable.ic_category_other;
            }
            int resId = context.getResources().getIdentifier(
                    iconResId,
                    "drawable",
                    context.getPackageName()
            );
            return resId != 0 ? resId : R.drawable.ic_category_other;
        }

        @ColorInt
        private int parseColorOrDefault(@Nullable String colorHex, @ColorInt int defaultColor) {
            if (colorHex == null || colorHex.trim().isEmpty()) {
                return defaultColor;
            }
            try {
                return Color.parseColor(colorHex);
            } catch (IllegalArgumentException ignored) {
                return defaultColor;
            }
        }
    }
}
