package com.group10.moneymate.data.local.dao;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;
import androidx.room.RawQuery;
import androidx.room.Transaction;
import androidx.sqlite.db.SimpleSQLiteQuery;
import androidx.sqlite.db.SupportSQLiteQuery;

import com.group10.moneymate.data.local.entity.CategoryEntity;

import java.util.List;

@Dao
public interface CategoryDao {
    @RawQuery
    int upsertLocalRaw(SupportSQLiteQuery query);

    default void upsertLocal(CategoryEntity category) {
        String sql = "INSERT INTO categories ("
                + "id, user_id, name, type, icon_name, parent_id, wallet_id, is_default, "
                + "created_at, updated_at, sync_status, is_deleted"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(id) DO UPDATE SET "
                + "user_id = excluded.user_id, "
                + "name = excluded.name, "
                + "type = excluded.type, "
                + "icon_name = excluded.icon_name, "
                + "parent_id = excluded.parent_id, "
                + "wallet_id = excluded.wallet_id, "
                + "is_default = excluded.is_default, "
                + "updated_at = excluded.updated_at, "
                + "sync_status = CASE WHEN categories.sync_status = 2 THEN 2 ELSE excluded.sync_status END, "
                + "is_deleted = CASE WHEN categories.is_deleted = 1 THEN 1 ELSE excluded.is_deleted END, "
                + "created_at = CASE WHEN categories.created_at IS NULL OR categories.created_at <= 0 THEN excluded.created_at ELSE categories.created_at END";
        upsertLocalRaw(new SimpleSQLiteQuery(sql, new Object[] {
                category.getId(),
                category.getUserId(),
                category.getName(),
                category.getType(),
                category.getIconName(),
                category.getParentId(),
                category.getWalletId(),
                category.isDefault() ? 1 : 0,
                category.getCreatedAt(),
                category.getUpdatedAt(),
                category.getSyncStatus(),
                category.isDeleted() ? 1 : 0
        }));
    }

    default void insertCategory(CategoryEntity category) {
        upsertLocal(category);
    }

    default long insertCategoryIgnore(CategoryEntity category) {
        upsertLocal(category);
        return 1L;
    }

    default void insertAll(List<CategoryEntity> categories) {
        for (CategoryEntity category : categories) {
            upsertLocal(category);
        }
    }


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
    @Deprecated
    List<CategoryEntity> getPendingSyncCategories(String userId);

    @Query("SELECT * FROM categories WHERE user_id = :userId AND sync_status != 0 " +
            "AND (updated_at > :lastSyncedAt OR (updated_at = :lastSyncedAt AND id > :lastSyncedId)) " +
            "ORDER BY updated_at ASC, id ASC LIMIT :limit OFFSET :offset")
    List<CategoryEntity> getPendingSyncCategoriesPagedSince(String userId,
                                                            long lastSyncedAt,
                                                            String lastSyncedId,
                                                            int limit,
                                                            int offset);

    @Query("UPDATE categories SET sync_status = 0 WHERE id = :id")
    void markSynced(String id);

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    @Query("DELETE FROM categories WHERE id = :id")
    void hardDeleteById(String id);

    @Query("UPDATE categories SET is_deleted = 1, sync_status = 2, updated_at = :updatedAt " +
            "WHERE user_id = :userId AND is_default = 0")
    void softDeleteAllCustomByUser(String userId, long updatedAt);

    @Query("SELECT * FROM categories WHERE name = :name AND type = :type " +
            "AND is_deleted = 0 AND is_default = 1 LIMIT 1")
    CategoryEntity getCategoryByNameAndTypeSync(String name, String type);

    @Query("UPDATE transactions SET category_id = :newId, updated_at = :updatedAt, " +
            "sync_status = CASE WHEN sync_status = 2 THEN 2 ELSE 1 END " +
            "WHERE category_id = :oldId")
    void remapTransactionCategoryId(String oldId, String newId, long updatedAt);

    @Query("UPDATE budgets SET category_id = :newId, updated_at = :updatedAt, " +
            "sync_status = CASE WHEN sync_status = 2 THEN 2 ELSE 1 END " +
            "WHERE category_id = :oldId")
    void remapBudgetCategoryId(String oldId, String newId, long updatedAt);

    @Query("UPDATE categories SET parent_id = :newId, updated_at = :updatedAt, " +
            "sync_status = CASE WHEN sync_status = 2 THEN 2 ELSE 1 END " +
            "WHERE parent_id = :oldId")
    void remapChildParentCategoryId(String oldId, String newId, long updatedAt);

    @Transaction
    default void remapCategoryReferencesAndDeleteOld(String oldId, String newId, long updatedAt) {
        remapTransactionCategoryId(oldId, newId, updatedAt);
        remapBudgetCategoryId(oldId, newId, updatedAt);
        remapChildParentCategoryId(oldId, newId, updatedAt);
        hardDeleteById(oldId);
    }
}
