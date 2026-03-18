package com.group10.moneymate.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentForgotPasswordBinding;


public class ForgotPasswordFragment extends Fragment {
    private FragmentForgotPasswordBinding binding;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentForgotPasswordBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        setupListeners();
        observeAuthState();
    }

    private void setupListeners() {
        binding.btnBack.setOnClickListener(this::navigateBackToLogin);
        binding.btnBackToLogin.setOnClickListener(this::navigateBackToLogin);

        binding.btnSendResetLink.setOnClickListener(v -> {
            clearInlineError();
            String email = String.valueOf(binding.etEmail.getText()).trim();
            if (!validateEmail(email)) {
                return;
            }
            viewModel.sendPasswordResetEmail(email);
        });
    }

    private void observeAuthState() {
        viewModel.getAuthState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) {
                return;
            }

            if (state == AuthViewModel.AuthState.LOADING) {
                setLoading(true);
                return;
            }

            setLoading(false);
            if (state == AuthViewModel.AuthState.PASSWORD_RESET_EMAIL_SENT) {
                showSuccessState();
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (!TextUtils.isEmpty(message)) {
                showInlineError(message);
            }
        });
    }

    private boolean validateEmail(String email) {
        if (TextUtils.isEmpty(email)) {
            showInlineError(getString(R.string.error_email_required));
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showInlineError(getString(R.string.error_email_invalid));
            return false;
        }

        return true;
    }

    private void setLoading(boolean isLoading) {
        binding.pbLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnSendResetLink.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
    }

    private void showSuccessState() {
        clearInlineError();
        binding.cvSuccess.setVisibility(View.VISIBLE);
        binding.btnBackToLogin.setVisibility(View.VISIBLE);
    }

    private void showInlineError(@NonNull String message) {
        binding.cvSuccess.setVisibility(View.GONE);
        binding.btnBackToLogin.setVisibility(View.GONE);
        binding.tilEmail.setError(message);
        binding.tvError.setText(message);
        binding.tvError.setVisibility(View.VISIBLE);
    }

    private void clearInlineError() {
        binding.tilEmail.setError(null);
        binding.tvError.setText(null);
        binding.tvError.setVisibility(View.GONE);
    }

    private void navigateBackToLogin(@NonNull View view) {
        Navigation.findNavController(view).navigate(
                ForgotPasswordFragmentDirections.actionForgotPasswordBackToLogin()
        );
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
