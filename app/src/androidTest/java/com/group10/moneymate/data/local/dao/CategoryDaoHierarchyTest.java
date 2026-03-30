package com.group10.moneymate.data.local.dao;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.group10.moneymate.data.local.AppDatabase;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.UserEntity;
import com.group10.moneymate.utils.Constants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class CategoryDaoHierarchyTest {

    private static final String USER_ID = "u_test_hierarchy";

    private AppDatabase database;
    private CategoryDao categoryDao;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        categoryDao = database.categoryDao();
        database.userDao().insertUser(buildUser(USER_ID));
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void getRootsByTypeAndWalletSync_returnsOnlyRootsForType() {
        CategoryEntity rootExpense = buildCategory("cat_root_exp", USER_ID, "Ăn uống", Constants.TYPE_EXPENSE, null, null);
        CategoryEntity rootIncome = buildCategory("cat_root_inc", USER_ID, "Lương", Constants.TYPE_INCOME, null, null);
        CategoryEntity childExpense = buildCategory("cat_child_exp", USER_ID, "Cà phê", Constants.TYPE_EXPENSE, rootExpense.getId(), null);

        categoryDao.insertCategory(rootExpense);
        categoryDao.insertCategory(rootIncome);
        categoryDao.insertCategory(childExpense);

        List<CategoryEntity> roots = categoryDao.getRootCategoriesByTypeAndWalletSync(USER_ID, Constants.TYPE_EXPENSE, null);

        assertEquals(1, roots.size());
        assertEquals(rootExpense.getId(), roots.get(0).getId());
    }

    @Test
    public void getChildrenByParentSync_returnsOnlyChildren() {
        CategoryEntity rootExpense = buildCategory("cat_root_food", USER_ID, "Ăn uống", Constants.TYPE_EXPENSE, null, null);
        CategoryEntity childCoffee = buildCategory("cat_child_coffee", USER_ID, "Cà phê", Constants.TYPE_EXPENSE, rootExpense.getId(), null);
        CategoryEntity childSnack = buildCategory("cat_child_snack", USER_ID, "Ăn vặt", Constants.TYPE_EXPENSE, rootExpense.getId(), null);

        categoryDao.insertCategory(rootExpense);
        categoryDao.insertCategory(childCoffee);
        categoryDao.insertCategory(childSnack);

        List<CategoryEntity> children = categoryDao.getChildrenByParentSync(USER_ID, rootExpense.getId());

        assertEquals(2, children.size());
        assertTrue(children.stream().anyMatch(item -> item.getId().equals(childCoffee.getId())));
        assertTrue(children.stream().anyMatch(item -> item.getId().equals(childSnack.getId())));
        assertFalse(children.stream().anyMatch(item -> item.getId().equals(rootExpense.getId())));
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

