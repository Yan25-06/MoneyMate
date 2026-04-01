package com.group10.moneymate.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.Constants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class CategoryRepositoryHierarchyValidationTest {

    private static final String USER_ID = "u_test_repo";
    private static final long CALLBACK_TIMEOUT_SECONDS = 3L;

    private AppDatabase database;
    private CategoryRepository repository;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        repository = new CategoryRepository(database.categoryDao());
        database.userDao().insertUser(buildUser(USER_ID));
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void validateHierarchyForCreate_rejectsDepthGreaterThanTwo() {
        CategoryEntity root = buildCategory("root_food", USER_ID, "Ăn uống", Constants.TYPE_EXPENSE, null, null);
        CategoryEntity child = buildCategory("child_coffee", USER_ID, "Cà phê", Constants.TYPE_EXPENSE, root.getId(), null);
        database.categoryDao().insertCategory(root);
        database.categoryDao().insertCategory(child);

        CategoryEntity grandChild = buildCategory("grand_child", USER_ID, "Latte", Constants.TYPE_EXPENSE, child.getId(), null);

        CategoryRepository.CategoryValidationResult result = repository.validateHierarchyForCreate(grandChild);

        assertFalse(result.isValid());
        assertEquals(CategoryRepository.CategoryValidationError.DEPTH_LIMIT_EXCEEDED, result.getError());
    }

    @Test
    public void validateHierarchyForCreate_rejectsSelfParent() {
        CategoryEntity category = buildCategory("self_parent", USER_ID, "Test", Constants.TYPE_EXPENSE, "self_parent", null);

        CategoryRepository.CategoryValidationResult result = repository.validateHierarchyForCreate(category);

        assertFalse(result.isValid());
        assertEquals(CategoryRepository.CategoryValidationError.SELF_PARENT_NOT_ALLOWED, result.getError());
    }

    @Test
    public void validateHierarchyForCreate_rejectsTypeMismatch() {
        CategoryEntity incomeRoot = buildCategory("income_root", USER_ID, "Lương", Constants.TYPE_INCOME, null, null);
        database.categoryDao().insertCategory(incomeRoot);

        CategoryEntity expenseChild = buildCategory("expense_child", USER_ID, "Ăn sáng", Constants.TYPE_EXPENSE, incomeRoot.getId(), null);

        CategoryRepository.CategoryValidationResult result = repository.validateHierarchyForCreate(expenseChild);

        assertFalse(result.isValid());
        assertEquals(CategoryRepository.CategoryValidationError.TYPE_MISMATCH, result.getError());
    }

    @Test
    public void deleteCategoryValidated_blocksParentWhenHasChildren() {
        CategoryEntity root = buildCategory("root_delete", USER_ID, "Nhà ở", Constants.TYPE_EXPENSE, null, null);
        CategoryEntity child = buildCategory("child_delete", USER_ID, "Tiền thuê", Constants.TYPE_EXPENSE, root.getId(), null);
        database.categoryDao().insertCategory(root);
        database.categoryDao().insertCategory(child);

        CategoryRepository.CategoryValidationResult result = repository.deleteCategoryValidated(root);

        assertFalse(result.isValid());
        assertEquals(CategoryRepository.CategoryValidationError.CANNOT_DELETE_WITH_CHILDREN, result.getError());
    }

    @Test
    public void validateHierarchyForCreate_acceptsValidChild() {
        CategoryEntity root = buildCategory("root_ok", USER_ID, "Di chuyển", Constants.TYPE_EXPENSE, null, null);
        database.categoryDao().insertCategory(root);

        CategoryEntity child = buildCategory("child_ok", USER_ID, "Xe buýt", Constants.TYPE_EXPENSE, root.getId(), null);

        CategoryRepository.CategoryValidationResult result = repository.validateHierarchyForCreate(child);

        assertTrue(result.isValid());
        assertEquals(CategoryRepository.CategoryValidationError.NONE, result.getError());
    }

    @Test
    public void addCategoryValidatedAsync_persistsRootCategoryAfterSuccessfulValidation() throws InterruptedException {
        CategoryEntity newRoot = buildCategory("will_be_replaced", USER_ID, "Root Async", Constants.TYPE_EXPENSE, null, null);
        newRoot.setIconName("ic_category_food");

        AtomicReference<CategoryRepository.CategoryValidationResult> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.addCategoryValidatedAsync(newRoot, result -> {
            resultRef.set(result);
            latch.countDown();
        });

        assertTrue(latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        CategoryRepository.CategoryValidationResult result = resultRef.get();
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(CategoryRepository.CategoryValidationError.NONE, result.getError());
        assertNotEquals("will_be_replaced", newRoot.getId());

        CategoryEntity persisted = database.categoryDao().getCategoryByIdSync(newRoot.getId());
        assertNotNull(persisted);
        assertEquals("Root Async", persisted.getName());
        assertNull(persisted.getParentId());
        assertFalse(persisted.isDeleted());
        assertEquals(SyncStatus.PENDING_UPLOAD, persisted.getSyncStatus());
    }

    @Test
    public void addCategoryValidatedAsync_persistsChildCategoryWithParentLink() throws InterruptedException {
        CategoryEntity root = buildCategory("root_async_parent", USER_ID, "Root Parent", Constants.TYPE_EXPENSE, null, null);
        database.categoryDao().insertCategory(root);

        CategoryEntity child = buildCategory("child_async_seed", USER_ID, "Child Async", Constants.TYPE_EXPENSE, root.getId(), null);
        AtomicReference<CategoryRepository.CategoryValidationResult> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.addCategoryValidatedAsync(child, result -> {
            resultRef.set(result);
            latch.countDown();
        });

        assertTrue(latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        CategoryRepository.CategoryValidationResult result = resultRef.get();
        assertNotNull(result);
        assertTrue(result.isValid());

        List<CategoryEntity> persistedChildren = database.categoryDao().getChildrenByParentSync(USER_ID, root.getId());
        CategoryEntity persistedChild = null;
        for (CategoryEntity item : persistedChildren) {
            if ("Child Async".equals(item.getName())) {
                persistedChild = item;
                break;
            }
        }

        assertNotNull(persistedChild);
        assertEquals(root.getId(), persistedChild.getParentId());
        assertEquals(Constants.TYPE_EXPENSE, persistedChild.getType());
        assertEquals(USER_ID, persistedChild.getUserId());
        assertFalse(persistedChild.isDeleted());
        assertEquals(SyncStatus.PENDING_UPLOAD, persistedChild.getSyncStatus());
    }

    @Test
    public void deleteCategoryValidatedAsync_whenParentHasChildren_keepsParentUndeleted() throws InterruptedException {
        CategoryEntity root = buildCategory("root_block_async_delete", USER_ID, "Root Keep", Constants.TYPE_EXPENSE, null, null);
        root.setSyncStatus(SyncStatus.SYNCED);
        CategoryEntity child = buildCategory("child_block_async_delete", USER_ID, "Child Keep", Constants.TYPE_EXPENSE, root.getId(), null);
        database.categoryDao().insertCategory(root);
        database.categoryDao().insertCategory(child);

        AtomicReference<CategoryRepository.CategoryValidationResult> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.deleteCategoryValidatedAsync(root, result -> {
            resultRef.set(result);
            latch.countDown();
        });

        assertTrue(latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        CategoryRepository.CategoryValidationResult result = resultRef.get();
        assertNotNull(result);
        assertFalse(result.isValid());
        assertEquals(CategoryRepository.CategoryValidationError.CANNOT_DELETE_WITH_CHILDREN, result.getError());

        CategoryEntity persistedRoot = database.categoryDao().getCategoryByIdSync(root.getId());
        assertNotNull(persistedRoot);
        assertFalse(persistedRoot.isDeleted());
        assertEquals(SyncStatus.SYNCED, persistedRoot.getSyncStatus());
    }

    @Test
    public void deleteCategoryValidatedAsync_whenLeafCustomCategory_softDeletesAndMarksPendingDelete() throws InterruptedException {
        CategoryEntity leaf = buildCategory("leaf_delete_ok", USER_ID, "Leaf Delete", Constants.TYPE_EXPENSE, null, null);
        leaf.setSyncStatus(SyncStatus.SYNCED);
        long beforeDeleteUpdatedAt = leaf.getUpdatedAt();
        database.categoryDao().insertCategory(leaf);

        AtomicReference<CategoryRepository.CategoryValidationResult> resultRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        repository.deleteCategoryValidatedAsync(leaf, result -> {
            resultRef.set(result);
            latch.countDown();
        });

        assertTrue(latch.await(CALLBACK_TIMEOUT_SECONDS, TimeUnit.SECONDS));
        CategoryRepository.CategoryValidationResult result = resultRef.get();
        assertNotNull(result);
        assertTrue(result.isValid());
        assertEquals(CategoryRepository.CategoryValidationError.NONE, result.getError());

        CategoryEntity persistedLeaf = database.categoryDao().getCategoryByIdSync(leaf.getId());
        assertNotNull(persistedLeaf);
        assertTrue(persistedLeaf.isDeleted());
        assertEquals(SyncStatus.PENDING_DELETE, persistedLeaf.getSyncStatus());
        assertTrue(persistedLeaf.getUpdatedAt() >= beforeDeleteUpdatedAt);
    }

    private UserEntity buildUser(String userId) {
        UserEntity user = new UserEntity();
        user.setId(userId);
        user.setEmail("test@moneymate.local");
        user.setDisplayName("Test User");
        user.setCurrency("VND");
        user.setLanguage("vi");
        user.setCreatedAt(System.currentTimeMillis());
        return user;
    }

    private CategoryEntity buildCategory(String id,
                                         String userId,
                                         String name,
                                         String type,
                                         String parentId,
                                         String walletId) {
        CategoryEntity entity = new CategoryEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setName(name);
        entity.setType(type);
        entity.setIconName("ic_category_default");
        entity.setParentId(parentId);
        entity.setWalletId(walletId);
        entity.setDefault(false);
        entity.setDeleted(false);
        entity.setSyncStatus(0);
        entity.setUpdatedAt(System.currentTimeMillis());
        return entity;
    }
}



