package com.group10.moneymate.ui.category;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.group10.moneymate.R;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.databinding.FragmentAddEditCategoryBinding;
import com.group10.moneymate.utils.Constants;

public class AddEditCategoryFragment extends Fragment {

    private FragmentAddEditCategoryBinding binding;
    private CategoryViewModel viewModel;

    // null = Add mode, non-null = Edit mode (từ Safe Args)
    private String categoryId;
    private CategoryEntity existingCategory;

    // Giá trị mặc định khi chưa chọn
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

        // Lấy categoryId từ Safe Args
        categoryId = AddEditCategoryFragmentArgs.fromBundle(getArguments()).getCategoryId();

        setupTypeToggle();
        setupColorPreview();
        setupSaveButton();
        loadExistingCategoryIfNeeded();
    }

    private void setupTypeToggle() {
        // Mặc định chọn Chi tiêu
        binding.rgType.check(R.id.rb_expense);
    }

    private void setupColorPreview() {
        // Cập nhật preview màu khi nhấn nút chọn màu
        binding.btnPickColor.setOnClickListener(v -> showColorPicker());
    }

    private void showColorPicker() {
        // Danh sách màu gợi ý
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
                })
                .show();
    }

    private void updateColorPreview() {
        try {
            int color = android.graphics.Color.parseColor(selectedColorHex);
            binding.vColorPreview.setBackgroundColor(color);
        } catch (IllegalArgumentException ignored) {}
    }

    private void setupSaveButton() {
        binding.btnSave.setOnClickListener(v -> saveCategory());
    }

    private void loadExistingCategoryIfNeeded() {
        if (categoryId == null) {
            // Add mode
            binding.btnSave.setText(R.string.btn_save);
            return;
        }

        // Edit mode — load category từ ViewModel
        binding.btnSave.setText(R.string.btn_update);
        viewModel.getCategoryById(categoryId).observe(getViewLifecycleOwner(), category -> {
            if (category == null) return;
            existingCategory = category;

            binding.etCategoryName.setText(category.getName());
            selectedColorHex  = category.getColorHex();
            selectedIconResId = category.getIconResId();
            updateColorPreview();

            // Set đúng radio button theo type
            if (Constants.TYPE_INCOME.equals(category.getType())) {
                binding.rgType.check(R.id.rb_income);
            } else {
                binding.rgType.check(R.id.rb_expense);
            }

            // Không cho phép đổi type của default category
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
            // Edit mode
            existingCategory.setName(name);
            existingCategory.setColorHex(selectedColorHex);
            existingCategory.setIconResId(selectedIconResId);
            if (!existingCategory.isDefault()) {
                existingCategory.setType(type);
            }
            viewModel.updateCategory(existingCategory);
            Toast.makeText(requireContext(), R.string.category_updated, Toast.LENGTH_SHORT).show();
        } else {
            // Add mode
            viewModel.addCategory(name, selectedIconResId, selectedColorHex, type);
            Toast.makeText(requireContext(), R.string.category_added, Toast.LENGTH_SHORT).show();
        }

        Navigation.findNavController(requireView()).navigateUp();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevent memory leak
    }
}