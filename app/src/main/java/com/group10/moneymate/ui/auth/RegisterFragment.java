package com.group10.moneymate.ui.auth;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.group10.moneymate.databinding.FragmentRegisterBinding;

/**
 * Fragment for email/password registration.
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
        setupListeners();
    }

    private void setupListeners() {
        binding.btnRegister.setOnClickListener(v -> {
            String email = String.valueOf(binding.etEmail.getText()).trim();
            String password = String.valueOf(binding.etPassword.getText()).trim();
            String confirmPassword = String.valueOf(binding.etConfirmPassword.getText()).trim();
            String displayName = String.valueOf(binding.etDisplayName.getText()).trim();

            viewModel.register(email, password, confirmPassword, displayName);
        });

        binding.tvLogin.setOnClickListener(v -> {
            Navigation.findNavController(v).navigate
                    (RegisterFragmentDirections.actionRegisterBackToLogin());
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
