package com.example.moneymate.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.moneymate.data.local.entity.CategoryEntity;

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

    @Query("SELECT * FROM categories WHERE userId = :userId OR isDefault = 1 ORDER BY isDefault DESC, name ASC")
    LiveData<List<CategoryEntity>> getAllCategories(String userId);

    @Query("SELECT * FROM categories WHERE (userId = :userId OR isDefault = 1) AND type = :type ORDER BY isDefault DESC, name ASC")
    LiveData<List<CategoryEntity>> getCategoriesByType(String userId, String type);

    @Query("SELECT * FROM categories WHERE id = :id")
    LiveData<CategoryEntity> getCategoryById(String id);

    @Query("SELECT * FROM categories WHERE isDefault = 1")
    List<CategoryEntity> getDefaultCategories();

    @Query("SELECT COUNT(*) FROM categories WHERE isDefault = 1")
    int getDefaultCategoryCount();


    @Query("DELETE FROM categories WHERE userId = :userId AND isDefault = 0")
    void deleteAllCustomByUser(String userId);
}
