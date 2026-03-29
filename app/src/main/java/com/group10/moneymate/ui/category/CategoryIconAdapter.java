package com.group10.moneymate.ui.category;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemCategoryIconBinding;

import java.util.ArrayList;
import java.util.List;

public class CategoryIconAdapter extends RecyclerView.Adapter<CategoryIconAdapter.ViewHolder> {

    public interface OnIconClickListener {
        void onIconClick(@NonNull CategoryIconItem item);
    }

    private final List<CategoryIconItem> items = new ArrayList<>();
    private OnIconClickListener clickListener;
    private String selectedIconResId;
    @ColorInt
    private int tintColor;

    public void submitList(@NonNull List<CategoryIconItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void setOnIconClickListener(OnIconClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void setSelectedIconResId(String selectedIconResId) {
        this.selectedIconResId = selectedIconResId;
        notifyDataSetChanged();
    }

    public void setTintColor(@ColorInt int tintColor) {
        this.tintColor = tintColor;
        notifyDataSetChanged();
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
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryIconBinding binding;

        ViewHolder(@NonNull ItemCategoryIconBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull CategoryIconItem item) {
            Context context = binding.getRoot().getContext();
            int iconResId = context.getResources().getIdentifier(
                    item.iconResId,
                    "drawable",
                    context.getPackageName()
            );
            binding.ivIconOption.setImageResource(iconResId != 0 ? iconResId : R.drawable.ic_category_other);
            binding.ivIconOption.setImageTintList(ColorStateList.valueOf(tintColor));
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
                if (clickListener != null) {
                    clickListener.onIconClick(item);
                }
            });
        }
    }

    public static class CategoryIconItem {
        public final String iconResId;
        public final String label;

        public CategoryIconItem(@NonNull String iconResId, @NonNull String label) {
            this.iconResId = iconResId;
            this.label = label;
        }
    }
}
