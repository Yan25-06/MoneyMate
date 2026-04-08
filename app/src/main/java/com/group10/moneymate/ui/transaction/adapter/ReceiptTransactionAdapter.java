package com.group10.moneymate.ui.transaction.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemReceiptTransactionBinding;

import java.util.Objects;

public class ReceiptTransactionAdapter extends ListAdapter<ReceiptTransactionAdapter.PendingReceiptItem,
        ReceiptTransactionAdapter.ViewHolder> {

    @Nullable
    private OnReceiptItemClickListener clickListener;

    public interface OnReceiptItemClickListener {
        void onReceiptItemClick(@NonNull PendingReceiptItem item);
    }

    public ReceiptTransactionAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnReceiptItemClickListener(@Nullable OnReceiptItemClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemReceiptTransactionBinding binding = ItemReceiptTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding, clickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemReceiptTransactionBinding binding;
        @Nullable
        private final OnReceiptItemClickListener clickListener;

        ViewHolder(@NonNull ItemReceiptTransactionBinding binding,
                   @Nullable OnReceiptItemClickListener clickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.clickListener = clickListener;
        }

        void bind(@NonNull PendingReceiptItem item) {
            binding.tvReceiptAmount.setText(item.getAmountLabel());
            binding.tvReceiptNote.setText(item.getNote());
            binding.tvReceiptCategory.setText(item.getCategoryLabel());
            binding.tvReceiptDate.setText(item.getDateLabel());
            binding.tvReceiptWarning.setText(item.getWarningLabel());
            binding.tvReceiptWarning.setVisibility(item.hasWarning() ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.ivReceiptWarning.setVisibility(item.hasWarning() ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.btnEditReceiptItem.setOnClickListener(v -> dispatchClick(item));
            binding.getRoot().setOnClickListener(v -> dispatchClick(item));
        }

        private void dispatchClick(@NonNull PendingReceiptItem item) {
            if (clickListener != null) {
                clickListener.onReceiptItemClick(item);
            }
        }
    }

    public static final class PendingReceiptItem {
        @NonNull
        private final String draftId;
        @NonNull
        private final String amountRaw;
        @NonNull
        private final String amountLabel;
        @NonNull
        private final String note;
        @NonNull
        private final String categoryHint;
        @NonNull
        private final String categoryLabel;
        private final long timestamp;
        @NonNull
        private final String dateLabel;
        private final int confidence;
        @NonNull
        private final String warningLabel;

        public PendingReceiptItem(@NonNull String draftId,
                                  @NonNull String amountRaw,
                                  @NonNull String amountLabel,
                                  @NonNull String note,
                                  @NonNull String categoryHint,
                                  @NonNull String categoryLabel,
                                  long timestamp,
                                  @NonNull String dateLabel,
                                  int confidence,
                                  @NonNull String warningLabel) {
            this.draftId = draftId;
            this.amountRaw = amountRaw;
            this.amountLabel = amountLabel;
            this.note = note;
            this.categoryHint = categoryHint;
            this.categoryLabel = categoryLabel;
            this.timestamp = timestamp;
            this.dateLabel = dateLabel;
            this.confidence = confidence;
            this.warningLabel = warningLabel;
        }

        @NonNull
        public String getDraftId() {
            return draftId;
        }

        @NonNull
        public String getAmountRaw() {
            return amountRaw;
        }

        @NonNull
        public String getAmountLabel() {
            return amountLabel;
        }

        @NonNull
        public String getNote() {
            return note;
        }

        @NonNull
        public String getCategoryHint() {
            return categoryHint;
        }

        @NonNull
        public String getCategoryLabel() {
            return categoryLabel;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @NonNull
        public String getDateLabel() {
            return dateLabel;
        }

        public int getConfidence() {
            return confidence;
        }

        @NonNull
        public String getWarningLabel() {
            return warningLabel;
        }

        public boolean hasWarning() {
            return !warningLabel.isEmpty();
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof PendingReceiptItem)) {
                return false;
            }
            PendingReceiptItem item = (PendingReceiptItem) other;
            return timestamp == item.timestamp
                    && confidence == item.confidence
                    && draftId.equals(item.draftId)
                    && amountRaw.equals(item.amountRaw)
                    && amountLabel.equals(item.amountLabel)
                    && note.equals(item.note)
                    && categoryHint.equals(item.categoryHint)
                    && categoryLabel.equals(item.categoryLabel)
                    && dateLabel.equals(item.dateLabel)
                    && warningLabel.equals(item.warningLabel);
        }

        @Override
        public int hashCode() {
            return Objects.hash(
                    draftId,
                    amountRaw,
                    amountLabel,
                    note,
                    categoryHint,
                    categoryLabel,
                    timestamp,
                    dateLabel,
                    confidence,
                    warningLabel
            );
        }
    }

    private static final DiffUtil.ItemCallback<PendingReceiptItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<PendingReceiptItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull PendingReceiptItem oldItem,
                                               @NonNull PendingReceiptItem newItem) {
                    return oldItem.getDraftId().equals(newItem.getDraftId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull PendingReceiptItem oldItem,
                                                  @NonNull PendingReceiptItem newItem) {
                    return oldItem.equals(newItem);
                }
            };
}
