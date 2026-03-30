package com.group10.moneymate.ui.category;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.ItemCategoryAddNewBinding;
import com.group10.moneymate.databinding.ItemCategoryChildRowBinding;
import com.group10.moneymate.databinding.ItemCategoryHierarchyBinding;
import com.group10.moneymate.utils.IconProvider;

public class CategoryAdapter extends ListAdapter<CategoryListItem, RecyclerView.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(com.group10.moneymate.data.local.entity.CategoryEntity item);
    }

    public interface OnAddNewClickListener {
        void onAddNewClick();
    }

    private OnItemClickListener clickListener;
    private OnAddNewClickListener addNewClickListener;

    public CategoryAdapter() {
        super(DIFF_CALLBACK);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnAddNewClickListener(OnAddNewClickListener listener) {
        this.addNewClickListener = listener;
    }

    private static final DiffUtil.ItemCallback<CategoryListItem> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<CategoryListItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull CategoryListItem oldItem,
                                               @NonNull CategoryListItem newItem) {
                    return oldItem.getStableId().equals(newItem.getStableId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull CategoryListItem oldItem,
                                                  @NonNull CategoryListItem newItem) {
                    return oldItem.contentEquals(newItem);
                }
            };

    @NonNull
    @Override
    public int getItemViewType(int position) {
        return getItem(position).getItemType() == CategoryListItem.ItemType.ADD_NEW
                ? R.layout.item_category_add_new
                : R.layout.item_category_hierarchy;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == R.layout.item_category_add_new) {
            ItemCategoryAddNewBinding binding = ItemCategoryAddNewBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new AddNewViewHolder(binding);
        }
        ItemCategoryHierarchyBinding binding = ItemCategoryHierarchyBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new CategoryViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        CategoryListItem item = getItem(position);
        if (holder instanceof AddNewViewHolder) {
            ((AddNewViewHolder) holder).bind(addNewClickListener);
        } else if (holder instanceof CategoryViewHolder) {
            ((CategoryViewHolder) holder).bind(item, clickListener);
        }
    }

    static class AddNewViewHolder extends RecyclerView.ViewHolder {

        private final ItemCategoryAddNewBinding binding;

        AddNewViewHolder(@NonNull ItemCategoryAddNewBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(OnAddNewClickListener listener) {
            binding.getRoot().setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAddNewClick();
                }
            });
        }
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryHierarchyBinding binding;

        CategoryViewHolder(@NonNull ItemCategoryHierarchyBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull CategoryListItem item, OnItemClickListener listener) {
            com.group10.moneymate.data.local.entity.CategoryEntity rootCategory = item.getRootCategory();
            if (rootCategory == null) {
                return;
            }
            Context context = binding.getRoot().getContext();
            binding.tvCategoryName.setText(rootCategory.getName());
            binding.tvCategoryWallets.setText(item.getWalletLabel());
            int rootIcon = IconProvider.resolveCategoryIconByType(
                    context,
                    rootCategory.getIconName(),
                    rootCategory.getType()
            );
            binding.ivCategoryIcon.setImageResource(rootIcon);

            boolean hasChildren = !item.getChildren().isEmpty();
            binding.viewRootConnector.setVisibility(hasChildren ? android.view.View.VISIBLE : android.view.View.GONE);
            binding.rootRow.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onItemClick(rootCategory);
                }
            });

            binding.childContainer.removeAllViews();
            if (hasChildren) {
                for (CategoryListItem.CategoryChildItem child : item.getChildren()) {
                    ItemCategoryChildRowBinding childBinding = ItemCategoryChildRowBinding.inflate(
                            LayoutInflater.from(context),
                            binding.childContainer,
                            false
                    );
                    com.group10.moneymate.data.local.entity.CategoryEntity childCategory = child.getCategory();
                    int childIcon = IconProvider.resolveCategoryIconByType(
                            context,
                            childCategory.getIconName(),
                            childCategory.getType()
                    );
                    childBinding.ivChildIcon.setImageResource(childIcon);
                    childBinding.tvChildName.setText(childCategory.getName());
                    childBinding.tvChildWallets.setText(child.getWalletLabel());
                    childBinding.getRoot().setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onItemClick(childCategory);
                        }
                    });
                    binding.childContainer.addView(childBinding.getRoot());
                }
            }
        }
    }
}
