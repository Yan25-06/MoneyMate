package com.group10.moneymate.ui.home;

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
            binding.cvCategoryIcon.setCardBackgroundColor(
                    ContextCompat.getColor(binding.getRoot().getContext(), android.R.color.white)
            );
            binding.ivCategoryIcon.setImageResource(item.getIconRes());
            binding.ivCategoryIcon.setImageTintList(null);
            binding.tvCategoryName.setText(item.getCategoryName());
            binding.tvCategoryAmount.setText(CurrencyFormatter.format(item.getAmount(), "VND"));
            binding.tvCategoryPercent.setText(item.getPercentLabel());
        }

        // ...existing code...
    }

    public static class ItemUiModel {
        private final String categoryId;
        private final int iconRes;
        private final int accentColor;
        @NonNull private final String categoryName;
        private final double amount;
        @NonNull private final String percentLabel;

        public ItemUiModel(@NonNull String categoryId,
                           int iconRes,
                           int accentColor,
                           @NonNull String categoryName,
                           double amount,
                           @NonNull String percentLabel) {
            this.categoryId = categoryId;
            this.iconRes = iconRes;
            this.accentColor = accentColor;
            this.categoryName = categoryName;
            this.amount = amount;
            this.percentLabel = percentLabel;
        }

        @NonNull
        public String getCategoryId() {
            return categoryId;
        }

        public int getIconRes() {
            return iconRes;
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
                    return oldItem.iconRes == newItem.iconRes
                            && oldItem.accentColor == newItem.accentColor
                            && Objects.equals(oldItem.categoryName, newItem.categoryName)
                            && oldItem.amount == newItem.amount
                            && Objects.equals(oldItem.percentLabel, newItem.percentLabel);
                }
            };
}
