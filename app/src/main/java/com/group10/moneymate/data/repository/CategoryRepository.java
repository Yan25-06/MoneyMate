package com.group10.moneymate.data.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.dao.CategoryDao;
import com.group10.moneymate.data.local.entity.CategoryEntity;
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

    public LiveData<CategoryEntity> getCategoryById(String id) {
        return categoryDao.getCategoryById(id);
    }

    // ─── Write (AppDatabase.databaseWriteExecutor) ────────────────────────────

    public void addCategory(CategoryEntity category) {
        category.setId(UUID.randomUUID().toString());
        category.setUpdatedAt(System.currentTimeMillis());
        category.setSyncStatus(1); // PENDING_UPLOAD
        category.setDeleted(false);
        AppDatabase.databaseWriteExecutor.execute(() ->
                categoryDao.insertCategory(category)
        );
    }

    public void updateCategory(CategoryEntity category) {
        category.setUpdatedAt(System.currentTimeMillis());
        category.setSyncStatus(1); // PENDING_UPLOAD
        AppDatabase.databaseWriteExecutor.execute(() ->
                categoryDao.updateCategory(category)
        );
    }

    /**
     * Soft delete — chỉ áp dụng cho danh mục tùy chỉnh (isDefault = false).
     * Danh mục mặc định không thể xóa.
     */
    public void deleteCategory(CategoryEntity category) {
        if (category.isDefault()) return;
        long updatedAt = System.currentTimeMillis();
        AppDatabase.databaseWriteExecutor.execute(() ->
                categoryDao.softDelete(category.getId(), updatedAt)
        );
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
                existing.setIconResId(defaultCategory.iconResId);
                existing.setColorHex(defaultCategory.colorHex);
                existing.setType(defaultCategory.type);
                existing.setDefault(true);
                existing.setDeleted(false);
                existing.setSyncStatus(0);
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
        entity.setIconResId(defaultCategory.iconResId);
        entity.setColorHex(defaultCategory.colorHex);
        entity.setType(defaultCategory.type);
        entity.setDefault(true);
        entity.setUpdatedAt(now);
        entity.setSyncStatus(0);
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
        if (!defaultCategory.iconResId.equals(existing.getIconResId())) {
            return true;
        }
        if (!defaultCategory.colorHex.equals(existing.getColorHex())) {
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
        otherCategory.setIconResId("ic_category_other");
        otherCategory.setColorHex("#64748B");
        otherCategory.setType(Constants.CATEGORY_TYPE_VIRTUAL_BUDGET);
        otherCategory.setDefault(true);
        otherCategory.setUpdatedAt(System.currentTimeMillis());
        otherCategory.setSyncStatus(0);
        otherCategory.setDeleted(false);
        return otherCategory;
    }
}
