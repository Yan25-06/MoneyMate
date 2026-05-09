package com.group10.moneymate.ui.transaction;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.group10.moneymate.data.local.entity.CategoryEntity;

public final class TransactionCategoryPickerItem {

    @NonNull
    private final CategoryGroup group;

    private TransactionCategoryPickerItem(@NonNull CategoryGroup group) {
        this.group = group;
    }

    @NonNull
    public static TransactionCategoryPickerItem forCategoryGroup(@NonNull CategoryEntity root,
                                                                 @NonNull java.util.List<CategoryChildItem> children,
                                                                 @NonNull String walletLabel) {
        return new TransactionCategoryPickerItem(new CategoryGroup(root, children, walletLabel));
    }

    @Nullable
    public CategoryGroup getGroup() {
        return group;
    }

    public boolean containsCategoryId(@Nullable String categoryId) {
        if (categoryId == null || group == null) {
            return false;
        }
        if (group.root.getId().equals(categoryId)) {
            return true;
        }
        for (CategoryChildItem child : group.children) {
            if (child.category.getId().equals(categoryId)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    public String getStableId() {
        if (group != null && group.root.getId() != null) {
            return group.root.getId();
        }
        return "UNKNOWN";
    }

    public boolean contentEquals(@NonNull TransactionCategoryPickerItem other) {
        if (group == null || other.group == null) {
            return false;
        }
        return group.contentEquals(other.group);
    }

    public static final class CategoryGroup {
        @NonNull
        private final CategoryEntity root;
        @NonNull
        private final java.util.List<CategoryChildItem> children;
        @NonNull
        private final String walletLabel;

        private CategoryGroup(@NonNull CategoryEntity root,
                              @NonNull java.util.List<CategoryChildItem> children,
                              @NonNull String walletLabel) {
            this.root = root;
            this.children = children;
            this.walletLabel = walletLabel;
        }

        @NonNull
        public CategoryEntity getRoot() {
            return root;
        }

        @NonNull
        public java.util.List<CategoryChildItem> getChildren() {
            return children;
        }

        @NonNull
        public String getWalletLabel() {
            return walletLabel;
        }

        private boolean contentEquals(@NonNull CategoryGroup other) {
            if (!root.getId().equals(other.root.getId())) {
                return false;
            }
            if (root.getUpdatedAt() != other.root.getUpdatedAt()) {
                return false;
            }
            if (!walletLabel.equals(other.walletLabel)) {
                return false;
            }
            if (children.size() != other.children.size()) {
                return false;
            }
            for (int i = 0; i < children.size(); i++) {
                if (!children.get(i).contentEquals(other.children.get(i))) {
                    return false;
                }
            }
            return true;
        }
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
