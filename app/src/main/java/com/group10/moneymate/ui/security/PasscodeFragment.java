package com.group10.moneymate.ui.security;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentPasscodeBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.PrefsManager;

import java.util.concurrent.TimeUnit;

/**
 * Fragment màn hình nhập mã PIN.
 *
 * Đọc mode và destination từ Activity intent extras:
 *   EXTRA_MODE           → khởi tạo SecurityViewModel.init(mode)
 *   EXTRA_FINISH_TO_HOME → sau SUCCESS chuyển HomeActivity hay chỉ finish()
 */
public class PasscodeFragment extends Fragment {

    private FragmentPasscodeBinding binding;
    private SecurityViewModel viewModel;

    private int mode;
    private boolean finishToHome;

    private ImageView[] dots;
    private CountDownTimer lockoutTimer;

    private GoogleSignInClient googleSignInClient;
    private final ActivityResultLauncher<Intent> googleSignInLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    this::handleGoogleSignInResult
            );

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPasscodeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Đọc extras từ host Activity
        Intent intent = requireActivity().getIntent();
        mode         = intent.getIntExtra(PasscodeActivity.EXTRA_MODE, SecurityViewModel.MODE_CREATE);
        finishToHome = intent.getBooleanExtra(PasscodeActivity.EXTRA_FINISH_TO_HOME, false);

        dots = new ImageView[]{
                binding.dot1, binding.dot2, binding.dot3,
                binding.dot4, binding.dot5, binding.dot6
        };

        viewModel = new ViewModelProvider(this).get(SecurityViewModel.class);
        viewModel.init(mode);

        setupTitle(mode);
        setupNumpad();
        setupGoogleSignIn();
        observeViewModel();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (lockoutTimer != null) lockoutTimer.cancel();
        binding = null;
    }

    // ─── Setup ────────────────────────────────────────────────────────────────

    private void setupTitle(int m) {
        switch (m) {
            case SecurityViewModel.MODE_CREATE:
                binding.tvTitle.setText(R.string.title_create_passcode);
                binding.tvSubtitle.setText(R.string.passcode_subtitle_create);
                break;
            case SecurityViewModel.MODE_CONFIRM:
                binding.tvTitle.setText(R.string.title_confirm_passcode);
                binding.tvSubtitle.setText(R.string.passcode_subtitle_confirm);
                break;
            case SecurityViewModel.MODE_CHANGE:
                binding.tvTitle.setText(R.string.title_enter_passcode);
                binding.tvSubtitle.setText(R.string.passcode_change_verify_hint);
                break;
            case SecurityViewModel.MODE_VERIFY:
            default:
                binding.tvTitle.setText(R.string.title_enter_passcode);
                binding.tvSubtitle.setText(R.string.passcode_subtitle_verify);
                break;
        }
    }

    private void setupNumpad() {
        binding.key0.setOnClickListener(v -> viewModel.onDigitEntered(0));
        binding.key1.setOnClickListener(v -> viewModel.onDigitEntered(1));
        binding.key2.setOnClickListener(v -> viewModel.onDigitEntered(2));
        binding.key3.setOnClickListener(v -> viewModel.onDigitEntered(3));
        binding.key4.setOnClickListener(v -> viewModel.onDigitEntered(4));
        binding.key5.setOnClickListener(v -> viewModel.onDigitEntered(5));
        binding.key6.setOnClickListener(v -> viewModel.onDigitEntered(6));
        binding.key7.setOnClickListener(v -> viewModel.onDigitEntered(7));
        binding.key8.setOnClickListener(v -> viewModel.onDigitEntered(8));
        binding.key9.setOnClickListener(v -> viewModel.onDigitEntered(9));
        binding.keyBackspace.setOnClickListener(v -> viewModel.onBackspace());

        // "Quên mã" chỉ hiển thị ở mode VERIFY
        if (mode == SecurityViewModel.MODE_VERIFY || mode == SecurityViewModel.MODE_CHANGE) {
            binding.keyForgot.setVisibility(View.VISIBLE);
            binding.keyForgot.setOnClickListener(v -> showForgotPinDialog());
        } else {
            binding.keyForgot.setVisibility(View.INVISIBLE);
        }
    }

    // ─── Observers ────────────────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getFilledDots().observe(getViewLifecycleOwner(), count -> updateDots(count, false));

        viewModel.getCurrentMode().observe(getViewLifecycleOwner(), newMode -> {
            if (newMode != null) setupTitle(newMode);
        });

        viewModel.getUiState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            switch (state) {
                case SUCCESS:
                    onSuccess();
                    break;
                case ERROR:
                    onError();
                    break;
                case LOCKED:
                    onLocked();
                    break;
                default:
                    break;
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg == null) {
                binding.tvSubtitle.setText(getSubtitleForMode());
                binding.tvSubtitle.setTextColor(
                        requireContext().getColor(R.color.statistics_text_secondary));
                return;
            }
            if (msg.equals("mismatch")) {
                binding.tvSubtitle.setText(R.string.error_passcode_mismatch);
            } else if (msg.startsWith("wrong:")) {
                int remaining = Integer.parseInt(msg.substring(6));
                binding.tvSubtitle.setText(
                        getString(R.string.error_passcode_wrong_with_attempts, remaining));
            }
            binding.tvSubtitle.setTextColor(
                    requireContext().getColor(R.color.expense_red));
        });

        viewModel.getLockoutUntil().observe(getViewLifecycleOwner(), until -> {
            if (until != null && until > 0) startLockoutCountdown(until);
        });
    }

    // ─── State handlers ───────────────────────────────────────────────────────

    private void onSuccess() {
        if (requireActivity() instanceof PasscodeActivity) {
            ((PasscodeActivity) requireActivity()).onPasscodeSuccess();
        } else {
            requireActivity().finish();
        }
    }

    private void onError() {
        updateDots(SecurityViewModel.PASSCODE_LENGTH, true);
        binding.llDots.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.shake));
        binding.llDots.postDelayed(() -> {
            if (binding != null) viewModel.resetAfterError();
        }, 500);
    }

    private void onLocked() {
        binding.tvLockout.setVisibility(View.VISIBLE);
        setNumpadEnabled(false);
    }

    // ─── Dot indicators ───────────────────────────────────────────────────────

    private void updateDots(int filled, boolean error) {
        for (int i = 0; i < dots.length; i++) {
            dots[i].setSelected(error);
            dots[i].setActivated(!error && i < filled);
        }
    }

    // ─── Lockout countdown ────────────────────────────────────────────────────

    private void startLockoutCountdown(long untilMs) {
        if (lockoutTimer != null) lockoutTimer.cancel();
        long remaining = untilMs - System.currentTimeMillis();
        if (remaining <= 0) {
            binding.tvLockout.setVisibility(View.GONE);
            setNumpadEnabled(true);
            return;
        }
        lockoutTimer = new CountDownTimer(remaining, 1000) {
            @Override
            public void onTick(long ms) {
                if (binding == null) return;
                long secs = TimeUnit.MILLISECONDS.toSeconds(ms) + 1;
                binding.tvLockout.setText(
                        getString(R.string.error_passcode_locked_with_time, secs));
            }
            @Override
            public void onFinish() {
                if (binding == null) return;
                binding.tvLockout.setVisibility(View.GONE);
                setNumpadEnabled(true);
                viewModel.init(mode);
            }
        }.start();
    }

    private void setNumpadEnabled(boolean enabled) {
        binding.key0.setEnabled(enabled);
        binding.key1.setEnabled(enabled);
        binding.key2.setEnabled(enabled);
        binding.key3.setEnabled(enabled);
        binding.key4.setEnabled(enabled);
        binding.key5.setEnabled(enabled);
        binding.key6.setEnabled(enabled);
        binding.key7.setEnabled(enabled);
        binding.key8.setEnabled(enabled);
        binding.key9.setEnabled(enabled);
        binding.keyBackspace.setEnabled(enabled);
    }

    // ─── Forgot PIN ──────────────────────────────────────────────────────────

    private void setupGoogleSignIn() {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .requestProfile()
                .build();
        googleSignInClient = GoogleSignIn.getClient(requireActivity(), gso);
    }

    /**
     * Kiểm tra provider hiện tại.
     * Nếu là Google -> gọi Google Sign-In để xác thực.
     * Nếu là Email -> hiện dialog nhập password.
     */
    private void showForgotPinDialog() {
        AppContainer container = ((MoneyMateApplication) requireActivity().getApplication())
                .getAppContainer();
        String provider = container.prefsManager.getAuthProvider();

        if (PrefsManager.PROVIDER_GOOGLE.equals(provider)) {
            // Xác thực bằng Google
            googleSignInClient.signOut().addOnCompleteListener(task -> {
                Intent signInIntent = googleSignInClient.getSignInIntent();
                googleSignInLauncher.launch(signInIntent);
            });
        } else {
            // Xác thực bằng Email/Password
            showPasswordDialog();
        }
    }

    private void showPasswordDialog() {
        EditText passwordInput = new EditText(requireContext());
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordInput.setHint(getString(R.string.hint_password));
        int paddingDp = (int) (16 * requireContext().getResources().getDisplayMetrics().density);
        passwordInput.setPadding(paddingDp, paddingDp / 2, paddingDp, paddingDp / 2);

        new MaterialAlertDialogBuilder(requireContext(),
                com.google.android.material.R.style.ThemeOverlay_Material3_MaterialAlertDialog)
                .setTitle(getString(R.string.title_forgot_passcode))
                .setMessage(getString(R.string.msg_forgot_passcode_enter_password))
                .setView(passwordInput)
                .setPositiveButton(getString(R.string.btn_confirm), (dialog, which) -> {
                    String password = passwordInput.getText().toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(requireContext(),
                                getString(R.string.error_auth_wrong_password),
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    verifyPasswordAndResetPin(password);
                })
                .setNegativeButton(getString(android.R.string.cancel), null)
                .setCancelable(true)
                .show();
    }

    /**
     * Gọi AuthRepository.verifyPasswordForPinReset() trên background thread.
     * Hiện loading state trong khi chờ.
     */
    private void verifyPasswordAndResetPin(String password) {
        // Disable numpad khi đang xử lý
        setNumpadEnabled(false);

        AppContainer container = ((MoneyMateApplication) requireActivity().getApplication())
                .getAppContainer();

        container.authRepository.verifyPasswordForPinReset(password,
                new com.group10.moneymate.data.repository.AuthRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        if (binding == null) return;
                        requireActivity().runOnUiThread(() -> {
                            if (binding == null) return;
                            // Reset lockout
                            container.prefsManager.setFailedAttempts(0);
                            container.prefsManager.setLockoutUntil(0L);
                            // Chuyển sang CREATE mode để nhập PIN mới
                            mode = SecurityViewModel.MODE_CREATE;
                            viewModel.init(SecurityViewModel.MODE_CREATE);
                            setupTitle(SecurityViewModel.MODE_CREATE);
                            binding.keyForgot.setVisibility(View.INVISIBLE);
                            setNumpadEnabled(true);
                            binding.tvLockout.setVisibility(View.GONE);
                        });
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        requireActivity().runOnUiThread(() -> {
                            if (binding == null) return;
                            setNumpadEnabled(true);
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    private void handleGoogleSignInResult(ActivityResult activityResult) {
        Task<GoogleSignInAccount> task =
                GoogleSignIn.getSignedInAccountFromIntent(activityResult.getData());
        try {
            GoogleSignInAccount account = task.getResult(ApiException.class);
            String idToken = account.getIdToken();
            if (idToken != null) {
                verifyGoogleAndResetPin(idToken);
            } else {
                Toast.makeText(requireContext(),
                        getString(R.string.error_auth_login_failed), Toast.LENGTH_SHORT).show();
            }
        } catch (ApiException e) {
            if (e.getStatusCode() != 12501) { // Lỗi người dùng chủ động hủy
                Toast.makeText(requireContext(),
                        getString(R.string.error_auth_login_failed), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void verifyGoogleAndResetPin(String idToken) {
        setNumpadEnabled(false);
        AppContainer container = ((MoneyMateApplication) requireActivity().getApplication())
                .getAppContainer();

        container.authRepository.verifyGoogleForPinReset(idToken,
                new com.group10.moneymate.data.repository.AuthRepository.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        if (binding == null) return;
                        requireActivity().runOnUiThread(() -> {
                            if (binding == null) return;
                            // Reset lockout
                            container.prefsManager.setFailedAttempts(0);
                            container.prefsManager.setLockoutUntil(0L);
                            // Chuyển sang CREATE mode để nhập PIN mới
                            mode = SecurityViewModel.MODE_CREATE;
                            viewModel.init(SecurityViewModel.MODE_CREATE);
                            setupTitle(SecurityViewModel.MODE_CREATE);
                            binding.keyForgot.setVisibility(View.INVISIBLE);
                            setNumpadEnabled(true);
                            binding.tvLockout.setVisibility(View.GONE);
                            Toast.makeText(requireContext(),
                                    "Xác thực thành công. Vui lòng tạo mã PIN mới.",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onError(String message) {
                        if (binding == null) return;
                        requireActivity().runOnUiThread(() -> {
                            if (binding == null) return;
                            setNumpadEnabled(true);
                            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
                        });
                    }
                });
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String getSubtitleForMode() {
        Integer m = viewModel.getCurrentMode().getValue();
        if (m == null) m = mode;
        switch (m) {
            case SecurityViewModel.MODE_CREATE:  return getString(R.string.passcode_subtitle_create);
            case SecurityViewModel.MODE_CONFIRM: return getString(R.string.passcode_subtitle_confirm);
            default:                             return getString(R.string.passcode_subtitle_verify);
        }
    }
}
