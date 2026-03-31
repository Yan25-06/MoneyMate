package com.group10.moneymate.ui.category;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.ItemCategoryWalletBinding;
import com.group10.moneymate.utils.IconProvider;

public class CategoryWalletAdapter extends ListAdapter<WalletEntity, CategoryWalletAdapter.ViewHolder> {

    public CategoryWalletAdapter() {
        super(DIFF_CALLBACK);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryWalletBinding binding = ItemCategoryWalletBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryWalletBinding binding;

        ViewHolder(@NonNull ItemCategoryWalletBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull WalletEntity wallet) {
            binding.tvWalletName.setText(wallet.getName());
            int iconRes = IconProvider.resolveWalletIcon(
                    binding.getRoot().getContext(),
                    wallet.getIconName(),
                    wallet.getType());
            binding.ivWalletIcon.setImageResource(iconRes);
        }
    }

    private static final DiffUtil.ItemCallback<WalletEntity> DIFF_CALLBACK = new DiffUtil.ItemCallback<WalletEntity>() {
        @Override
        public boolean areItemsTheSame(@NonNull WalletEntity oldItem, @NonNull WalletEntity newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull WalletEntity oldItem, @NonNull WalletEntity newItem) {
            return oldItem.getUpdatedAt() == newItem.getUpdatedAt();
        }
    };
}
