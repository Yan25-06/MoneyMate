package com.group10.moneymate.ui.budget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemBudgetBreakdownBinding;

public class BudgetBreakdownAdapter extends ListAdapter<BudgetUIModel, BudgetBreakdownAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onClick(BudgetUIModel item);
    }

    private OnItemClickListener onItemClickListener;

    public BudgetBreakdownAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.onItemClickListener = onItemClickListener;
    }

    private static final DiffUtil.ItemCallback<BudgetUIModel> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<BudgetUIModel>() {
                @Override
                public boolean areItemsTheSame(@NonNull BudgetUIModel oldItem, @NonNull BudgetUIModel newItem) {
                    return oldItem.getBudgetEntity().getId().equals(newItem.getBudgetEntity().getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull BudgetUIModel oldItem, @NonNull BudgetUIModel newItem) {
                    return oldItem.getSpentAmount() == newItem.getSpentAmount()
                            && oldItem.getBudgetEntity().getAmount() == newItem.getBudgetEntity().getAmount();
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(ItemBudgetBreakdownBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        ));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), onItemClickListener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemBudgetBreakdownBinding binding;

        ViewHolder(@NonNull ItemBudgetBreakdownBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull BudgetUIModel item, OnItemClickListener clickListener) {
            Context context = binding.getRoot().getContext();
            int iconTint = BudgetUiUtils.parseColorOrDefault(
                    item.getCategoryColorHex(),
                    ContextCompat.getColor(context, R.color.budget_safe_green)
            );
            binding.ivIcon.setImageResource(BudgetUiUtils.resolveCategoryIcon(
                    context,
                    item.getCategoryIcon(),
                    item.getCategoryName()
            ));
            binding.ivIcon.setImageTintList(ColorStateList.valueOf(iconTint));
            binding.iconContainer.setBackgroundTintList(ColorStateList.valueOf(
                    androidx.core.graphics.ColorUtils.setAlphaComponent(iconTint, 32)
            ));
            binding.tvCategory.setText(item.getCategoryName());
            binding.tvAmount.setText(BudgetUiUtils.formatCurrency(item.getSpentAmount()));
            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onClick(item);
                }
            });
        }
    }
}
