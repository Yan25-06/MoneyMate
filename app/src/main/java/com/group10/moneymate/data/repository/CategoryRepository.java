package com.group10.moneymate.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.CategoryDao;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Repository for category data.
 * Write operations chạy trên {@link AppDatabase#databaseWriteExecutor}.
 */
public class CategoryRepository {

    public interface CategoryValidationCallback {
        void onCompleted(@NonNull CategoryValidationResult result);
    }

    public interface ChildrenCheckCallback {
        void onResult(boolean hasChildren);
    }

    public enum CategoryValidationError {
        NONE,
        SELF_PARENT_NOT_ALLOWED,
        PARENT_NOT_FOUND,
        DEPTH_LIMIT_EXCEEDED,
        TYPE_MISMATCH,
        WALLET_SCOPE_MISMATCH,
        CANNOT_MOVE_PARENT_WITH_CHILDREN,
        DEFAULT_CATEGORY_CANNOT_DELETE,
        CANNOT_DELETE_WITH_CHILDREN
    }

    public static final class CategoryValidationResult {
        private final boolean valid;
        @NonNull
        private final CategoryValidationError error;
        @NonNull
        private final String errorKey;

        private CategoryValidationResult(boolean valid,
                                         @NonNull CategoryValidationError error,
                                         @NonNull String errorKey) {
            this.valid = valid;
            this.error = error;
            this.errorKey = errorKey;
        }

        @NonNull
        public static CategoryValidationResult success() {
            return new CategoryValidationResult(true, CategoryValidationError.NONE, "");
        }

        @NonNull
        public static CategoryValidationResult failure(@NonNull CategoryValidationError error,
                                                       @NonNull String errorKey) {
            return new CategoryValidationResult(false, error, errorKey);
        }

        public boolean isValid() {
            return valid;
        }

        @NonNull
        public CategoryValidationError getError() {
            return error;
        }

        @NonNull
        public String getErrorKey() {
            return errorKey;
        }
    }

    private final CategoryDao categoryDao;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public CategoryRepository(CategoryDao categoryDao) {
        this.categoryDao = categoryDao;
    }

    // ─── Read (LiveData — Room tự chạy trên background) ──────────────────────

    public LiveData<List<CategoryEntity>> getAllCategories(String userId) {
        return categoryDao.getAllCategories(userId);
    }

    public LiveData<List<CategoryEntity>> getCategoriesByType(String userId, String type) {
        return categoryDao.getCategoriesByType(userId, type);
    }

    public LiveData<List<CategoryEntity>> getCategoriesByTypeIncludingDeleted(String userId, String type) {
        return categoryDao.getCategoriesByTypeIncludingDeleted(userId, type);
    }

    public LiveData<List<CategoryEntity>> getCategoriesByTypeAndWallet(String userId,
                                                                       String type,
                                                                       @Nullable String walletId) {
        return categoryDao.getCategoriesByTypeAndWallet(userId, type, walletId);
    }

    public LiveData<List<CategoryEntity>> getRootCategoriesByTypeAndWallet(String userId,
                                                                            String type,
                                                                            @Nullable String walletId) {
        return categoryDao.getRootCategoriesByTypeAndWallet(userId, type, walletId);
    }

    public LiveData<List<CategoryEntity>> getParentCategoriesWithChildrenByTypeAndWallet(String userId,
                                                                                          String type,
                                                                                          @Nullable String walletId) {
        return categoryDao.getParentCategoriesWithChildrenByTypeAndWallet(userId, type, walletId);
    }

    public LiveData<List<CategoryEntity>> getParentCategoriesWithChildrenByTypeAndWalletIncludingDeleted(String userId,
                                                                                                          String type,
                                                                                                          @Nullable String walletId) {
        return categoryDao.getParentCategoriesWithChildrenByTypeAndWalletIncludingDeleted(userId, type, walletId);
    }

    public LiveData<List<CategoryEntity>> getChildrenByParent(String userId, String parentId) {
        return categoryDao.getChildrenByParent(userId, parentId);
    }

    public LiveData<CategoryEntity> getCategoryById(String id) {
        return categoryDao.getCategoryById(id);
    }

    public LiveData<CategoryEntity> getCategoryByIdIncludingDeleted(String id) {
        return categoryDao.getCategoryByIdIncludingDeleted(id);
    }

    // ─── Write (AppDatabase.databaseWriteExecutor) ────────────────────────────

    public void addCategory(CategoryEntity category) {
        addCategoryValidatedAsync(category, result -> {
            if (!result.isValid()) {
                throw new IllegalArgumentException("CATEGORY_VALIDATION:" + result.getError().name());
            }
        });
    }

    public void updateCategory(CategoryEntity category) {
        updateCategoryValidatedAsync(category, result -> {
            if (!result.isValid()) {
                throw new IllegalArgumentException("CATEGORY_VALIDATION:" + result.getError().name());
            }
        });
    }

    public void addCategoryValidatedAsync(@NonNull CategoryEntity category,
                                          @NonNull CategoryValidationCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            CategoryValidationResult validationResult = prepareAndValidateForCreate(category);
            if (!validationResult.isValid()) {
                postValidationResult(callback, validationResult);
                return;
            }
            categoryDao.insertCategory(category);
            postValidationResult(callback, CategoryValidationResult.success());
        });
    }

    public void updateCategoryValidatedAsync(@NonNull CategoryEntity category,
                                             @NonNull CategoryValidationCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            CategoryValidationResult validationResult = prepareAndValidateForUpdate(category);
            if (!validationResult.isValid()) {
                postValidationResult(callback, validationResult);
                return;
            }
            categoryDao.updateCategory(category);
            postValidationResult(callback, CategoryValidationResult.success());
        });
    }

    public void deleteCategoryValidatedAsync(@NonNull CategoryEntity category,
                                             @NonNull CategoryValidationCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            CategoryValidationResult validationResult = validateDelete(category);
            if (!validationResult.isValid()) {
                postValidationResult(callback, validationResult);
                return;
            }
            categoryDao.softDeleteCascade(category.getId(), System.currentTimeMillis());
            postValidationResult(callback, CategoryValidationResult.success());
        });
    }

    private void postValidationResult(@NonNull CategoryValidationCallback callback,
                                      @NonNull CategoryValidationResult result) {
        mainHandler.post(() -> callback.onCompleted(result));
    }

    @NonNull
    private CategoryValidationResult prepareAndValidateForCreate(@NonNull CategoryEntity category) {
        category.setId(UUID.randomUUID().toString());
        if (category.getIconName().trim().isEmpty()) {
            category.setIconName("ic_category_default");
        }
        CategoryValidationResult validationResult = validateHierarchyForCreate(category);
        if (!validationResult.isValid()) {
            return validationResult;
        }
        category.setUpdatedAt(System.currentTimeMillis());
        category.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        category.setDeleted(false);
        return CategoryValidationResult.success();
    }

    @NonNull
    private CategoryValidationResult prepareAndValidateForUpdate(@NonNull CategoryEntity category) {
        if (category.getIconName().trim().isEmpty()) {
            category.setIconName("ic_category_default");
        }
        CategoryValidationResult validationResult = validateHierarchyForUpdate(category);
        if (!validationResult.isValid()) {
            return validationResult;
        }
        category.setUpdatedAt(System.currentTimeMillis());
        category.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        return CategoryValidationResult.success();
    }

    @NonNull
    public CategoryValidationResult addCategoryValidated(@NonNull CategoryEntity category) {
        return prepareAndValidateForCreate(category);
    }

    @NonNull
    public CategoryValidationResult updateCategoryValidated(@NonNull CategoryEntity category) {
        return prepareAndValidateForUpdate(category);
    }

    @NonNull
    public CategoryValidationResult validateHierarchyForCreate(@NonNull CategoryEntity category) {
        return validateHierarchy(category, false);
    }

    @NonNull
    public CategoryValidationResult validateHierarchyForUpdate(@NonNull CategoryEntity category) {
        return validateHierarchy(category, true);
    }

    @NonNull
    private CategoryValidationResult validateHierarchy(@NonNull CategoryEntity category, boolean isUpdate) {
        String parentId = normalizeNullable(category.getParentId());
        if (parentId == null) {
            return CategoryValidationResult.success();
        }

        String categoryId = normalizeNullable(category.getId());
        if (categoryId != null && categoryId.equals(parentId)) {
            return CategoryValidationResult.failure(
                    CategoryValidationError.SELF_PARENT_NOT_ALLOWED,
                    "category.validation.self_parent"
            );
        }

        CategoryEntity parent = categoryDao.getCategoryByIdForValidationSync(parentId, category.getUserId());
        if (parent == null) {
            return CategoryValidationResult.failure(
                    CategoryValidationError.PARENT_NOT_FOUND,
                    "category.validation.parent_not_found"
            );
        }

        if (normalizeNullable(parent.getParentId()) != null) {
            return CategoryValidationResult.failure(
                    CategoryValidationError.DEPTH_LIMIT_EXCEEDED,
                    "category.validation.depth_limit_exceeded"
            );
        }

        if (!safeEquals(parent.getType(), category.getType())) {
            return CategoryValidationResult.failure(
                    CategoryValidationError.TYPE_MISMATCH,
                    "category.validation.type_mismatch"
            );
        }

        if (!isWalletScopeMatch(parent.getWalletId(), category.getWalletId())) {
            return CategoryValidationResult.failure(
                    CategoryValidationError.WALLET_SCOPE_MISMATCH,
                    "category.validation.wallet_scope_mismatch"
            );
        }

        if (isUpdate && categoryId != null) {
            int activeChildren = categoryDao.countActiveChildrenSync(categoryId, category.getUserId());
            if (activeChildren > 0) {
                return CategoryValidationResult.failure(
                        CategoryValidationError.CANNOT_MOVE_PARENT_WITH_CHILDREN,
                        "category.validation.cannot_move_parent_with_children"
                );
            }
        }

        return CategoryValidationResult.success();
    }

    @Nullable
    private String normalizeNullable(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean safeEquals(@Nullable String left, @Nullable String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private boolean isWalletScopeMatch(@Nullable String parentWalletId, @Nullable String childWalletId) {
        String normalizedParentWalletId = normalizeNullable(parentWalletId);
        String normalizedChildWalletId = normalizeNullable(childWalletId);
        if (normalizedParentWalletId == null && normalizedChildWalletId == null) {
            return true;
        }
        if (normalizedParentWalletId == null || normalizedChildWalletId == null) {
            return false;
        }
        return normalizedParentWalletId.equals(normalizedChildWalletId);
    }

    /**
     * Soft delete cascade cho danh mục tùy chỉnh.
     * Danh mục mặc định không thể xóa.
     */
    public void deleteCategory(CategoryEntity category) {
        deleteCategoryValidatedAsync(category, result -> {
            if (!result.isValid()) {
                throw new IllegalArgumentException("CATEGORY_VALIDATION:" + result.getError().name());
            }
        });
    }

    @NonNull
    public CategoryValidationResult deleteCategoryValidated(@NonNull CategoryEntity category) {
        return validateDelete(category);
    }

    @NonNull
    private CategoryValidationResult validateDelete(@NonNull CategoryEntity category) {
        if (category.isDefault()) {
            return CategoryValidationResult.failure(
                    CategoryValidationError.DEFAULT_CATEGORY_CANNOT_DELETE,
                    "category.validation.default_cannot_delete"
            );
        }
        return CategoryValidationResult.success();
    }

    // ─── Seed ─────────────────────────────────────────────────────────────────

    /**
     * Seed danh mục mặc định nếu chưa có.
     * Kiểm tra {@code getDefaultCategoryCount()} để tránh seed lại khi user
     * đăng nhập nhiều lần.
     * <p>
     * Gọi sau khi đăng ký hoặc đăng nhập thành công.
     */
    public void seedDefaults() {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            syncDefaultCategories();
            ensureVirtualOtherCategoriesExistInternal();
        });
    }

    public void ensureVirtualOtherCategoryExists() {
        ensureVirtualOtherCategoryExists(null);
    }

    public void ensureVirtualOtherCategoryExists(@Nullable Runnable onComplete) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            ensureVirtualOtherCategoriesExistInternal();
            if (onComplete != null) {
                mainHandler.post(onComplete);
            }
        });
    }

    private void ensureVirtualOtherCategoriesExistInternal() {
        if (categoryDao.getCategoryByIdSync(Constants.CATEGORY_ID_OTHER) == null) {
            categoryDao.insertCategory(buildOtherCategory(Constants.CATEGORY_ID_OTHER));
        }
        if (categoryDao.getCategoryByIdSync(Constants.CATEGORY_ID_OTHER_LEGACY) == null) {
            categoryDao.insertCategory(buildOtherCategory(Constants.CATEGORY_ID_OTHER_LEGACY));
        }
    }

    private void syncDefaultCategories() {
        List<Constants.DefaultCategory> defaults = Constants.getDefaultCategories();
        List<CategoryEntity> existingDefaults = categoryDao.getDefaultCategories();
        Map<String, CategoryEntity> existingByKey = new HashMap<>();

        for (CategoryEntity category : existingDefaults) {
            existingByKey.put(buildDefaultKey(category.getName(), category.getType()), category);
        }

        List<CategoryEntity> missingDefaults = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Constants.DefaultCategory defaultCategory : defaults) {
            String key = buildDefaultKey(defaultCategory.name, defaultCategory.type);
            CategoryEntity existing = existingByKey.get(key);
            if (existing == null) {
                missingDefaults.add(buildDefaultCategory(defaultCategory, now));
                continue;
            }

            if (shouldRefreshDefaultCategory(existing, defaultCategory)) {
                existing.setUserId(null);
                existing.setName(defaultCategory.name);
                existing.setIconName(resolveCategoryIconName(defaultCategory.iconName));
                existing.setParentId(null);
                existing.setWalletId(null);
                existing.setType(defaultCategory.type);
                existing.setDefault(true);
                existing.setDeleted(false);
                existing.setSyncStatus(SyncStatus.SYNCED);
                existing.setUpdatedAt(now);
                categoryDao.updateCategory(existing);
            }
        }

        if (!missingDefaults.isEmpty()) {
            categoryDao.insertAll(missingDefaults);
        }
    }

    @NonNull
    private String buildDefaultKey(@Nullable String name, @Nullable String type) {
        String safeName = name != null ? name.trim() : "";
        String safeType = type != null ? type.trim() : "";
        return safeName + "|" + safeType;
    }

    @NonNull
    private CategoryEntity buildDefaultCategory(@NonNull Constants.DefaultCategory defaultCategory, long now) {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(null);
        entity.setName(defaultCategory.name);
        entity.setIconName(resolveCategoryIconName(defaultCategory.iconName));
        entity.setParentId(null);
        entity.setWalletId(null);
        entity.setType(defaultCategory.type);
        entity.setDefault(true);
        entity.setUpdatedAt(now);
        entity.setSyncStatus(SyncStatus.SYNCED);
        entity.setDeleted(false);
        return entity;
    }

    private boolean shouldRefreshDefaultCategory(@NonNull CategoryEntity existing,
                                                 @NonNull Constants.DefaultCategory defaultCategory) {
        if (existing.isDeleted()) {
            return true;
        }
        if (!existing.isDefault()) {
            return true;
        }
        if (existing.getUserId() != null) {
            return true;
        }
        if (!resolveCategoryIconName(defaultCategory.iconName).equals(existing.getIconName())) {
            return true;
        }
        if (existing.getParentId() != null || existing.getWalletId() != null) {
            return true;
        }
        return !defaultCategory.type.equals(existing.getType());
    }

    @NonNull
    private CategoryEntity buildOtherCategory(@NonNull String categoryId) {
        CategoryEntity otherCategory = new CategoryEntity();
        otherCategory.setId(categoryId);
        otherCategory.setUserId(null);
        otherCategory.setName("Các mục khác");
        otherCategory.setIconName("ic_category_other");
        otherCategory.setParentId(null);
        otherCategory.setWalletId(null);
        otherCategory.setType(Constants.CATEGORY_TYPE_VIRTUAL_BUDGET);
        otherCategory.setDefault(true);
        otherCategory.setUpdatedAt(System.currentTimeMillis());
        otherCategory.setSyncStatus(SyncStatus.SYNCED);
        otherCategory.setDeleted(false);
        return otherCategory;
    }

    @NonNull
    private String resolveCategoryIconName(@Nullable String iconName) {
        if (iconName == null || iconName.trim().isEmpty()) {
            return "ic_category_default";
        }
        return iconName;
    }

    public void hasActiveChildrenAsync(@NonNull String parentId,
                                       @Nullable String userId,
                                       @NonNull ChildrenCheckCallback callback) {
        AppDatabase.databaseWriteExecutor.execute(() -> {
            int count = categoryDao.countChildrenSync(parentId, userId);
            mainHandler.post(() -> callback.onResult(count > 0));
        });
    }
}
