package com.group10.moneymate.ui.auth;

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

import com.group10.moneymate.databinding.FragmentRegisterBinding;
import com.group10.moneymate.utils.ValidationResult;

/**
 * Fragment for user registration.
 * Sau khi đăng ký thành công → điều hướng sang PasscodeFragment (CREATE mode).
 * Không còn hỗ trợ đăng nhập khách.
 */
public class RegisterFragment extends Fragment {

    private FragmentRegisterBinding binding;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentRegisterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        observeAuthState();
        setupListeners();
    }

    // ─── Observers ────────────────────────────────────────────────────────────

    private void observeAuthState() {
        viewModel.getAuthState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            if (state == AuthViewModel.AuthState.LOADING) {
                setLoading(true);
                return;
            }

            setLoading(false);

            if (state == AuthViewModel.AuthState.REGISTERED_NEEDS_PASSCODE) {
                // Đăng ký xong → seed category + chuyển sang tạo passcode
                seedDefaultCategories();
                navigateToCreatePasscode();
            }
        });

        viewModel.getValidationError().observe(getViewLifecycleOwner(), result -> {
            if (result == null || result.isSuccess()) {
                return;
            }
            showValidationError(result);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (!TextUtils.isEmpty(message)) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─── Listeners ────────────────────────────────────────────────────────────

    private void setupListeners() {
        // Nút quay lại
        binding.topAppBar.setNavigationOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());

        binding.btnRegister.setOnClickListener(v -> {
            clearInputErrors();
            String displayName      = String.valueOf(binding.etDisplayName.getText()).trim();
            String email            = String.valueOf(binding.etEmail.getText()).trim();
            String password         = String.valueOf(binding.etPassword.getText()).trim();
            String confirmPassword  = String.valueOf(binding.etConfirmPassword.getText()).trim();
            viewModel.register(email, password, confirmPassword, displayName);
        });

        binding.tvLogin.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        RegisterFragmentDirections.actionRegisterBackToLogin()
                )
        );
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void seedDefaultCategories() {
        com.group10.moneymate.di.AppContainer container =
                ((com.group10.moneymate.di.MoneyMateApplication) requireActivity().getApplication())
                        .getAppContainer();
        container.seedDefaultCategoriesIfNeeded();
    }

    private void navigateToCreatePasscode() {
        Navigation.findNavController(requireView())
                .navigate(RegisterFragmentDirections.actionRegisterToPasscode());
    }

    private void setLoading(boolean isLoading) {
        binding.btnRegister.setEnabled(!isLoading);
        binding.etDisplayName.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
        binding.etConfirmPassword.setEnabled(!isLoading);
    }

    private void clearInputErrors() {
        binding.tilDisplayName.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmPassword.setError(null);
        viewModel.clearValidationError();
    }

    private void showValidationError(@NonNull ValidationResult result) {
        String field = result.getErrorField();
        if (ValidationResult.FIELD_USERNAME.equals(field)) {
            binding.tilDisplayName.setError(result.getErrorMessage());
            binding.etDisplayName.requestFocus();
            return;
        }
        if (ValidationResult.FIELD_EMAIL.equals(field)) {
            binding.tilEmail.setError(result.getErrorMessage());
            binding.etEmail.requestFocus();
            return;
        }
        if (ValidationResult.FIELD_PASSWORD.equals(field)) {
            binding.tilPassword.setError(result.getErrorMessage());
            binding.etPassword.requestFocus();
            return;
        }
        if (ValidationResult.FIELD_CONFIRM_PASSWORD.equals(field)) {
            binding.tilConfirmPassword.setError(result.getErrorMessage());
            binding.etConfirmPassword.requestFocus();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}