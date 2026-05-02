package com.group10.moneymate.ui.transaction;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.models.DebtType;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.R;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransactionCategoryPickerViewModel extends AndroidViewModel {

    public static final String TYPE_DEBT = "DEBT";

    private final CategoryRepository categoryRepository;
    private final com.group10.moneymate.data.repository.WalletRepository walletRepository;
    private final String userId;
    private final String allWalletLabel;

    private final MutableLiveData<Filter> filterState =
            new MutableLiveData<>(new Filter(Constants.TYPE_EXPENSE, null));

    private final LiveData<List<TransactionCategoryPickerItem>> items;
    private final LiveData<List<com.group10.moneymate.data.local.entity.WalletEntity>> wallets;

    public TransactionCategoryPickerViewModel(@NonNull Application application) {
        super(application);
        MoneyMateApplication app = (MoneyMateApplication) application;
        AppContainer container = app.getAppContainer();
        categoryRepository = container.categoryRepository;
        walletRepository = container.walletRepository;
        userId = container.authRepository.getCurrentUserId();
        allWalletLabel = application.getString(R.string.category_wallet_scope_all);

        wallets = walletRepository.getAllByUser(userId);
        items = Transformations.switchMap(filterState, this::createItemsSource);
    }

    public LiveData<List<TransactionCategoryPickerItem>> getItems() {
        return items;
    }

    public void setSelectedType(@NonNull String type) {
        Filter current = filterState.getValue();
        String walletId = current == null ? null : current.walletId;
        updateFilter(type, walletId);
    }

    public void setSelectedWalletId(@Nullable String walletId) {
        Filter current = filterState.getValue();
        String type = current == null ? Constants.TYPE_EXPENSE : current.type;
        updateFilter(type, walletId);
    }

    private void updateFilter(@NonNull String type, @Nullable String walletId) {
        Filter current = filterState.getValue();
        if (current != null && current.matches(type, walletId)) {
            return;
        }
        filterState.setValue(new Filter(type, walletId));
    }

    @NonNull
    private List<TransactionCategoryPickerItem> buildDebtItems() {
        List<TransactionCategoryPickerItem> debtItems = new ArrayList<>();
        debtItems.add(TransactionCategoryPickerItem.forDebt(DebtType.LEND));
        debtItems.add(TransactionCategoryPickerItem.forDebt(DebtType.BORROW));
        debtItems.add(TransactionCategoryPickerItem.forDebt(DebtType.DEBT_COLLECTION));
        debtItems.add(TransactionCategoryPickerItem.forDebt(DebtType.REPAYMENT));
        return debtItems;
    }

    @NonNull
    private List<TransactionCategoryPickerItem> buildCategoryItems(@Nullable List<CategoryEntity> categories,
                                                                   @Nullable List<com.group10.moneymate.data.local.entity.WalletEntity> walletList) {
        List<TransactionCategoryPickerItem> results = new ArrayList<>();
        if (categories == null || categories.isEmpty()) {
            return results;
        }

        List<CategoryEntity> roots = new ArrayList<>();
        Map<String, List<CategoryEntity>> childrenByParent = new HashMap<>();
        partitionCategories(categories, roots, childrenByParent);

        roots.sort(defaultCategoryComparator());
        for (CategoryEntity root : roots) {
            results.add(buildRootItem(root, childrenByParent.get(root.getId()), walletList));
        }

        // Orphaned children (missing parent) are listed after roots to avoid hiding data.
        for (Map.Entry<String, List<CategoryEntity>> entry : childrenByParent.entrySet()) {
            if (containsRoot(roots, entry.getKey())) {
                continue;
            }
            addOrphanedChildren(results, entry.getValue(), walletList);
        }

        return results;
    }

    @NonNull
    private LiveData<List<TransactionCategoryPickerItem>> createItemsSource(@NonNull Filter filter) {
        if (TYPE_DEBT.equals(filter.type)) {
            MutableLiveData<List<TransactionCategoryPickerItem>> debtItems = new MutableLiveData<>();
            debtItems.setValue(buildDebtItems());
            return debtItems;
        }
        return createCategoryItemsSource(filter);
    }

    @NonNull
    private LiveData<List<TransactionCategoryPickerItem>> createCategoryItemsSource(@NonNull Filter filter) {
        LiveData<List<CategoryEntity>> source = categoryRepository.getCategoriesByTypeAndWallet(
                userId,
                filter.type,
                filter.walletId
        );
        androidx.lifecycle.MediatorLiveData<List<TransactionCategoryPickerItem>> result =
                new androidx.lifecycle.MediatorLiveData<>();
        result.addSource(source, categories ->
                result.setValue(buildCategoryItems(categories, wallets.getValue())));
        result.addSource(wallets, walletList ->
                result.setValue(buildCategoryItems(source.getValue(), walletList)));
        return result;
    }

    private void partitionCategories(@NonNull List<CategoryEntity> categories,
                                     @NonNull List<CategoryEntity> roots,
                                     @NonNull Map<String, List<CategoryEntity>> childrenByParent) {
        for (CategoryEntity category : categories) {
            if (category == null) {
                continue;
            }
            String parentId = normalizeNullable(category.getParentId());
            if (parentId == null) {
                roots.add(category);
            } else {
                childrenByParent
                        .computeIfAbsent(parentId, key -> new ArrayList<>())
                        .add(category);
            }
        }
    }

    @NonNull
    private TransactionCategoryPickerItem buildRootItem(
            @NonNull CategoryEntity root,
            @Nullable List<CategoryEntity> children,
            @Nullable List<com.group10.moneymate.data.local.entity.WalletEntity> walletList
    ) {
        return TransactionCategoryPickerItem.forCategoryGroup(
                root,
                buildChildItems(children, walletList),
                resolveWalletLabel(root, walletList)
        );
    }

    @NonNull
    private List<TransactionCategoryPickerItem.CategoryChildItem> buildChildItems(
            @Nullable List<CategoryEntity> children,
            @Nullable List<com.group10.moneymate.data.local.entity.WalletEntity> walletList
    ) {
        List<TransactionCategoryPickerItem.CategoryChildItem> childItems = new ArrayList<>();
        if (children == null || children.isEmpty()) {
            return childItems;
        }
        children.sort(Comparator.comparing(CategoryEntity::getName, String::compareToIgnoreCase));
        for (CategoryEntity child : children) {
            childItems.add(new TransactionCategoryPickerItem.CategoryChildItem(
                    child,
                    resolveWalletLabel(child, walletList)
            ));
        }
        return childItems;
    }

    private void addOrphanedChildren(@NonNull List<TransactionCategoryPickerItem> results,
                                     @Nullable List<CategoryEntity> children,
                                     @Nullable List<com.group10.moneymate.data.local.entity.WalletEntity> walletList) {
        if (children == null || children.isEmpty()) {
            return;
        }
        children.sort(Comparator.comparing(CategoryEntity::getName, String::compareToIgnoreCase));
        for (CategoryEntity child : children) {
            results.add(TransactionCategoryPickerItem.forCategoryGroup(
                    child,
                    new ArrayList<>(),
                    resolveWalletLabel(child, walletList)
            ));
        }
    }

    @NonNull
    private String resolveWalletLabel(@NonNull CategoryEntity category,
                                      @Nullable List<com.group10.moneymate.data.local.entity.WalletEntity> walletList) {
        String walletId = normalizeNullable(category.getWalletId());
        if (walletId == null) {
            return allWalletLabel;
        }
        if (walletList != null) {
            for (com.group10.moneymate.data.local.entity.WalletEntity wallet : walletList) {
                if (walletId.equals(wallet.getId())) {
                    return wallet.getName();
                }
            }
        }
        return allWalletLabel;
    }

    private boolean containsRoot(@NonNull List<CategoryEntity> roots, @NonNull String id) {
        for (CategoryEntity root : roots) {
            if (id.equals(root.getId())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private String normalizeNullable(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @NonNull
    private Comparator<CategoryEntity> defaultCategoryComparator() {
        return (left, right) -> {
            if (left.isDefault() != right.isDefault()) {
                return left.isDefault() ? -1 : 1;
            }
            return left.getName().compareToIgnoreCase(right.getName());
        };
    }

    private static final class Filter {
        @NonNull
        private final String type;
        @Nullable
        private final String walletId;

        private Filter(@NonNull String type, @Nullable String walletId) {
            this.type = type;
            this.walletId = walletId;
        }

        private boolean matches(@NonNull String type, @Nullable String walletId) {
            if (!this.type.equals(type)) {
                return false;
            }
            if (this.walletId == null) {
                return walletId == null;
            }
            return this.walletId.equals(walletId);
        }
    }
}
