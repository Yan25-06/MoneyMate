package com.group10.moneymate.ui.category;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.databinding.ItemParentCategoryPickerBinding;
import com.group10.moneymate.utils.IconProvider;

public class ParentCategoryPickerAdapter
        extends ListAdapter<CategoryEntity, ParentCategoryPickerAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(@NonNull CategoryEntity category);
    }

    @Nullable
    private OnItemClickListener clickListener;
    @Nullable
    private String selectedParentId;

    public ParentCategoryPickerAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(@Nullable OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setSelectedParentId(@Nullable String selectedParentId) {
        this.selectedParentId = selectedParentId;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemParentCategoryPickerBinding binding = ItemParentCategoryPickerBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position), clickListener, selectedParentId);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemParentCategoryPickerBinding binding;

        ViewHolder(@NonNull ItemParentCategoryPickerBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull CategoryEntity category,
                  @Nullable OnItemClickListener listener,
                  @Nullable String selectedParentId) {
            Context context = binding.getRoot().getContext();
            binding.ivParentIcon.setImageResource(
                    IconProvider.resolveCategoryIcon(context, category.getIconName())
            );
            binding.tvParentName.setText(category.getName());

            boolean selected = category.getId() != null && category.getId().equals(selectedParentId);
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
                    listener.onItemClick(category);
                }
            });
        }
    }

    private static final DiffUtil.ItemCallback<CategoryEntity> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CategoryEntity>() {
                @Override
                public boolean areItemsTheSame(@NonNull CategoryEntity oldItem,
                                               @NonNull CategoryEntity newItem) {
                    return oldItem.getId().equals(newItem.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull CategoryEntity oldItem,
                                                  @NonNull CategoryEntity newItem) {
                    return oldItem.getUpdatedAt() == newItem.getUpdatedAt();
                }
            };
}

