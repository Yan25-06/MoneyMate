package com.group10.moneymate.ui.transaction;

import android.net.Uri;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.graphics.ColorUtils;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.group10.moneymate.R;
import com.group10.moneymate.ai.receipt.ReceiptScanContract;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.DebtEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.repository.DebtRepository;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.databinding.DialogTransactionScanSourceBinding;
import com.group10.moneymate.databinding.FragmentAddEditTransactionBinding;
import com.group10.moneymate.databinding.ItemDebtBinding;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.utils.DateUtils;
import com.group10.moneymate.di.MoneyMateApplication;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.FileUtils;
import com.group10.moneymate.utils.IconProvider;
import com.group10.moneymate.utils.MoneyMateDatePickerHelper;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.models.DebtType;
import com.group10.moneymate.utils.TimeWindowUtils;
import com.group10.moneymate.utils.LoadingHelper;
import com.group10.moneymate.workers.AIReceiptScannerWorker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.text.Normalizer;

public class AddEditTransactionFragment extends Fragment {

    public static final String REQUEST_KEY_OCR_DRAFT_SAVED = "ocr_draft_saved";
    public static final String RESULT_KEY_OCR_DRAFT_ID = "ocr_draft_id";
    public static final String RESULT_KEY_SAVED_TRANSACTION_ID = "saved_transaction_id";
    public static final String RESULT_TRANSACTION_CHANGED = "result_transaction_changed";
    public static final String RESULT_TRANSACTION_CHANGED_ID = "result_transaction_changed_id";
    public static final String RESULT_TRANSACTION_CHANGE_TYPE = "result_transaction_change_type";
    public static final String CHANGE_TYPE_INSERT = "insert";
    public static final String CHANGE_TYPE_UPDATE = "update";
    public static final String IMAGE_INPUT_SOURCE_CAMERA = "camera";
    public static final String IMAGE_INPUT_SOURCE_GALLERY = "gallery";

    private FragmentAddEditTransactionBinding binding;
    private TransactionViewModel viewModel;
    private com.group10.moneymate.ui.debt.DebtViewModel debtViewModel;

    // State
    private String currentType = Constants.TYPE_EXPENSE;
    private boolean isDebtTabSelected;
    private String selectedCategoryId = null;
    private String selectedCategoryName = null;
    private String selectedIconName = null;
    private DebtType selectedDebtType = null;
    private String selectedLinkedDebtId = null; // for DEBT_COLLECTION / REPAYMENT
    private String selectedWalletId = null;
    private long selectedTimestamp = System.currentTimeMillis();
    private long selectedDebtDueTimestamp = 0L;
    private List<WalletEntity> walletList = new ArrayList<>();
    private List<WalletEntity> allWalletList = new ArrayList<>();
    private List<WalletEntity> activeWalletList = new ArrayList<>();
    @Nullable
    private LiveData<CategoryEntity> selectedCategorySource;
    @Nullable
    private Observer<CategoryEntity> selectedCategoryObserver;
    @Nullable
    private LiveData<List<CategoryEntity>> ocrCategoryPrefillSource;
    @Nullable
    private Observer<List<CategoryEntity>> ocrCategoryPrefillObserver;
    private boolean isFormattingAmount;
    private boolean isLoadingEdit;
    private boolean isManualIcon;
    private boolean isPreparingReceiptImage;
    private boolean isSaving;
    private final LoadingHelper loadingHelper = new LoadingHelper();
    @Nullable
    private BottomSheetDialog scanSourceDialog;
    @Nullable
    private String selectedReceiptImagePath;
    @Nullable
    private String selectedReceiptImageUri;
    @Nullable
    private String selectedReceiptInputSource;
    @Nullable
    private String ocrDraftId;
    @Nullable
    private String ocrDraftCategoryHint;
    @Nullable
    private LiveData<WorkInfo> receiptScanWorkInfoSource;
    @Nullable
    private Observer<WorkInfo> receiptScanWorkInfoObserver;
    @Nullable
    private UUID currentReceiptScanWorkId;
    private ActivityResultLauncher<String> galleryPickerLauncher;
    private final ExecutorService receiptImageExecutor = Executors.newSingleThreadExecutor();
    @Nullable
    private String savedTransactionId;
    @Nullable
    private String savedTransactionChangeType;

    // Edit mode
    private String transactionId = null;
    private TransactionEntity originalTransaction = null;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        galleryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleGalleryImageSelected);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        binding = FragmentAddEditTransactionBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        debtViewModel = new ViewModelProvider(this).get(com.group10.moneymate.ui.debt.DebtViewModel.class);

        AddEditTransactionFragmentArgs args = AddEditTransactionFragmentArgs.fromBundle(
                getArguments() != null ? getArguments() : new Bundle());
        transactionId = args.getTransactionId();
        ocrDraftId = args.getOcrDraftId();

        setupInsets();
        setupToolbar();
        setupTypeToggle();
        setupCategoryPickerRow();
        setupDatePicker();
        setupDebtDueDatePicker();
        setupTextInputs();
        setupScanEntry();
        setupSaveButton();
        observePickerResults();
        observeCameraCaptureResults();
        observeWallets();

        if (transactionId != null) {
            binding.topAppBar.setTitle(R.string.transaction_edit_title);
            binding.btnSave.setText(R.string.btn_update);
            updateScanEntryVisibility();
            loadExistingTransaction();
        } else {
            // Add mode: chọn EXPENSE mặc định, ngày hôm nay
            binding.topAppBar.setTitle(R.string.add_transaction);
            binding.btnSave.setText(R.string.btn_save);

            String preselectedTab = getArguments() != null ? getArguments().getString("preselectedTab") : null;
            if ("DEBT".equals(preselectedTab)) {
                binding.toggleType.check(R.id.btn_debt);
            } else {
                binding.toggleType.check(R.id.btn_expense);
            }
            binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
            updateAmountAccent();
            updateTypeToggleAppearance();
            updateCategorySelectionUi();
            applyOcrDraftPrefill(args);
            updateScanEntryVisibility();
        }
    }

    private void setupToolbar() {
        binding.topAppBar.setNavigationOnClickListener(v -> {
            if (isSaving) {
                return;
            }
            Navigation.findNavController(v).navigateUp();
        });
    }

    private void setupInsets() {
        final int initialAppBarTopPadding = binding.appBarLayout.getPaddingTop();
        final int initialScrollBottomPadding = binding.scrollContent.getPaddingBottom();
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            binding.appBarLayout.setPadding(
                    binding.appBarLayout.getPaddingLeft(),
                    initialAppBarTopPadding + systemBars.top,
                    binding.appBarLayout.getPaddingRight(),
                    binding.appBarLayout.getPaddingBottom());
            binding.scrollContent.setPadding(
                    binding.scrollContent.getPaddingLeft(),
                    binding.scrollContent.getPaddingTop(),
                    binding.scrollContent.getPaddingRight(),
                    initialScrollBottomPadding + systemBars.bottom);
            return insets;
        });
    }

    // ─── Type Toggle ──────────────────────────────────────────────────────────

    private void setupTypeToggle() {
        binding.toggleType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || isLoadingEdit)
                return;
            if (checkedId == R.id.btn_expense) {
                currentType = Constants.TYPE_EXPENSE;
                isDebtTabSelected = false;
            } else if (checkedId == R.id.btn_income) {
                currentType = Constants.TYPE_INCOME;
                isDebtTabSelected = false;
            } else if (checkedId == R.id.btn_debt) {
                currentType = TransactionCategoryPickerViewModel.TYPE_DEBT;
                isDebtTabSelected = true;
            }
            selectedCategoryId = null;
            selectedCategoryName = null;
            selectedDebtType = null;
            isManualIcon = false;
            selectedIconName = null;
            updateAmountAccent();
            updateTypeToggleAppearance();
            updateCategorySelectionUi();
            updateDebtFieldsVisibility();
        });
    }

    private void setupCategoryPickerRow() {
        binding.layoutCategoryPicker.setOnClickListener(v -> {
            AddEditTransactionFragmentDirections.ActionAddEditTransactionFragmentToTransactionCategoryPickerFragment action = AddEditTransactionFragmentDirections
                    .actionAddEditTransactionFragmentToTransactionCategoryPickerFragment();
            action.setSelectedCategoryId(selectedCategoryId);
            action.setTransactionType(resolvePickerTransactionType());
            Navigation.findNavController(v).navigate(action);
        });
    }

    // ─── Date Picker ──────────────────────────────────────────────────────────

    private void setupDatePicker() {
        binding.etDate.setOnClickListener(v -> {
            MoneyMateDatePickerHelper.showSingleDatePicker(
                    this,
                    TimeWindowUtils.toDeviceLocalDate(selectedTimestamp),
                    "transaction_single_date",
                    date -> {
                        selectedTimestamp = TimeWindowUtils.startOfDayLocalDateUtc(date);
                        binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
                    });
        });
        binding.btnPrevDate.setOnClickListener(v -> shiftSelectedDate(-1));
        binding.btnNextDate.setOnClickListener(v -> shiftSelectedDate(1));
    }

    private void shiftSelectedDate(int dayOffset) {
        java.time.LocalDate shiftedDate = TimeWindowUtils.toDeviceLocalDate(selectedTimestamp)
                .plusDays(dayOffset);
        selectedTimestamp = TimeWindowUtils.startOfDayLocalDateUtc(shiftedDate);
        binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
    }

    private void setupDebtDueDatePicker() {
        binding.etDebtDueDate.setOnClickListener(v -> {
            java.time.LocalDate initialDate = selectedDebtDueTimestamp > 0
                    ? TimeWindowUtils.toDeviceLocalDate(selectedDebtDueTimestamp)
                    : java.time.LocalDate.now().plusMonths(1);
            MoneyMateDatePickerHelper.showSingleDatePicker(
                    this,
                    initialDate,
                    "debt_due_date",
                    date -> {
                        selectedDebtDueTimestamp = TimeWindowUtils.startOfDayLocalDateUtc(date);
                        binding.etDebtDueDate.setText(DateUtils.formatDate(selectedDebtDueTimestamp));
                    });
        });
    }

    private void setupTextInputs() {
        binding.etNote.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        binding.etAmount.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // No-op: input is reformatted only after the latest user edit is available.
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // No-op: the formatter relies on the settled text in afterTextChanged().
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (isFormattingAmount) {
                    return;
                }
                String digits = CurrencyFormatter.extractDigits(editable.toString());
                if (digits.isEmpty()) {
                    return;
                }
                isFormattingAmount = true;
                String formatted = CurrencyFormatter.formatInputAmount(Long.parseLong(digits));
                binding.etAmount.setText(formatted);
                binding.etAmount.setSelection(formatted.length());
                isFormattingAmount = false;
            }
        });
    }

    private void setupScanEntry() {
        binding.fabScanTransaction.setOnClickListener(v -> {
            if (isSaving) {
                return;
            }
            showScanSourceChooser();
        });
    }

    private void showScanSourceChooser() {
        dismissScanSourceDialog();

        DialogTransactionScanSourceBinding dialogBinding = DialogTransactionScanSourceBinding
                .inflate(getLayoutInflater());
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(dialogBinding.getRoot());
        dialogBinding.tvScanSourceSubtitle.setText(resolvePreScanSourceSubtitle());

        dialogBinding.btnScanFromCamera.setOnClickListener(v -> {
            selectedReceiptInputSource = IMAGE_INPUT_SOURCE_CAMERA;
            dialog.dismiss();
            NavDirections action = AddEditTransactionFragmentDirections
                    .actionAddEditTransactionFragmentToCameraFragment();
            Navigation.findNavController(binding.getRoot()).navigate(action);
        });
        dialogBinding.btnScanFromGallery.setOnClickListener(v -> {
            selectedReceiptInputSource = IMAGE_INPUT_SOURCE_GALLERY;
            dialog.dismiss();
            launchGalleryPicker();
        });
        dialog.setOnDismissListener(dialogInterface -> {
            if (scanSourceDialog == dialog) {
                scanSourceDialog = null;
            }
        });

        scanSourceDialog = dialog;
        dialog.show();
    }

    private void dismissScanSourceDialog() {
        if (scanSourceDialog != null) {
            scanSourceDialog.dismiss();
            scanSourceDialog = null;
        }
    }

    private void launchGalleryPicker() {
        if (isPreparingReceiptImage || isSaving) {
            return;
        }
        galleryPickerLauncher.launch("image/*");
    }

    private void handleGalleryImageSelected(@Nullable Uri sourceUri) {
        if (sourceUri == null || !isAdded()) {
            return;
        }
        startGalleryImport(sourceUri);
    }

    private void startGalleryImport(@NonNull Uri sourceUri) {
        Executor mainExecutor = ContextCompat.getMainExecutor(requireContext());
        android.content.Context appContext = requireContext().getApplicationContext();

        startReceiptPreparationUi(R.string.transaction_scan_gallery_loading);
        receiptImageExecutor.execute(() -> {
            try {
                FileUtils.ReceiptImageCopyResult copyResult = FileUtils.copyReceiptImageToInternalStorage(appContext,
                        sourceUri);
                mainExecutor.execute(() -> finishGalleryImportSuccess(copyResult));
            } catch (FileUtils.InvalidReceiptImageException exception) {
                mainExecutor.execute(() -> finishGalleryImportError(R.string.transaction_scan_gallery_invalid_image));
            } catch (FileUtils.ReceiptImageTooLargeException exception) {
                mainExecutor.execute(() -> finishGalleryImportError(R.string.transaction_scan_gallery_image_too_large));
            } catch (FileUtils.ReceiptImageStorageException exception) {
                mainExecutor.execute(() -> finishGalleryImportError(R.string.transaction_scan_gallery_copy_failed));
            }
        });
    }

    private void startReceiptPreparationUi(int messageResId) {
        isPreparingReceiptImage = true;
        updateActionState();
        loadingHelper.show(this, messageResId);
    }

    private void stopReceiptPreparationUi() {
        isPreparingReceiptImage = false;
        updateActionState();
        loadingHelper.dismiss();
    }

    private void finishGalleryImportSuccess(@NonNull FileUtils.ReceiptImageCopyResult copyResult) {
        stopReceiptPreparationUi();
        if (!isAdded()) {
            return;
        }
        applySelectedReceiptImage(copyResult.getInternalPath(), copyResult.getInternalUri());
    }

    private void finishGalleryImportError(int messageResId) {
        stopReceiptPreparationUi();
        if (!isAdded()) {
            return;
        }
        Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show();
    }

    private void applySelectedReceiptImage(@NonNull String imagePath,
            @NonNull String imageUri) {
        selectedReceiptImagePath = imagePath;
        selectedReceiptImageUri = imageUri;
        enqueueReceiptScan(imagePath, imageUri);
    }

    private void observeCameraCaptureResults() {
        getParentFragmentManager().setFragmentResultListener(
                CameraFragment.REQUEST_KEY_CAPTURED_IMAGE,
                getViewLifecycleOwner(),
                (requestKey, bundle) -> {
                    String imagePath = bundle.getString(CameraFragment.RESULT_KEY_IMAGE_PATH);
                    String imageUri = bundle.getString(CameraFragment.RESULT_KEY_IMAGE_URI);
                    if (!TextUtils.isEmpty(imagePath) && !TextUtils.isEmpty(imageUri)) {
                        applySelectedReceiptImage(imagePath, imageUri);
                    }
                    getParentFragmentManager().clearFragmentResult(CameraFragment.REQUEST_KEY_CAPTURED_IMAGE);
                });
    }

    private void enqueueReceiptScan(@NonNull String imagePath, @NonNull String imageUri) {
        if (!isAdded()) {
            return;
        }

        clearReceiptScanWorkObservation();
        startReceiptPreparationUi(resolveProcessingMessageRes());

        OneTimeWorkRequest request = AIReceiptScannerWorker.createRequest(imagePath, imageUri);
        WorkManager workManager = WorkManager.getInstance(requireContext().getApplicationContext());
        currentReceiptScanWorkId = request.getId();
        observeReceiptScanWork(workManager, currentReceiptScanWorkId);
        workManager.enqueue(request);
    }

    private void observeReceiptScanWork(@NonNull WorkManager workManager, @NonNull UUID workId) {
        receiptScanWorkInfoSource = workManager.getWorkInfoByIdLiveData(workId);
        receiptScanWorkInfoObserver = workInfo -> {
            if (workInfo == null || !workId.equals(currentReceiptScanWorkId) || !workInfo.getState().isFinished()) {
                return;
            }

            clearReceiptScanWorkObservation();
            stopReceiptPreparationUi();

            if (!isAdded() || binding == null) {
                return;
            }

            if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                navigateToTransactionConfirmation(workInfo.getOutputData());
                return;
            }

            handleReceiptScanFailure(workInfo.getOutputData());
        };
        receiptScanWorkInfoSource.observe(getViewLifecycleOwner(), receiptScanWorkInfoObserver);
    }

    private void navigateToTransactionConfirmation(@NonNull Data outputData) {
        AddEditTransactionFragmentDirections.ActionAddEditTransactionFragmentToTransactionConfirmationFragment action = AddEditTransactionFragmentDirections
                .actionAddEditTransactionFragmentToTransactionConfirmationFragment();
        action.setImagePath(outputData.getString(ReceiptScanContract.KEY_IMAGE_PATH));
        action.setImageUri(outputData.getString(ReceiptScanContract.KEY_IMAGE_URI));
        action.setAmount(outputData.getString(ReceiptScanContract.KEY_AMOUNT));
        action.setTimestamp(outputData.getLong(
                ReceiptScanContract.KEY_TIMESTAMP,
                ReceiptScanContract.UNKNOWN_TIMESTAMP));
        action.setMerchant(outputData.getString(ReceiptScanContract.KEY_MERCHANT));
        action.setCategoryHint(outputData.getString(ReceiptScanContract.KEY_CATEGORY_HINT));
        action.setNoteHint(outputData.getString(ReceiptScanContract.KEY_NOTE_HINT));
        action.setItemsJson(outputData.getString(ReceiptScanContract.KEY_ITEMS_JSON));
        action.setProcessingSource(outputData.getString(ReceiptScanContract.KEY_PROCESSING_SOURCE));
        action.setProcessingDetail(outputData.getString(ReceiptScanContract.KEY_PROCESSING_DETAIL));
        action.setImageInputSource(selectedReceiptInputSource);
        action.setConfidence(outputData.getInt(
                ReceiptScanContract.KEY_CONFIDENCE,
                ReceiptScanContract.CONFIDENCE_LOW));
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void handleReceiptScanFailure(@NonNull Data outputData) {
        selectedReceiptImagePath = outputData.getString(ReceiptScanContract.KEY_IMAGE_PATH);
        selectedReceiptImageUri = outputData.getString(ReceiptScanContract.KEY_IMAGE_URI);
        Toast.makeText(requireContext(), R.string.transaction_scan_failed, Toast.LENGTH_SHORT).show();
    }

    private int resolvePreScanSourceSubtitle() {
        String processingDetail = AIReceiptScannerWorker.resolvePreScanProcessingDetail(requireContext());
        if (ReceiptScanContract.DETAIL_CLOUD_PRIMARY.equals(processingDetail)) {
            return R.string.transaction_scan_source_subtitle_cloud;
        }
        if (ReceiptScanContract.DETAIL_LOCAL_RATE_LIMITED.equals(processingDetail)) {
            return R.string.transaction_scan_source_subtitle_rate_limited;
        }
        return R.string.transaction_scan_source_subtitle_local;
    }

    private int resolveProcessingMessageRes() {
        String processingDetail = AIReceiptScannerWorker.resolvePreScanProcessingDetail(requireContext());
        if (ReceiptScanContract.DETAIL_CLOUD_PRIMARY.equals(processingDetail)) {
            return R.string.transaction_scan_processing_cloud;
        }
        if (ReceiptScanContract.DETAIL_LOCAL_RATE_LIMITED.equals(processingDetail)) {
            return R.string.transaction_scan_processing_local_rate_limited;
        }
        return R.string.transaction_scan_processing_local;
    }

    private void clearReceiptScanWorkObservation() {
        if (receiptScanWorkInfoSource != null && receiptScanWorkInfoObserver != null) {
            receiptScanWorkInfoSource.removeObserver(receiptScanWorkInfoObserver);
        }
        receiptScanWorkInfoSource = null;
        receiptScanWorkInfoObserver = null;
        currentReceiptScanWorkId = null;
    }

    private void applyOcrDraftPrefill(@NonNull AddEditTransactionFragmentArgs args) {
        String draftAmount = args.getOcrDraftAmount();
        if (!TextUtils.isEmpty(draftAmount)) {
            try {
                long parsedAmount = Long.parseLong(draftAmount);
                binding.etAmount.setText(CurrencyFormatter.formatInputAmount(parsedAmount));
            } catch (NumberFormatException exception) {
                binding.etAmount.setText(draftAmount);
            }
        }

        String draftNote = args.getOcrDraftNote();
        if (!TextUtils.isEmpty(draftNote)) {
            binding.etNote.setText(draftNote);
        }

        long draftTimestamp = args.getOcrDraftTimestamp();
        if (draftTimestamp > 0L) {
            selectedTimestamp = draftTimestamp;
            binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
        }

        String draftCategoryId = args.getOcrDraftCategoryId();
        if (!TextUtils.isEmpty(draftCategoryId)) {
            selectedCategoryId = draftCategoryId;
        }
        String draftCategoryHint = args.getOcrDraftCategoryHint();
        if (!TextUtils.isEmpty(draftCategoryHint)) {
            ocrDraftCategoryHint = draftCategoryHint.trim();
            selectedCategoryName = ocrDraftCategoryHint;
        }
        if (!TextUtils.isEmpty(draftCategoryId)) {
            loadSelectedCategory();
        } else if (!TextUtils.isEmpty(draftCategoryHint)) {
            updateCategorySelectionUi();
            resolveCategoryFromOcrHintIfNeeded();
        }

        String draftImagePath = args.getOcrDraftImagePath();
        if (!TextUtils.isEmpty(draftImagePath)) {
            selectedReceiptImagePath = draftImagePath;
            selectedReceiptImageUri = Uri.fromFile(new java.io.File(draftImagePath)).toString();
        }

        String draftWalletId = args.getOcrDraftWalletId();
        if (!TextUtils.isEmpty(draftWalletId)) {
            selectedWalletId = draftWalletId;
        }
    }

    private void updateAmountAccent() {
        int accentColor = ContextCompat.getColor(
                requireContext(),
                Constants.TYPE_INCOME.equals(getEffectiveTypeForUi())
                        ? R.color.transfer_blue
                        : R.color.transaction_expense_accent);
        binding.etAmount.setTextColor(accentColor);
        binding.viewAmountAccent.setBackgroundColor(accentColor);
        binding.btnSave.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.transaction_income_accent)));
    }

    private void updateTypeToggleAppearance() {
        boolean expenseSelected = !isDebtTabSelected && Constants.TYPE_EXPENSE.equals(currentType);
        boolean incomeSelected = !isDebtTabSelected && Constants.TYPE_INCOME.equals(currentType);
        styleTypeButton(binding.btnExpense, expenseSelected, true);
        styleTypeButton(binding.btnIncome, incomeSelected, false);
        styleDebtButton(binding.btnDebt, isDebtTabSelected);
    }

    private void styleTypeButton(@NonNull com.google.android.material.button.MaterialButton button,
            boolean selected,
            boolean expenseButton) {
        int backgroundColor = ContextCompat.getColor(
                requireContext(),
                selected
                        ? (expenseButton ? R.color.transaction_expense_soft
                                : R.color.statistics_period_selected_background)
                        : R.color.white);
        int textColor = ContextCompat.getColor(
                requireContext(),
                selected
                        ? (expenseButton ? R.color.transaction_expense_accent : R.color.transfer_blue)
                        : R.color.transaction_muted_text);
        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        button.setStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.transaction_border)));
        button.setTextColor(textColor);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private void styleDebtButton(@NonNull com.google.android.material.button.MaterialButton button,
            boolean selected) {
        int accentColor = ContextCompat.getColor(requireContext(), R.color.budget_warning_orange);
        int backgroundColor = selected
                ? ColorUtils.setAlphaComponent(accentColor, 28)
                : ContextCompat.getColor(requireContext(), R.color.white);
        int textColor = selected
                ? accentColor
                : ContextCompat.getColor(requireContext(), R.color.transaction_muted_text);
        button.setBackgroundTintList(ColorStateList.valueOf(backgroundColor));
        button.setStrokeColor(ColorStateList.valueOf(
                ContextCompat.getColor(requireContext(), R.color.transaction_border)));
        button.setTextColor(textColor);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    // ─── Wallet Dropdown ──────────────────────────────────────────────────────

    private void observeWallets() {
        viewModel.getWallets().observe(getViewLifecycleOwner(), wallets -> {
            allWalletList = wallets != null ? wallets : new ArrayList<>();
            refreshWalletDropdown();
        });
        viewModel.getActiveWallets().observe(getViewLifecycleOwner(), wallets -> {
            activeWalletList = wallets != null ? wallets : new ArrayList<>();
            refreshWalletDropdown();
        });
    }

    private void refreshWalletDropdown() {
        List<WalletEntity> displayWallets = new ArrayList<>(activeWalletList);
        addExistingEditWallet(displayWallets);
        walletList = displayWallets;
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                R.layout.item_wallet_dropdown,
                buildWalletNames(displayWallets));
        binding.dropdownWallet.setAdapter(adapter);
        binding.dropdownWallet.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < walletList.size()) {
                selectedWalletId = walletList.get(position).getId();
            }
        });
        applySelectedWalletSelection();
    }

    private void applySelectedWalletSelection() {
        if (selectedWalletId == null || walletList.isEmpty()) {
            return;
        }
        for (WalletEntity wallet : walletList) {
            if (selectedWalletId.equals(wallet.getId())) {
                binding.dropdownWallet.setText(wallet.getName(), false);
                return;
            }
        }
    }

    private void observePickerResults() {
        NavBackStackEntry backStackEntry = Navigation.findNavController(requireView()).getCurrentBackStackEntry();
        if (backStackEntry == null) {
            return;
        }
        observeCategoryIdResult(backStackEntry);
        observeCategoryTypeResult(backStackEntry);
        observeDebtTypeResult(backStackEntry);
    }

    private void loadSelectedCategory() {
        if (selectedCategorySource != null && selectedCategoryObserver != null) {
            selectedCategorySource.removeObserver(selectedCategoryObserver);
        }
        if (selectedCategoryId == null) {
            updateCategorySelectionUi();
            return;
        }
        selectedCategorySource = viewModel.getCategoryByIdIncludingDeleted(selectedCategoryId);
        selectedCategoryObserver = category -> {
            if (category == null) {
                return;
            }
            selectedCategoryName = category.getName();
            if (!isManualIcon) {
                selectedIconName = category.getIconName();
            }
            updateCategorySelectionUi();
        };
        selectedCategorySource.observe(getViewLifecycleOwner(), selectedCategoryObserver);
    }

    private void resolveCategoryFromOcrHintIfNeeded() {
        if (TextUtils.isEmpty(ocrDraftCategoryHint) || !TextUtils.isEmpty(selectedCategoryId)) {
            return;
        }
        clearOcrCategoryPrefillObserver();
        ocrCategoryPrefillSource = viewModel.getExpenseCategories();
        ocrCategoryPrefillObserver = categories -> {
            String matchedCategoryId = resolveCategoryIdFromHint(categories, ocrDraftCategoryHint);
            if (TextUtils.isEmpty(matchedCategoryId)) {
                return;
            }
            selectedCategoryId = matchedCategoryId;
            loadSelectedCategory();
            clearOcrCategoryPrefillObserver();
        };
        ocrCategoryPrefillSource.observe(getViewLifecycleOwner(), ocrCategoryPrefillObserver);
    }

    private void clearOcrCategoryPrefillObserver() {
        if (ocrCategoryPrefillSource != null && ocrCategoryPrefillObserver != null) {
            ocrCategoryPrefillSource.removeObserver(ocrCategoryPrefillObserver);
        }
        ocrCategoryPrefillSource = null;
        ocrCategoryPrefillObserver = null;
    }

    @Nullable
    private String resolveCategoryIdFromHint(@Nullable List<CategoryEntity> categories, @Nullable String categoryHint) {
        if (categories == null || categories.isEmpty() || TextUtils.isEmpty(categoryHint)) {
            return null;
        }
        String normalizedHint = normalizeLookupValue(categoryHint);
        if (TextUtils.isEmpty(normalizedHint)) {
            return null;
        }

        CategoryEntity partialCandidate = null;
        for (CategoryEntity category : categories) {
            String normalizedName = normalizeLookupValue(category.getName());
            if (normalizedHint.equals(normalizedName)) {
                return category.getId();
            }
            boolean overlaps = normalizedName.contains(normalizedHint)
                    || normalizedHint.contains(normalizedName);
            if (!overlaps) {
                continue;
            }
            if (partialCandidate != null && !partialCandidate.getId().equals(category.getId())) {
                return null;
            }
            partialCandidate = category;
        }
        return partialCandidate != null ? partialCandidate.getId() : null;
    }

    @NonNull
    private String normalizeLookupValue(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void updateCategorySelectionUi() {
        binding.tvCategoryValue.setText(resolveCategorySelectionLabel());

        int iconRes = IconProvider.resolveCategoryIconByType(
                requireContext(),
                selectedIconName,
                getEffectiveTypeForUi());
        binding.ivCategoryIcon.setImageResource(iconRes);
    }

    private String getEffectiveTypeForUi() {
        if (selectedDebtType != null) {
            switch (selectedDebtType) {
                case BORROW:
                case DEBT_COLLECTION:
                    return Constants.TYPE_INCOME;
                case LEND:
                case REPAYMENT:
                default:
                    return Constants.TYPE_EXPENSE;
            }
        }
        return currentType;
    }

    @NonNull
    private String resolvePickerTransactionType() {
        return isDebtTabSelected ? TransactionCategoryPickerViewModel.TYPE_DEBT : currentType;
    }

    private void addExistingEditWallet(@NonNull List<WalletEntity> displayWallets) {
        if (transactionId == null || selectedWalletId == null || containsWallet(displayWallets, selectedWalletId)) {
            return;
        }
        for (WalletEntity wallet : allWalletList) {
            if (selectedWalletId.equals(wallet.getId())) {
                displayWallets.add(wallet);
                return;
            }
        }
    }

    private boolean containsWallet(@NonNull List<WalletEntity> wallets, @NonNull String walletId) {
        for (WalletEntity wallet : wallets) {
            if (walletId.equals(wallet.getId())) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private List<String> buildWalletNames(@NonNull List<WalletEntity> wallets) {
        List<String> walletNames = new ArrayList<>();
        for (WalletEntity wallet : wallets) {
            walletNames.add(wallet.getName());
        }
        return walletNames;
    }

    private void observeCategoryIdResult(@NonNull NavBackStackEntry backStackEntry) {
        backStackEntry.getSavedStateHandle()
                .getLiveData(TransactionCategoryPickerFragment.RESULT_CATEGORY_ID)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    selectedCategoryId = value.toString();
                    resetCategoryIdentity();
                    selectedDebtType = null;
                    isDebtTabSelected = false;
                    loadSelectedCategory();
                    backStackEntry.getSavedStateHandle()
                            .set(TransactionCategoryPickerFragment.RESULT_CATEGORY_ID, null);
                });
    }

    private void observeCategoryTypeResult(@NonNull NavBackStackEntry backStackEntry) {
        backStackEntry.getSavedStateHandle()
                .getLiveData(TransactionCategoryPickerFragment.RESULT_CATEGORY_TYPE)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    applySelectedType(value.toString());
                    backStackEntry.getSavedStateHandle()
                            .set(TransactionCategoryPickerFragment.RESULT_CATEGORY_TYPE, null);
                });
    }

    private void observeDebtTypeResult(@NonNull NavBackStackEntry backStackEntry) {
        backStackEntry.getSavedStateHandle()
                .getLiveData(TransactionCategoryPickerFragment.RESULT_DEBT_TYPE)
                .observe(getViewLifecycleOwner(), value -> {
                    if (value == null) {
                        return;
                    }
                    applyDebtSelection(DebtType.valueOf(value.toString()));
                    backStackEntry.getSavedStateHandle()
                            .set(TransactionCategoryPickerFragment.RESULT_DEBT_TYPE, null);
                });
    }

    private void resetCategoryIdentity() {
        selectedCategoryName = null;
        isManualIcon = false;
        selectedIconName = null;
    }

    private void applySelectedType(@NonNull String type) {
        isLoadingEdit = true;
        if (TransactionCategoryPickerViewModel.TYPE_DEBT.equals(type)) {
            isDebtTabSelected = true;
            binding.toggleType.check(R.id.btn_debt);
        } else {
            isDebtTabSelected = false;
            currentType = type;
            binding.toggleType.check(resolveTypeToggleButtonId(type));
        }
        updateAmountAccent();
        updateTypeToggleAppearance();
        isLoadingEdit = false;
    }

    private void applyDebtSelection(@NonNull DebtType debtType) {
        selectedDebtType = debtType;
        selectedCategoryId = null;
        selectedCategoryName = null;
        isManualIcon = false;
        selectedIconName = null;
        isDebtTabSelected = true;
        switch (debtType) {
            case BORROW:
            case DEBT_COLLECTION:
                currentType = Constants.TYPE_INCOME;
                break;
            case LEND:
            case REPAYMENT:
            default:
                currentType = Constants.TYPE_EXPENSE;
                break;
        }
        isLoadingEdit = true;
        binding.toggleType.check(R.id.btn_debt);
        isLoadingEdit = false;
        updateAmountAccent();
        updateTypeToggleAppearance();
        updateCategorySelectionUi();
        updateDebtFieldsVisibility();
        // Show debt picker when the user picks a cashback type
        if (debtType == DebtType.DEBT_COLLECTION || debtType == DebtType.REPAYMENT) {
            showDebtPickerDialog(debtType);
        }
    }

    /**
     * One-shot dialog that lets the user pick which ongoing debt they want to settle.
     * DEBT_COLLECTION → shows LEND debts (money I lent out, now collecting back).
     * REPAYMENT      → shows BORROW debts (money I borrowed, now paying back).
     */
    private void showDebtPickerDialog(@NonNull DebtType debtType) {
        String queryType = (debtType == DebtType.DEBT_COLLECTION)
                ? DebtType.LEND.name()
                : DebtType.BORROW.name();

        LiveData<List<DebtEntity>> source = debtViewModel.getOngoingDebtsByType(queryType);
        Observer<List<DebtEntity>>[] holderRef = new Observer[1];
        holderRef[0] = debts -> {
            source.removeObserver(holderRef[0]); // one-shot
            if (!isAdded()) return;

            if (debts == null || debts.isEmpty()) {
                Toast.makeText(requireContext(), R.string.debt_empty, Toast.LENGTH_SHORT).show();
                // revert selection
                selectedDebtType = null;
                selectedLinkedDebtId = null;
                updateCategorySelectionUi();
                updateDebtFieldsVisibility();
                return;
            }

            // Build a BottomSheetDialog with a RecyclerView using item_debt.xml
            BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
            View sheetView = LayoutInflater.from(requireContext())
                    .inflate(R.layout.dialog_debt_picker, null, false);
            RecyclerView rv = sheetView.findViewById(R.id.rv_debt_picker);
            rv.setLayoutManager(new LinearLayoutManager(requireContext()));
            rv.setAdapter(new RecyclerView.Adapter<RecyclerView.ViewHolder>() {
                @NonNull
                @Override
                public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    ItemDebtBinding b = ItemDebtBinding.inflate(
                            LayoutInflater.from(parent.getContext()), parent, false);
                    return new RecyclerView.ViewHolder(b.getRoot()) {};
                }

                @Override
                public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                    DebtEntity d = debts.get(position);
                    ItemDebtBinding b = ItemDebtBinding.bind(holder.itemView);

                    // Avatar letter & Color
                    String name = d.getPersonName();
                    b.tvAvatarLetter.setText(name != null && !name.isEmpty()
                            ? String.valueOf(name.charAt(0)).toUpperCase(java.util.Locale.getDefault())
                            : "?");
                    
                    int badgeColor = ContextCompat.getColor(
                            requireContext(),
                            DebtType.LEND.name().equals(d.getType())
                                    ? R.color.transaction_expense_accent
                                    : R.color.transfer_blue);
                    View avatarBg = (View) b.tvAvatarLetter.getParent();
                    avatarBg.setBackgroundTintList(ColorStateList.valueOf(badgeColor));

                    // Person name
                    b.tvPersonName.setText(name);

                    // Due date
                    Long due = d.getDueDate();
                    b.tvDueDate.setText(due != null && due > 0
                            ? com.group10.moneymate.utils.DateUtils.formatDate(due)
                            : getString(R.string.debt_no_due_date));

                    // Remaining & total amounts
                    b.tvRemainingAmount.setText(
                            CurrencyFormatter.format(d.getRemainingAmount(), "VND"));
                    b.tvTotalAmount.setText(
                            CurrencyFormatter.format(d.getAmount(), "VND"));

                    // Click to select
                    holder.itemView.setOnClickListener(v -> {
                        selectedLinkedDebtId = d.getId();
                        selectedCategoryName = d.getPersonName();
                        updateCategorySelectionUi();
                        sheet.dismiss();
                    });
                }

                @Override
                public int getItemCount() {
                    return debts.size();
                }
            });

            sheet.setContentView(sheetView);
            sheet.setOnCancelListener(dialogInterface -> {
                selectedDebtType = null;
                selectedLinkedDebtId = null;
                selectedCategoryName = null;
                updateCategorySelectionUi();
                updateDebtFieldsVisibility();
            });
            sheet.show();
        };
        source.observe(getViewLifecycleOwner(), holderRef[0]);
    }

    private int resolveTypeToggleButtonId(@NonNull String type) {
        return Constants.TYPE_INCOME.equals(type) ? R.id.btn_income : R.id.btn_expense;
    }

    @NonNull
    private String resolveCategorySelectionLabel() {
        if (selectedDebtType != null) {
            switch (selectedDebtType) {
                case LEND:
                    return getString(R.string.debt_type_lend);
                case BORROW:
                    return getString(R.string.debt_type_borrow);
                case DEBT_COLLECTION:
                    return selectedCategoryName != null
                            ? getString(R.string.debt_type_collection) + " — " + selectedCategoryName
                            : getString(R.string.debt_type_collection);
                case REPAYMENT:
                    return selectedCategoryName != null
                            ? getString(R.string.debt_type_repayment) + " — " + selectedCategoryName
                            : getString(R.string.debt_type_repayment);
            }
        }
        if (selectedCategoryName != null) {
            return selectedCategoryName;
        }
        return getString(R.string.category_pick_placeholder);
    }

    private void updateDebtFieldsVisibility() {
        if (binding == null) {
            return;
        }
        boolean isNewDebt = selectedDebtType == DebtType.LEND || selectedDebtType == DebtType.BORROW;
        binding.layoutDebtPerson.setVisibility(isNewDebt ? View.VISIBLE : View.GONE);
        binding.layoutDebtDueDate.setVisibility(isNewDebt ? View.VISIBLE : View.GONE);
    }

    // ─── Load existing transaction (Edit mode) ────────────────────────────────

    private void loadExistingTransaction() {
        viewModel.getTransactionById(transactionId).observe(getViewLifecycleOwner(), transaction -> {
            if (transaction == null)
                return;
            // Chỉ populate lần đầu
            if (originalTransaction != null)
                return;
            originalTransaction = transaction;
            isLoadingEdit = true;

            binding.etAmount.setText(CurrencyFormatter.formatInputAmount((long) transaction.getAmount()));
            binding.etNote.setText(TextUtils.isEmpty(transaction.getNote()) ? "" : transaction.getNote());
            selectedTimestamp = transaction.getTimestamp();
            binding.etDate.setText(DateUtils.formatDate(selectedTimestamp));
            selectedCategoryId = transaction.getCategoryId();
            selectedWalletId = transaction.getWalletId();
            currentType = transaction.getType();
            isDebtTabSelected = false;

            if (Constants.TYPE_INCOME.equals(currentType)) {
                binding.toggleType.check(R.id.btn_income);
            } else {
                binding.toggleType.check(R.id.btn_expense);
            }
            updateAmountAccent();
            updateTypeToggleAppearance();
            loadSelectedCategory();
            refreshWalletDropdown();
            applySelectedWalletSelection();
            isLoadingEdit = false;
        });
    }

    // ─── Save ─────────────────────────────────────────────────────────────────

    private void setupSaveButton() {
        binding.btnSave.setOnClickListener(v -> {
            if (isSaving) {
                return;
            }
            if (!validateForm())
                return;

            String amountStr = Objects.requireNonNull(binding.etAmount.getText()).toString().trim();
            double amount = CurrencyFormatter.parseFormattedAmount(amountStr);
            String note = binding.etNote.getText() != null
                    ? binding.etNote.getText().toString().trim()
                    : "";

            String walletId = selectedWalletId;
            if (walletId == null) {
                String walletName = binding.dropdownWallet.getText().toString().trim();
                for (WalletEntity w : walletList) {
                    if (w.getName().equals(walletName)) {
                        walletId = w.getId();
                        break;
                    }
                }
            }

            MoneyMateApplication app = (MoneyMateApplication) requireActivity().getApplication();
            String uid = app.getAppContainer().authRepository.getCurrentUserId();
            if (TextUtils.isEmpty(uid)) {
                Toast.makeText(requireContext(), R.string.error_auth_required, Toast.LENGTH_SHORT).show();
                return;
            }

            String effectiveType = getEffectiveTypeForUi();
            startSavingUi();

            if (originalTransaction != null) {
                // Edit mode
                TransactionEntity updated = new TransactionEntity();
                updated.setId(originalTransaction.getId());
                updated.setUserId(uid);
                updated.setWalletId(walletId);
                updated.setCategoryId(selectedCategoryId);
                updated.setAmount(amount);
                updated.setType(effectiveType);
                updated.setNote(note);
                updated.setTimestamp(selectedTimestamp);
                updated.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                updated.setUpdatedAt(System.currentTimeMillis());
                viewModel.updateTransaction(updated,
                        new com.group10.moneymate.data.repository.TransactionRepository.WriteCallback() {
                            @Override
                            public void onSuccess() {
                                savedTransactionId = updated.getId();
                                savedTransactionChangeType = CHANGE_TYPE_UPDATE;
                                finishSavingAndNavigateUp();
                            }

                            @Override
                            public void onError(@NonNull Throwable throwable) {
                                stopSavingUi();
                                if (isAdded()) {
                                    Toast.makeText(requireContext(), R.string.common_save_failed, Toast.LENGTH_SHORT)
                                            .show();
                                }
                            }
                        });
            } else {
                // Add mode
                TransactionEntity transaction = new TransactionEntity();
                transaction.setId(UUID.randomUUID().toString());
                transaction.setUserId(uid);
                transaction.setWalletId(walletId);
                transaction.setCategoryId(selectedCategoryId);
                transaction.setAmount(amount);
                transaction.setType(effectiveType);
                transaction.setNote(note);
                transaction.setTimestamp(selectedTimestamp);
                transaction.setImagePath(selectedReceiptImagePath);
                transaction.setDeleted(false);
                transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
                transaction.setUpdatedAt(System.currentTimeMillis());

                // Debt creation path: LEND or BORROW creates a debt + transaction atomically
                if (selectedDebtType == DebtType.LEND || selectedDebtType == DebtType.BORROW) {
                    performDebtCreation(uid, transaction, amount);
                } else if ((selectedDebtType == DebtType.DEBT_COLLECTION
                        || selectedDebtType == DebtType.REPAYMENT)
                        && selectedLinkedDebtId != null) {
                    // Cashback: settle an existing debt with this transaction
                    performCashbackFromTransaction(transaction, amount);
                } else {
                    maybeInsertOcrTransaction(transaction);
                }
            }
        });
    }

    private void performDebtCreation(@NonNull String uid,
                                      @NonNull TransactionEntity transaction,
                                      double amount) {
        String personName = binding.etDebtPersonName.getText() != null
                ? binding.etDebtPersonName.getText().toString().trim()
                : "";
        if (personName.isEmpty()) {
            stopSavingUi();
            Toast.makeText(requireContext(), R.string.debt_error_person_required, Toast.LENGTH_SHORT).show();
            return;
        }

        com.group10.moneymate.data.local.entity.DebtEntity debt =
                new com.group10.moneymate.data.local.entity.DebtEntity();
        debt.setUserId(uid);
        debt.setPersonName(personName);
        debt.setType(selectedDebtType.name());
        debt.setAmount(amount);
        debt.setRemainingAmount(amount);
        debt.setStatus(com.group10.moneymate.models.DebtStatus.ACTIVE.name());

        // Parse due date if provided
        String dueDateText = binding.etDebtDueDate.getText() != null
                ? binding.etDebtDueDate.getText().toString().trim()
                : "";
        if (!dueDateText.isEmpty()) {
            debt.setDueDate(selectedDebtDueTimestamp);
        }

        // Note from transaction
        String txNote = transaction.getNote();
        debt.setNote(txNote);

        // Use DebtViewModel to create atomically
        debtViewModel.createDebtWithTransaction(debt, transaction,
                new DebtRepository.WriteCallback() {
                    @Override
                    public void onSuccess() {
                        savedTransactionId = transaction.getId();
                        savedTransactionChangeType = CHANGE_TYPE_INSERT;
                        if (isAdded()) {
                            Toast.makeText(requireContext(), R.string.debt_created_success,
                                    Toast.LENGTH_SHORT).show();
                        }
                        finishSavingAndNavigateUp();
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        stopSavingUi();
                        if (isAdded()) {
                            Toast.makeText(requireContext(), R.string.common_save_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void performCashbackFromTransaction(@NonNull TransactionEntity transaction, double amount) {
        debtViewModel.createCashbackTransaction(selectedLinkedDebtId, amount, transaction,
                new DebtRepository.WriteCallback() {
                    @Override
                    public void onSuccess() {
                        savedTransactionId = transaction.getId();
                        savedTransactionChangeType = CHANGE_TYPE_INSERT;
                        if (isAdded()) {
                            Toast.makeText(requireContext(), R.string.debt_cashback_success,
                                    Toast.LENGTH_SHORT).show();
                        }
                        finishSavingAndNavigateUp();
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        stopSavingUi();
                        if (isAdded()) {
                            Toast.makeText(requireContext(), R.string.common_save_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void maybeInsertOcrTransaction(@NonNull TransactionEntity transaction) {
        if (TextUtils.isEmpty(ocrDraftId)) {
            performInsertTransaction(transaction);
            return;
        }
        viewModel.checkOcrDuplicateCandidates(
                Collections.singletonList(buildDuplicateCandidate(transaction)),
                new TransactionRepository.DuplicateCheckCallback() {
                    @Override
                    public void onCompleted(@NonNull TransactionRepository.DuplicateCheckResult result) {
                        if (!isAdded()) {
                            stopSavingUi();
                            return;
                        }
                        if (!result.hasSuspectedDuplicates()) {
                            performInsertTransaction(transaction);
                            return;
                        }
                        stopSavingUi();
                        showDuplicateConfirmationDialog(
                                getString(R.string.transaction_scan_duplicate_message_single),
                                () -> {
                                    startSavingUi();
                                    performInsertTransaction(transaction);
                                });
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        stopSavingUi();
                        if (isAdded()) {
                            Toast.makeText(requireContext(), R.string.transaction_scan_duplicate_check_failed,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void performInsertTransaction(@NonNull TransactionEntity transaction) {
        viewModel.insertTransaction(transaction, new TransactionRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                savedTransactionId = transaction.getId();
                savedTransactionChangeType = CHANGE_TYPE_INSERT;
                finishSavingAndNavigateUp();
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                stopSavingUi();
                if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.common_save_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    @NonNull
    private TransactionRepository.OcrDuplicateCandidate buildDuplicateCandidate(
            @NonNull TransactionEntity transaction) {
        String candidateId = !TextUtils.isEmpty(ocrDraftId)
                ? ocrDraftId
                : transaction.getId();
        return new TransactionRepository.OcrDuplicateCandidate(
                candidateId,
                selectedReceiptImagePath,
                transaction.getAmount(),
                transaction.getTimestamp(),
                transaction.getNote());
    }

    private void showDuplicateConfirmationDialog(@NonNull String message,
            @NonNull Runnable onConfirm) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.transaction_scan_duplicate_title)
                .setMessage(message)
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.transaction_scan_duplicate_confirm_save,
                        (dialog, which) -> onConfirm.run())
                .show();
    }

    private void startSavingUi() {
        isSaving = true;
        updateActionState();
        loadingHelper.show(this, R.string.common_saving);
    }

    private void stopSavingUi() {
        isSaving = false;
        updateActionState();
        loadingHelper.dismiss();
    }

    private void updateActionState() {
        if (binding == null) {
            return;
        }
        boolean enabled = !isSaving && !isPreparingReceiptImage;
        binding.btnSave.setEnabled(enabled);
        binding.topAppBar.setEnabled(enabled);
        binding.fabScanTransaction.setEnabled(enabled);
        updateScanEntryVisibility();
    }

    private void updateScanEntryVisibility() {
        if (binding == null) {
            return;
        }
        boolean shouldShowScanEntry = transactionId == null && TextUtils.isEmpty(ocrDraftId);
        binding.fabScanTransaction.setVisibility(shouldShowScanEntry ? View.VISIBLE : View.GONE);
    }

    private void finishSavingAndNavigateUp() {
        if (binding == null || !isAdded()) {
            loadingHelper.dismiss();
            return;
        }
        dispatchOcrDraftSavedResultIfNeeded();
        dispatchTransactionChangedResultIfNeeded();
        stopSavingUi();
        Navigation.findNavController(binding.getRoot()).navigateUp();
    }

    private void dispatchOcrDraftSavedResultIfNeeded() {
        if (TextUtils.isEmpty(ocrDraftId)) {
            return;
        }
        Bundle result = new Bundle();
        result.putString(RESULT_KEY_OCR_DRAFT_ID, ocrDraftId);
        if (originalTransaction != null) {
            result.putString(RESULT_KEY_SAVED_TRANSACTION_ID, originalTransaction.getId());
        }
        getParentFragmentManager().setFragmentResult(REQUEST_KEY_OCR_DRAFT_SAVED, result);
    }

    private void dispatchTransactionChangedResultIfNeeded() {
        if (TextUtils.isEmpty(savedTransactionId)) {
            return;
        }
        NavController navController = Navigation.findNavController(binding.getRoot());
        NavBackStackEntry previous = navController.getPreviousBackStackEntry();
        if (previous == null) {
            return;
        }
        previous.getSavedStateHandle().set(RESULT_TRANSACTION_CHANGED, true);
        previous.getSavedStateHandle().set(RESULT_TRANSACTION_CHANGED_ID, savedTransactionId);
        previous.getSavedStateHandle().set(RESULT_TRANSACTION_CHANGE_TYPE, savedTransactionChangeType);
    }

    // ─── Validation ───────────────────────────────────────────────────────────

    private boolean validateForm() {
        String amountStr = binding.etAmount.getText() != null
                ? binding.etAmount.getText().toString().trim()
                : "";

        if (TextUtils.isEmpty(amountStr)) {
            Toast.makeText(requireContext(), R.string.error_amount_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        double amount;
        try {
            amount = CurrencyFormatter.parseFormattedAmount(amountStr);
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), R.string.error_amount_invalid, Toast.LENGTH_SHORT).show();
            return false;
        }

        if (amount <= 0) {
            Toast.makeText(requireContext(), R.string.error_amount_positive, Toast.LENGTH_SHORT).show();
            return false;
        }

        if (selectedCategoryId == null && selectedDebtType == null) {
            Toast.makeText(requireContext(), R.string.error_category_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        String walletName = binding.dropdownWallet.getText() != null
                ? binding.dropdownWallet.getText().toString().trim()
                : "";
        if (TextUtils.isEmpty(walletName)) {
            Toast.makeText(requireContext(), R.string.error_wallet_required, Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    @Override
    public void onDestroyView() {
        if (selectedCategorySource != null && selectedCategoryObserver != null) {
            selectedCategorySource.removeObserver(selectedCategoryObserver);
        }
        clearOcrCategoryPrefillObserver();
        clearReceiptScanWorkObservation();
        dismissScanSourceDialog();
        loadingHelper.dismiss();
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onDestroy() {
        receiptImageExecutor.shutdownNow();
        super.onDestroy();
    }
}
