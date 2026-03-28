package com.group10.moneymate.ui.category;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.navigation.Navigation;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.databinding.FragmentAddEditCategoryBinding;
import com.group10.moneymate.utils.Constants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AddEditCategoryFragment extends Fragment {

    private FragmentAddEditCategoryBinding binding;
    private CategoryViewModel viewModel;
    private CategoryIconAdapter iconAdapter;

    private String categoryId;
    private CategoryEntity existingCategory;

    private String selectedColorHex = "#9E9E9E";
    private String selectedIconResId = "ic_other";

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

        categoryId = AddEditCategoryFragmentArgs.fromBundle(getArguments()).getCategoryId();

        setupTypeToggle();
        setupColorPreview();
        setupIconGrid();
        setupSaveButton();
        loadExistingCategoryIfNeeded();
        updateColorPreview();
        updateIconPreview();
    }

    private void setupTypeToggle() {
        binding.rgType.check(R.id.rb_expense);
    }

    private void setupColorPreview() {
        binding.btnPickColor.setOnClickListener(v -> showColorPicker());
    }

    private void setupIconGrid() {
        iconAdapter = new CategoryIconAdapter();
        iconAdapter.submitList(buildIconItems());
        iconAdapter.setSelectedIconResId(selectedIconResId);
        iconAdapter.setTintColor(parseSelectedColor());
        iconAdapter.setOnIconClickListener(item -> {
            selectedIconResId = item.iconResId;
            updateIconPreview();
        });
        binding.rvIconOptions.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        binding.rvIconOptions.setAdapter(iconAdapter);
        binding.rvIconOptions.setNestedScrollingEnabled(false);
    }

    private void showColorPicker() {
        String[] colors = {
                "#F44336", "#FF9800", "#FFEB3B", "#4CAF50",
                "#2196F3", "#9C27B0", "#E91E63", "#00BCD4",
                "#795548", "#607D8B", "#009688", "#3F51B5"
        };
        String[] colorNames = {
                "Đỏ", "Cam", "Vàng", "Xanh lá",
                "Xanh dương", "Tím", "Hồng", "Xanh ngọc",
                "Nâu", "Xám xanh", "Ngọc lam", "Chàm"
        };

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.btn_pick_color)
                .setItems(colorNames, (dialog, which) -> {
                    selectedColorHex = colors[which];
                    updateColorPreview();
                    updateIconPreview();
                })
                .show();
    }

    private void updateColorPreview() {
        binding.vColorPreview.setBackgroundColor(parseSelectedColor());
        if (iconAdapter != null) {
            iconAdapter.setTintColor(parseSelectedColor());
        }
    }

    private void updateIconPreview() {
        int iconResId = requireContext().getResources().getIdentifier(
                selectedIconResId,
                "drawable",
                requireContext().getPackageName()
        );
        if (iconResId == 0) {
            iconResId = R.drawable.ic_other;
        }
        binding.ivIconPreview.setImageResource(iconResId);
        binding.ivIconPreview.setColorFilter(parseSelectedColor());
        binding.tvIconName.setText(findIconLabel(selectedIconResId));
        if (iconAdapter != null) {
            iconAdapter.setSelectedIconResId(selectedIconResId);
        }
    }

    private int parseSelectedColor() {
        try {
            return android.graphics.Color.parseColor(selectedColorHex);
        } catch (IllegalArgumentException ignored) {
            return ContextCompat.getColor(requireContext(), R.color.md_theme_primary);
        }
    }

    private void setupSaveButton() {
        binding.btnSave.setOnClickListener(v -> saveCategory());
    }

    private void loadExistingCategoryIfNeeded() {
        if (categoryId == null) {
            binding.btnSave.setText(R.string.btn_save);
            return;
        }

        binding.btnSave.setText(R.string.btn_update);
        viewModel.getCategoryById(categoryId).observe(getViewLifecycleOwner(), category -> {
            if (category == null) {
                return;
            }
            existingCategory = category;

            binding.etCategoryName.setText(category.getName());
            selectedColorHex = category.getColorHex();
            selectedIconResId = category.getIconResId();
            updateColorPreview();
            updateIconPreview();

            if (Constants.TYPE_INCOME.equals(category.getType())) {
                binding.rgType.check(R.id.rb_income);
            } else {
                binding.rgType.check(R.id.rb_expense);
            }

            if (category.isDefault()) {
                binding.rgType.setEnabled(false);
                binding.rbExpense.setEnabled(false);
                binding.rbIncome.setEnabled(false);
            }
        });
    }

    private void saveCategory() {
        String name = binding.etCategoryName.getText() != null
                ? binding.etCategoryName.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(name)) {
            binding.tilCategoryName.setError(getString(R.string.error_name_required));
            return;
        }
        binding.tilCategoryName.setError(null);

        String type = binding.rgType.getCheckedRadioButtonId() == R.id.rb_income
                ? Constants.TYPE_INCOME
                : Constants.TYPE_EXPENSE;

        if (existingCategory != null) {
            existingCategory.setName(name);
            existingCategory.setColorHex(selectedColorHex);
            existingCategory.setIconResId(selectedIconResId);
            if (!existingCategory.isDefault()) {
                existingCategory.setType(type);
            }
            viewModel.updateCategory(existingCategory);
            Toast.makeText(requireContext(), R.string.category_updated, Toast.LENGTH_SHORT).show();
        } else {
            viewModel.addCategory(name, selectedIconResId, selectedColorHex, type);
            Toast.makeText(requireContext(), R.string.category_added, Toast.LENGTH_SHORT).show();
        }

        Navigation.findNavController(requireView()).navigateUp();
    }

    @NonNull
    private List<CategoryIconAdapter.CategoryIconItem> buildIconItems() {
        Map<String, String> uniqueItems = new LinkedHashMap<>();
        for (Constants.DefaultCategory category : Constants.getDefaultCategories()) {
            if (!uniqueItems.containsKey(category.iconResId)) {
                uniqueItems.put(category.iconResId, category.name);
            }
        }

        List<CategoryIconAdapter.CategoryIconItem> items = new ArrayList<>();
        for (Map.Entry<String, String> entry : uniqueItems.entrySet()) {
            items.add(new CategoryIconAdapter.CategoryIconItem(entry.getKey(), entry.getValue()));
        }
        return items;
    }

    @NonNull
    private String findIconLabel(@NonNull String iconResId) {
        for (Constants.DefaultCategory category : Constants.getDefaultCategories()) {
            if (iconResId.equals(category.iconResId)) {
                return category.name;
            }
        }
        return getString(R.string.btn_pick_icon);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
