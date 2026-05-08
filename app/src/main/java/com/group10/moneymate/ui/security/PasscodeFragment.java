package com.group10.moneymate.ui.security;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentPasscodeBinding;
import com.group10.moneymate.ui.auth.LoginActivity;

import java.util.Locale;
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
                // Bước đầu của CHANGE: xác nhận PIN cũ
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
            binding.keyForgot.setOnClickListener(v -> navigateToForgotPin());
        } else {
            binding.keyForgot.setVisibility(View.INVISIBLE);
        }
    }

    // ─── Observers ────────────────────────────────────────────────────────────

    private void observeViewModel() {
        // Số dots đã lấp đầy
        viewModel.getFilledDots().observe(getViewLifecycleOwner(), count -> updateDots(count, false));

        // Mode thay đổi (CREATE → CONFIRM)
        viewModel.getCurrentMode().observe(getViewLifecycleOwner(), newMode -> {
            if (newMode != null) setupTitle(newMode);
        });

        // UI State
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

        // Error message
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
            if (msg == null) {
                binding.tvSubtitle.setText(getSubtitleForMode());
                binding.tvSubtitle.setTextColor(0xAAFFFFFF);
                return;
            }
            if (msg.equals("mismatch")) {
                binding.tvSubtitle.setText(R.string.error_passcode_mismatch);
            } else if (msg.startsWith("wrong:")) {
                int remaining = Integer.parseInt(msg.substring(6));
                binding.tvSubtitle.setText(
                        getString(R.string.error_passcode_wrong_with_attempts, remaining));
            }
            binding.tvSubtitle.setTextColor(0xFFF44336); // red
        });

        // Lockout timestamp
        viewModel.getLockoutUntil().observe(getViewLifecycleOwner(), until -> {
            if (until != null && until > 0) startLockoutCountdown(until);
        });
    }

    // ─── State handlers ───────────────────────────────────────────────────────

    private void onSuccess() {
        if (finishToHome && requireActivity() instanceof PasscodeActivity) {
            ((PasscodeActivity) requireActivity()).navigateToHomeAndFinish();
        } else {
            requireActivity().finish();
        }
    }

    private void onError() {
        // Đổi dots sang đỏ
        updateDots(SecurityViewModel.PASSCODE_LENGTH, true);
        // Shake animation
        binding.llDots.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.shake));
        // Reset sau 500ms
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
            dots[i].setSelected(error);          // selected = error (red)
            dots[i].setActivated(!error && i < filled); // activated = filled (white)
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
                viewModel.init(mode); // reset state
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

    // ─── Navigation ───────────────────────────────────────────────────────────

    private void navigateToForgotPin() {
        // Chuyển về LoginActivity (password fallback)
        Intent intent = new Intent(requireContext(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
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
