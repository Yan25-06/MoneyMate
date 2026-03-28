package com.group10.moneymate.ui.category;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.tabs.TabLayout;
import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentCategoryListBinding;
import com.group10.moneymate.utils.Constants;

public class CategoryListFragment extends Fragment {

    private FragmentCategoryListBinding binding;
    private CategoryViewModel viewModel;
    private CategoryAdapter adapter;

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
        setupFab();
        observeViewModel();
    }

    private void setupRecyclerView() {
        adapter = new CategoryAdapter();
        binding.rvCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvCategories.setAdapter(adapter);

        // Click item → Edit mode (truyền categoryId qua Safe Args)
        adapter.setOnItemClickListener(item -> {
            CategoryListFragmentDirections.ActionCategoryListToAddEdit action =
                    CategoryListFragmentDirections.actionCategoryListToAddEdit();
            action.setCategoryId(item.getId());
            Navigation.findNavController(requireView()).navigate(action);
        });

        // Xóa danh mục tùy chỉnh với confirm dialog
        adapter.setOnItemDeleteListener(item -> new AlertDialog.Builder(requireContext())
                    .setTitle(R.string.dialog_delete_title)
                    .setMessage(R.string.dialog_delete_category_message)
                    .setPositiveButton(R.string.btn_delete, (dialog, which) ->
                            viewModel.deleteCategory(item))
                    .setNegativeButton(R.string.btn_cancel, null)
                    .show()
        );
    }

    private void setupTabs() {
        binding.tlCategoryType.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String type = tab.getPosition() == 0
                        ? Constants.TYPE_EXPENSE
                        : Constants.TYPE_INCOME;
                viewModel.setSelectedType(type);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void setupFab() {
        binding.fabAdd.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        CategoryListFragmentDirections.actionCategoryListToAddEdit()
                )
        );
    }

    private void observeViewModel() {
        viewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            adapter.submitList(categories);
            binding.tvEmpty.setVisibility(
                    (categories == null || categories.isEmpty()) ? View.VISIBLE : View.GONE);
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null; // Prevent memory leak
    }
}
