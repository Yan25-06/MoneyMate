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
import java.util.List;
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
            if (categoryDao.getDefaultCategoryCount() == 0) {
                List<Constants.DefaultCategory> defaults = Constants.getDefaultCategories();
                List<CategoryEntity> entities = new ArrayList<>();
                long now = System.currentTimeMillis();

                for (Constants.DefaultCategory dc : defaults) {
                    CategoryEntity entity = new CategoryEntity();
                    entity.setId(UUID.randomUUID().toString());
                    entity.setUserId(null);        // null = dùng chung cho mọi user
                    entity.setName(dc.name);
                    entity.setIconResId(dc.iconResId);
                    entity.setColorHex(dc.colorHex);
                    entity.setType(dc.type);
                    entity.setDefault(true);
                    entity.setUpdatedAt(now);
                    entity.setSyncStatus(0);       // SYNCED — default categories không cần sync
                    entity.setDeleted(false);
                    entities.add(entity);
                }

                categoryDao.insertAll(entities);
            }
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
