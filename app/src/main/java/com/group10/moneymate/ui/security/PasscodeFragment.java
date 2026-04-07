package com.group10.moneymate.ui.security;

import android.content.Intent;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentPasscodeBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.ui.main.HomeActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Màn hình nhập passcode hỗ trợ 3 mode:
 *
 *   MODE_CREATE  (0) — tạo passcode lần đầu sau khi đăng ký
 *   MODE_CONFIRM (1) — xác nhận lại passcode (so sánh với lần nhập trước)
 *   MODE_VERIFY  (2) — nhập passcode để đăng nhập (online & offline)
 *
 * Arguments (Safe Args từ nav_passcode.xml / nav_auth.xml):
 *   passcode_mode         (int,     default 0)
 *   passcode_finish_to_home (boolean, default false)
 */
public class PasscodeFragment extends Fragment {

    private FragmentPasscodeBinding binding;
    private SecurityViewModel viewModel;

    private final StringBuilder enteredDigits = new StringBuilder();
    private List<View> dots;
    private int currentMode;
    private boolean finishToHome;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentPasscodeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Lấy arguments
        Bundle args = getArguments();
        currentMode   = args != null ? args.getInt("passcode_mode", SecurityViewModel.MODE_CREATE)
                : SecurityViewModel.MODE_CREATE;
        finishToHome  = args != null && args.getBoolean("passcode_finish_to_home", false);

        viewModel = new ViewModelProvider(requireActivity()).get(SecurityViewModel.class);

        setupDots();
        setupNumpad();
        setupUI();
        setupBackButton();
        observeViewModel();
    }

    // ─── UI setup ─────────────────────────────────────────────────────────────

    private void setupDots() {
        dots = new ArrayList<>();
        dots.add(binding.dot1);
        dots.add(binding.dot2);
        dots.add(binding.dot3);
        dots.add(binding.dot4);
        dots.add(binding.dot5);
        dots.add(binding.dot6);
    }

    private void setupUI() {
        switch (currentMode) {
            case SecurityViewModel.MODE_CREATE:
                binding.tvPasscodeTitle.setText(R.string.title_create_passcode);
                binding.tvPasscodeSubtitle.setText(R.string.passcode_subtitle_create);
                binding.btnBack.setVisibility(View.GONE); // không cho back khi tạo lần đầu
                break;
            case SecurityViewModel.MODE_CONFIRM:
                binding.tvPasscodeTitle.setText(R.string.title_confirm_passcode);
                binding.tvPasscodeSubtitle.setText(R.string.passcode_subtitle_confirm);
                binding.btnBack.setVisibility(View.VISIBLE);
                break;
            case SecurityViewModel.MODE_VERIFY:
                binding.tvPasscodeTitle.setText(R.string.title_enter_passcode);
                binding.tvPasscodeSubtitle.setText(R.string.passcode_subtitle_verify);
                binding.btnBack.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void setupBackButton() {
        binding.btnBack.setOnClickListener(v ->
                Navigation.findNavController(v).navigateUp());
    }

    private void setupNumpad() {
        binding.btn0.setOnClickListener(v -> onDigitPressed("0"));
        binding.btn1.setOnClickListener(v -> onDigitPressed("1"));
        binding.btn2.setOnClickListener(v -> onDigitPressed("2"));
        binding.btn3.setOnClickListener(v -> onDigitPressed("3"));
        binding.btn4.setOnClickListener(v -> onDigitPressed("4"));
        binding.btn5.setOnClickListener(v -> onDigitPressed("5"));
        binding.btn6.setOnClickListener(v -> onDigitPressed("6"));
        binding.btn7.setOnClickListener(v -> onDigitPressed("7"));
        binding.btn8.setOnClickListener(v -> onDigitPressed("8"));
        binding.btn9.setOnClickListener(v -> onDigitPressed("9"));
        binding.btnBackspace.setOnClickListener(v -> onBackspacePressed());
    }

    // ─── Input logic ──────────────────────────────────────────────────────────

    private void onDigitPressed(String digit) {
        if (enteredDigits.length() >= 6) return;

        enteredDigits.append(digit);
        updateDots();
        hideError();

        if (enteredDigits.length() == 6) {
            onPasscodeComplete();
        }
    }

    private void onBackspacePressed() {
        if (enteredDigits.length() == 0) return;
        enteredDigits.deleteCharAt(enteredDigits.length() - 1);
        updateDots();
        hideError();
    }

    private void onPasscodeComplete() {
        String passcode = enteredDigits.toString();

        if (currentMode == SecurityViewModel.MODE_CREATE) {
            // Lưu tạm và navigate sang CONFIRM
            viewModel.setPendingPasscode(passcode);
            navigateToConfirm();
        } else {
            // CONFIRM hoặc VERIFY → submit cho ViewModel xử lý
            viewModel.submitPasscode(passcode, currentMode);
        }
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    private void navigateToConfirm() {
        Bundle bundle = new Bundle();
        bundle.putInt("passcode_mode", SecurityViewModel.MODE_CONFIRM);
        bundle.putBoolean("passcode_finish_to_home", finishToHome);
        Navigation.findNavController(requireView())
                .navigate(R.id.passcodeFragment, bundle);
    }

    private void navigateToHome() {
        AppContainer container = ((MoneyMateApplication) requireActivity().getApplication())
                .getAppContainer();
        container.bootstrapLocalData();

        Intent intent = new Intent(requireContext(), HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finish();
    }

    // ─── Observe ──────────────────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getPasscodeState().observe(getViewLifecycleOwner(), state -> {
            if (state == null) return;
            switch (state) {
                case PASSCODE_SAVED:
                    // CREATE + CONFIRM xong → vào app
                    navigateToHome();
                    break;

                case PASSCODE_VERIFIED:
                    // VERIFY thành công → vào app
                    navigateToHome();
                    break;

                case PASSCODE_WRONG:
                    showError(getString(R.string.error_passcode_wrong));
                    shakeDotsAndReset();
                    viewModel.resetState();
                    break;

                case LOCKED_OUT:
                    showError(getString(R.string.error_passcode_locked));
                    setNumpadEnabled(false);
                    break;

                case ERROR:
                    viewModel.getErrorMessage().observe(getViewLifecycleOwner(), msg -> {
                        if (msg != null) showError(msg);
                    });
                    resetInput();
                    viewModel.resetState();
                    break;

                default:
                    break;
            }
        });
    }

    // ─── Dot display ──────────────────────────────────────────────────────────

    private void updateDots() {
        int filled = enteredDigits.length();
        for (int i = 0; i < dots.size(); i++) {
            dots.get(i).setActivated(i < filled);
        }
    }

    private void resetDots() {
        for (View dot : dots) {
            dot.setActivated(false);
        }
    }

    // ─── Error / animation ────────────────────────────────────────────────────

    private void showError(String message) {
        binding.tvPasscodeError.setText(message);
        binding.tvPasscodeError.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        binding.tvPasscodeError.setVisibility(View.INVISIBLE);
    }

    private void shakeDotsAndReset() {
        // Shake animation
        binding.layoutPasscodeDots.startAnimation(
                AnimationUtils.loadAnimation(requireContext(), R.anim.shake));

        // Vibrate
        Vibrator vibrator = ContextCompat.getSystemService(requireContext(), Vibrator.class);
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE));
        }

        // Reset input sau animation
        binding.layoutPasscodeDots.postDelayed(this::resetInput, 400);
    }

    private void resetInput() {
        enteredDigits.setLength(0);
        resetDots();
    }

    private void setNumpadEnabled(boolean enabled) {
        binding.btn0.setEnabled(enabled);
        binding.btn1.setEnabled(enabled);
        binding.btn2.setEnabled(enabled);
        binding.btn3.setEnabled(enabled);
        binding.btn4.setEnabled(enabled);
        binding.btn5.setEnabled(enabled);
        binding.btn6.setEnabled(enabled);
        binding.btn7.setEnabled(enabled);
        binding.btn8.setEnabled(enabled);
        binding.btn9.setEnabled(enabled);
        binding.btnBackspace.setEnabled(enabled);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}