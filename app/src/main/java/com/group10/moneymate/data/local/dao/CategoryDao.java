package com.group10.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.group10.moneymate.data.local.entity.CategoryEntity;

import java.util.List;

@Dao
public interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCategory(CategoryEntity category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<CategoryEntity> categories);

    @Update
    void updateCategory(CategoryEntity category);

    @Delete
    void deleteCategory(CategoryEntity category);

    @Query("SELECT * FROM categories WHERE (user_id = :userId OR is_default = 1) AND is_deleted = 0 ORDER BY is_default DESC, name ASC")
    LiveData<List<CategoryEntity>> getAllCategories(String userId);

    @Query("SELECT * FROM categories WHERE (user_id = :userId OR is_default = 1) AND type = :type AND is_deleted = 0 ORDER BY is_default DESC, name ASC")
    LiveData<List<CategoryEntity>> getCategoriesByType(String userId, String type);

    @Query("SELECT * FROM categories WHERE id = :id AND is_deleted = 0")
    LiveData<CategoryEntity> getCategoryById(String id);

    @Query("SELECT * FROM categories WHERE is_default = 1")
    List<CategoryEntity> getDefaultCategories();

    @Query("SELECT COUNT(*) FROM categories WHERE is_default = 1")
    int getDefaultCategoryCount();

    @Query("UPDATE categories SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt WHERE id = :id")
    void softDelete(String id, long updatedAt);

    @Query("SELECT * FROM categories WHERE user_id = :userId AND sync_status != 0")
    List<CategoryEntity> getPendingSyncCategories(String userId);

    @Query("DELETE FROM categories WHERE user_id = :userId AND is_default = 0")
    void deleteAllCustomByUser(String userId);
}