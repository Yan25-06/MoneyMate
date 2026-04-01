package com.group10.moneymate.ui.budget;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.dto.WalletWithBalance;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.ItemBudgetWalletPickerBinding;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.IconProvider;

import java.util.Objects;

public class BudgetWalletPickerAdapter extends ListAdapter<WalletWithBalance, BudgetWalletPickerAdapter.ViewHolder> {

    public interface Listener {
        void onSelect(@NonNull WalletEntity wallet);

        void onEdit(@NonNull WalletEntity wallet);
    }

    private Listener listener;
    private String selectedWalletId;

    protected BudgetWalletPickerAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void setSelectedWalletId(String selectedWalletId) {
        this.selectedWalletId = selectedWalletId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        return new ViewHolder(ItemBudgetWalletPickerBinding.inflate(inflater, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemBudgetWalletPickerBinding binding;

        ViewHolder(@NonNull ItemBudgetWalletPickerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull WalletWithBalance item) {
            WalletEntity wallet = item.getWallet();
            boolean archived = wallet.isArchived();
            android.content.Context context = binding.getRoot().getContext();
            binding.tvWalletName.setText(wallet.getName());
            binding.tvWalletBalance.setText(CurrencyFormatter.format(item.getCurrentBalance(), "VND"));
            binding.tvWalletBalance.setTextColor(ContextCompat.getColor(
                    context,
                    resolveBalanceTextColor(archived, item.getCurrentBalance())));
            binding.tvWalletArchivedNote.setVisibility(archived ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.tvWalletArchivedNote.setText(R.string.wallet_picker_archived_note);
            binding.ivWalletIcon.setImageResource(IconProvider.resolveWalletIcon(
                    context,
                    wallet.getIconName(),
                    wallet.getType()));
            boolean isSelected = wallet.getId().equals(selectedWalletId);
            binding.vSelectedDot.setVisibility(isSelected && !archived
                    ? android.view.View.VISIBLE
                    : android.view.View.INVISIBLE);
            binding.getRoot().setBackgroundResource(resolveBackground(isSelected));
            binding.getRoot().setAlpha(archived ? 0.6f : 1f);
            binding.ivWalletIcon.setAlpha(archived ? 0.55f : 1f);
            binding.tvWalletName.setTextColor(ContextCompat.getColor(
                    context,
                    archived ? R.color.statistics_text_muted : android.R.color.black));
            binding.btnEditWallet.setImageTintList(android.content.res.ColorStateList.valueOf(
                    ContextCompat.getColor(
                            context,
                            resolveEditTint(isSelected))));
            binding.getRoot().setOnClickListener(v -> {
                if (!archived && listener != null) {
                    listener.onSelect(wallet);
                }
            });
            binding.btnEditWallet.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEdit(wallet);
                }
            });
        }

        private int resolveBalanceTextColor(boolean archived, double balance) {
            if (archived) {
                return R.color.statistics_text_muted;
            }
            return balance < 0 ? R.color.expense_red : R.color.statistics_text_primary;
        }

        private int resolveBackground(boolean selected) {
            return selected ? R.drawable.bg_budget_wallet_picker_selected : android.R.color.white;
        }

        private int resolveEditTint(boolean selected) {
            return selected ? R.color.budget_safe_green : R.color.budget_text_secondary;
        }
    }

    private static final DiffUtil.ItemCallback<WalletWithBalance> DIFF_CALLBACK = new DiffUtil.ItemCallback<WalletWithBalance>() {
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
                    && oldItem.getCurrentBalance() == newItem.getCurrentBalance();
        }
    };
}
