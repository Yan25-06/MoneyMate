package com.group10.moneymate.ui.auth;

    import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentRegisterBinding;
import com.group10.moneymate.ui.main.HomeActivity;

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
        observeAuthState();
        setupListeners();
    }

    private void observeAuthState() {
        viewModel.getAuthState().observe(getViewLifecycleOwner(), state -> {
            if (state == AuthViewModel.AuthState.AUTHENTICATED) {
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
        binding.btnRegister.setOnClickListener(v -> submitRegistration());

        binding.tvLogin.setOnClickListener(v -> Navigation.findNavController(v).navigate
                (RegisterFragmentDirections.actionRegisterBackToLogin()));
    }

    private void submitRegistration() {
        clearInputErrors();

        String displayName = String.valueOf(binding.etDisplayName.getText()).trim();
        String email = String.valueOf(binding.etEmail.getText()).trim();
        String password = String.valueOf(binding.etPassword.getText()).trim();
        String confirmPassword = String.valueOf(binding.etConfirmPassword.getText()).trim();

        if (!validateInputs(displayName, email, password, confirmPassword)) {
            return;
        }

        viewModel.register(email, password, confirmPassword, displayName);
    }

    private boolean validateInputs(String displayName, String email, String password, String confirmPassword) {
        if (TextUtils.isEmpty(displayName)) {
            binding.tilDisplayName.setError(getString(R.string.error_display_name_required));
            return false;
        }

        if (TextUtils.isEmpty(email)) {
            binding.tilEmail.setError(getString(R.string.error_email_required));
            return false;
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilEmail.setError(getString(R.string.error_email_invalid));
            return false;
        }

        if (TextUtils.isEmpty(password)) {
            binding.tilPassword.setError(getString(R.string.error_password_required));
            return false;
        }

        if (TextUtils.isEmpty(confirmPassword)) {
            binding.tilConfirmPassword.setError(getString(R.string.error_confirm_password_required));
            return false;
        }

        if (!TextUtils.equals(password, confirmPassword)) {
            binding.tilConfirmPassword.setError(getString(R.string.error_passwords_do_not_match));
            return false;
        }

        return true;
    }

    private void clearInputErrors() {
        binding.tilDisplayName.setError(null);
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        binding.tilConfirmPassword.setError(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
