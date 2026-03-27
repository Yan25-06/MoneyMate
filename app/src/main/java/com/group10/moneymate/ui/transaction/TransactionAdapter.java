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

        void bind(TransactionEntity transaction) {
            Context context = binding.getRoot().getContext();
            double amount = transaction.getAmount();
            String type = transaction.getType();

            // 1. Set màu chữ và Icon dựa theo Loại Giao Dịch
            if ("INCOME".equals(type)) {
                binding.tvAmount.setText(String.format("+%s", CurrencyFormatter.format(amount, "VND")));
                // Dùng màu từ resources thay vì hard-code
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.green_500));

                binding.ivCategoryIcon.setImageResource(R.drawable.outline_attach_money_24);

            } else if ("EXPENSE".equals(type)) {
                binding.tvAmount.setText(String.format("-%s", CurrencyFormatter.format(amount, "VND")));
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.red_500));

                binding.ivCategoryIcon.setImageResource(R.drawable.ic_spending);

            } else {
                // TRANSFER
                binding.tvAmount.setText(CurrencyFormatter.format(amount, "VND"));
                // Thay màu hard-code bằng màu primary của theme
                binding.tvAmount.setTextColor(ContextCompat.getColor(context, R.color.md_theme_primary));

                binding.ivCategoryIcon.setImageResource(R.drawable.outline_payments_24);
            }

            // 2. Set Note
            String note = transaction.getNote();
            // Nếu Note trống, có thể để mặc định là "Chưa có ghi chú" hoặc "Chi tiêu" để giao diện không bị hụt.
            binding.tvNote.setText((note != null && !note.trim().isEmpty()) ? note : "Giao dịch " + type);

            // 3. Set Date
            binding.tvDate.setText(DateUtils.formatDate(transaction.getTimestamp()));

            // 4. Listeners
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
        holder.bind(getItem(position));
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
                    return oldItem.getAmount() == newItem.getAmount()
                            && oldItem.getType().equals(newItem.getType())
                            && oldItem.getTimestamp() == newItem.getTimestamp()
                            && java.util.Objects.equals(oldItem.getNote(), newItem.getNote())
                            && java.util.Objects.equals(oldItem.getCategoryId(), newItem.getCategoryId());
                }
            };
}