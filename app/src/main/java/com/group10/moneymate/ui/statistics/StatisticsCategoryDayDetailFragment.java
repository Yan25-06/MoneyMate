package com.group10.moneymate.ui.statistics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.group10.moneymate.databinding.FragmentStatisticsCategoryDayDetailBinding;

public class StatisticsCategoryDayDetailFragment extends Fragment {

    private FragmentStatisticsCategoryDayDetailBinding binding;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatisticsCategoryDayDetailBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        binding.statisticsHeader.btnHeaderBack.setVisibility(View.VISIBLE);
        binding.statisticsHeader.btnHeaderBack.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
