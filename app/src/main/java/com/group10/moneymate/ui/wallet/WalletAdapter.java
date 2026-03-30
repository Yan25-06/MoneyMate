package com.group10.moneymate.ui.wallet;

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
import com.group10.moneymate.utils.IconProvider;

import java.util.Objects;

public class WalletAdapter extends ListAdapter<WalletEntity, WalletAdapter.WalletViewHolder> {

    public interface WalletItemListener {
        void onEdit(WalletEntity wallet);
        void onArchive(WalletEntity wallet);
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
            binding.ivIcon.setImageResource(IconProvider.resolveWalletIcon(
                    binding.getRoot().getContext(),
                    wallet.getIconName(),
                    wallet.getType()
            ));
            binding.cvIcon.setCardBackgroundColor(
                    ContextCompat.getColor(binding.getRoot().getContext(), android.R.color.white)
            );
            binding.ivIcon.setImageTintList(null);
            binding.tvBalance.setTextColor(wallet.getBalance() < 0
                    ? ContextCompat.getColor(binding.getRoot().getContext(), R.color.expense_red)
                    : ContextCompat.getColor(binding.getRoot().getContext(), R.color.statistics_text_primary));

            binding.btnMenu.setOnClickListener(v -> showPopupMenu(v, wallet));
        }

        private void showPopupMenu(View anchor, WalletEntity wallet) {
            PopupMenu popupMenu = new PopupMenu(anchor.getContext(), anchor);
            popupMenu.getMenuInflater().inflate(R.menu.menu_wallet_item, popupMenu.getMenu());
            popupMenu.getMenu().findItem(R.id.action_archive_wallet).setVisible(!wallet.isArchived());
            popupMenu.setOnMenuItemClickListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.action_edit_wallet) {
                    if (listener != null) {
                        listener.onEdit(wallet);
                    }
                    return true;
                }
                if (itemId == R.id.action_archive_wallet) {
                    if (listener != null) {
                        listener.onArchive(wallet);
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
                            && oldItem.isArchived() == newItem.isArchived()
                            && oldItem.isExcluded() == newItem.isExcluded()
                            && oldItem.getSyncStatus() == newItem.getSyncStatus()
                            && Objects.equals(oldItem.getName(), newItem.getName())
                            && Objects.equals(oldItem.getType(), newItem.getType())
                            && Objects.equals(oldItem.getIconName(), newItem.getIconName());
                }
            };
}
