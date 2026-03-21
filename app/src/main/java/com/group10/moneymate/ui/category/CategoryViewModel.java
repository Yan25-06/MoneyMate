package com.group10.moneymate.ui.category;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.google.firebase.auth.FirebaseAuth;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.Constants;

import java.util.List;

public class CategoryViewModel extends AndroidViewModel {

    private final CategoryRepository repository;
    private final String userId;

    // Tab hiện tại: TYPE_EXPENSE hoặc TYPE_INCOME
    private final MutableLiveData<String> selectedType =
            new MutableLiveData<>(Constants.TYPE_EXPENSE);

    // Danh sách danh mục lọc theo tab đang chọn
    private final LiveData<List<CategoryEntity>> categories;

    public CategoryViewModel(@NonNull Application application) {
        super(application);
        AppContainer container = ((MoneyMateApplication) application).appContainer;
        repository = container.categoryRepository;
        userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";

        // Khi selectedType thay đổi → query lại từ Room
        categories = Transformations.switchMap(selectedType, type ->
                repository.getCategoriesByType(userId, type)
        );
    }

    // ─── Expose LiveData ──────────────────────────────────────────────────────

    public LiveData<List<CategoryEntity>> getCategories() {
        return categories;
    }
    public LiveData<String> getSelectedType() {
        return selectedType;
    }

    // Add getter for single category by id used by AddEdit fragment
    public LiveData<CategoryEntity> getCategoryById(String id) {
        return repository.getCategoryById(id);
    }

    // ─── Tab filter ───────────────────────────────────────────────────────────

    public void setSelectedType(String type) {
        selectedType.setValue(type);
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    public void addCategory(String name, String iconResId, String colorHex, String type) {
        CategoryEntity entity = new CategoryEntity();
        entity.setUserId(userId);
        entity.setName(name);
        entity.setIconResId(iconResId);
        entity.setColorHex(colorHex);
        entity.setType(type);
        entity.setDefault(false);
        repository.addCategory(entity);
    }

    public void updateCategory(CategoryEntity category) {
        repository.updateCategory(category);
    }

    /**
     * Chỉ xóa được danh mục tùy chỉnh (isDefault = false).
     * Repository sẽ guard nếu là default.
     */
    public void deleteCategory(CategoryEntity category) {
        repository.deleteCategory(category);
    }
}