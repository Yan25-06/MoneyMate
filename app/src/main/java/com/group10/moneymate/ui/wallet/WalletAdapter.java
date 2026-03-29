package com.group10.moneymate.ui.wallet;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.ItemWalletBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.util.Objects;

public class WalletAdapter extends ListAdapter<WalletEntity, WalletAdapter.WalletViewHolder> {

    public interface WalletItemListener {
        void onEdit(WalletEntity wallet);
        void onDelete(WalletEntity wallet);
    }

    private WalletItemListener listener;

    public WalletAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setWalletItemListener(WalletItemListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public WalletViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemWalletBinding binding = ItemWalletBinding.inflate(inflater, parent, false);
        return new WalletViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull WalletViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class WalletViewHolder extends RecyclerView.ViewHolder {

        private final ItemWalletBinding binding;

        WalletViewHolder(@NonNull ItemWalletBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(WalletEntity wallet) {
            binding.tvName.setText(wallet.getName());
            binding.tvType.setText(getTypeText(binding.getRoot().getContext(), wallet.getType()));
            binding.tvBalance.setText(CurrencyFormatter.format(wallet.getBalance(), "VND"));
            binding.ivIcon.setImageResource(getTypeIcon(wallet.getType()));
            int accentColor = parseColorOrDefault(
                    wallet.getColorHex(),
                    ContextCompat.getColor(binding.getRoot().getContext(), R.color.statistics_wallet_icon)
            );
            binding.cvIcon.setCardBackgroundColor(applyAlpha(accentColor, 0.14f));
            binding.ivIcon.setImageTintList(ColorStateList.valueOf(accentColor));
            binding.tvBalance.setTextColor(wallet.getBalance() < 0
                    ? ContextCompat.getColor(binding.getRoot().getContext(), R.color.expense_red)
                    : ContextCompat.getColor(binding.getRoot().getContext(), R.color.statistics_text_primary));

            binding.btnMenu.setOnClickListener(v -> showPopupMenu(v, wallet));
        }

        private void showPopupMenu(View anchor, WalletEntity wallet) {
            PopupMenu popupMenu = new PopupMenu(anchor.getContext(), anchor);
            popupMenu.getMenuInflater().inflate(R.menu.menu_wallet_item, popupMenu.getMenu());
            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_edit_wallet) {
                    if (listener != null) {
                        listener.onEdit(wallet);
                    }
                    return true;
                }
                if (itemId == R.id.action_delete_wallet) {
                    if (listener != null) {
                        listener.onDelete(wallet);
                    }
                    return true;
                }
                return false;
            });
            popupMenu.show();
        }
    }

    private String getTypeText(android.content.Context context, String type) {
        if ("BANK".equals(type)) {
            return context.getString(R.string.wallet_type_bank);
        }
        if ("E_WALLET".equals(type)) {
            return context.getString(R.string.wallet_type_ewallet);
        }
        return context.getString(R.string.wallet_type_cash);
    }

    private int getTypeIcon(String type) {
        if ("BANK".equals(type)) {
            return R.drawable.outline_account_balance_24;
        }
        if ("E_WALLET".equals(type)) {
            return R.drawable.outline_credit_card_24;
        }
        return R.drawable.outline_payments_24;
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

    private static final DiffUtil.ItemCallback<WalletEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<WalletEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull WalletEntity oldItem, @NonNull WalletEntity newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull WalletEntity oldItem, @NonNull WalletEntity newItem) {
                    return oldItem.getBalance() == newItem.getBalance()
                            && oldItem.isDeleted() == newItem.isDeleted()
                            && oldItem.isExcluded() == newItem.isExcluded()
                            && oldItem.getSyncStatus() == newItem.getSyncStatus()
                            && Objects.equals(oldItem.getName(), newItem.getName())
                            && Objects.equals(oldItem.getType(), newItem.getType())
                            && Objects.equals(oldItem.getColorHex(), newItem.getColorHex());
                }
            };
}
