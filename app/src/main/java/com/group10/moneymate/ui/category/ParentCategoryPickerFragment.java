package com.group10.moneymate.ui.category;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.group10.moneymate.databinding.FragmentParentCategoryPickerBinding;
import com.group10.moneymate.utils.Constants;

import java.util.ArrayList;
import java.util.List;

public class ParentCategoryPickerFragment extends Fragment {

    public static final String RESULT_PARENT_ID = "selectedParentId";
    public static final String RESULT_PARENT_LABEL = "selectedParentLabel";

    private FragmentParentCategoryPickerBinding binding;
    private CategoryViewModel viewModel;
    private ParentCategoryPickerAdapter adapter;

    private String selectedParentId;
    private String selectedType = Constants.TYPE_EXPENSE;
    @Nullable
    private String currentCategoryId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentParentCategoryPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(CategoryViewModel.class);

        ParentCategoryPickerFragmentArgs args = ParentCategoryPickerFragmentArgs.fromBundle(
                getArguments() == null ? new Bundle() : getArguments()
        );
        selectedParentId = args.getSelectedParentId();
        selectedType = args.getCategoryType() == null ? Constants.TYPE_EXPENSE : args.getCategoryType();
        currentCategoryId = args.getCurrentCategoryId();

        binding.topAppBar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        adapter = new ParentCategoryPickerAdapter();
        adapter.setSelectedParentId(selectedParentId);
        adapter.setOnItemClickListener(this::returnSelection);

        binding.rvParentCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvParentCategories.setAdapter(adapter);

        viewModel.setSelectedType(selectedType);
        viewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            List<com.group10.moneymate.data.local.entity.CategoryEntity> filtered = categories;
            if (currentCategoryId != null && categories != null && !categories.isEmpty()) {
                filtered = new ArrayList<>();
                for (com.group10.moneymate.data.local.entity.CategoryEntity category : categories) {
                    if (!currentCategoryId.equals(category.getId())) {
                        filtered.add(category);
                    }
                }
            }
            adapter.submitList(filtered);
            boolean isEmpty = filtered == null || filtered.isEmpty();
            binding.tvEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            binding.rvParentCategories.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        });
    }

    private void returnSelection(@NonNull com.group10.moneymate.data.local.entity.CategoryEntity category) {
        NavController navController = Navigation.findNavController(requireView());
        NavBackStackEntry previous = navController.getPreviousBackStackEntry();
        if (previous != null) {
            previous.getSavedStateHandle().set(RESULT_PARENT_ID, category.getId());
            previous.getSavedStateHandle().set(RESULT_PARENT_LABEL, category.getName());
        }
        navController.navigateUp();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}

