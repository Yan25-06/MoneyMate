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

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentLoginBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.ui.main.HomeActivity;
import com.group10.moneymate.ui.security.SecurityViewModel;

/**
 * Fragment for email/password login.
 * Không còn hỗ trợ đăng nhập khách (anonymous).
 * Hiển thị nút "Đăng nhập bằng mã PIN" nếu passcode đã được thiết lập.
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

        setupPasscodeLoginButton();
        observeAuthState();
        setupListeners();
    }

    // ─── Passcode login button ────────────────────────────────────────────────

    /**
     * Hiển thị nút login bằng passcode chỉ khi passcode đã được thiết lập.
     * Nút này hoạt động offline vì passcode lưu local.
     */
    private void setupPasscodeLoginButton() {
        AppContainer container = ((MoneyMateApplication) requireActivity().getApplication())
                .getAppContainer();

        if (container.authRepository.isPasscodeEnabled()) {
            binding.btnPasscodeLogin.setVisibility(View.VISIBLE);
            binding.btnPasscodeLogin.setOnClickListener(v -> navigateToPasscodeVerify());
        } else {
            binding.btnPasscodeLogin.setVisibility(View.GONE);
        }
    }

    private void navigateToPasscodeVerify() {
        Bundle args = new Bundle();
        args.putInt("passcode_mode", SecurityViewModel.MODE_VERIFY);
        args.putBoolean("passcode_finish_to_home", true);

        Navigation.findNavController(requireView())
                .navigate(R.id.action_login_to_passcode, args);
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

            if (state == AuthViewModel.AuthState.AUTHENTICATED) {
                // Login email/password thành công
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

    // ─── Listeners ────────────────────────────────────────────────────────────

    private void setupListeners() {
        binding.btnLogin.setOnClickListener(v -> {
            String email    = String.valueOf(binding.etEmail.getText()).trim();
            String password = String.valueOf(binding.etPassword.getText()).trim();
            viewModel.login(email, password);
        });

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

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void openHomeActivity() {
        Intent intent = new Intent(requireContext(), HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void setLoading(boolean isLoading) {
        binding.btnLogin.setEnabled(!isLoading);
        binding.btnPasscodeLogin.setEnabled(!isLoading);
        binding.etEmail.setEnabled(!isLoading);
        binding.etPassword.setEnabled(!isLoading);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}