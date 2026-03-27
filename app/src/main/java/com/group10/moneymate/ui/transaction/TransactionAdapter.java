package com.group10.moneymate.ui.transaction;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.databinding.ItemTransactionBinding;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.DateUtils;

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

        ViewHolder(ItemTransactionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TransactionEntity transaction) {
            // Amount + màu theo loại
            double amount = transaction.getAmount();
            String type   = transaction.getType();

            if ("INCOME".equals(type)) {
                binding.tvAmount.setText(String.format("+%s", CurrencyFormatter.format(amount, "VND")));
                binding.tvAmount.setTextColor(Color.parseColor("#4CAF50"));
            } else if ("EXPENSE".equals(type)) {
                binding.tvAmount.setText(String.format("-%s", CurrencyFormatter.format(amount, "VND")));
                binding.tvAmount.setTextColor(Color.parseColor("#F44336"));
            } else {
                // TRANSFER
                binding.tvAmount.setText(CurrencyFormatter.format(amount, "VND"));
                binding.tvAmount.setTextColor(Color.parseColor("#2196F3"));
            }

            // Note
            String note = transaction.getNote();
            binding.tvNote.setText((note != null && !note.isEmpty()) ? note : "—");

            // Date
            binding.tvDate.setText(DateUtils.formatDate(transaction.getTimestamp()));

            // Type badge
            binding.tvType.setText(type);

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
                            && java.util.Objects.equals(oldItem.getCategoryId(), newItem.getCategoryId());
                }
            };
}