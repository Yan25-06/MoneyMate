package com.group10.moneymate.ui.transaction;


import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import com.group10.moneymate.databinding.ItemLedgerSectionHeaderBinding;

public class LedgerSectionHeaderAdapter extends RecyclerView.Adapter<LedgerSectionHeaderAdapter.ViewHolder> {

    @NonNull
    private final SectionHeaderItem item;

    public LedgerSectionHeaderAdapter(@NonNull SectionHeaderItem item) {
        this.item = item;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemLedgerSectionHeaderBinding binding = ItemLedgerSectionHeaderBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(item);
    }

    @Override
    public int getItemCount() {
        return 1;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        @NonNull
        private final ItemLedgerSectionHeaderBinding binding;

        ViewHolder(@NonNull ItemLedgerSectionHeaderBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull SectionHeaderItem item) {
            binding.ivSectionIcon.setImageResource(item.getIconRes());
            binding.ivSectionIcon.setImageTintList(null);
            binding.cvSectionIconContainer.setCardBackgroundColor(
                    binding.getRoot().getContext().getColor(android.R.color.white)
            );
            binding.tvSectionTitle.setText(item.getTitle());
            binding.tvSectionSubtitle.setText(item.getSubtitle());
            binding.tvSectionAmount.setText(item.getAmountLabel());
            binding.cvSectionAmountChip.setCardBackgroundColor(item.getAmountColor());
        }
    }

    public static class SectionHeaderItem {
        @NonNull
        private final String title;
        @NonNull
        private final String subtitle;
        @NonNull
        private final String amountLabel;
        private final int iconRes;
        private final int accentColor;
        private final int containerColor;
        private final int amountColor;

        public SectionHeaderItem(@NonNull String title,
                                 @NonNull String subtitle,
                                 @NonNull String amountLabel,
                                 int iconRes,
                                 int accentColor,
                                 int containerColor,
                                 int amountColor) {
            this.title = title;
            this.subtitle = subtitle;
            this.amountLabel = amountLabel;
            this.iconRes = iconRes;
            this.accentColor = accentColor;
            this.containerColor = containerColor;
            this.amountColor = amountColor;
        }

        @NonNull
        public String getTitle() {
            return title;
        }

        @NonNull
        public String getSubtitle() {
            return subtitle;
        }

        @NonNull
        public String getAmountLabel() {
            return amountLabel;
        }

        public int getIconRes() {
            return iconRes;
        }

        public int getAccentColor() {
            return accentColor;
        }

        public int getContainerColor() {
            return containerColor;
        }

        public int getAmountColor() {
            return amountColor;
        }
    }
}
