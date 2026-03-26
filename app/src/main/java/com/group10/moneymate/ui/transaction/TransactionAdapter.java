package com.group10.moneymate.ui.transaction;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.databinding.ItemTransactionBinding;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.DateUtils;
import com.group10.moneymate.utils.PrefsManager;

public class TransactionAdapter extends ListAdapter<TransactionEntity, TransactionAdapter.ViewHolder> {

    private OnTransactionClickListener clickListener;
    private OnTransactionLongClickListener longClickListener;

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

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTransactionBinding binding = ItemTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemTransactionBinding binding;
        private final PrefsManager prefsManager;

        ViewHolder(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.prefsManager = new PrefsManager(binding.getRoot().getContext());
        }

        void bind(TransactionEntity transaction) {
            Context context = binding.getRoot().getContext();
            double amount = transaction.getAmount();
            String type = transaction.getType();
            String currency = prefsManager.getCurrency();

            if ("INCOME".equals(type)) {
                binding.tvAmount.setText(String.format("+%s", CurrencyFormatter.format(amount, currency)));
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.income_color));
            } else if ("EXPENSE".equals(type)) {
                binding.tvAmount.setText(String.format("-%s", CurrencyFormatter.format(amount, currency)));
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.expense_color));
            } else {
                // TRANSFER
                binding.tvAmount.setText(CurrencyFormatter.format(amount, currency));
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.transfer_color));
            }

            String note = transaction.getNote();
            binding.tvNote.setText((note != null && !note.isEmpty())
                    ? note
                    : context.getString(R.string.note_empty));

            binding.tvDate.setText(DateUtils.formatDate(transaction.getTimestamp()));
            binding.tvType.setText(getTypeLabel(context, type));

            // Click
            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) clickListener.onTransactionClick(transaction);
            });

            binding.getRoot().setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onTransactionLongClick(transaction);
                    return true;
                }
                return false;
            });
        }
    }

    private String getTypeLabel(Context context, String type) {
        if ("INCOME".equals(type)) {
            return context.getString(R.string.income);
        }
        if ("EXPENSE".equals(type)) {
            return context.getString(R.string.expense);
        }
        return context.getString(R.string.transfer);
    }

    private static final DiffUtil.ItemCallback<TransactionEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<TransactionEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull TransactionEntity oldItem,
                                               @NonNull TransactionEntity newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull TransactionEntity oldItem,
                                                  @NonNull TransactionEntity newItem) {
                    return oldItem.getAmount() == newItem.getAmount()
                            && oldItem.getType().equals(newItem.getType())
                            && oldItem.getTimestamp() == newItem.getTimestamp()
                            && java.util.Objects.equals(oldItem.getNote(), newItem.getNote())
                            && java.util.Objects.equals(oldItem.getCategoryId(), newItem.getCategoryId())
                            && java.util.Objects.equals(oldItem.getWalletId(), newItem.getWalletId())
                            && java.util.Objects.equals(oldItem.getToWalletId(), newItem.getToWalletId());
                }
            };
}