package com.group10.moneymate.data.local.dao;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
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

    @Query("SELECT * FROM categories WHERE (user_id = :userId OR is_default = 1) " +
            "AND is_deleted = 0 " +
            "AND parent_id IS NULL " +
            "AND id NOT IN ('VIRTUAL_OTHER', 'VIRTUAL_OTHER_CATEGORIES') " +
            "ORDER BY is_default DESC, name ASC")
    LiveData<List<CategoryEntity>> getAllCategories(String userId);

    @Query("SELECT * FROM categories WHERE (user_id = :userId OR is_default = 1) " +
            "AND type = :type " +
            "AND is_deleted = 0 " +
            "AND parent_id IS NULL " +
            "ORDER BY is_default DESC, name ASC")
    LiveData<List<CategoryEntity>> getCategoriesByType(String userId, String type);

    @Query("SELECT * FROM categories WHERE (user_id = :userId OR is_default = 1) " +
            "AND type = :type " +
            "ORDER BY is_default DESC, name ASC")
    LiveData<List<CategoryEntity>> getCategoriesByTypeIncludingDeleted(String userId, String type);

    @Query("SELECT * FROM categories WHERE (user_id = :userId OR is_default = 1) " +
            "AND type = :type " +
            "AND is_deleted = 0 " +
            "AND (:walletId IS NULL OR wallet_id IS NULL OR wallet_id = :walletId) " +
            "ORDER BY is_default DESC, name ASC")
    LiveData<List<CategoryEntity>> getCategoriesByTypeAndWallet(String userId,
                                                                String type,
                                                                @Nullable String walletId);

    @Query("SELECT * FROM categories WHERE (user_id = :userId OR is_default = 1) " +
            "AND type = :type " +
            "AND is_deleted = 0 " +
            "AND parent_id IS NULL " +
            "AND (:walletId IS NULL OR wallet_id IS NULL OR wallet_id = :walletId) " +
            "ORDER BY is_default DESC, name ASC")
    LiveData<List<CategoryEntity>> getRootCategoriesByTypeAndWallet(String userId,
                                                                    String type,
                                                                    @Nullable String walletId);

    @Query("SELECT * FROM categories root " +
            "WHERE (root.user_id = :userId OR root.is_default = 1) " +
            "AND root.type = :type " +
            "AND root.is_deleted = 0 " +
            "AND root.parent_id IS NULL " +
            "AND (:walletId IS NULL OR root.wallet_id IS NULL OR root.wallet_id = :walletId) " +
            "AND EXISTS (" +
            "    SELECT 1 FROM categories child " +
            "    WHERE child.parent_id = root.id " +
            "    AND child.is_deleted = 0 " +
            "    AND (child.user_id = :userId OR child.is_default = 1)" +
            ") " +
            "ORDER BY root.is_default DESC, root.name ASC")
    LiveData<List<CategoryEntity>> getParentCategoriesWithChildrenByTypeAndWallet(String userId,
                                                                                  String type,
                                                                                  @Nullable String walletId);

    @Query("SELECT * FROM categories root " +
            "WHERE (root.user_id = :userId OR root.is_default = 1) " +
            "AND root.type = :type " +
            "AND root.parent_id IS NULL " +
            "AND (:walletId IS NULL OR root.wallet_id IS NULL OR root.wallet_id = :walletId) " +
            "AND EXISTS (" +
            "    SELECT 1 FROM categories child " +
            "    WHERE child.parent_id = root.id " +
            "    AND (child.user_id = :userId OR child.is_default = 1)" +
            ") " +
            "ORDER BY root.is_default DESC, root.name ASC")
    LiveData<List<CategoryEntity>> getParentCategoriesWithChildrenByTypeAndWalletIncludingDeleted(String userId,
                                                                                                  String type,
                                                                                                  @Nullable String walletId);

    @Query("SELECT * FROM categories WHERE (user_id = :userId OR is_default = 1) " +
            "AND type = :type " +
            "AND is_deleted = 0 " +
            "AND parent_id IS NULL " +
            "AND (:walletId IS NULL OR wallet_id IS NULL OR wallet_id = :walletId) " +
            "ORDER BY is_default DESC, name ASC")
    List<CategoryEntity> getRootCategoriesByTypeAndWalletSync(String userId,
                                                              String type,
                                                              @Nullable String walletId);

    @Query("SELECT * FROM categories WHERE (user_id = :userId OR is_default = 1) " +
            "AND is_deleted = 0 " +
            "AND parent_id = :parentId " +
            "ORDER BY name ASC")
    LiveData<List<CategoryEntity>> getChildrenByParent(String userId, String parentId);

    @Query("SELECT * FROM categories WHERE (user_id = :userId OR is_default = 1) " +
            "AND is_deleted = 0 " +
            "AND parent_id = :parentId " +
            "ORDER BY name ASC")
    List<CategoryEntity> getChildrenByParentSync(String userId, String parentId);

    @Query("SELECT * FROM categories WHERE id = :id AND is_deleted = 0")
    LiveData<CategoryEntity> getCategoryById(String id);

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    LiveData<CategoryEntity> getCategoryByIdIncludingDeleted(String id);

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    CategoryEntity getCategoryByIdSync(String id);

    @Query("SELECT * FROM categories WHERE id = :id " +
            "AND is_deleted = 0 " +
            "AND (:userId IS NULL OR user_id = :userId OR is_default = 1) " +
            "LIMIT 1")
    CategoryEntity getCategoryByIdForValidationSync(String id, @Nullable String userId);

    @Query("SELECT COUNT(*) FROM categories WHERE parent_id = :parentId " +
            "AND is_deleted = 0 " +
            "AND (:userId IS NULL OR user_id = :userId OR is_default = 1)")
    int countActiveChildrenSync(String parentId, @Nullable String userId);

    @Query("SELECT COUNT(*) FROM categories WHERE parent_id = :parentId " +
            "AND (:userId IS NULL OR user_id = :userId OR is_default = 1)")
    int countChildrenSync(String parentId, @Nullable String userId);

    @Query("SELECT * FROM categories WHERE is_default = 1 AND is_deleted = 0")
    List<CategoryEntity> getDefaultCategories();

    @Query("SELECT COUNT(*) FROM categories WHERE is_default = 1")
    int getDefaultCategoryCount();

    @Transaction
    @Query("UPDATE categories SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt " +
            "WHERE id = :id OR parent_id = :id")
    void softDeleteCascade(String id, long updatedAt);

    @Query("SELECT * FROM categories WHERE user_id = :userId AND sync_status != 0")
    List<CategoryEntity> getPendingSyncCategories(String userId);

    @Query("UPDATE categories SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt " +
            "WHERE user_id = :userId AND is_default = 0")
    void softDeleteAllCustomByUser(String userId, long updatedAt);
}
