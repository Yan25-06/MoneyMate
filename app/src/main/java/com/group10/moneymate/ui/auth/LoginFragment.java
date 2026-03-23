package com.group10.moneymate.ui.auth;

import android.content.Intent;
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

import com.group10.moneymate.databinding.FragmentLoginBinding;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.ui.main.HomeActivity;

/**
 * Fragment for email/password login.
 */
public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private AuthViewModel viewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentLoginBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);
        observeAuthState();
        setupListeners();
    }

    private void observeAuthState() {
        viewModel.getAuthState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            if (state == AuthViewModel.AuthState.LOADING) {
                setLoading(true);
                return;
            }

            setLoading(false);

            if (state == AuthViewModel.AuthState.AUTHENTICATED) {
                // Seed default categories sau khi auth thành công
                ((MoneyMateApplication) requireActivity().getApplication())
                        .getAppContainer()
                        .seedDefaultCategoriesIfNeeded();
                openHomeActivity();
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (!TextUtils.isEmpty(message)) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void openHomeActivity() {
        Intent intent = new Intent(requireContext(), HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void setupListeners() {
        binding.btnLogin.setOnClickListener(v -> {
            String email    = String.valueOf(binding.etEmail.getText()).trim();
            String password = String.valueOf(binding.etPassword.getText()).trim();
            viewModel.login(email, password);
        });

        binding.btnGuestLogin.setOnClickListener(v -> viewModel.loginAnonymously());

        binding.tvRegister.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        LoginFragmentDirections.actionLoginToRegister()
                )
        );

        binding.tvForgotPassword.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        LoginFragmentDirections.actionLoginToForgotPassword()
                )
        );
    }

    private void setLoading(boolean isLoading) {
        binding.btnLogin.setEnabled(!isLoading);
        binding.btnGuestLogin.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}