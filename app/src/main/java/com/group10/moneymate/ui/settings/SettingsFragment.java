package com.group10.moneymate.ui.settings;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.group10.moneymate.databinding.FragmentSettingsBinding;
import com.group10.moneymate.ui.auth.LoginActivity;

public class SettingsFragment extends Fragment {
    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        setupListeners();
        observeLogout();
    }

    private void setupListeners() {
        binding.btnLogout.setOnClickListener(v -> onLogoutClicked());
    }

    private void observeLogout() {
        viewModel.getLogoutSuccess().observe(getViewLifecycleOwner(), logoutSuccess -> {
            if (logoutSuccess) {
                navigateToLogin();
            }
        });
    }

    private void onLogoutClicked() {
        viewModel.signOut();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
