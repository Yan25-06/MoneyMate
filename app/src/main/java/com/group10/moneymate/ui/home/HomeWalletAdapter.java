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
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.ItemHomeWalletBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

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
            int accent = parseColorOrDefault(
                    wallet.getColorHex(),
                    ContextCompat.getColor(binding.getRoot().getContext(), R.color.statistics_wallet_icon)
            );
            binding.cvWalletIcon.setCardBackgroundColor(applyAlpha(accent, 0.14f));
            binding.ivWalletIcon.setImageResource(resolveWalletIcon(wallet.getType()));
            binding.ivWalletIcon.setImageTintList(ColorStateList.valueOf(accent));
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

        private int resolveWalletIcon(@Nullable String type) {
            if ("BANK".equals(type)) {
                return R.drawable.outline_account_balance_24;
            }
            if ("E_WALLET".equals(type)) {
                return R.drawable.outline_credit_card_24;
            }
            return R.drawable.outline_account_balance_wallet_24;
        }

        private int applyAlpha(int color, float alphaFraction) {
            int alpha = Math.min(255, Math.max(0, Math.round(alphaFraction * 255f)));
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        }

        private int parseColorOrDefault(@Nullable String colorHex, int defaultColor) {
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
                            && Objects.equals(oldItem.getColorHex(), newItem.getColorHex());
                }
            };
}
