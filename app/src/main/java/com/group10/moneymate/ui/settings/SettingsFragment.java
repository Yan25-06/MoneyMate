package com.group10.moneymate.ui.settings;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.chip.Chip;
import com.group10.moneymate.BuildConfig;
import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentSettingsBinding;
import com.group10.moneymate.di.AppContainer;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.ui.auth.LoginActivity;
import com.group10.moneymate.ui.security.PasscodeActivity;
import com.group10.moneymate.ui.security.SecurityViewModel;
import com.group10.moneymate.ui.sync.SyncViewModel;

import java.util.Date;

public class SettingsFragment extends Fragment {

    private FragmentSettingsBinding binding;
    private SettingsViewModel viewModel;
    private SyncViewModel syncViewModel;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(SettingsViewModel.class);

        // SyncViewModel dùng scope của Activity để share với HomeFragment nếu cần
        syncViewModel = new ViewModelProvider(requireActivity()).get(SyncViewModel.class);

        setupWindowInsets();
        setupStaticUi();
        setupListeners();
        observeLogout();
        observeSyncState();
    }

    private void setupWindowInsets() {
        final int initialAppBarTopPadding = binding.appBarLayout.getPaddingTop();
        final int initialScrollBottomPadding = binding.scrollSettings.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appBarLayout.setPadding(
                    binding.appBarLayout.getPaddingLeft(),
                    initialAppBarTopPadding + systemBars.top,
                    binding.appBarLayout.getPaddingRight(),
                    binding.appBarLayout.getPaddingBottom());
            binding.scrollSettings.setPadding(
                    binding.scrollSettings.getPaddingLeft(),
                    binding.scrollSettings.getPaddingTop(),
                    binding.scrollSettings.getPaddingRight(),
                    initialScrollBottomPadding + systemBars.bottom);
            return insets;
        });
    }

    private void setupStaticUi() {
        String[] dateFormats = getResources().getStringArray(R.array.date_formats);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_moneymate_dropdown_option,
                dateFormats);
        binding.tvVersion.setText(String.format("%s %s", getString(R.string.app_name), BuildConfig.VERSION_NAME));

        // Trạng thái mặc định cho sync card — trước khi observe
        binding.tvSyncStatus.setText(getString(R.string.sync_status_idle));
        updateSyncChip(SyncViewModel.SyncState.IDLE);
    }

    private void setupListeners() {
        binding.btnProfile.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                SettingsFragmentDirections.actionSettingsToProfile()));

        binding.btnSecurity.setOnClickListener(v -> openChangePasscode());

        binding.btnWallets.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                SettingsFragmentDirections.actionSettingsToWallets()));

        binding.btnCategories.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                SettingsFragmentDirections.actionSettingsToCategories()));

        binding.btnBudgets.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                SettingsFragmentDirections.actionSettingsToStatistics()));

        binding.btnDebts.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                SettingsFragmentDirections.actionSettingsToDebts()));

        binding.btnEvents.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                SettingsFragmentDirections.actionSettingsToEvents()));

        binding.btnNotifications.setOnClickListener(v -> Navigation.findNavController(v).navigate(
                SettingsFragmentDirections.actionSettingsToNotifications()));

        // THÊM MỚI: Nút đồng bộ ngay
        binding.btnSyncNow.setOnClickListener(v -> {
            syncViewModel.syncNow();
            // Disable nút ngay lập tức để tránh double-click
            binding.btnSyncNow.setEnabled(false);
        });

        binding.btnLogout.setOnClickListener(v -> onLogoutClicked());
    }

    private void observeLogout() {
        viewModel.getLogoutSuccess().observe(getViewLifecycleOwner(), logoutSuccess -> {
            if (Boolean.TRUE.equals(logoutSuccess)) {
                navigateToLogin();
            }
        });
    }

    // ─── THÊM MỚI: Observe sync state ───────────────────────────────────────

    private void observeSyncState() {
        syncViewModel.getSyncState().observe(getViewLifecycleOwner(), this::renderSyncState);
    }

    private void renderSyncState(@NonNull SyncViewModel.SyncState state) {
        switch (state) {
            case SYNCING:
                renderSyncing();
                break;
            case SUCCESS:
                renderSuccess();
                break;
            case FAILED:
                renderFailed();
                break;
            case IDLE:
            default:
                renderIdle();
                break;
        }
    }

    private void renderSyncing() {
        // Spinner hiện, icon ẩn
        binding.ivSyncIcon.setVisibility(View.GONE);
        binding.pbSyncSpinner.setVisibility(View.VISIBLE);
        // Progress bar hiện
        binding.progressSync.setVisibility(View.VISIBLE);
        // Status text
        binding.tvSyncStatus.setText(getString(R.string.sync_status_syncing));
        binding.tvSyncStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary));
        // Chip
        updateSyncChip(SyncViewModel.SyncState.SYNCING);
        // Nút disable
        binding.btnSyncNow.setEnabled(false);
        binding.btnSyncNow.setText(getString(R.string.sync_syncing_button));
    }

    private void renderSuccess() {
        // Spinner ẩn, icon hiện
        binding.ivSyncIcon.setVisibility(View.VISIBLE);
        binding.pbSyncSpinner.setVisibility(View.GONE);
        binding.ivSyncIcon.setImageResource(R.drawable.outline_history_24);
        binding.ivSyncIcon.setImageTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.income_green)));
        // Progress bar ẩn
        binding.progressSync.setVisibility(View.GONE);
        // Status text với thời điểm sync
        String timeStr = DateFormat.getTimeFormat(requireContext()).format(new Date());
        binding.tvSyncStatus.setText(getString(R.string.sync_status_success, timeStr));
        binding.tvSyncStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.income_green));
        // Chip
        updateSyncChip(SyncViewModel.SyncState.SUCCESS);
        // Nút re-enable
        binding.btnSyncNow.setEnabled(true);
        binding.btnSyncNow.setText(getString(R.string.sync_now_button));
    }

    private void renderFailed() {
        // Spinner ẩn, icon cảnh báo
        binding.ivSyncIcon.setVisibility(View.VISIBLE);
        binding.pbSyncSpinner.setVisibility(View.GONE);
        binding.ivSyncIcon.setImageResource(R.drawable.outline_warning_amber_24);
        binding.ivSyncIcon.setImageTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.expense_red)));
        // Progress bar ẩn
        binding.progressSync.setVisibility(View.GONE);
        // Status text
        binding.tvSyncStatus.setText(getString(R.string.sync_status_failed));
        binding.tvSyncStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.expense_red));
        // Chip
        updateSyncChip(SyncViewModel.SyncState.FAILED);
        // Nút thử lại
        binding.btnSyncNow.setEnabled(true);
        binding.btnSyncNow.setText(getString(R.string.sync_retry_button));
    }

    private void renderIdle() {
        binding.ivSyncIcon.setVisibility(View.VISIBLE);
        binding.pbSyncSpinner.setVisibility(View.GONE);
        binding.ivSyncIcon.setImageResource(R.drawable.outline_history_24);
        binding.ivSyncIcon.setImageTintList(
                android.content.res.ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.income_green)));
        binding.progressSync.setVisibility(View.GONE);
        binding.tvSyncStatus.setText(getString(R.string.sync_status_idle));
        binding.tvSyncStatus.setTextColor(
                ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary));
        updateSyncChip(SyncViewModel.SyncState.IDLE);
        binding.btnSyncNow.setEnabled(true);
        binding.btnSyncNow.setText(getString(R.string.sync_now_button));
    }

    /**
     * Cập nhật Chip trạng thái sync theo từng state.
     * SUCCESS = xanh, FAILED = đỏ, SYNCING = ẩn, IDLE = xám.
     */
    private void updateSyncChip(@NonNull SyncViewModel.SyncState state) {
        Chip chip = binding.chipSyncState;
        switch (state) {
            case SUCCESS:
                chip.setVisibility(View.VISIBLE);
                chip.setText(getString(R.string.sync_chip_success));
                chip.setChipBackgroundColorResource(R.color.green_500);
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
                break;
            case FAILED:
                chip.setVisibility(View.VISIBLE);
                chip.setText(getString(R.string.sync_chip_failed));
                chip.setChipBackgroundColor(
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#FEE2E2")));
                chip.setTextColor(ContextCompat.getColor(requireContext(), R.color.expense_red));
                break;
            case SYNCING:
                chip.setVisibility(View.GONE);
                break;
            case IDLE:
            default:
                chip.setVisibility(View.VISIBLE);
                chip.setText(getString(R.string.sync_chip_idle));
                chip.setChipBackgroundColorResource(R.color.statistics_card_inner_bg);
                chip.setTextColor(
                        ContextCompat.getColor(requireContext(), R.color.statistics_text_secondary));
                break;
        }
    }

    // ─── Security ─────────────────────────────────────────────────────────────

    /**
     * Mở PasscodeActivity ở MODE_CHANGE:
     *  1. Nhập PIN cũ để xác nhận
     *  2. Nhập PIN mới
     *  3. Nhập lại PIN mới để xác nhận
     *
     * Nếu chưa thiết lập PIN → mở trực tiếp ở MODE_CREATE.
     */
    private void openChangePasscode() {
        AppContainer container = ((MoneyMateApplication) requireActivity().getApplication())
                .getAppContainer();

        int mode = container.authRepository.isPasscodeEnabled()
                ? SecurityViewModel.MODE_CHANGE
                : SecurityViewModel.MODE_CREATE;

        Intent intent = new Intent(requireContext(), PasscodeActivity.class);
        intent.putExtra(PasscodeActivity.EXTRA_MODE, mode);
        intent.putExtra(PasscodeActivity.EXTRA_FINISH_TO_HOME, false); // finish() về Settings
        startActivity(intent);
    }

    // ─── Navigation ──────────────────────────────────────────────────────────

    private void onLogoutClicked() {
        viewModel.signOut();
    }

    private void navigateToLogin() {
        Intent intent = new Intent(requireActivity(), LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}