package com.group10.moneymate.ui.profile;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.group10.moneymate.databinding.FragmentProfileBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private ProfileViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupWindowInsets();

        AppContainer container = ((MoneyMateApplication) requireActivity().getApplication()).getAppContainer();
        ProfileViewModelFactory factory = new ProfileViewModelFactory(container.authRepository, container.database.userDao());
        viewModel = new ViewModelProvider(this, factory).get(ProfileViewModel.class);

        setupToolbar();
        setupListeners();
        observeViewModel();
    }

    private void setupWindowInsets() {
        final int initialTopPadding = binding.appBarLayout.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            androidx.core.graphics.Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appBarLayout.setPadding(
                    binding.appBarLayout.getPaddingLeft(),
                    initialTopPadding + systemBars.top,
                    binding.appBarLayout.getPaddingRight(),
                    binding.appBarLayout.getPaddingBottom());
            return insets;
        });
    }

    private void setupToolbar() {
        binding.toolbar.setNavigationOnClickListener(v -> 
                NavHostFragment.findNavController(this).navigateUp());
    }

    private void setupListeners() {
        binding.btnEditProfile.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "Tính năng chỉnh sửa hồ sơ đang được phát triển.", Toast.LENGTH_SHORT).show();
        });
    }

    private void observeViewModel() {
        viewModel.getUserProfile().observe(getViewLifecycleOwner(), user -> {
            if (user != null) {
                // Display Name
                String displayName = user.getDisplayName();
                if (TextUtils.isEmpty(displayName)) {
                    displayName = "Người dùng";
                }
                binding.tvDisplayName.setText(displayName);

                // Avatar initial
                if (!TextUtils.isEmpty(displayName)) {
                    binding.tvAvatarInitial.setText(String.valueOf(displayName.charAt(0)).toUpperCase());
                } else {
                    binding.tvAvatarInitial.setText("?");
                }

                // Email
                binding.tvEmail.setText(user.getEmail() != null ? user.getEmail() : "Chưa cập nhật");

                // Currency
                binding.tvCurrency.setText(user.getCurrency() != null ? user.getCurrency() : "VND");

                // Language
                binding.tvLanguage.setText(user.getLanguage() != null ? user.getLanguage() : "vi");
            }
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
