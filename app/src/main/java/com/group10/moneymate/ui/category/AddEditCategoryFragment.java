package com.group10.moneymate.ui.category;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.databinding.DialogCategoryActionBinding;
import com.group10.moneymate.databinding.FragmentAddEditCategoryBinding;
import com.group10.moneymate.ui.transaction.CategoryIconPickerFragment;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.utils.IconProvider;
import com.group10.moneymate.utils.LoadingHelper;

import java.util.ArrayList;
import java.util.List;

public class AddEditCategoryFragment extends Fragment {

    private FragmentAddEditCategoryBinding binding;
    private CategoryViewModel viewModel;
    private CategoryWalletAdapter walletAdapter;

    private String categoryId;
    private CategoryEntity existingCategory;

    private String selectedIconResId = "ic_category_other";
    @Nullable
    private String selectedParentId;
    @Nullable
    private String selectedParentLabel;
    @Nullable
    private LiveData<List<CategoryEntity>> childrenSource;
    @Nullable
    private CategoryViewModel.CategoryAction pendingCategoryAction;
    @NonNull
    private String selectedType = Constants.TYPE_EXPENSE;
    private boolean isSaving;
    private final LoadingHelper loadingHelper = new LoadingHelper();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentAddEditCategoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CategoryViewModel.class);

        Bundle args = getArguments() == null ? new Bundle() : getArguments();
        AddEditCategoryFragmentArgs safeArgs = AddEditCategoryFragmentArgs.fromBundle(args);
        categoryId = safeArgs.getCategoryId();
        String initialType = safeArgs.getInitialType();

        setupToolbar();
        setupIconRow();
        setupParentRow();
        setupWalletList();
        setupSaveButton();
        observeParentSelectionResult();
        observeCategoryActionResult();

        if (Constants.TYPE_INCOME.equals(initialType)) {
            selectedType = Constants.TYPE_INCOME;
        }
        binding.tvTypeLabel.setText(Constants.TYPE_INCOME.equals(selectedType)
                ? R.string.income
                : R.string.expense);

        loadExistingCategoryIfNeeded();
        observeWallets();
    }

    private void setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener(v -> {
            if (isSaving) {
                return;
            }
            Navigation.findNavController(v).navigateUp();
        });
        if (categoryId != null) {
            binding.topAppBar.inflateMenu(R.menu.menu_edit_category);
            binding.topAppBar.setOnMenuItemClickListener(item -> {
                if (item.getItemId() == R.id.action_delete) {
                    handleDeleteClick();
                    return true;
                }
                return false;
            });
        }
    }

    private void setupIconRow() {
        binding.etCategoryName.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        binding.ivCategoryIcon.setOnClickListener(v -> {
            AddEditCategoryFragmentDirections.ActionAddEditCategoryFragmentToCategoryIconPickerFragment action =
                    AddEditCategoryFragmentDirections.actionAddEditCategoryFragmentToCategoryIconPickerFragment();
            action.setSelectedIconName(selectedIconResId);
            Navigation.findNavController(v).navigate(action);
        });
    }

    private void setupParentRow() {
        binding.layoutParentRow.setOnClickListener(v -> {
            AddEditCategoryFragmentDirections.ActionAddEditCategoryFragmentToParentCategoryPickerFragment action =
                    AddEditCategoryFragmentDirections.actionAddEditCategoryFragmentToParentCategoryPickerFragment();
            action.setSelectedParentId(selectedParentId);
            action.setCategoryType(selectedType);
            action.setCurrentCategoryId(categoryId);
            Navigation.findNavController(v).navigate(action);
        });
        updateParentRow();
    }

    private void setupWalletList() {
        walletAdapter = new CategoryWalletAdapter();
        binding.rvWallets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvWallets.setAdapter(walletAdapter);
        binding.rvWallets.setNestedScrollingEnabled(false);
    }

    private void observeWallets() {
        viewModel.getActiveWallets().observe(getViewLifecycleOwner(), wallets -> {
            List<WalletEntity> items = wallets == null ? new ArrayList<>() : wallets;
            if (existingCategory == null || existingCategory.getWalletId() == null) {
                walletAdapter.submitList(items);
                return;
            }
            List<WalletEntity> filtered = new ArrayList<>();
            for (WalletEntity wallet : items) {
                if (existingCategory.getWalletId().equals(wallet.getId())) {
                    filtered.add(wallet);
                    break;
                }
            }
            walletAdapter.submitList(filtered.isEmpty() ? items : filtered);
        });
    }

    private void handleDeleteClick() {
        if (existingCategory == null) {
            return;
        }
        showDeleteConfirmDialog(existingCategory.getName());
    }

    private void showDeleteConfirmDialog(@NonNull String name) {
        DialogCategoryActionBinding dialogBinding = DialogCategoryActionBinding.inflate(
                LayoutInflater.from(requireContext())
        );
        dialogBinding.ivDialogIcon.setImageResource(R.drawable.outline_delete_24);
        dialogBinding.ivDialogIcon.setImageTintList(ColorStateList.valueOf(
                requireContext().getColor(R.color.budget_danger_red)
        ));
        dialogBinding.tvDialogTitle.setText(R.string.category_delete_confirm_title);
        dialogBinding.tvDialogMessage.setText(getString(R.string.category_delete_confirm_message, name));

        AlertDialog dialog = new MaterialAlertDialogBuilder(
                requireContext(),
                R.style.ThemeOverlay_MoneyMate_CategoryDialog
        )
                .setView(dialogBinding.getRoot())
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton(R.string.btn_delete, (dialogInterface, which) -> {
                    pendingCategoryAction = CategoryViewModel.CategoryAction.DELETE;
                    startSavingUi();
                    viewModel.deleteCategory(existingCategory);
                })
                .show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(requireContext().getColor(R.color.budget_danger_red));
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(requireContext().getColor(R.color.budget_text_secondary));
    }

    private void observeParentSelectionResult() {
        NavBackStackEntry backStackEntry = Navigation.findNavController(requireView()).getCurrentBackStackEntry();
        if (backStackEntry == null) {
            return;
        }
        backStackEntry.getSavedStateHandle()
                .getLiveData(ParentCategoryPickerFragment.RESULT_PARENT_ID)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    selectedParentId = value.toString();
                    updateParentRow();
                    backStackEntry.getSavedStateHandle()
                            .set(ParentCategoryPickerFragment.RESULT_PARENT_ID, null);
                });

        backStackEntry.getSavedStateHandle()
                .getLiveData(ParentCategoryPickerFragment.RESULT_PARENT_LABEL)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    selectedParentLabel = value.toString();
                    updateParentRow();
                    backStackEntry.getSavedStateHandle()
                            .set(ParentCategoryPickerFragment.RESULT_PARENT_LABEL, null);
                });

        backStackEntry.getSavedStateHandle()
                .getLiveData(CategoryIconPickerFragment.RESULT_ICON_NAME)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    selectedIconResId = value.toString();
                    updateIconPreview();
                    backStackEntry.getSavedStateHandle()
                            .set(CategoryIconPickerFragment.RESULT_ICON_NAME, null);
                });
    }

    private void updateParentRow() {
        if (selectedParentLabel != null && selectedParentId != null) {
            binding.tvParentName.setText(selectedParentLabel);
            binding.tvParentHint.setVisibility(View.GONE);
            return;
        }
        if (selectedParentId != null) {
            viewModel.getCategoryById(selectedParentId).observe(getViewLifecycleOwner(), parent -> {
                if (parent == null) {
                    return;
                }
                binding.tvParentName.setText(parent.getName());
                binding.tvParentHint.setVisibility(View.GONE);
            });
            return;
        }
        binding.tvParentName.setText("");
        binding.tvParentHint.setVisibility(View.VISIBLE);
    }

    private void updateIconPreview() {
        int iconResId = IconProvider.resolveCategoryIcon(requireContext(), selectedIconResId);
        binding.ivCategoryIcon.setImageResource(iconResId);
    }

    private void loadExistingCategoryIfNeeded() {
        if (categoryId == null) {
            binding.topAppBar.setTitle(R.string.add_group);
            binding.btnSave.setText(R.string.btn_save);
            return;
        }

        binding.topAppBar.setTitle(R.string.edit_group);
        binding.btnSave.setText(R.string.btn_update);
        viewModel.getCategoryById(categoryId).observe(getViewLifecycleOwner(), category -> {
            if (category == null) {
                return;
            }
            if (existingCategory != null) {
                return;
            }
            existingCategory = category;

            binding.etCategoryName.setText(category.getName());
            selectedIconResId = category.getIconName() == null || category.getIconName().trim().isEmpty()
                    ? "ic_category_other"
                    : category.getIconName();
            updateIconPreview();

            selectedParentId = normalizeNullable(category.getParentId());
            selectedParentLabel = null;
            updateParentRow();

            selectedType = Constants.TYPE_INCOME.equals(category.getType())
                    ? Constants.TYPE_INCOME
                    : Constants.TYPE_EXPENSE;
            binding.tvTypeLabel.setText(Constants.TYPE_INCOME.equals(selectedType)
                    ? R.string.income
                    : R.string.expense);
        });
    }

    private void saveCategory() {
        if (isSaving) {
            return;
        }
        String name = binding.etCategoryName.getText() != null
                ? binding.etCategoryName.getText().toString().trim().replaceAll("\\s{2,}", " ")
                : "";

        if (TextUtils.isEmpty(name)) {
            binding.etCategoryName.setError(getString(R.string.error_name_required));
            return;
        }

        if (existingCategory != null) {
            existingCategory.setName(name);
            existingCategory.setIconName(selectedIconResId);
            existingCategory.setParentId(selectedParentId);
            if (!existingCategory.isDefault()) {
                existingCategory.setType(selectedType);
            }
            pendingCategoryAction = CategoryViewModel.CategoryAction.UPDATE;
            startSavingUi();
            viewModel.updateCategory(existingCategory);
        } else {
            pendingCategoryAction = CategoryViewModel.CategoryAction.ADD;
            startSavingUi();
            viewModel.addCategory(name, selectedIconResId, "", selectedType, selectedParentId);
        }
    }

    private void setupSaveButton() {
        binding.btnSave.setOnClickListener(v -> saveCategory());
    }

    private void observeCategoryActionResult() {
        viewModel.getCategoryActionResult().observe(getViewLifecycleOwner(), result -> {
            if (result == null || pendingCategoryAction == null) {
                return;
            }
            if (result.getAction() != pendingCategoryAction) {
                return;
            }

            stopSavingUi();
            CategoryRepository.CategoryValidationResult validationResult = result.getValidationResult();
            if (!validationResult.isValid()) {
                showValidationError(validationResult);
                pendingCategoryAction = null;
                viewModel.clearCategoryActionResult();
                return;
            }

            if (result.getAction() == CategoryViewModel.CategoryAction.UPDATE) {
                Toast.makeText(requireContext(), R.string.category_updated, Toast.LENGTH_SHORT).show();
            } else if (result.getAction() == CategoryViewModel.CategoryAction.ADD) {
                Toast.makeText(requireContext(), R.string.category_added, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(requireContext(), R.string.category_deleted, Toast.LENGTH_SHORT).show();
            }
            pendingCategoryAction = null;
            viewModel.clearCategoryActionResult();
            Navigation.findNavController(requireView()).navigateUp();
        });
    }

    private void startSavingUi() {
        isSaving = true;
        binding.btnSave.setEnabled(false);
        loadingHelper.show(this, R.string.common_saving);
    }

    private void stopSavingUi() {
        isSaving = false;
        if (binding != null) {
            binding.btnSave.setEnabled(true);
        }
        loadingHelper.dismiss();
    }

    private void showValidationError(@NonNull CategoryRepository.CategoryValidationResult result) {
        String message = mapValidationKeyToMessage(result.getErrorKey());
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @NonNull
    private String mapValidationKeyToMessage(@Nullable String errorKey) {
        if ("category.validation.self_parent".equals(errorKey)) {
            return getString(R.string.category_validation_self_parent);
        }
        if ("category.validation.parent_not_found".equals(errorKey)) {
            return getString(R.string.category_validation_parent_not_found);
        }
        if ("category.validation.depth_limit_exceeded".equals(errorKey)) {
            return getString(R.string.category_validation_depth_limit_exceeded);
        }
        if ("category.validation.type_mismatch".equals(errorKey)) {
            return getString(R.string.category_validation_type_mismatch);
        }
        if ("category.validation.wallet_scope_mismatch".equals(errorKey)) {
            return getString(R.string.category_validation_wallet_scope_mismatch);
        }
        if ("category.validation.cannot_move_parent_with_children".equals(errorKey)) {
            return getString(R.string.category_validation_cannot_move_parent_with_children);
        }
        if ("category.validation.default_cannot_delete".equals(errorKey)) {
            return getString(R.string.category_validation_default_cannot_delete);
        }
        return getString(R.string.category_validation_generic_error);
    }

    @NonNull
    private String getSelectedType() {
        return selectedType;
    }

    @Nullable
    private String normalizeNullable(@Nullable String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public void onDestroyView() {
        loadingHelper.dismiss();
        if (childrenSource != null) {
            childrenSource.removeObservers(getViewLifecycleOwner());
        }
        super.onDestroyView();
        binding = null;
    }
}
