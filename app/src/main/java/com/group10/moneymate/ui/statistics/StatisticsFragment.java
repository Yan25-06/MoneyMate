package com.group10.moneymate.ui.statistics;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.databinding.FragmentStatisticsBinding;

public class StatisticsFragment extends Fragment {

    private FragmentStatisticsBinding binding;
    private StatisticsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentStatisticsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        MoneyMateApplication app = (MoneyMateApplication) requireActivity().getApplication();
        StatisticsViewModel.Factory factory =
                new StatisticsViewModel.Factory(app.getAppContainer().transactionRepository);
        viewModel = new ViewModelProvider(this, factory).get(StatisticsViewModel.class);

        binding.statisticsHeader.btnHeaderBack.setVisibility(View.GONE);
        applyWindowInsets();
    }

    private void applyWindowInsets() {
        final int initialTopPadding = binding.statisticsHeader.getRoot().getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.statisticsHeader.getRoot(), (headerView, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            headerView.setPadding(
                    headerView.getPaddingLeft(),
                    initialTopPadding + systemBars.top,
                    headerView.getPaddingRight(),
                    headerView.getPaddingBottom()
            );
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.statisticsHeader.getRoot());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
