package com.group10.moneymate.ui.category;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.data.repository.WalletRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.Constants;

import java.util.List;

public class CategoryViewModel extends AndroidViewModel {

    public enum CategoryAction {
        ADD,
        UPDATE,
        DELETE
    }

    public static final class CategoryActionResult {
        @NonNull
        private final CategoryAction action;
        @NonNull
        private final CategoryRepository.CategoryValidationResult validationResult;

        public CategoryActionResult(@NonNull CategoryAction action,
                                    @NonNull CategoryRepository.CategoryValidationResult validationResult) {
            this.action = action;
            this.validationResult = validationResult;
        }

        @NonNull
        public CategoryAction getAction() {
            return action;
        }

        @NonNull
        public CategoryRepository.CategoryValidationResult getValidationResult() {
            return validationResult;
        }
    }

    private final CategoryRepository repository;
    private final WalletRepository walletRepository;
    private final String userId;

    // Tab hiện tại: TYPE_EXPENSE hoặc TYPE_INCOME
    private final MutableLiveData<String> selectedType =
            new MutableLiveData<>(Constants.TYPE_EXPENSE);

    // Danh sách danh mục lọc theo tab đang chọn
    private final LiveData<List<CategoryEntity>> categories;
    private final LiveData<List<WalletEntity>> activeWallets;
    private final MutableLiveData<CategoryActionResult> categoryActionResult = new MutableLiveData<>();

    public CategoryViewModel(@NonNull Application application) {
        super(application);
        AppContainer container = ((MoneyMateApplication) application).getAppContainer();
        repository = container.categoryRepository;
        walletRepository = container.walletRepository;
        userId = container.authRepository.getCurrentUserId();

        // Khi selectedType thay đổi → query lại từ Room
        categories = Transformations.switchMap(selectedType, type ->
                repository.getCategoriesByType(userId, type)
        );

        activeWallets = walletRepository.getActiveByUser(userId);
    }

    // ─── Expose LiveData ──────────────────────────────────────────────────────

    public LiveData<List<CategoryEntity>> getCategories() {
        return categories;
    }

    public LiveData<CategoryActionResult> getCategoryActionResult() {
        return categoryActionResult;
    }

    public LiveData<List<WalletEntity>> getActiveWallets() {
        return activeWallets;
    }

    public void clearCategoryActionResult() {
        categoryActionResult.setValue(null);
    }

    public LiveData<String> getSelectedType() {
        return selectedType;
    }

    // Add getter for single category by id used by AddEdit fragment
    public LiveData<CategoryEntity> getCategoryById(String id) {
        return repository.getCategoryById(id);
    }

    public LiveData<List<CategoryEntity>> getRootCategoriesByTypeAndWallet(String type,
                                                                            @Nullable String walletId) {
        return repository.getRootCategoriesByTypeAndWallet(userId, type, walletId);
    }

    public LiveData<List<CategoryEntity>> getChildrenByParent(String parentId) {
        return repository.getChildrenByParent(userId, parentId);
    }

    public LiveData<List<CategoryEntity>> getCategoriesByTypeAndWallet(String type,
                                                                       @Nullable String walletId) {
        return repository.getCategoriesByTypeAndWallet(userId, type, walletId);
    }

    // ─── Tab filter ───────────────────────────────────────────────────────────

    public void setSelectedType(String type) {
        selectedType.setValue(type);
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    public void addCategory(String name,
                            String iconResId,
                            String colorHex,
                            String type,
                            @Nullable String parentId) {
        CategoryEntity entity = new CategoryEntity();
        entity.setUserId(userId);
        entity.setName(name);
        entity.setIconName(iconResId);
        entity.setType(type);
        entity.setParentId(parentId);
        entity.setWalletId(null);
        entity.setDefault(false);
        repository.addCategoryValidatedAsync(entity,
                result -> categoryActionResult.setValue(new CategoryActionResult(CategoryAction.ADD, result)));
    }

    public void updateCategory(CategoryEntity category) {
        repository.updateCategoryValidatedAsync(category,
                result -> categoryActionResult.setValue(new CategoryActionResult(CategoryAction.UPDATE, result)));
    }

    /**
     * Chỉ xóa được danh mục tùy chỉnh (isDefault = false).
     * Repository sẽ guard nếu là default.
     */
    public void deleteCategory(CategoryEntity category) {
        repository.deleteCategoryValidatedAsync(category,
                result -> categoryActionResult.setValue(new CategoryActionResult(CategoryAction.DELETE, result)));
    }
}