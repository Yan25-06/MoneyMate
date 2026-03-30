package com.group10.moneymate.ui.transaction;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentCategoryIconPickerBinding;
import com.group10.moneymate.ui.category.CategoryIconAdapter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CategoryIconPickerFragment extends Fragment {

    public static final String RESULT_ICON_NAME = "selectedIconName";

    private FragmentCategoryIconPickerBinding binding;
    private CategoryIconOnlyAdapter iconAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentCategoryIconPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        CategoryIconPickerFragmentArgs args = CategoryIconPickerFragmentArgs.fromBundle(
                getArguments() == null ? new Bundle() : getArguments()
        );

        binding.topAppBar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        iconAdapter = new CategoryIconOnlyAdapter();
        iconAdapter.submitList(buildIconItems());
        iconAdapter.setSelectedIconResId(args.getSelectedIconName());
        iconAdapter.setTintColor(requireContext().getColor(R.color.transaction_income_accent));
        iconAdapter.setOnIconClickListener(item -> {
            NavController navController = Navigation.findNavController(requireView());
            NavBackStackEntry previous = navController.getPreviousBackStackEntry();
            if (previous != null) {
                previous.getSavedStateHandle().set(RESULT_ICON_NAME, item.iconResId);
            }
            navController.navigateUp();
        });

        binding.rvIconOptions.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        binding.rvIconOptions.setAdapter(iconAdapter);
    }

    @NonNull
    private List<CategoryIconAdapter.CategoryIconItem> buildIconItems() {
        List<String> iconNames = new ArrayList<>();
        for (Field field : R.drawable.class.getFields()) {
            String name = field.getName();
            if (name.startsWith("ic_category_")) {
                iconNames.add(name);
            }
        }
        Collections.sort(iconNames);
        List<CategoryIconAdapter.CategoryIconItem> items = new ArrayList<>();
        for (String iconName : iconNames) {
            items.add(new CategoryIconAdapter.CategoryIconItem(iconName, iconName));
        }
        return items;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
