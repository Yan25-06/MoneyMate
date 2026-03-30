package com.group10.moneymate.ui.home;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.databinding.ItemHomeWalletBinding;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.IconProvider;

import java.util.Objects;

public class HomeWalletAdapter extends ListAdapter<WalletWithBalance, HomeWalletAdapter.ViewHolder> {

    private static final String HIDDEN_BALANCE_MASK = "********";

    public interface OnWalletClickListener {
        void onWalletClick(@NonNull WalletWithBalance wallet);
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

        void bind(@NonNull WalletWithBalance item, boolean balancesVisible) {
            com.group10.moneymate.data.local.entity.WalletEntity wallet = item.getWallet();
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
                    ? CurrencyFormatter.format(item.getCurrentBalance(), "VND")
                    : HIDDEN_BALANCE_MASK);
            binding.tvWalletBalance.setTextColor(ContextCompat.getColor(
                    binding.getRoot().getContext(),
                    item.getCurrentBalance() < 0 ? R.color.expense_red : R.color.statistics_text_primary
            ));
            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onWalletClick(item);
                }
            });
        }


    }

    private static final DiffUtil.ItemCallback<WalletWithBalance> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<WalletWithBalance>() {
                @Override
                public boolean areItemsTheSame(@NonNull WalletWithBalance oldItem,
                                               @NonNull WalletWithBalance newItem) {
                    return Objects.equals(oldItem.getWallet().getId(), newItem.getWallet().getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull WalletWithBalance oldItem,
                                                  @NonNull WalletWithBalance newItem) {
                    return Objects.equals(oldItem.getWallet().getName(), newItem.getWallet().getName())
                            && Objects.equals(oldItem.getWallet().getType(), newItem.getWallet().getType())
                            && oldItem.getCurrentBalance() == newItem.getCurrentBalance()
                            && oldItem.getWallet().isArchived() == newItem.getWallet().isArchived()
                            && Objects.equals(oldItem.getWallet().getIconName(), newItem.getWallet().getIconName());
                }
            };
}
