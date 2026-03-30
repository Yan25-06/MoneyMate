package com.group10.moneymate.ui.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.ItemHomeWalletBinding;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.IconProvider;

import java.util.Objects;

public class HomeWalletAdapter extends ListAdapter<WalletEntity, HomeWalletAdapter.ViewHolder> {

    private static final String HIDDEN_BALANCE_MASK = "********";

    public interface OnWalletClickListener {
        void onWalletClick(@NonNull WalletEntity wallet);
    }

    @Nullable
    private OnWalletClickListener clickListener;
    private boolean balancesVisible = true;

    public HomeWalletAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnWalletClickListener(@Nullable OnWalletClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setBalancesVisible(boolean balancesVisible) {
        this.balancesVisible = balancesVisible;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemHomeWalletBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        ), clickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), balancesVisible);
    }

    static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        @NonNull private final ItemHomeWalletBinding binding;
        @Nullable private final OnWalletClickListener clickListener;

        ViewHolder(@NonNull ItemHomeWalletBinding binding,
                   @Nullable OnWalletClickListener clickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.clickListener = clickListener;
        }

        void bind(@NonNull WalletEntity wallet, boolean balancesVisible) {
            binding.cvWalletIcon.setCardBackgroundColor(
                    ContextCompat.getColor(binding.getRoot().getContext(), android.R.color.white)
            );
            binding.ivWalletIcon.setImageResource(IconProvider.resolveWalletIcon(
                    binding.getRoot().getContext(),
                    wallet.getIconName(),
                    wallet.getType()
            ));
            binding.ivWalletIcon.setImageTintList(null);
            binding.tvWalletName.setText(wallet.getName());
            binding.tvWalletBalance.setText(balancesVisible
                    ? CurrencyFormatter.format(wallet.getBalance(), "VND")
                    : HIDDEN_BALANCE_MASK);
            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onWalletClick(wallet);
                }
            });
        }


    }

    private static final DiffUtil.ItemCallback<WalletEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<WalletEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull WalletEntity oldItem, @NonNull WalletEntity newItem) {
                    return Objects.equals(oldItem.getId(), newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull WalletEntity oldItem, @NonNull WalletEntity newItem) {
                    return Objects.equals(oldItem.getName(), newItem.getName())
                            && Objects.equals(oldItem.getType(), newItem.getType())
                            && oldItem.getBalance() == newItem.getBalance()
                            && oldItem.isArchived() == newItem.isArchived()
                            && Objects.equals(oldItem.getIconName(), newItem.getIconName());
                }
            };
}
