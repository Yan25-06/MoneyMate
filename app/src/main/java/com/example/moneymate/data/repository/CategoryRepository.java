package com.example.moneymate.data.repository;

import androidx.lifecycle.LiveData;

import com.example.moneymate.data.local.dao.CategoryDao;
import com.example.moneymate.data.local.entity.CategoryEntity;

import java.util.List;

/**
 * Repository for category data.
 */
public class CategoryRepository {
    private final CategoryDao categoryDao;

    public CategoryRepository(CategoryDao categoryDao) {
        this.categoryDao = categoryDao;
    }

    public LiveData<List<CategoryEntity>> getAllCategories(String userId) {
        return categoryDao.getAllCategories(userId);
    }

    public LiveData<List<CategoryEntity>> getCategoriesByType(String userId, String type) {
        return categoryDao.getCategoriesByType(userId, type);
    }

    public LiveData<CategoryEntity> getCategoryById(String id) {
        return categoryDao.getCategoryById(id);
    }

    public void insertCategory(CategoryEntity category) {
        categoryDao.insertCategory(category);
    }

    public void insertAll(List<CategoryEntity> categories) {
        categoryDao.insertAll(categories);
    }

    public void updateCategory(CategoryEntity category) {
        categoryDao.updateCategory(category);
    }

    public void deleteCategory(CategoryEntity category) {
        categoryDao.deleteCategory(category);
    }

    public int getDefaultCategoryCount() {
        return categoryDao.getDefaultCategoryCount();
    }
}
