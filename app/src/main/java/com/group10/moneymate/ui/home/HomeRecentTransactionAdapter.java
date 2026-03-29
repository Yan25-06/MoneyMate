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
import com.group10.moneymate.databinding.ItemHomeRecentTransactionBinding;

import java.util.Objects;

public class HomeRecentTransactionAdapter extends ListAdapter<HomeRecentTransactionAdapter.ItemUiModel, HomeRecentTransactionAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(@NonNull ItemUiModel item);
    }

    @Nullable
    private OnItemClickListener clickListener;

    public HomeRecentTransactionAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(@Nullable OnItemClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemHomeRecentTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        ), clickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        @NonNull private final ItemHomeRecentTransactionBinding binding;
        @Nullable private final OnItemClickListener clickListener;

        ViewHolder(@NonNull ItemHomeRecentTransactionBinding binding,
                   @Nullable OnItemClickListener clickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.clickListener = clickListener;
        }

        void bind(@NonNull ItemUiModel item) {
            binding.cvTransactionIcon.setCardBackgroundColor(applyAlpha(item.getAccentColor(), 0.14f));
            binding.ivTransactionIcon.setImageResource(item.getIconResId());
            binding.ivTransactionIcon.setImageTintList(ColorStateList.valueOf(item.getAccentColor()));
            binding.tvTransactionTitle.setText(item.getTitle());
            binding.tvTransactionDate.setText(item.getDateLabel());
            binding.tvTransactionAmount.setText(item.getAmountLabel());
            binding.tvTransactionAmount.setTextColor(item.getAmountColor());
            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onItemClick(item);
                }
            });
        }

        private int applyAlpha(int color, float alphaFraction) {
            int alpha = Math.min(255, Math.max(0, Math.round(alphaFraction * 255f)));
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        }
    }

    public static class ItemUiModel {
        @NonNull private final String transactionId;
        private final int iconResId;
        private final int accentColor;
        @NonNull private final String title;
        @NonNull private final String dateLabel;
        @NonNull private final String amountLabel;
        private final int amountColor;

        public ItemUiModel(@NonNull String transactionId,
                           int iconResId,
                           int accentColor,
                           @NonNull String title,
                           @NonNull String dateLabel,
                           @NonNull String amountLabel,
                           int amountColor) {
            this.transactionId = transactionId;
            this.iconResId = iconResId;
            this.accentColor = accentColor;
            this.title = title;
            this.dateLabel = dateLabel;
            this.amountLabel = amountLabel;
            this.amountColor = amountColor;
        }

        @NonNull
        public String getTransactionId() {
            return transactionId;
        }

        public int getIconResId() {
            return iconResId;
        }

        public int getAccentColor() {
            return accentColor;
        }

        @NonNull
        public String getTitle() {
            return title;
        }

        @NonNull
        public String getDateLabel() {
            return dateLabel;
        }

        @NonNull
        public String getAmountLabel() {
            return amountLabel;
        }

        public int getAmountColor() {
            return amountColor;
        }
    }

    private static final DiffUtil.ItemCallback<ItemUiModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<ItemUiModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull ItemUiModel oldItem, @NonNull ItemUiModel newItem) {
                    return Objects.equals(oldItem.transactionId, newItem.transactionId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull ItemUiModel oldItem, @NonNull ItemUiModel newItem) {
                    return oldItem.iconResId == newItem.iconResId
                            && oldItem.accentColor == newItem.accentColor
                            && oldItem.amountColor == newItem.amountColor
                            && Objects.equals(oldItem.title, newItem.title)
                            && Objects.equals(oldItem.dateLabel, newItem.dateLabel)
                            && Objects.equals(oldItem.amountLabel, newItem.amountLabel);
                }
            };
}
