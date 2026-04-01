package com.group10.moneymate.ui.wallet;

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
import com.group10.moneymate.databinding.FragmentWalletIconPickerBinding;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class WalletIconPickerFragment extends Fragment {

    public static final String RESULT_ICON_NAME = "selectedWalletIconName";

    private FragmentWalletIconPickerBinding binding;
    private WalletIconOnlyAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentWalletIconPickerBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        WalletIconPickerFragmentArgs args = WalletIconPickerFragmentArgs.fromBundle(
                getArguments() == null ? new Bundle() : getArguments()
        );

        binding.topAppBar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        adapter = new WalletIconOnlyAdapter();
        adapter.submitList(buildWalletIconItems());
        adapter.setSelectedIconName(args.getSelectedIconName());
        adapter.setAccentColor(requireContext().getColor(R.color.statistics_wallet_icon));
        adapter.setOnIconClickListener(item -> {
            NavController navController = Navigation.findNavController(requireView());
            NavBackStackEntry previous = navController.getPreviousBackStackEntry();
            if (previous != null) {
                previous.getSavedStateHandle().set(RESULT_ICON_NAME, item.iconName);
            }
            navController.navigateUp();
        });

        binding.rvIconOptions.setLayoutManager(new GridLayoutManager(requireContext(), 4));
        binding.rvIconOptions.setAdapter(adapter);
    }

    @NonNull
    private List<WalletIconOnlyAdapter.WalletIconItem> buildWalletIconItems() {
        List<String> iconNames = new ArrayList<>();
        for (Field field : R.drawable.class.getFields()) {
            String name = field.getName();
            if (name.startsWith("ic_wallet_")) {
                iconNames.add(name);
            }
        }
        Collections.sort(iconNames);
        List<WalletIconOnlyAdapter.WalletIconItem> items = new ArrayList<>();
        for (String iconName : iconNames) {
            items.add(new WalletIconOnlyAdapter.WalletIconItem(iconName));
        }
        return items;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
