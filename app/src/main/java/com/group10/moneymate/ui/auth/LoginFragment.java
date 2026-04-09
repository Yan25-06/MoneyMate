package com.group10.moneymate.ui.auth;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentLoginBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.ui.main.HomeActivity;
import com.group10.moneymate.ui.security.SecurityViewModel;
import com.group10.moneymate.utils.ValidationResult;

public class LoginFragment extends Fragment {

    private FragmentLoginBinding binding;
    private AuthViewModel viewModel;
    private GoogleSignInClient googleSignInClient;

    // Luu tam idToken trong khi cho user nhap password de link
    private String pendingLinkIdToken;

    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::handleGoogleSignInResult
            );

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

        setupGoogleSignIn();
        setupPasscodeLoginButton();
        observeAuthState();
        setupListeners();
    }

    // Google Sign-In setup

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
    }

    private void launchGoogleSignIn() {
        googleSignInClient.signOut().addOnCompleteListener(task -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            googleSignInLauncher.launch(signInIntent);
        });
    }

    private void handleGoogleSignInResult(ActivityResult activityResult) {
        Task<GoogleSignInAccount> task =
                GoogleSignIn.getSignedInAccountFromIntent(activityResult.getData());
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            String idToken = account.getIdToken();
            if (idToken != null) {
                viewModel.loginWithGoogle(idToken);
            } else {
                Toast.makeText(requireContext(),
                        getString(R.string.error_auth_login_failed), Toast.LENGTH_SHORT).show();
            }
        } catch (ApiException e) {
            if (e.getStatusCode() != 12501) {
                Toast.makeText(requireContext(),
                        getString(R.string.error_auth_login_failed), Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Hien dialog hoi password khi Google Sign-In gap account da ton tai.
     * Sau khi user nhap dung password, goi viewModel.linkPendingGoogle(password)
     * de link Google vao account email/password -> ca hai cach deu dang nhap duoc.
     */
    private void showLinkAccountDialog(String email) {
        EditText passwordInput = new EditText(requireContext());
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint(getString(R.string.hint_password));

        new AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.dialog_link_account_title))
                .setMessage(getString(R.string.dialog_link_account_message, email))
                .setView(passwordInput)
                .setPositiveButton(getString(R.string.dialog_link_account_confirm), (dialog, which) -> {
                    String password = passwordInput.getText().toString().trim();
                    if (TextUtils.isEmpty(password)) {
                        Toast.makeText(requireContext(),
                                getString(R.string.error_auth_wrong_password), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    viewModel.linkPendingGoogle(password);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .setCancelable(false)
                .show();
    }

    // Passcode login button

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

    // Observers

    private void observeAuthState() {
        viewModel.getAuthState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;

            if (state == AuthViewModel.AuthState.LOADING) {
                setLoading(true);
                return;
            }

            setLoading(false);

            if (state == AuthViewModel.AuthState.AUTHENTICATED) {
                ((MoneyMateApplication) requireActivity().getApplication())
                        .getAppContainer().seedDefaultCategoriesIfNeeded();
                openHomeActivity();
                return;
            }

            // Google collision: email da ton tai voi provider email/password
            // Hien dialog de user xac nhan va link hai account
            if (state == AuthViewModel.AuthState.GOOGLE_LINK_REQUIRED) {
                String email = viewModel.getPendingGoogleEmail();
                showLinkAccountDialog(email != null ? email : "");
            }
        });

        viewModel.getValidationError().observe(getViewLifecycleOwner(), result -> {
            if (result == null || result.isSuccess()) return;
            showValidationError(result);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (!TextUtils.isEmpty(message))
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        });
    }

    // Listeners

    private void setupListeners() {
        binding.btnLogin.setOnClickListener(v -> {
            clearInputErrors();
            String email = String.valueOf(binding.etEmail.getText()).trim();
            String password = String.valueOf(binding.etPassword.getText()).trim();
            viewModel.login(email, password);
        });

        binding.btnGoogleLogin.setOnClickListener(v -> launchGoogleSignIn());

        binding.tvRegister.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        LoginFragmentDirections.actionLoginToRegister()));

        binding.tvForgotPassword.setOnClickListener(v ->
                Navigation.findNavController(v).navigate(
                        LoginFragmentDirections.actionLoginToForgotPassword()));
    }

    private void clearInputErrors() {
        binding.tilEmail.setError(null);
        binding.tilPassword.setError(null);
        viewModel.clearValidationError();
    }

    private void showValidationError(@NonNull ValidationResult result) {
        String field = result.getErrorField();
        if (ValidationResult.FIELD_LOGIN.equals(field) || ValidationResult.FIELD_EMAIL.equals(field)) {
            binding.tilEmail.setError(result.getErrorMessage());
            binding.etEmail.requestFocus();
            return;
        }
        if (ValidationResult.FIELD_PASSWORD.equals(field)) {
            binding.tilPassword.setError(result.getErrorMessage());
            binding.etPassword.requestFocus();
        }
    }

    // Helpers

    private void openHomeActivity() {
        Intent intent = new Intent(requireContext(), HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void setLoading(boolean isLoading) {
        binding.pbLoading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnLogin.setEnabled(!isLoading);
        binding.btnGoogleLogin.setEnabled(!isLoading);
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