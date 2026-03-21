package com.group10.moneymate.ui.category;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.databinding.ItemCategoryBinding;

public class CategoryAdapter extends ListAdapter<CategoryEntity, CategoryAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(CategoryEntity item);
    }

    public interface OnItemDeleteListener {
        void onItemDelete(CategoryEntity item);
    }

    private OnItemClickListener clickListener;
    private OnItemDeleteListener deleteListener;

    public CategoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnItemDeleteListener(OnItemDeleteListener listener) {
        this.deleteListener = listener;
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
                    return oldItem.getId().equals(newItem.getId())
                            && oldItem.getName().equals(newItem.getName())
                            && oldItem.getColorHex().equals(newItem.getColorHex())
                            && oldItem.isDefault() == newItem.isDefault();
                }
            };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCategoryBinding binding = ItemCategoryBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final ItemCategoryBinding binding;

        ViewHolder(ItemCategoryBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CategoryEntity item) {
            binding.tvCategoryName.setText(item.getName());

            // Badge "Mặc định" chỉ hiện với default category
            binding.tvDefaultBadge.setVisibility(item.isDefault() ? View.VISIBLE : View.GONE);

            // Màu icon theo colorHex
            try {
                int color = android.graphics.Color.parseColor(item.getColorHex());
                binding.ivCategoryIcon.setColorFilter(color);
            } catch (IllegalArgumentException e) {
                binding.ivCategoryIcon.setColorFilter(
                        binding.getRoot().getContext()
                                .getColor(R.color.md_theme_primary));
            }

            // Nút xóa: chỉ hiển thị với danh mục tùy chỉnh
            binding.btnDelete.setVisibility(item.isDefault() ? View.GONE : View.VISIBLE);
            binding.btnDelete.setOnClickListener(v -> {
                if (deleteListener != null) deleteListener.onItemDelete(item);
            });

            // Click để edit
            itemView.setOnClickListener(v -> {
                if (clickListener != null) clickListener.onItemClick(item);
            });
        }
    }
}