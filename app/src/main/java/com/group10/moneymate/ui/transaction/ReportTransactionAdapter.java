package com.group10.moneymate.ui.transaction;

import android.content.Context;
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
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.databinding.ItemReportTransactionBinding;
import com.group10.moneymate.utils.CurrencyFormatter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ReportTransactionAdapter extends ListAdapter<TransactionEntity, ReportTransactionAdapter.ViewHolder> {

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

            binding.ivCategoryIcon.setImageResource(presentation.getIconResId());
            binding.ivCategoryIcon.setImageTintList(ColorStateList.valueOf(presentation.getAccentColor()));
            binding.cvCategoryIconContainer.setCardBackgroundColor(applyAlpha(presentation.getAccentColor(), 0.12f));
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

        private int applyAlpha(int color, float alphaFraction) {
            int alpha = Math.min(255, Math.max(0, Math.round(alphaFraction * 255f)));
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
        }
    }

    public static class TransactionPresentation {
        private final int iconResId;
        private final int accentColor;
        @NonNull private final String title;
        @NonNull private final String subtitle;
        @NonNull private final String amountLabel;
        private final int amountColor;

        public TransactionPresentation(int iconResId,
                                       int accentColor,
                                       @NonNull String title,
                                       @NonNull String subtitle,
                                       @NonNull String amountLabel,
                                       int amountColor) {
            this.iconResId = iconResId;
            this.accentColor = accentColor;
            this.title = title;
            this.subtitle = subtitle;
            this.amountLabel = amountLabel;
            this.amountColor = amountColor;
        }

        public int getIconResId() {
            return iconResId;
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
            int accent = ContextCompat.getColor(
                    context,
                    "INCOME".equals(type) ? R.color.transfer_blue : ("EXPENSE".equals(type) ? R.color.expense_red : R.color.statistics_text_secondary)
            );
            return new TransactionPresentation(
                    "INCOME".equals(type) ? R.drawable.outline_attach_money_24 : ("TRANSFER".equals(type) ? R.drawable.outline_payments_24 : R.drawable.ic_spending),
                    accent,
                    context.getString(R.string.ledger_section_unknown),
                    context.getString(R.string.transaction_detail_no_note),
                    CurrencyFormatter.format(0d, "VND"),
                    accent
            );
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
