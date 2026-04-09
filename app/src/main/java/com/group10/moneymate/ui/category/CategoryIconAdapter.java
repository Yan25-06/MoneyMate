package com.group10.moneymate.ui.category;

import android.content.Context;
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
import com.group10.moneymate.databinding.ItemCategoryIconBinding;
import com.group10.moneymate.utils.IconProvider;

public class CategoryIconAdapter extends ListAdapter<CategoryIconAdapter.CategoryIconItem,
        CategoryIconAdapter.ViewHolder> {

    public interface OnIconClickListener {
        void onIconClick(@NonNull CategoryIconItem item);
    }

    private OnIconClickListener clickListener;
    private String selectedIconResId;
    @ColorInt
    private int tintColor;

    public CategoryIconAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnIconClickListener(OnIconClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setSelectedIconResId(String selectedIconResId) {
        this.selectedIconResId = selectedIconResId;
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setTintColor(@ColorInt int tintColor) {
        this.tintColor = tintColor;
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryIconBinding binding = ItemCategoryIconBinding.inflate(
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

    @Override
    public int getItemCount() {
        return super.getItemCount();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryIconBinding binding;

        ViewHolder(@NonNull ItemCategoryIconBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull CategoryIconItem item,
                  @Nullable OnIconClickListener listener,
                  @Nullable String selectedIconResId,
                  @ColorInt int tintColor) {
            Context context = binding.getRoot().getContext();
            binding.ivIconOption.setImageResource(
                    IconProvider.resolveCategoryIcon(context, item.iconResId)
            );
            binding.ivIconOption.setImageTintList(null);
            binding.tvIconOptionName.setText(item.label);

            boolean selected = item.iconResId.equals(selectedIconResId);
            int strokeColor = selected
                    ? tintColor
                    : context.getColor(R.color.budget_divider);
            int backgroundColor = selected
                    ? ColorUtils.setAlphaComponent(tintColor, 28)
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

    private static final DiffUtil.ItemCallback<CategoryIconItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CategoryIconItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull CategoryIconItem oldItem,
                                               @NonNull CategoryIconItem newItem) {
                    return oldItem.iconResId.equals(newItem.iconResId);
                }

                @Override
                public boolean areContentsTheSame(@NonNull CategoryIconItem oldItem,
                                                  @NonNull CategoryIconItem newItem) {
                    return oldItem.iconResId.equals(newItem.iconResId)
                            && oldItem.label.equals(newItem.label);
                }
            };

    public static class CategoryIconItem {
        public final String iconResId;
        public final String label;

        public CategoryIconItem(@NonNull String iconResId, @NonNull String label) {
            this.iconResId = iconResId;
            this.label = label;
        }
    }
}
