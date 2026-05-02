package com.group10.moneymate.ui.transaction;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemTransactionCategoryPickerBinding;
import com.group10.moneymate.models.DebtType;
import com.group10.moneymate.utils.IconProvider;

import java.util.ArrayList;

public class TransactionCategoryPickerAdapter
        extends ListAdapter<TransactionCategoryPickerItem, RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(@NonNull TransactionCategoryPickerItem item);
    }

    @Nullable
    private OnItemClickListener clickListener;
    @Nullable
    private String selectedCategoryId;
    @Nullable
    private DebtType selectedDebtType;

    public TransactionCategoryPickerAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setSelectedCategoryId(@Nullable String selectedCategoryId) {
        this.selectedCategoryId = selectedCategoryId;
        submitList(new ArrayList<>(getCurrentList()));
    }

    public void setSelectedDebtType(@Nullable DebtType selectedDebtType) {
        this.selectedDebtType = selectedDebtType;
        submitList(new ArrayList<>(getCurrentList()));
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == R.layout.item_transaction_debt_picker) {
            com.group10.moneymate.databinding.ItemTransactionDebtPickerBinding binding =
                    com.group10.moneymate.databinding.ItemTransactionDebtPickerBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    );
            return new DebtViewHolder(binding);
        }
        ItemTransactionCategoryPickerBinding binding = ItemTransactionCategoryPickerBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new CategoryViewHolder(binding);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).isDebt()
                ? R.layout.item_transaction_debt_picker
                : R.layout.item_transaction_category_picker;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        TransactionCategoryPickerItem item = getItem(position);
        if (holder instanceof DebtViewHolder) {
            ((DebtViewHolder) holder).bind(item, clickListener, selectedDebtType);
        } else if (holder instanceof CategoryViewHolder) {
            ((CategoryViewHolder) holder).bind(item, clickListener, selectedCategoryId);
        }
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final ItemTransactionCategoryPickerBinding binding;

        CategoryViewHolder(@NonNull ItemTransactionCategoryPickerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull TransactionCategoryPickerItem item,
                  @Nullable OnItemClickListener listener,
                  @Nullable String selectedCategoryId) {
            Context context = binding.getRoot().getContext();
            TransactionCategoryPickerItem.CategoryGroup group = item.getGroup();
            if (group == null) {
                return;
            }
            com.group10.moneymate.data.local.entity.CategoryEntity root = group.getRoot();
            binding.tvCategoryName.setText(root.getName());
            binding.tvCategoryWallets.setText(group.getWalletLabel());
            int iconRes = IconProvider.resolveCategoryIcon(context, root.getIconName());
            binding.ivCategoryIcon.setImageResource(iconRes);

            boolean hasChildren = !group.getChildren().isEmpty();
            binding.viewRootConnector.setVisibility(hasChildren ? View.VISIBLE : View.GONE);

            binding.rootRow.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });

            binding.childContainer.removeAllViews();
            if (hasChildren) {
                for (TransactionCategoryPickerItem.CategoryChildItem child : group.getChildren()) {
                    com.group10.moneymate.databinding.ItemCategoryChildRowBinding childBinding =
                            com.group10.moneymate.databinding.ItemCategoryChildRowBinding.inflate(
                                    LayoutInflater.from(context),
                                    binding.childContainer,
                                    false
                            );
                    com.group10.moneymate.data.local.entity.CategoryEntity childCategory = child.getCategory();
                    int childIcon = IconProvider.resolveCategoryIcon(context, childCategory.getIconName());
                    childBinding.ivChildIcon.setImageResource(childIcon);
                    childBinding.tvChildName.setText(childCategory.getName());
                    childBinding.tvChildWallets.setText(child.getWalletLabel());
                    childBinding.getRoot().setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onItemClick(TransactionCategoryPickerItem.forCategoryGroup(
                                    childCategory,
                                    new java.util.ArrayList<>(),
                                    child.getWalletLabel()
                            ));
                        }
                    });
                    binding.childContainer.addView(childBinding.getRoot());
                }
            }

            boolean selected = item.containsCategoryId(selectedCategoryId);
            styleSelection(selected, context);
        }

        private void styleSelection(boolean selected, @NonNull Context context) {
            int strokeColor = context.getColor(selected
                    ? R.color.transaction_income_accent
                    : R.color.transaction_border);
            int backgroundColor = context.getColor(selected
                    ? R.color.transaction_chip_bg_selected
                    : R.color.white);
            binding.getRoot().setStrokeColor(ColorStateList.valueOf(strokeColor));
            binding.getRoot().setCardBackgroundColor(ColorStateList.valueOf(backgroundColor));
        }
    }

    static class DebtViewHolder extends RecyclerView.ViewHolder {
        private final com.group10.moneymate.databinding.ItemTransactionDebtPickerBinding binding;

        DebtViewHolder(@NonNull com.group10.moneymate.databinding.ItemTransactionDebtPickerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull TransactionCategoryPickerItem item,
                  @Nullable OnItemClickListener listener,
                  @Nullable DebtType selectedDebtType) {
            Context context = binding.getRoot().getContext();
            DebtType debtType = item.getDebtType();
            if (debtType == null) {
                return;
            }

            int iconRes;
            int nameRes;
            switch (debtType) {
                case LEND:
                    iconRes = R.drawable.outline_attach_money_24;
                    nameRes = R.string.debt_type_lend;
                    break;
                case BORROW:
                    iconRes = R.drawable.outline_account_balance_wallet_24;
                    nameRes = R.string.debt_type_borrow;
                    break;
                case DEBT_COLLECTION:
                    iconRes = R.drawable.outline_payments_24;
                    nameRes = R.string.debt_type_collection;
                    break;
                case REPAYMENT:
                    iconRes = R.drawable.outline_credit_card_24;
                    nameRes = R.string.debt_type_repayment;
                    break;
                default:
                    iconRes = R.drawable.outline_attach_money_24;
                    nameRes = R.string.debt_type_lend;
                    break;
            }
            binding.ivDebtIcon.setImageResource(iconRes);
            binding.tvDebtName.setText(nameRes);

            boolean selected = debtType == selectedDebtType;
            int strokeColor = context.getColor(selected
                    ? R.color.transaction_income_accent
                    : R.color.transaction_border);
            int backgroundColor = context.getColor(selected
                    ? R.color.transaction_chip_bg_selected
                    : R.color.white);
            binding.getRoot().setStrokeColor(ColorStateList.valueOf(strokeColor));
            binding.getRoot().setCardBackgroundColor(ColorStateList.valueOf(backgroundColor));

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(item);
                }
            });
        }
    }

    private static final DiffUtil.ItemCallback<TransactionCategoryPickerItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<TransactionCategoryPickerItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull TransactionCategoryPickerItem oldItem,
                                               @NonNull TransactionCategoryPickerItem newItem) {
                    return oldItem.getStableId().equals(newItem.getStableId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull TransactionCategoryPickerItem oldItem,
                                                  @NonNull TransactionCategoryPickerItem newItem) {
                    return oldItem.contentEquals(newItem);
                }
            };
}
