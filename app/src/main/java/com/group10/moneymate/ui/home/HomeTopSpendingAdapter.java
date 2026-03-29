package com.group10.moneymate.ui.home;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemHomeTopSpendingBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.util.Objects;

public class HomeTopSpendingAdapter extends ListAdapter<HomeTopSpendingAdapter.ItemUiModel, HomeTopSpendingAdapter.ViewHolder> {

    public HomeTopSpendingAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemHomeTopSpendingBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        ));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        @NonNull private final ItemHomeTopSpendingBinding binding;

        ViewHolder(@NonNull ItemHomeTopSpendingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull ItemUiModel item) {
            binding.cvCategoryIcon.setCardBackgroundColor(applyAlpha(item.getAccentColor(), 0.14f));
            binding.ivCategoryIcon.setImageResource(item.getIconResId());
            binding.ivCategoryIcon.setImageTintList(ColorStateList.valueOf(item.getAccentColor()));
            binding.tvCategoryName.setText(item.getCategoryName());
            binding.tvCategoryAmount.setText(CurrencyFormatter.format(item.getAmount(), "VND"));
            binding.tvCategoryPercent.setText(item.getPercentLabel());
        }

        private int applyAlpha(int color, float alphaFraction) {
            int alpha = Math.min(255, Math.max(0, Math.round(alphaFraction * 255f)));
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        }
    }

    public static class ItemUiModel {
        private final String categoryId;
        private final int iconResId;
        private final int accentColor;
        @NonNull private final String categoryName;
        private final double amount;
        @NonNull private final String percentLabel;

        public ItemUiModel(@NonNull String categoryId,
                           int iconResId,
                           int accentColor,
                           @NonNull String categoryName,
                           double amount,
                           @NonNull String percentLabel) {
            this.categoryId = categoryId;
            this.iconResId = iconResId;
            this.accentColor = accentColor;
            this.categoryName = categoryName;
            this.amount = amount;
            this.percentLabel = percentLabel;
        }

        @NonNull
        public String getCategoryId() {
            return categoryId;
        }

        public int getIconResId() {
            return iconResId;
        }

        public int getAccentColor() {
            return accentColor;
        }

        @NonNull
        public String getCategoryName() {
            return categoryName;
        }

        public double getAmount() {
            return amount;
        }

        @NonNull
        public String getPercentLabel() {
            return percentLabel;
        }
    }

    private static final DiffUtil.ItemCallback<ItemUiModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ItemUiModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull ItemUiModel oldItem, @NonNull ItemUiModel newItem) {
                    return Objects.equals(oldItem.categoryId, newItem.categoryId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ItemUiModel oldItem, @NonNull ItemUiModel newItem) {
                    return oldItem.iconResId == newItem.iconResId
                            && oldItem.accentColor == newItem.accentColor
                            && Objects.equals(oldItem.categoryName, newItem.categoryName)
                            && oldItem.amount == newItem.amount
                            && Objects.equals(oldItem.percentLabel, newItem.percentLabel);
                }
            };
}
