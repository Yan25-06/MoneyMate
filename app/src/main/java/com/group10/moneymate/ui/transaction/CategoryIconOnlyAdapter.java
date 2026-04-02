package com.group10.moneymate.ui.transaction;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemCategoryIconOnlyBinding;
import com.group10.moneymate.ui.category.CategoryIconAdapter;
import com.group10.moneymate.utils.IconProvider;

import java.util.ArrayList;

public class CategoryIconOnlyAdapter extends ListAdapter<CategoryIconAdapter.CategoryIconItem,
        CategoryIconOnlyAdapter.ViewHolder> {

    public interface OnIconClickListener {
        void onIconClick(@NonNull CategoryIconAdapter.CategoryIconItem item);
    }

    @Nullable
    private OnIconClickListener clickListener;
    @Nullable
    private String selectedIconResId;
    @ColorInt
    private int tintColor;

    public CategoryIconOnlyAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnIconClickListener(@Nullable OnIconClickListener listener) {
        this.clickListener = listener;
    }

    public void setSelectedIconResId(@Nullable String selectedIconResId) {
        this.selectedIconResId = selectedIconResId;
        submitList(new ArrayList<>(getCurrentList()));
    }

    public void setTintColor(@ColorInt int tintColor) {
        this.tintColor = tintColor;
        submitList(new ArrayList<>(getCurrentList()));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryIconOnlyBinding binding = ItemCategoryIconOnlyBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), clickListener, selectedIconResId, tintColor);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryIconOnlyBinding binding;

        ViewHolder(@NonNull ItemCategoryIconOnlyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull CategoryIconAdapter.CategoryIconItem item,
                  @Nullable OnIconClickListener listener,
                  @Nullable String selectedIconResId,
                  @ColorInt int tintColor) {
            Context context = binding.getRoot().getContext();
            binding.ivIconOption.setImageResource(
                    IconProvider.resolveCategoryIcon(context, item.iconResId)
            );

            int effectiveTint = tintColor == 0
                    ? context.getColor(R.color.transaction_income_accent)
                    : tintColor;
            boolean selected = item.iconResId.equals(selectedIconResId);
            int strokeColor = selected
                    ? effectiveTint
                    : context.getColor(R.color.budget_divider);
            int backgroundColor = selected
                    ? ColorUtils.setAlphaComponent(effectiveTint, 28)
                    : context.getColor(android.R.color.white);
            binding.getRoot().setStrokeColor(strokeColor);
            binding.getRoot().setCardBackgroundColor(backgroundColor);

            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onIconClick(item);
                }
            });
        }
    }

    private static final DiffUtil.ItemCallback<CategoryIconAdapter.CategoryIconItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CategoryIconAdapter.CategoryIconItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull CategoryIconAdapter.CategoryIconItem oldItem,
                                               @NonNull CategoryIconAdapter.CategoryIconItem newItem) {
                    return oldItem.iconResId.equals(newItem.iconResId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull CategoryIconAdapter.CategoryIconItem oldItem,
                                                  @NonNull CategoryIconAdapter.CategoryIconItem newItem) {
                    return oldItem.iconResId.equals(newItem.iconResId)
                            && oldItem.label.equals(newItem.label);
                }
            };
}
