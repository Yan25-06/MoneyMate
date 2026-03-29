package com.group10.moneymate.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.group10.moneymate.BuildConfig;
import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentSettingsBinding;
import com.group10.moneymate.ui.auth.LoginActivity;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        setupWindowInsets();
        setupStaticUi();
        setupListeners();
        observeLogout();
    }

    private void setupWindowInsets() {
        final int initialTopPadding = binding.scrollSettings.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollSettings, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.scrollSettings.setPadding(
                    binding.scrollSettings.getPaddingLeft(),
                    initialTopPadding + systemBars.top,
                    binding.scrollSettings.getPaddingRight(),
                    binding.scrollSettings.getPaddingBottom()
            );
            return insets;
        });
    }

    private void setupStaticUi() {
        String[] dateFormats = getResources().getStringArray(R.array.date_formats);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_moneymate_dropdown_option,
                dateFormats
        );
        binding.dropdownDateFormat.setAdapter(adapter);
        if (dateFormats.length > 0) {
            binding.dropdownDateFormat.setText(dateFormats[0], false);
        }
        binding.dropdownDateFormat.setOnClickListener(v -> binding.dropdownDateFormat.showDropDown());
        binding.tvVersion.setText(getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME);
    }

    private void setupListeners() {
        // ── Tài khoản ─────────────────────────────────────────────────────────
        binding.btnProfile.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        SettingsFragmentDirections.actionSettingsToProfile()
                )
        );

        binding.btnSecurity.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        SettingsFragmentDirections.actionSettingsToPasscode()
                )
        );

        // ── Quản lý dữ liệu ───────────────────────────────────────────────────
        binding.btnWallets.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        SettingsFragmentDirections.actionSettingsToWallets()
                )
        );

        binding.btnCategories.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        SettingsFragmentDirections.actionSettingsToCategories()
                )
        );

        binding.btnBudgets.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        SettingsFragmentDirections.actionSettingsToStatistics()
                )
        );

        binding.btnDebts.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        SettingsFragmentDirections.actionSettingsToDebts()
                )
        );

        binding.btnEvents.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        SettingsFragmentDirections.actionSettingsToEvents()
                )
        );

        // ── Đăng xuất ─────────────────────────────────────────────────────────
        binding.btnLogout.setOnClickListener(v -> onLogoutClicked());
    }

    private void observeLogout() {
        viewModel.getLogoutSuccess().observe(getViewLifecycleOwner(), logoutSuccess -> {
            if (Boolean.TRUE.equals(logoutSuccess)) {
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
