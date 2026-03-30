package com.group10.moneymate.ui.category;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.group10.moneymate.data.local.entity.CategoryEntity;

public final class CategoryListItem {

    public enum ItemType {
        ADD_NEW,
        GROUP
    }

    @NonNull
    private final ItemType itemType;
    @Nullable
    private final CategoryEntity rootCategory;
    @NonNull
    private final java.util.List<CategoryChildItem> children;
    @NonNull
    private final String walletLabel;

    private CategoryListItem(@NonNull ItemType itemType,
                             @Nullable CategoryEntity rootCategory,
                             @NonNull java.util.List<CategoryChildItem> children,
                             @NonNull String walletLabel) {
        this.itemType = itemType;
        this.rootCategory = rootCategory;
        this.children = children;
        this.walletLabel = walletLabel;
    }

    @NonNull
    public static CategoryListItem addNew(@NonNull String walletLabel) {
        return new CategoryListItem(ItemType.ADD_NEW, null, new java.util.ArrayList<>(), walletLabel);
    }

    @NonNull
    public static CategoryListItem group(@NonNull CategoryEntity rootCategory,
                                         @NonNull java.util.List<CategoryChildItem> children,
                                         @NonNull String walletLabel) {
        return new CategoryListItem(ItemType.GROUP, rootCategory, children, walletLabel);
    }

    @NonNull
    public ItemType getItemType() {
        return itemType;
    }

    @Nullable
    public CategoryEntity getRootCategory() {
        return rootCategory;
    }

    @NonNull
    public java.util.List<CategoryChildItem> getChildren() {
        return children;
    }

    @NonNull
    public String getWalletLabel() {
        return walletLabel;
    }

    @NonNull
    public String getStableId() {
        if (itemType == ItemType.ADD_NEW) {
            return "ADD_NEW";
        }
        if (rootCategory != null && rootCategory.getId() != null) {
            return rootCategory.getId();
        }
        return "UNKNOWN";
    }

    public boolean contentEquals(@NonNull CategoryListItem other) {
        if (itemType != other.itemType) {
            return false;
        }
        if (itemType == ItemType.ADD_NEW) {
            return true;
        }
        if (rootCategory == null || other.rootCategory == null) {
            return false;
        }
        if (!rootCategory.getId().equals(other.rootCategory.getId())) {
            return false;
        }
        if (rootCategory.getUpdatedAt() != other.rootCategory.getUpdatedAt()) {
            return false;
        }
        if (!walletLabel.equals(other.walletLabel)) {
            return false;
        }
        if (children.size() != other.children.size()) {
            return false;
        }
        for (int i = 0; i < children.size(); i++) {
            CategoryChildItem left = children.get(i);
            CategoryChildItem right = other.children.get(i);
            if (!left.contentEquals(right)) {
                return false;
            }
        }
        return true;
    }

    public static final class CategoryChildItem {
        @NonNull
        private final CategoryEntity category;
        @NonNull
        private final String walletLabel;

        public CategoryChildItem(@NonNull CategoryEntity category, @NonNull String walletLabel) {
            this.category = category;
            this.walletLabel = walletLabel;
        }

        @NonNull
        public CategoryEntity getCategory() {
            return category;
        }

        @NonNull
        public String getWalletLabel() {
            return walletLabel;
        }

        private boolean contentEquals(@NonNull CategoryChildItem other) {
            return category.getId().equals(other.category.getId())
                    && category.getUpdatedAt() == other.category.getUpdatedAt()
                    && walletLabel.equals(other.walletLabel);
        }
    }
}

