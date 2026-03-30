package com.group10.moneymate.ui.category;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.databinding.FragmentCategoryListBinding;
import com.group10.moneymate.utils.Constants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryListFragment extends Fragment {

    private static final String RESULT_SELECTED_WALLET_ID = "result_selected_wallet_id";
    private static final String RESULT_SELECTED_WALLET_LABEL = "result_selected_wallet_label";

    private FragmentCategoryListBinding binding;
    private CategoryViewModel viewModel;
    private CategoryAdapter adapter;
    private String currentType = Constants.TYPE_EXPENSE;
    private boolean isDebtTab;
    private String selectedWalletId;
    private String selectedWalletLabel;
    private List<WalletEntity> walletOptions = new ArrayList<>();
    private LiveData<List<CategoryEntity>> currentCategorySource;
    private Observer<List<CategoryEntity>> currentCategoryObserver;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoryListBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CategoryViewModel.class);

        setupRecyclerView();
        setupTabs();
        setupToolbar();
        setupWalletFilter();
        observeCategoryActions();
        observeWallets();
        observeCategories(currentType, selectedWalletId);
    }

    private void setupToolbar() {
        binding.tbCategory.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
    }

    private void setupRecyclerView() {
        adapter = new CategoryAdapter();
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategories.setAdapter(adapter);

        adapter.setOnAddNewClickListener(() -> {
            if (isDebtTab) {
                return;
            }
            CategoryListFragmentDirections.ActionCategoryListToAddEdit action =
                    CategoryListFragmentDirections.actionCategoryListToAddEdit();
            action.setInitialType(currentType);
            Navigation.findNavController(requireView()).navigate(action);
        });

        adapter.setOnItemClickListener(item -> {
            CategoryListFragmentDirections.ActionCategoryListToAddEdit action =
                    CategoryListFragmentDirections.actionCategoryListToAddEdit();
            action.setCategoryId(item.getId());
            Navigation.findNavController(requireView()).navigate(action);
        });
    }

    private void observeCategoryActions() {
        viewModel.getCategoryActionResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null || result.getAction() != CategoryViewModel.CategoryAction.DELETE) {
                return;
            }
            CategoryRepository.CategoryValidationResult validationResult = result.getValidationResult();
            if (!validationResult.isValid()) {
                Toast.makeText(requireContext(), mapValidationKeyToMessage(validationResult.getErrorKey()), Toast.LENGTH_SHORT).show();
            }
            viewModel.clearCategoryActionResult();
        });
    }

    private String mapValidationKeyToMessage(String errorKey) {
        if ("category.validation.default_cannot_delete".equals(errorKey)) {
            return getString(R.string.category_validation_default_cannot_delete);
        }
        if ("category.validation.cannot_delete_with_children".equals(errorKey)) {
            return getString(R.string.category_validation_cannot_delete_with_children);
        }
        return getString(R.string.category_validation_generic_error);
    }

    private void setupTabs() {
        binding.tlCategoryType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 1) {
                    currentType = Constants.TYPE_INCOME;
                    isDebtTab = false;
                } else if (tab.getPosition() == 2) {
                    currentType = Constants.TYPE_EXPENSE;
                    isDebtTab = true;
                } else {
                    currentType = Constants.TYPE_EXPENSE;
                    isDebtTab = false;
                }
                if (isDebtTab) {
                    if (currentCategorySource != null && currentCategoryObserver != null) {
                        currentCategorySource.removeObserver(currentCategoryObserver);
                    }
                    adapter.submitList(new ArrayList<>());
                    binding.tvEmpty.setVisibility(View.VISIBLE);
                    return;
                }
                observeCategories(currentType, selectedWalletId);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupWalletFilter() {
        binding.layoutWalletFilter.setOnClickListener(v -> {
            CategoryListFragmentDirections.ActionCategoryListToWalletPicker action =
                    CategoryListFragmentDirections.actionCategoryListToWalletPicker();
            action.setSelectedWalletId(selectedWalletId);
            Navigation.findNavController(v).navigate(action);
        });
        updateWalletFilterLabel();
    }

    private void observeWallets() {
        viewModel.getActiveWallets().observe(getViewLifecycleOwner(), wallets -> {
            walletOptions = wallets == null ? new ArrayList<>() : wallets;
            if (selectedWalletId != null && !containsWalletId(selectedWalletId)) {
                selectedWalletId = null;
                selectedWalletLabel = null;
            }
            updateWalletFilterLabel();
            updateWalletFilterIcon();
        });

        NavBackStackEntry currentEntry = Navigation.findNavController(requireView()).getCurrentBackStackEntry();
        if (currentEntry != null) {
            SavedStateHandle savedStateHandle = currentEntry.getSavedStateHandle();
            savedStateHandle.getLiveData(RESULT_SELECTED_WALLET_ID, (String) null)
                    .observe(getViewLifecycleOwner(), walletId -> {
                        selectedWalletId = walletId;
                        if (!isDebtTab) {
                            observeCategories(currentType, selectedWalletId);
                        }
                        updateWalletFilterLabel();
                        updateWalletFilterIcon();
                    });
            savedStateHandle.getLiveData(RESULT_SELECTED_WALLET_LABEL, getString(R.string.category_wallet_scope_all))
                    .observe(getViewLifecycleOwner(), label -> {
                        selectedWalletLabel = label != null ? label : getString(R.string.category_wallet_scope_all);
                        updateWalletFilterLabel();
                        updateWalletFilterIcon();
                    });
        }
    }

    private void updateWalletFilterLabel() {
        if (selectedWalletLabel != null) {
            binding.tvWalletFilterValue.setText(selectedWalletLabel);
        } else {
            binding.tvWalletFilterValue.setText(getString(R.string.category_wallet_scope_all));
        }
    }

    private void updateWalletFilterIcon() {
        if (binding == null) {
            return;
        }
        if (selectedWalletId == null) {
            binding.ivWalletFilterIcon.setVisibility(View.GONE);
            return;
        }
        for (WalletEntity wallet : walletOptions) {
            if (selectedWalletId.equals(wallet.getId())) {
                int iconRes = com.group10.moneymate.utils.IconProvider.resolveWalletIcon(
                        requireContext(),
                        wallet.getIconName(),
                        wallet.getType()
                );
                binding.ivWalletFilterIcon.setImageResource(iconRes);
                binding.ivWalletFilterIcon.setImageTintList(null);
                binding.ivWalletFilterIcon.setVisibility(View.VISIBLE);
                return;
            }
        }
        binding.ivWalletFilterIcon.setVisibility(View.GONE);
    }

    private boolean containsWalletId(String walletId) {
        for (WalletEntity wallet : walletOptions) {
            if (walletId.equals(wallet.getId())) {
                return true;
            }
        }
        return false;
    }

    private void observeCategories(String type, String walletId) {
        if (currentCategorySource != null && currentCategoryObserver != null) {
            currentCategorySource.removeObserver(currentCategoryObserver);
        }
        currentCategorySource = viewModel.getCategoriesByTypeAndWallet(type, walletId);
        currentCategoryObserver = categories -> {
            List<CategoryListItem> items = buildListItems(categories);
            adapter.submitList(items);
            binding.tvEmpty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        };
        currentCategorySource.observe(getViewLifecycleOwner(), currentCategoryObserver);
    }

    private List<CategoryListItem> buildListItems(List<CategoryEntity> categories) {
        List<CategoryListItem> items = new ArrayList<>();
        if (!isDebtTab) {
            items.add(CategoryListItem.addNew(getString(R.string.category_add_new)));
        }
        if (categories == null || categories.isEmpty()) {
            return items;
        }

        Map<String, List<CategoryEntity>> childrenByParent = new HashMap<>();
        List<CategoryEntity> roots = new ArrayList<>();

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

        roots.sort(defaultCategoryComparator());
        for (CategoryEntity root : roots) {
            List<CategoryListItem.CategoryChildItem> childItems = new ArrayList<>();
            List<CategoryEntity> children = childrenByParent.get(root.getId());
            if (children != null) {
                children.sort(Comparator.comparing(CategoryEntity::getName, String::compareToIgnoreCase));
                for (CategoryEntity child : children) {
                    childItems.add(new CategoryListItem.CategoryChildItem(
                            child,
                            resolveWalletLabel(child)
                    ));
                }
            }
            items.add(CategoryListItem.group(root, childItems, resolveWalletLabel(root)));
        }

        for (Map.Entry<String, List<CategoryEntity>> entry : childrenByParent.entrySet()) {
            if (containsRoot(roots, entry.getKey())) {
                continue;
            }
            List<CategoryEntity> children = entry.getValue();
            if (children == null) {
                continue;
            }
            children.sort(Comparator.comparing(CategoryEntity::getName, String::compareToIgnoreCase));
            for (CategoryEntity child : children) {
                items.add(CategoryListItem.group(
                        child,
                        new ArrayList<>(),
                        resolveWalletLabel(child)
                ));
            }
        }

        return items;
    }

    private String resolveWalletLabel(CategoryEntity category) {
        String walletId = normalizeNullable(category.getWalletId());
        if (walletId == null) {
            return getString(R.string.category_wallet_scope_all);
        }
        for (WalletEntity wallet : walletOptions) {
            if (walletId.equals(wallet.getId())) {
                return wallet.getName();
            }
        }
        return getString(R.string.budget_unknown_wallet);
    }

    private boolean containsRoot(List<CategoryEntity> roots, String id) {
        for (CategoryEntity root : roots) {
            if (id.equals(root.getId())) {
                return true;
            }
        }
        return false;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Comparator<CategoryEntity> defaultCategoryComparator() {
        return (left, right) -> {
            if (left.isDefault() != right.isDefault()) {
                return left.isDefault() ? -1 : 1;
            }
            return left.getName().compareToIgnoreCase(right.getName());
        };
    }

    @Override
    public void onDestroyView() {
        if (currentCategorySource != null && currentCategoryObserver != null) {
            currentCategorySource.removeObserver(currentCategoryObserver);
        }
        super.onDestroyView();
        binding = null; // Prevent memory leak
    }
}
