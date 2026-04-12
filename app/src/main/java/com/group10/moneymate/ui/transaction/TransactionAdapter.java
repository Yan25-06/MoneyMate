package com.group10.moneymate.ui.transaction;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.databinding.ItemTransactionBinding;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.DateUtils;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class TransactionAdapter extends ListAdapter<TransactionEntity, TransactionAdapter.ViewHolder> {

    private OnTransactionClickListener clickListener;
    private OnTransactionLongClickListener longClickListener;
    @NonNull
    private Map<String, CategoryPresentation> categoryPresentationMap = Collections.emptyMap();
    @NonNull
    private Map<String, WalletPresentation> walletPresentationMap = Collections.emptyMap();

    public interface OnTransactionClickListener {
        void onTransactionClick(TransactionEntity transaction);
    }

    public interface OnTransactionLongClickListener {
        void onTransactionLongClick(TransactionEntity transaction);
    }

    public TransactionAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnTransactionClickListener(OnTransactionClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnTransactionLongClickListener(OnTransactionLongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setCategoryPresentationMap(@Nullable Map<String, CategoryPresentation> categoryPresentationMap) {
        if (categoryPresentationMap == null || categoryPresentationMap.isEmpty()) {
            this.categoryPresentationMap = Collections.emptyMap();
            return;
        }
        this.categoryPresentationMap = new HashMap<>(categoryPresentationMap);
    }

    public void setWalletPresentationMap(@Nullable Map<String, WalletPresentation> walletPresentationMap) {
        if (walletPresentationMap == null || walletPresentationMap.isEmpty()) {
            this.walletPresentationMap = Collections.emptyMap();
            return;
        }
        this.walletPresentationMap = new HashMap<>(walletPresentationMap);
    }

    public void addItems(@Nullable java.util.List<TransactionEntity> newItems) {
        if (newItems == null || newItems.isEmpty()) {
            return;
        }
        java.util.List<TransactionEntity> merged = new ArrayList<>(getCurrentList());
        merged.addAll(newItems);
        submitList(merged);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemTransactionBinding binding;
        private final OnTransactionClickListener clickListener;
        private final OnTransactionLongClickListener longClickListener;

        ViewHolder(ItemTransactionBinding binding,
                   OnTransactionClickListener clickListener,
                   OnTransactionLongClickListener longClickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.clickListener = clickListener;
            this.longClickListener = longClickListener;
        }

        void bind(TransactionEntity transaction,
                  @NonNull Map<String, CategoryPresentation> categoryPresentationMap,
                  @NonNull Map<String, WalletPresentation> walletPresentationMap) {
            Context context = binding.getRoot().getContext();
            double amount = transaction.getAmount();
            String type = transaction.getType();
            CategoryPresentation categoryPresentation = transaction.getCategoryId() != null
                    ? categoryPresentationMap.get(transaction.getCategoryId())
                    : null;
            WalletPresentation walletPresentation = transaction.getWalletId() != null
                    ? walletPresentationMap.get(transaction.getWalletId())
                    : null;

            if ("INCOME".equals(type)) {
                binding.tvAmount.setText(String.format("+%s", CurrencyFormatter.format(amount, "VND")));
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.transfer_blue));
            } else if ("EXPENSE".equals(type)) {
                binding.tvAmount.setText(String.format("-%s", CurrencyFormatter.format(amount, "VND")));
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_red));
            } else {
                binding.tvAmount.setText(CurrencyFormatter.format(amount, "VND"));
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.statistics_text_secondary));
            }

            applyCategoryIcon(context, categoryPresentation, walletPresentation);

            String dateText = DateUtils.formatDate(transaction.getTimestamp());
            binding.tvDate.setText(dateText);
            String note = transaction.getNote();
            binding.tvNote.setText((note != null && !note.trim().isEmpty())
                    ? note
                    : context.getString(R.string.transaction_detail_no_note));

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTransactionClick(transaction);
                }
            });

            binding.getRoot().setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onTransactionLongClick(transaction);
                    return true;
                }
                return false;
            });
        }

        private void applyCategoryIcon(@NonNull Context context,
                                       @Nullable CategoryPresentation categoryPresentation,
                                       @Nullable WalletPresentation walletPresentation) {
            int iconRes;
            if (categoryPresentation != null) {
                iconRes = categoryPresentation.getIconRes();
            } else {
                iconRes = resolveWalletIconRes(walletPresentation != null
                        ? walletPresentation.getWalletType()
                        : null);
            }

            binding.ivCategoryIcon.setImageResource(iconRes);
            binding.cvCategoryIconContainer.setCardBackgroundColor(
                    ContextCompat.getColor(context, android.R.color.white)
            );
        }

        private int resolveWalletIconRes(@Nullable String walletType) {
            if ("BANK".equals(walletType)) {
                return R.drawable.outline_account_balance_24;
            }
            if ("E_WALLET".equals(walletType)) {
                return R.drawable.outline_credit_card_24;
            }
            return R.drawable.outline_account_balance_wallet_24;
        }

        // ...existing code...
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTransactionBinding binding = ItemTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding, clickListener, longClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), categoryPresentationMap, walletPresentationMap);
    }

    public static class CategoryPresentation {
        private final int iconRes;
        private final int accentColor;

        public CategoryPresentation(int iconRes, int accentColor) {
            this.iconRes = iconRes;
            this.accentColor = accentColor;
        }

        public int getIconRes() {
            return iconRes;
        }

        public int getAccentColor() {
            return accentColor;
        }
    }

    public static class WalletPresentation {
        private final String walletType;
        private final int accentColor;

        public WalletPresentation(@Nullable String walletType, int accentColor) {
            this.walletType = walletType;
            this.accentColor = accentColor;
        }

        @Nullable
        public String getWalletType() {
            return walletType;
        }

        public int getAccentColor() {
            return accentColor;
        }
    }

    private static final DiffUtil.ItemCallback<TransactionEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<>() {
                @Override
                public boolean areItemsTheSame(@NonNull TransactionEntity oldItem,
                                               @NonNull TransactionEntity newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull TransactionEntity oldItem,
                                                  @NonNull TransactionEntity newItem) {
                    return oldItem.getUpdatedAt() == newItem.getUpdatedAt()
                            && oldItem.getAmount() == newItem.getAmount()
                            && oldItem.getType().equals(newItem.getType())
                            && oldItem.getTimestamp() == newItem.getTimestamp()
                            && oldItem.isDeleted() == newItem.isDeleted()
                            && Objects.equals(oldItem.getNote(), newItem.getNote())
                            && Objects.equals(oldItem.getCategoryId(), newItem.getCategoryId())
                            && Objects.equals(oldItem.getWalletId(), newItem.getWalletId());
                }
            };
}
