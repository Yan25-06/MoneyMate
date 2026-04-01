package com.group10.moneymate.ui.transaction;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.databinding.ItemReportTransactionBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ReportTransactionAdapter extends ListAdapter<TransactionEntity, ReportTransactionAdapter.ViewHolder> {

    private static final String TYPE_INCOME = "INCOME";
    private static final String TYPE_EXPENSE = "EXPENSE";
    private static final String TYPE_TRANSFER = "TRANSFER";

    public interface OnTransactionClickListener {
        void onTransactionClick(@NonNull TransactionEntity transaction);
    }

    @NonNull
    private Map<String, TransactionPresentation> presentationMap = Collections.emptyMap();
    @Nullable
    private OnTransactionClickListener clickListener;

    public ReportTransactionAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setPresentationMap(@Nullable Map<String, TransactionPresentation> presentationMap) {
        if (presentationMap == null || presentationMap.isEmpty()) {
            this.presentationMap = Collections.emptyMap();
            return;
        }
        this.presentationMap = new HashMap<>(presentationMap);
    }

    public void setOnTransactionClickListener(@Nullable OnTransactionClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReportTransactionBinding binding = ItemReportTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding, clickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), presentationMap);
    }

    static class ViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
        @NonNull
        private final ItemReportTransactionBinding binding;
        @Nullable
        private final OnTransactionClickListener clickListener;

        ViewHolder(@NonNull ItemReportTransactionBinding binding,
                   @Nullable OnTransactionClickListener clickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.clickListener = clickListener;
        }

        void bind(@NonNull TransactionEntity transaction,
                  @NonNull Map<String, TransactionPresentation> presentationMap) {
            Context context = binding.getRoot().getContext();
            TransactionPresentation presentation = presentationMap.get(transaction.getId());
            if (presentation == null) {
                presentation = TransactionPresentation.fallback(context, transaction.getType());
            }

            binding.ivCategoryIcon.setImageResource(presentation.getIconRes());
            binding.ivCategoryIcon.setImageTintList(null);
            binding.cvCategoryIconContainer.setCardBackgroundColor(
                    ContextCompat.getColor(context, android.R.color.white)
            );
            binding.tvTitle.setText(presentation.getTitle());
            binding.tvSubtitle.setText(presentation.getSubtitle());
            binding.tvAmount.setText(presentation.getAmountLabel());
            binding.tvAmount.setTextColor(presentation.getAmountColor());

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTransactionClick(transaction);
                }
            });
        }

        // ...existing code...
    }

    public static class TransactionPresentation {
        private final int iconRes;
        private final int accentColor;
        @NonNull private final String title;
        @NonNull private final String subtitle;
        @NonNull private final String amountLabel;
        private final int amountColor;

        public TransactionPresentation(int iconRes,
                                       int accentColor,
                                       @NonNull String title,
                                       @NonNull String subtitle,
                                       @NonNull String amountLabel,
                                       int amountColor) {
            this.iconRes = iconRes;
            this.accentColor = accentColor;
            this.title = title;
            this.subtitle = subtitle;
            this.amountLabel = amountLabel;
            this.amountColor = amountColor;
        }

        public int getIconRes() {
            return iconRes;
        }

        public int getAccentColor() {
            return accentColor;
        }

        @NonNull
        public String getTitle() {
            return title;
        }

        @NonNull
        public String getSubtitle() {
            return subtitle;
        }

        @NonNull
        public String getAmountLabel() {
            return amountLabel;
        }

        public int getAmountColor() {
            return amountColor;
        }

        @NonNull
        static TransactionPresentation fallback(@NonNull Context context, @Nullable String type) {
            int accent = ContextCompat.getColor(context, resolveAccentColor(type));
            return new TransactionPresentation(
                    resolveIcon(type),
                    accent,
                    context.getString(R.string.ledger_section_unknown),
                    context.getString(R.string.transaction_detail_no_note),
                    CurrencyFormatter.format(0d, "VND"),
                    accent
            );
        }

        private static int resolveAccentColor(@Nullable String type) {
            if (TYPE_INCOME.equals(type)) {
                return R.color.transfer_blue;
            }
            if (TYPE_EXPENSE.equals(type)) {
                return R.color.expense_red;
            }
            return R.color.statistics_text_secondary;
        }

        private static int resolveIcon(@Nullable String type) {
            if (TYPE_INCOME.equals(type)) {
                return R.drawable.outline_attach_money_24;
            }
            if (TYPE_TRANSFER.equals(type)) {
                return R.drawable.outline_payments_24;
            }
            return R.drawable.ic_category_spending;
        }
    }

    private static final DiffUtil.ItemCallback<TransactionEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<TransactionEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull TransactionEntity oldItem, @NonNull TransactionEntity newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull TransactionEntity oldItem, @NonNull TransactionEntity newItem) {
                    return oldItem.getAmount() == newItem.getAmount()
                            && oldItem.getTimestamp() == newItem.getTimestamp()
                            && Objects.equals(oldItem.getType(), newItem.getType())
                            && Objects.equals(oldItem.getCategoryId(), newItem.getCategoryId())
                            && Objects.equals(oldItem.getWalletId(), newItem.getWalletId())
                            && Objects.equals(oldItem.getNote(), newItem.getNote());
                }
            };
}
