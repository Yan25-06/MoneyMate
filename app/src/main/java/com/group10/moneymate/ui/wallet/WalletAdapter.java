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
import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.ItemWalletBinding;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.IconProvider;

import java.util.Objects;

public class WalletAdapter extends ListAdapter<WalletWithBalance, WalletAdapter.WalletViewHolder> {

    public interface WalletItemListener {
        void onEdit(WalletEntity wallet);
        void onArchive(WalletEntity wallet);
        void onRestore(WalletEntity wallet);
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

        void bind(WalletWithBalance item) {
            WalletEntity wallet = item.getWallet();
            boolean archived = wallet.isArchived();
            android.content.Context context = binding.getRoot().getContext();
            binding.tvName.setText(wallet.getName());
            binding.tvType.setText(resolveTypeText(wallet.getType()));
            binding.tvBalance.setText(CurrencyFormatter.format(item.getCurrentBalance(), "VND"));
            binding.ivIcon.setImageResource(IconProvider.resolveWalletIcon(
                    context,
                    wallet.getIconName(),
                    wallet.getType()
            ));
            applyArchivedStyle(context, archived, item.getCurrentBalance());
            binding.btnMenu.setOnClickListener(v -> showPopupMenu(v, wallet));
        }

        private void applyArchivedStyle(@NonNull android.content.Context context,
                                        boolean archived,
                                        double currentBalance) {
            int primaryTextColor = ContextCompat.getColor(
                    context,
                    archived ? R.color.statistics_text_muted : R.color.statistics_text_primary
            );
            int secondaryTextColor = ContextCompat.getColor(context, R.color.statistics_text_secondary);
            int mutedColor = ContextCompat.getColor(context, R.color.statistics_text_muted);
            int balanceColor = currentBalance < 0
                    ? ContextCompat.getColor(context, archived ? R.color.statistics_text_muted : R.color.expense_red)
                    : primaryTextColor;

            binding.tvName.setTextColor(primaryTextColor);
            binding.tvType.setTextColor(archived ? mutedColor : secondaryTextColor);
            binding.tvBalance.setTextColor(balanceColor);
            binding.tvArchivedBadge.setVisibility(archived ? View.VISIBLE : View.GONE);
            binding.cvIcon.setCardBackgroundColor(ContextCompat.getColor(
                    context,
                    archived ? R.color.statistics_card_inner_bg : android.R.color.white
            ));
            binding.ivIcon.setImageTintList(archived
                    ? android.content.res.ColorStateList.valueOf(mutedColor)
                    : null);
            binding.ivIcon.setAlpha(archived ? 0.72f : 1f);
            binding.getRoot().setAlpha(archived ? 0.78f : 1f);
        }

        private void showPopupMenu(View anchor, WalletEntity wallet) {
            PopupMenu popupMenu = new PopupMenu(anchor.getContext(), anchor);
            popupMenu.getMenuInflater().inflate(R.menu.menu_wallet_item, popupMenu.getMenu());
            popupMenu.getMenu().findItem(R.id.action_archive_wallet).setVisible(!wallet.isArchived());
            popupMenu.getMenu().findItem(R.id.action_restore_wallet).setVisible(wallet.isArchived());
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
                if (itemId == R.id.action_restore_wallet) {
                    if (listener != null) {
                        listener.onRestore(wallet);
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

        @NonNull
        private String resolveTypeText(@Nullable String type) {
            if ("BANK".equals(type)) {
                return binding.getRoot().getContext().getString(R.string.wallet_type_bank);
            }
            if ("E_WALLET".equals(type)) {
                return binding.getRoot().getContext().getString(R.string.wallet_type_ewallet);
            }
            return binding.getRoot().getContext().getString(R.string.wallet_type_cash);
        }
    }

    private static final DiffUtil.ItemCallback<WalletWithBalance> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<WalletWithBalance>() {
                @Override
                public boolean areItemsTheSame(@NonNull WalletWithBalance oldItem,
                                               @NonNull WalletWithBalance newItem) {
                    return oldItem.getWallet().getId().equals(newItem.getWallet().getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull WalletWithBalance oldItem,
                                                  @NonNull WalletWithBalance newItem) {
                    WalletEntity oldWallet = oldItem.getWallet();
                    WalletEntity newWallet = newItem.getWallet();
                    return oldItem.getCurrentBalance() == newItem.getCurrentBalance()
                            && oldWallet.isDeleted() == newWallet.isDeleted()
                            && oldWallet.isArchived() == newWallet.isArchived()
                            && oldWallet.isExcluded() == newWallet.isExcluded()
                            && oldWallet.getSyncStatus() == newWallet.getSyncStatus()
                            && Objects.equals(oldWallet.getName(), newWallet.getName())
                            && Objects.equals(oldWallet.getType(), newWallet.getType())
                            && Objects.equals(oldWallet.getIconName(), newWallet.getIconName());
                }
            };
}
