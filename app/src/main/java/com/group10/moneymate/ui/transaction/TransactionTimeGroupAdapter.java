package com.group10.moneymate.ui.transaction;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.databinding.ItemTransactionTimeGroupCardBinding;
import com.group10.moneymate.databinding.ItemTransactionTimeGroupRowBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TransactionTimeGroupAdapter extends ListAdapter<TransactionTimeGroupAdapter.GroupItem, TransactionTimeGroupAdapter.ViewHolder> {

    public interface OnTransactionClickListener {
        void onTransactionClick(@NonNull TransactionEntity transaction);
    }

    @Nullable
    private OnTransactionClickListener clickListener;

    public TransactionTimeGroupAdapter() {
        super(DIFF_CALLBACK);
    }

    public void submitList(@Nullable List<GroupItem> nextItems) {
        super.submitList(nextItems == null ? new ArrayList<>() : new ArrayList<>(nextItems));
    }

    public void setOnTransactionClickListener(@Nullable OnTransactionClickListener clickListener) {
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTransactionTimeGroupCardBinding binding = ItemTransactionTimeGroupCardBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), clickListener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        @NonNull
        private final ItemTransactionTimeGroupCardBinding binding;

        ViewHolder(@NonNull ItemTransactionTimeGroupCardBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull GroupItem item,
                  @Nullable OnTransactionClickListener clickListener) {
            binding.tvPrimaryValue.setText(item.getPrimaryValue());
            binding.tvTitle.setText(item.getTitle());
            binding.tvSubtitle.setText(item.getSubtitle());
            binding.tvGroupTotal.setText(item.getTotalLabel());
            binding.layoutRows.removeAllViews();

            LayoutInflater inflater = LayoutInflater.from(binding.getRoot().getContext());
            List<RowItem> rows = item.getRows();
            for (int index = 0; index < rows.size(); index++) {
                RowItem row = rows.get(index);
                ItemTransactionTimeGroupRowBinding rowBinding = ItemTransactionTimeGroupRowBinding.inflate(
                        inflater,
                        binding.layoutRows,
                        false
                );
                rowBinding.ivCategoryIcon.setImageResource(row.getIconRes());
                rowBinding.ivCategoryIcon.setImageTintList(null);
                rowBinding.tvTitle.setText(row.getTitle());
                rowBinding.tvSubtitle.setText(row.getSubtitle());
                rowBinding.tvAmount.setText(row.getAmountLabel());
                rowBinding.tvAmount.setTextColor(row.getAmountColor());
                rowBinding.viewDivider.setVisibility(index == rows.size() - 1 ? View.GONE : View.VISIBLE);
                rowBinding.getRoot().setOnClickListener(v -> {
                    if (clickListener != null) {
                        clickListener.onTransactionClick(row.getTransaction());
                    }
                });
                binding.layoutRows.addView(rowBinding.getRoot());
            }
        }
    }

    public static final class GroupItem {
        @NonNull
        private final String id;
        @NonNull
        private final String primaryValue;
        @NonNull
        private final String title;
        @NonNull
        private final String subtitle;
        @NonNull
        private final String totalLabel;
        @NonNull
        private final List<RowItem> rows;

        public GroupItem(@NonNull String id,
                         @NonNull String primaryValue,
                         @NonNull String title,
                         @NonNull String subtitle,
                         @NonNull String totalLabel,
                         @NonNull List<RowItem> rows) {
            this.id = id;
            this.primaryValue = primaryValue;
            this.title = title;
            this.subtitle = subtitle;
            this.totalLabel = totalLabel;
            this.rows = rows;
        }

        @NonNull
        public String getId() {
            return id;
        }

        @NonNull
        public String getPrimaryValue() {
            return primaryValue;
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
        public String getTotalLabel() {
            return totalLabel;
        }

        @NonNull
        public List<RowItem> getRows() {
            return rows;
        }
    }

    public static final class RowItem {
        @NonNull
        private final TransactionEntity transaction;
        private final int iconRes;
        @NonNull
        private final String title;
        @NonNull
        private final String subtitle;
        @NonNull
        private final String amountLabel;
        @ColorInt
        private final int amountColor;

        public RowItem(@NonNull TransactionEntity transaction,
                       int iconRes,
                       @NonNull String title,
                       @NonNull String subtitle,
                       @NonNull String amountLabel,
                       @ColorInt int amountColor) {
            this.transaction = transaction;
            this.iconRes = iconRes;
            this.title = title;
            this.subtitle = subtitle;
            this.amountLabel = amountLabel;
            this.amountColor = amountColor;
        }

        @NonNull
        public TransactionEntity getTransaction() {
            return transaction;
        }

        public int getIconRes() {
            return iconRes;
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
    }

    private static final DiffUtil.ItemCallback<GroupItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<GroupItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull GroupItem oldItem, @NonNull GroupItem newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull GroupItem oldItem, @NonNull GroupItem newItem) {
                    if (!Objects.equals(oldItem.getPrimaryValue(), newItem.getPrimaryValue())
                            || !Objects.equals(oldItem.getTitle(), newItem.getTitle())
                            || !Objects.equals(oldItem.getSubtitle(), newItem.getSubtitle())
                            || !Objects.equals(oldItem.getTotalLabel(), newItem.getTotalLabel())) {
                        return false;
                    }

                    List<RowItem> oldRows = oldItem.getRows();
                    List<RowItem> newRows = newItem.getRows();
                    if (oldRows.size() != newRows.size()) {
                        return false;
                    }
                    for (int i = 0; i < oldRows.size(); i++) {
                        RowItem oldRow = oldRows.get(i);
                        RowItem newRow = newRows.get(i);
                        if (!oldRow.getTransaction().getId().equals(newRow.getTransaction().getId())
                                || oldRow.getTransaction().getUpdatedAt() != newRow.getTransaction().getUpdatedAt()
                                || oldRow.getIconRes() != newRow.getIconRes()
                                || oldRow.getAmountColor() != newRow.getAmountColor()
                                || !Objects.equals(oldRow.getTitle(), newRow.getTitle())
                                || !Objects.equals(oldRow.getSubtitle(), newRow.getSubtitle())
                                || !Objects.equals(oldRow.getAmountLabel(), newRow.getAmountLabel())) {
                            return false;
                        }
                    }
                    return true;
                }
            };
}
