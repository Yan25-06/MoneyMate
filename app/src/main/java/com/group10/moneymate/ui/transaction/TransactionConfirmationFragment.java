package com.group10.moneymate.ui.transaction;

import android.content.res.ColorStateList;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDirections;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.group10.moneymate.R;
import com.group10.moneymate.ai.receipt.ReceiptScanContract;
import com.group10.moneymate.data.local.entity.CategoryEntity;
import com.group10.moneymate.data.local.entity.TransactionEntity;
import com.group10.moneymate.data.local.entity.WalletEntity;
import com.group10.moneymate.data.repository.TransactionRepository;
import com.group10.moneymate.databinding.FragmentTransactionConfirmationBinding;
import com.group10.moneymate.models.SyncStatus;
import com.group10.moneymate.ui.transaction.adapter.ReceiptTransactionAdapter;
import com.group10.moneymate.utils.Constants;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.DateUtils;
import com.group10.moneymate.utils.FileUtils;
import com.group10.moneymate.utils.LoadingHelper;
import com.group10.moneymate.workers.AIReceiptScannerWorker;

import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TransactionConfirmationFragment extends Fragment {

    private static final int HIGH_CONFIDENCE = 2;
    private static final int MAX_THUMBNAIL_SIZE_PX = 720;
    private static final int MAX_PREVIEW_SIZE_PX = 1800;
    private static final float PREVIEW_MAX_SCALE_MULTIPLIER = 4f;
    private static final float PREVIEW_DOUBLE_TAP_SCALE_MULTIPLIER = 2f;

    private FragmentTransactionConfirmationBinding binding;
    private TransactionViewModel viewModel;
    private ReceiptTransactionAdapter adapter;
    private final List<ReceiptTransactionAdapter.PendingReceiptItem> pendingItems = new ArrayList<>();
    private final List<WalletEntity> activeWallets = new ArrayList<>();
    private final List<CategoryEntity> availableExpenseCategories = new ArrayList<>();
    private final LoadingHelper loadingHelper = new LoadingHelper();

    @Nullable
    private LiveData<List<CategoryEntity>> expenseCategoriesSource;
    @Nullable
    private Observer<List<CategoryEntity>> expenseCategoriesObserver;
    @Nullable
    private TransactionConfirmationFragmentArgs confirmationArgs;
    @Nullable
    private String saveAllWalletId;
    private boolean isSavingAll;
    @Nullable
    private String currentImagePath;
    @Nullable
    private String currentImageUri;
    @Nullable
    private String currentAmount;
    @Nullable
    private String currentMerchant;
    @Nullable
    private String currentCategoryHint;
    @Nullable
    private String currentNoteHint;
    @Nullable
    private String currentItemsJson;
    @Nullable
    private String currentProcessingSource;
    @Nullable
    private String currentProcessingDetail;
    @Nullable
    private String currentImageInputSource;
    private long currentTimestamp = -1L;
    private int currentConfidence;
    @Nullable
    private LiveData<WorkInfo> receiptScanWorkInfoSource;
    @Nullable
    private Observer<WorkInfo> receiptScanWorkInfoObserver;
    @Nullable
    private UUID currentReceiptScanWorkId;
    private ActivityResultLauncher<String> galleryPickerLauncher;
    private final ExecutorService receiptImageExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        galleryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                this::handleGalleryImageSelected
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTransactionConfirmationBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TransactionViewModel.class);
        confirmationArgs = TransactionConfirmationFragmentArgs.fromBundle(
                getArguments() != null ? getArguments() : new Bundle()
        );

        setupToolbar();
        setupRecyclerView();
        setupActions();
        observeDraftSaveResults();
        observeCameraCaptureResults();
        observeSaveDependencies();
        bindCurrentStateFromArgs(confirmationArgs);
        renderCurrentState();
    }

    private void setupToolbar() {
        binding.btnConfirmationBack.setOnClickListener(v -> {
            if (isSavingAll) {
                return;
            }
            Navigation.findNavController(v).navigateUp();
        });
    }

    private void setupRecyclerView() {
        adapter = new ReceiptTransactionAdapter();
        adapter.setOnReceiptItemClickListener(this::openDraftInAddEditScreen);
        binding.rvReceiptTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvReceiptTransactions.setAdapter(adapter);
    }

    private void setupActions() {
        binding.btnSaveAllReceiptTransactions.setOnClickListener(v -> attemptSaveAll());
        binding.btnConfirmationRescan.setOnClickListener(v -> handleRescanAction());
    }

    private void observeDraftSaveResults() {
        getParentFragmentManager().setFragmentResultListener(
                AddEditTransactionFragment.REQUEST_KEY_OCR_DRAFT_SAVED,
                getViewLifecycleOwner(),
                (requestKey, bundle) -> {
                    String draftId = bundle.getString(AddEditTransactionFragment.RESULT_KEY_OCR_DRAFT_ID);
                    if (!TextUtils.isEmpty(draftId)) {
                        removePendingItem(draftId);
                    }
                    getParentFragmentManager().clearFragmentResult(AddEditTransactionFragment.REQUEST_KEY_OCR_DRAFT_SAVED);
                }
        );
    }

    private void observeSaveDependencies() {
        viewModel.getActiveWallets().observe(getViewLifecycleOwner(), wallets -> {
            activeWallets.clear();
            if (wallets != null) {
                activeWallets.addAll(wallets);
            }
            refreshSaveAllWalletResolution();
        });
    }

    private void refreshSaveAllWalletResolution() {
        if (activeWallets.size() == 1) {
            saveAllWalletId = activeWallets.get(0).getId();
            observeExpenseCategories(saveAllWalletId);
        } else {
            saveAllWalletId = null;
            clearExpenseCategoryObserver();
            availableExpenseCategories.clear();
        }

        if (binding != null) {
            renderCurrentState();
        }
    }

    private void observeExpenseCategories(@Nullable String walletId) {
        clearExpenseCategoryObserver();
        expenseCategoriesSource = viewModel.getExpenseCategoriesForWallet(walletId);
        expenseCategoriesObserver = categories -> {
            availableExpenseCategories.clear();
            if (categories != null) {
                availableExpenseCategories.addAll(categories);
            }
            if (binding != null) {
                renderCurrentState();
            }
        };
        expenseCategoriesSource.observe(getViewLifecycleOwner(), expenseCategoriesObserver);
    }

    private void clearExpenseCategoryObserver() {
        if (expenseCategoriesSource != null && expenseCategoriesObserver != null) {
            expenseCategoriesSource.removeObserver(expenseCategoriesObserver);
        }
        expenseCategoriesSource = null;
        expenseCategoriesObserver = null;
    }

    private void bindCurrentStateFromArgs(@NonNull TransactionConfirmationFragmentArgs args) {
        currentImagePath = args.getImagePath();
        currentImageUri = null;
        currentAmount = args.getAmount();
        currentTimestamp = args.getTimestamp();
        currentMerchant = args.getMerchant();
        currentCategoryHint = args.getCategoryHint();
        currentNoteHint = args.getNoteHint();
        currentItemsJson = args.getItemsJson();
        currentProcessingSource = args.getProcessingSource();
        currentProcessingDetail = args.getProcessingDetail();
        currentImageInputSource = args.getImageInputSource();
        currentConfidence = args.getConfidence();
    }

    private void renderCurrentState() {
        pendingItems.clear();
        pendingItems.addAll(buildPendingItems());
        adapter.submitList(new ArrayList<>(pendingItems));

        updateThumbnail(currentImagePath, currentImageUri);
        updateProcessingSource();
        updateRescanAction();
        updateSummary();
        updateSaveWalletLabel();
        updateEmptyState();
    }

    @NonNull
    private List<ReceiptTransactionAdapter.PendingReceiptItem> buildPendingItems() {
        List<ReceiptTransactionAdapter.PendingReceiptItem> fallbackItems = new ArrayList<>();
        fallbackItems.add(createPendingItem(
                "ocr_root_0",
                currentAmount,
                resolveFallbackNote(currentNoteHint, currentMerchant),
                currentCategoryHint,
                currentTimestamp,
                currentConfidence
        ));
        return fallbackItems;
    }

    @NonNull
    private ReceiptTransactionAdapter.PendingReceiptItem createPendingItem(@NonNull String draftId,
                                                                           @Nullable String amountRaw,
                                                                           @Nullable String note,
                                                                           @Nullable String categoryHint,
                                                                           long timestamp,
                                                                           int confidence) {
        String safeAmountRaw = amountRaw == null ? "" : amountRaw.trim();
        String safeCategoryHint = categoryHint == null ? "" : categoryHint.trim();
        String safeNote = note == null || note.trim().isEmpty()
                ? getString(R.string.transaction_scan_confirmation_note_fallback)
                : note.trim();
        long safeTimestamp = timestamp > 0L ? timestamp : ReceiptScanContract.UNKNOWN_TIMESTAMP;

        return new ReceiptTransactionAdapter.PendingReceiptItem(
                draftId,
                safeAmountRaw,
                resolveAmountLabel(safeAmountRaw),
                safeNote,
                safeCategoryHint,
                resolveCategoryLabel(safeCategoryHint),
                safeTimestamp,
                resolveDateLabel(timestamp),
                resolvePendingWalletLabel(),
                confidence,
                buildWarningLabel(safeAmountRaw, safeCategoryHint, confidence)
        );
    }

    @NonNull
    private String resolveAmountLabel(@NonNull String amountRaw) {
        if (TextUtils.isEmpty(amountRaw)) {
            return getString(R.string.transaction_scan_confirmation_amount_missing);
        }
        try {
            double amount = Double.parseDouble(amountRaw);
            return CurrencyFormatter.format(amount, getString(R.string.currency_vnd));
        } catch (NumberFormatException exception) {
            return amountRaw;
        }
    }

    @NonNull
    private String resolveCategoryLabel(@NonNull String categoryHint) {
        if (categoryHint.isEmpty()) {
            return getString(R.string.transaction_scan_confirmation_category_missing_label);
        }
        return categoryHint;
    }

    @NonNull
    private String resolveDateLabel(long timestamp) {
        if (timestamp <= 0L) {
            return getString(R.string.transaction_scan_confirmation_date_missing);
        }
        return DateUtils.formatDate(timestamp);
    }

    @NonNull
    private String buildWarningLabel(@NonNull String amountRaw,
                                     @NonNull String categoryHint,
                                     int confidence) {
        List<String> warnings = new ArrayList<>();
        if (TextUtils.isEmpty(amountRaw)) {
            warnings.add(getString(R.string.transaction_scan_confirmation_warning_missing_amount));
        }
        if (TextUtils.isEmpty(categoryHint)) {
            warnings.add(getString(R.string.transaction_scan_confirmation_warning_missing_category));
        }
        if (confidence < HIGH_CONFIDENCE) {
            warnings.add(getString(R.string.transaction_scan_confirmation_warning_low_confidence));
        }
        return warnings.isEmpty()
                ? ""
                : TextUtils.join(getString(R.string.transaction_scan_confirmation_warning_separator), warnings);
    }

    @NonNull
    private String resolveFallbackNote(@Nullable String noteHint, @Nullable String merchant) {
        if (!TextUtils.isEmpty(noteHint)) {
            return noteHint.trim();
        }
        if (!TextUtils.isEmpty(merchant)) {
            return merchant.trim();
        }
        return getString(R.string.transaction_scan_confirmation_note_fallback);
    }

    @NonNull
    private String resolveItemNote(@Nullable String noteHint,
                                   @Nullable String itemName,
                                   @Nullable String overallNoteHint,
                                   @Nullable String merchant) {
        if (!TextUtils.isEmpty(noteHint)) {
            return noteHint.trim();
        }
        if (!TextUtils.isEmpty(itemName)) {
            return itemName.trim();
        }
        return resolveFallbackNote(overallNoteHint, merchant);
    }

    @NonNull
    private String resolveCategoryHint(@Nullable String itemCategoryHint, @Nullable String overallCategoryHint) {
        if (!TextUtils.isEmpty(itemCategoryHint)) {
            return itemCategoryHint.trim();
        }
        if (!TextUtils.isEmpty(overallCategoryHint)) {
            return overallCategoryHint.trim();
        }
        return "";
    }

    private void updateThumbnail(@Nullable String imagePath, @Nullable String imageUri) {
        if (TextUtils.isEmpty(imagePath) && TextUtils.isEmpty(imageUri)) {
            binding.ivReceiptThumbnail.setImageResource(R.drawable.outline_receipt_24);
            binding.ivReceiptThumbnail.setImageTintList(ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.transaction_income_accent)
            ));
            binding.ivReceiptThumbnail.setOnClickListener(null);
            return;
        }

        Bitmap thumbnailBitmap = decodeSampledBitmap(
                imagePath,
                imageUri,
                MAX_THUMBNAIL_SIZE_PX,
                MAX_THUMBNAIL_SIZE_PX
        );
        if (thumbnailBitmap == null) {
            binding.ivReceiptThumbnail.setImageResource(R.drawable.outline_receipt_24);
            binding.ivReceiptThumbnail.setImageTintList(ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.transaction_income_accent)
            ));
            binding.ivReceiptThumbnail.setOnClickListener(null);
            return;
        }
        binding.ivReceiptThumbnail.setImageBitmap(thumbnailBitmap);
        binding.ivReceiptThumbnail.setImageTintList(null);
        binding.ivReceiptThumbnail.setOnClickListener(v -> showZoomedReceiptPreview());
    }

    private void updateSummary() {
        String merchant = currentMerchant;
        if (!TextUtils.isEmpty(merchant)) {
            binding.tvConfirmationSummaryTitle.setText(merchant);
        } else {
            binding.tvConfirmationSummaryTitle.setText(R.string.transaction_scan_confirmation_summary_title);
        }
        binding.tvConfirmationSummarySubtitle.setVisibility(View.GONE);
        binding.tvConfirmationSaveWallet.setVisibility(View.GONE);

        boolean hasPendingWarnings = currentConfidence < HIGH_CONFIDENCE || hasAnyPendingWarning();
        boolean hasSummaryWarning = hasPendingWarnings || saveAllWalletId == null;
        binding.layoutConfirmationSummaryWarning.setVisibility(hasSummaryWarning ? View.VISIBLE : View.GONE);
        binding.tvConfirmationSummaryWarning.setText(resolveSummaryWarningMessage(hasPendingWarnings));
    }

    private void updateProcessingSource() {
        String processingSource = currentProcessingSource;
        String processingDetail = currentProcessingDetail;
        boolean isCloud = ReceiptScanContract.SOURCE_CLOUD.equals(processingSource);
        binding.tvConfirmationProcessingSource.setText(
                resolveProcessingSourceLabel(processingSource, processingDetail)
        );
        binding.tvConfirmationProcessingSource.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(
                        requireContext(),
                        isCloud ? R.color.transaction_income_accent : R.color.budget_warning_orange
                )
        ));
        binding.tvConfirmationProcessingSource.setTextColor(ContextCompat.getColor(
                requireContext(),
                isCloud ? R.color.white : R.color.statistics_text_primary
        ));
    }

    @NonNull
    private String resolveProcessingSourceLabel(@Nullable String processingSource,
                                                @Nullable String processingDetail) {
        if (ReceiptScanContract.SOURCE_CLOUD.equals(processingSource)) {
            return getString(R.string.transaction_scan_confirmation_source_cloud);
        }
        if (ReceiptScanContract.DETAIL_LOCAL_NO_NETWORK.equals(processingDetail)) {
            return getString(R.string.transaction_scan_confirmation_source_local_offline);
        }
        if (ReceiptScanContract.DETAIL_LOCAL_RATE_LIMITED.equals(processingDetail)) {
            return getString(R.string.transaction_scan_confirmation_source_local_rate_limited);
        }
        if (ReceiptScanContract.SOURCE_LOCAL.equals(processingSource)) {
            return getString(R.string.transaction_scan_confirmation_source_local_fallback);
        }
        return getString(R.string.transaction_scan_confirmation_source_unknown);
    }

    @NonNull
    private String resolveSummaryWarningMessage(boolean hasPendingWarnings) {
        if (saveAllWalletId == null) {
            return getString(R.string.transaction_scan_confirmation_wallet_warning);
        }
        if (hasPendingWarnings) {
            return getString(R.string.transaction_scan_confirmation_summary_warning);
        }
        return "";
    }

    private boolean hasAnyPendingWarning() {
        for (ReceiptTransactionAdapter.PendingReceiptItem pendingItem : pendingItems) {
            if (pendingItem.hasWarning()) {
                return true;
            }
        }
        return false;
    }

    private void updateSaveWalletLabel() {
        // Wallet info is rendered inline on each pending item card.
    }

    @NonNull
    private String resolvePendingWalletLabel() {
        if (TextUtils.isEmpty(saveAllWalletId)) {
            return getString(
                    R.string.transaction_scan_confirmation_wallet_inline_label,
                    getString(R.string.transaction_scan_confirmation_wallet_unresolved_value)
            );
        }
        return getString(
                R.string.transaction_scan_confirmation_wallet_inline_label,
                resolveWalletName(saveAllWalletId)
        );
    }

    @NonNull
    private String resolveWalletName(@Nullable String walletId) {
        if (TextUtils.isEmpty(walletId)) {
            return getString(R.string.transaction_scan_confirmation_wallet_unresolved_value);
        }
        for (WalletEntity wallet : activeWallets) {
            if (walletId.equals(wallet.getId())) {
                return wallet.getName();
            }
        }
        return getString(R.string.transaction_scan_confirmation_wallet_unresolved_value);
    }

    private void updateRescanAction() {
        if (AddEditTransactionFragment.IMAGE_INPUT_SOURCE_CAMERA.equals(currentImageInputSource)) {
            binding.btnConfirmationRescan.setText(R.string.transaction_scan_confirmation_rescan_camera);
            binding.btnConfirmationRescan.setIconResource(R.drawable.ic_category_camera);
            return;
        }
        binding.btnConfirmationRescan.setText(R.string.transaction_scan_confirmation_rescan_gallery);
        binding.btnConfirmationRescan.setIconResource(R.drawable.outline_receipt_24);
    }

    private void updateEmptyState() {
        boolean isEmpty = pendingItems.isEmpty();
        binding.layoutConfirmationEmpty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvReceiptTransactions.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        updateActionState();
    }

    private void updateActionState() {
        if (binding == null) {
            return;
        }
        boolean hasPendingItems = !pendingItems.isEmpty();
        binding.btnConfirmationBack.setEnabled(!isSavingAll);
        binding.btnSaveAllReceiptTransactions.setEnabled(!isSavingAll && hasPendingItems);
        binding.btnConfirmationRescan.setEnabled(!isSavingAll);
    }

    private void openDraftInAddEditScreen(@NonNull ReceiptTransactionAdapter.PendingReceiptItem item) {
        if (isSavingAll) {
            return;
        }
        TransactionConfirmationFragmentDirections.ActionTransactionConfirmationFragmentToAddEditTransactionFragment action =
                TransactionConfirmationFragmentDirections.actionTransactionConfirmationFragmentToAddEditTransactionFragment();
        action.setTransactionId(null);
        action.setOcrDraftId(item.getDraftId());
        action.setOcrDraftAmount(item.getAmountRaw());
        action.setOcrDraftNote(item.getNote());
        action.setOcrDraftTimestamp(item.getTimestamp());
        action.setOcrDraftCategoryHint(item.getCategoryHint());
        action.setOcrDraftCategoryId(resolveCategoryId(item.getCategoryHint()));
        action.setOcrDraftImagePath(currentImagePath);
        action.setOcrDraftWalletId(saveAllWalletId);
        Navigation.findNavController(binding.getRoot()).navigate(action);
    }

    private void attemptSaveAll() {
        if (isSavingAll || pendingItems.isEmpty()) {
            return;
        }

        String userId = viewModel.getCurrentUserId();
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(requireContext(), R.string.error_auth_required, Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(saveAllWalletId)) {
            Toast.makeText(requireContext(), R.string.transaction_scan_confirmation_save_all_wallet_unresolved, Toast.LENGTH_SHORT).show();
            return;
        }

        List<TransactionEntity> transactionsToSave = new ArrayList<>();
        int unresolvedCount = 0;
        for (ReceiptTransactionAdapter.PendingReceiptItem pendingItem : pendingItems) {
            TransactionEntity transaction = buildTransactionForSave(pendingItem, userId, saveAllWalletId);
            if (transaction == null) {
                unresolvedCount++;
                continue;
            }
            transactionsToSave.add(transaction);
        }

        if (transactionsToSave.size() != pendingItems.size()) {
            Toast.makeText(
                    requireContext(),
                    getString(R.string.transaction_scan_confirmation_save_all_unresolved_items, unresolvedCount),
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        startSavingAllUi();
        viewModel.checkOcrDuplicateCandidates(
                buildDuplicateCandidates(transactionsToSave),
                new TransactionRepository.DuplicateCheckCallback() {
                    @Override
                    public void onCompleted(@NonNull TransactionRepository.DuplicateCheckResult result) {
                        if (!isAdded()) {
                            stopSavingAllUi();
                            return;
                        }
                        if (!result.hasSuspectedDuplicates()) {
                            performConfirmedSaveAll(transactionsToSave);
                            return;
                        }
                        stopSavingAllUi();
                        showDuplicateConfirmationDialog(result.getSuspectedDuplicates().size(), () -> {
                            startSavingAllUi();
                            performConfirmedSaveAll(transactionsToSave);
                        });
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        stopSavingAllUi();
                        if (isAdded()) {
                            Toast.makeText(requireContext(), R.string.transaction_scan_duplicate_check_failed, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
        );
    }

    @Nullable
    private TransactionEntity buildTransactionForSave(
            @NonNull ReceiptTransactionAdapter.PendingReceiptItem pendingItem,
            @NonNull String userId,
            @NonNull String walletId
    ) {
        double amount = parsePendingAmount(pendingItem.getAmountRaw());
        if (amount <= 0d) {
            return null;
        }

        String categoryId = resolveCategoryId(pendingItem.getCategoryHint());
        if (TextUtils.isEmpty(categoryId)) {
            return null;
        }

        long now = System.currentTimeMillis();
        TransactionEntity transaction = new TransactionEntity();
        transaction.setId(UUID.randomUUID().toString());
        transaction.setUserId(userId);
        transaction.setWalletId(walletId);
        transaction.setCategoryId(categoryId);
        transaction.setAmount(amount);
        transaction.setType(Constants.TYPE_EXPENSE);
        transaction.setNote(pendingItem.getNote());
        transaction.setTimestamp(pendingItem.getTimestamp() > 0L ? pendingItem.getTimestamp() : now);
        transaction.setImagePath(currentImagePath);
        transaction.setCreatedAt(now);
        transaction.setUpdatedAt(now);
        transaction.setDeleted(false);
        transaction.setSyncStatus(SyncStatus.PENDING_UPLOAD);
        return transaction;
    }

    @NonNull
    private List<TransactionRepository.OcrDuplicateCandidate> buildDuplicateCandidates(
            @NonNull List<TransactionEntity> transactionsToSave
    ) {
        List<TransactionRepository.OcrDuplicateCandidate> candidates = new ArrayList<>();
        String imagePath = currentImagePath;
        int limit = Math.min(pendingItems.size(), transactionsToSave.size());
        for (int index = 0; index < limit; index++) {
            ReceiptTransactionAdapter.PendingReceiptItem pendingItem = pendingItems.get(index);
            TransactionEntity transaction = transactionsToSave.get(index);
            candidates.add(new TransactionRepository.OcrDuplicateCandidate(
                    pendingItem.getDraftId(),
                    imagePath,
                    transaction.getAmount(),
                    transaction.getTimestamp(),
                    transaction.getNote()
            ));
        }
        return candidates;
    }

    private double parsePendingAmount(@Nullable String amountRaw) {
        if (TextUtils.isEmpty(amountRaw)) {
            return 0d;
        }
        try {
            return Double.parseDouble(amountRaw.trim());
        } catch (NumberFormatException exception) {
            return 0d;
        }
    }

    @Nullable
    private String resolveCategoryId(@Nullable String categoryHint) {
        if (TextUtils.isEmpty(categoryHint)) {
            return null;
        }
        String normalizedHint = normalizeLookupValue(categoryHint);
        if (normalizedHint.isEmpty()) {
            return null;
        }

        CategoryEntity partialCandidate = null;
        for (CategoryEntity category : availableExpenseCategories) {
            String normalizedName = normalizeLookupValue(category.getName());
            if (normalizedName.equals(normalizedHint)) {
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

    private void startSavingAllUi() {
        isSavingAll = true;
        updateActionState();
        loadingHelper.show(this, R.string.common_saving);
    }

    private void stopSavingAllUi() {
        isSavingAll = false;
        updateActionState();
        loadingHelper.dismiss();
    }

    private void finishSaveAllSuccess(int savedCount) {
        stopSavingAllUi();
        if (!isAdded() || binding == null) {
            return;
        }
        Toast.makeText(
                requireContext(),
                getString(R.string.transaction_scan_confirmation_save_all_success, savedCount),
                Toast.LENGTH_SHORT
        ).show();
        NavController navController = Navigation.findNavController(binding.getRoot());
        boolean popped = navController.popBackStack(R.id.addEditTransactionFragment, true);
        if (!popped) {
            navController.navigateUp();
        }
    }

    private void performConfirmedSaveAll(@NonNull List<TransactionEntity> transactionsToSave) {
        viewModel.insertTransactions(transactionsToSave, new TransactionRepository.WriteCallback() {
            @Override
            public void onSuccess() {
                finishSaveAllSuccess(transactionsToSave.size());
            }

            @Override
            public void onError(@NonNull Throwable throwable) {
                stopSavingAllUi();
                if (isAdded()) {
                    Toast.makeText(requireContext(), R.string.transaction_scan_confirmation_save_all_failed, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showDuplicateConfirmationDialog(int suspectedCount,
                                                 @NonNull Runnable onConfirm) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.transaction_scan_duplicate_title)
                .setMessage(getString(
                        R.string.transaction_scan_duplicate_message_multiple,
                        suspectedCount
                ))
                .setNegativeButton(R.string.common_cancel, null)
                .setPositiveButton(R.string.transaction_scan_duplicate_confirm_save,
                        (dialog, which) -> onConfirm.run())
                .show();
    }

    private void removePendingItem(@NonNull String draftId) {
        Iterator<ReceiptTransactionAdapter.PendingReceiptItem> iterator = pendingItems.iterator();
        boolean removed = false;
        while (iterator.hasNext()) {
            if (draftId.equals(iterator.next().getDraftId())) {
                iterator.remove();
                removed = true;
                break;
            }
        }
        if (!removed) {
            return;
        }
        adapter.submitList(new ArrayList<>(pendingItems));
        updateSummary();
        updateSaveWalletLabel();
        updateEmptyState();
        Toast.makeText(requireContext(), R.string.transaction_scan_confirmation_item_saved_removed, Toast.LENGTH_SHORT).show();
    }

    private void handleRescanAction() {
        if (isSavingAll) {
            return;
        }
        if (AddEditTransactionFragment.IMAGE_INPUT_SOURCE_CAMERA.equals(currentImageInputSource)) {
            NavDirections action =
                    TransactionConfirmationFragmentDirections.actionTransactionConfirmationFragmentToCameraFragment();
            Navigation.findNavController(binding.getRoot()).navigate(action);
            return;
        }
        galleryPickerLauncher.launch("image/*");
    }

    private void handleGalleryImageSelected(@Nullable Uri sourceUri) {
        if (sourceUri == null || !isAdded()) {
            return;
        }
        Executor mainExecutor = ContextCompat.getMainExecutor(requireContext());
        android.content.Context appContext = requireContext().getApplicationContext();
        startRescanUi(R.string.transaction_scan_gallery_loading);
        receiptImageExecutor.execute(() -> {
            try {
                FileUtils.ReceiptImageCopyResult copyResult =
                        FileUtils.copyReceiptImageToInternalStorage(appContext, sourceUri);
                mainExecutor.execute(() -> {
                    stopRescanUi();
                    currentImageInputSource = AddEditTransactionFragment.IMAGE_INPUT_SOURCE_GALLERY;
                    enqueueReceiptScan(copyResult.getInternalPath(), copyResult.getInternalUri());
                });
            } catch (FileUtils.InvalidReceiptImageException exception) {
                mainExecutor.execute(() -> finishRescanError(R.string.transaction_scan_gallery_invalid_image));
            } catch (FileUtils.ReceiptImageTooLargeException exception) {
                mainExecutor.execute(() -> finishRescanError(R.string.transaction_scan_gallery_image_too_large));
            } catch (FileUtils.ReceiptImageStorageException exception) {
                mainExecutor.execute(() -> finishRescanError(R.string.transaction_scan_gallery_copy_failed));
            }
        });
    }

    private void observeCameraCaptureResults() {
        getParentFragmentManager().setFragmentResultListener(
                CameraFragment.REQUEST_KEY_CAPTURED_IMAGE,
                getViewLifecycleOwner(),
                (requestKey, bundle) -> {
                    String imagePath = bundle.getString(CameraFragment.RESULT_KEY_IMAGE_PATH);
                    String imageUri = bundle.getString(CameraFragment.RESULT_KEY_IMAGE_URI);
                    if (!TextUtils.isEmpty(imagePath) && !TextUtils.isEmpty(imageUri)) {
                        currentImageInputSource = AddEditTransactionFragment.IMAGE_INPUT_SOURCE_CAMERA;
                        enqueueReceiptScan(imagePath, imageUri);
                    }
                    getParentFragmentManager().clearFragmentResult(CameraFragment.REQUEST_KEY_CAPTURED_IMAGE);
                }
        );
    }

    private void enqueueReceiptScan(@NonNull String imagePath, @NonNull String imageUri) {
        clearReceiptScanWorkObservation();
        startRescanUi(resolveProcessingMessageRes());
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
            stopRescanUi();

            if (!isAdded() || binding == null) {
                return;
            }

            if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                applyScanOutput(workInfo.getOutputData());
                return;
            }

            Toast.makeText(requireContext(), R.string.transaction_scan_failed, Toast.LENGTH_SHORT).show();
        };
        receiptScanWorkInfoSource.observe(getViewLifecycleOwner(), receiptScanWorkInfoObserver);
    }

    private void applyScanOutput(@NonNull Data outputData) {
        currentImagePath = outputData.getString(ReceiptScanContract.KEY_IMAGE_PATH);
        currentImageUri = outputData.getString(ReceiptScanContract.KEY_IMAGE_URI);
        currentAmount = outputData.getString(ReceiptScanContract.KEY_AMOUNT);
        currentTimestamp = outputData.getLong(
                ReceiptScanContract.KEY_TIMESTAMP,
                ReceiptScanContract.UNKNOWN_TIMESTAMP
        );
        currentMerchant = outputData.getString(ReceiptScanContract.KEY_MERCHANT);
        currentCategoryHint = outputData.getString(ReceiptScanContract.KEY_CATEGORY_HINT);
        currentNoteHint = outputData.getString(ReceiptScanContract.KEY_NOTE_HINT);
        currentItemsJson = outputData.getString(ReceiptScanContract.KEY_ITEMS_JSON);
        currentProcessingSource = outputData.getString(ReceiptScanContract.KEY_PROCESSING_SOURCE);
        currentProcessingDetail = outputData.getString(ReceiptScanContract.KEY_PROCESSING_DETAIL);
        currentConfidence = outputData.getInt(
                ReceiptScanContract.KEY_CONFIDENCE,
                ReceiptScanContract.CONFIDENCE_LOW
        );
        renderCurrentState();
    }

    private void startRescanUi(int messageResId) {
        isSavingAll = true;
        updateActionState();
        loadingHelper.show(this, messageResId);
    }

    private void stopRescanUi() {
        isSavingAll = false;
        updateActionState();
        loadingHelper.dismiss();
    }

    private void finishRescanError(int messageResId) {
        stopRescanUi();
        if (isAdded()) {
            Toast.makeText(requireContext(), messageResId, Toast.LENGTH_SHORT).show();
        }
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

    private void showZoomedReceiptPreview() {
        if (TextUtils.isEmpty(currentImagePath) && TextUtils.isEmpty(currentImageUri)) {
            return;
        }
        Bitmap previewBitmap = decodeSampledBitmap(
                currentImagePath,
                currentImageUri,
                MAX_PREVIEW_SIZE_PX,
                MAX_PREVIEW_SIZE_PX
        );
        if (previewBitmap == null) {
            Toast.makeText(requireContext(), R.string.transaction_scan_confirmation_preview_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }

        Dialog dialog = new Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        View dialogView = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_receipt_preview, null, false);
        FrameLayout root = dialogView.findViewById(R.id.layout_preview_root);
        ImageView imageView = dialogView.findViewById(R.id.iv_receipt_preview);
        ImageButton closeButton = dialogView.findViewById(R.id.btn_preview_close);
        TextView hintText = dialogView.findViewById(R.id.tv_preview_hint);

        root.setOnClickListener(v -> dialog.dismiss());
        hintText.setText(R.string.transaction_scan_confirmation_zoom_hint);

        imageView.setImageBitmap(previewBitmap);
        bindZoomGestures(imageView);
        imageView.setOnClickListener(v -> {
            // Prevent root click from dismissing when the user taps the image.
        });

        closeButton.setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(dialogView);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
        dialog.setOnDismissListener(dialogInterface -> {
            imageView.setImageDrawable(null);
            if (!previewBitmap.isRecycled()) {
                previewBitmap.recycle();
            }
        });
        dialog.show();
    }

    @Nullable
    private Bitmap decodeSampledBitmap(@Nullable String imagePath,
                                       @Nullable String imageUri,
                                       int reqWidth,
                                       int reqHeight) {
        Bitmap fromPath = decodeSampledBitmapFromPath(imagePath, reqWidth, reqHeight);
        if (fromPath != null) {
            return fromPath;
        }
        return decodeSampledBitmapFromUri(imageUri, reqWidth, reqHeight);
    }

    @Nullable
    private Bitmap decodeSampledBitmapFromPath(@Nullable String imagePath, int reqWidth, int reqHeight) {
        if (TextUtils.isEmpty(imagePath)) {
            return null;
        }

        File imageFile = new File(imagePath);
        if (!imageFile.exists()) {
            return null;
        }

        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imagePath, boundsOptions);

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inSampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight);
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap decodedBitmap = BitmapFactory.decodeFile(imagePath, decodeOptions);
        if (decodedBitmap == null) {
            return null;
        }
        return applyExifOrientation(decodedBitmap, imagePath, null);
    }

    @Nullable
    private Bitmap decodeSampledBitmapFromUri(@Nullable String imageUri, int reqWidth, int reqHeight) {
        if (TextUtils.isEmpty(imageUri)) {
            return null;
        }

        try {
            Uri uri = Uri.parse(imageUri);

            BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
            boundsOptions.inJustDecodeBounds = true;
            try (InputStream boundsStream = requireContext().getContentResolver().openInputStream(uri)) {
                if (boundsStream == null) {
                    return null;
                }
                BitmapFactory.decodeStream(boundsStream, null, boundsOptions);
            }

            BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
            decodeOptions.inSampleSize = calculateInSampleSize(boundsOptions, reqWidth, reqHeight);
            decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
            try (InputStream decodeStream = requireContext().getContentResolver().openInputStream(uri)) {
                if (decodeStream == null) {
                    return null;
                }
                Bitmap decodedBitmap = BitmapFactory.decodeStream(decodeStream, null, decodeOptions);
                if (decodedBitmap == null) {
                    return null;
                }
                return applyExifOrientation(decodedBitmap, null, uri);
            }
        } catch (IOException | IllegalArgumentException exception) {
            return null;
        }
    }

    @NonNull
    private Bitmap applyExifOrientation(@NonNull Bitmap sourceBitmap,
                                        @Nullable String imagePath,
                                        @Nullable Uri imageUri) {
        int orientation = ExifInterface.ORIENTATION_UNDEFINED;

        try {
            if (!TextUtils.isEmpty(imagePath)) {
                ExifInterface exifInterface = new ExifInterface(imagePath);
                orientation = exifInterface.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                );
            } else if (imageUri != null) {
                try (InputStream exifStream = requireContext().getContentResolver().openInputStream(imageUri)) {
                    if (exifStream != null) {
                        ExifInterface exifInterface = new ExifInterface(exifStream);
                        orientation = exifInterface.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_NORMAL
                        );
                    }
                }
            }
        } catch (IOException exception) {
            return sourceBitmap;
        }

        Matrix rotationMatrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                rotationMatrix.postRotate(90f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                rotationMatrix.postRotate(180f);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                rotationMatrix.postRotate(270f);
                break;
            case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                rotationMatrix.preScale(-1f, 1f);
                break;
            case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                rotationMatrix.preScale(1f, -1f);
                break;
            default:
                break;
        }

        if (rotationMatrix.isIdentity()) {
            return sourceBitmap;
        }

        Bitmap transformedBitmap = Bitmap.createBitmap(
                sourceBitmap,
                0,
                0,
                sourceBitmap.getWidth(),
                sourceBitmap.getHeight(),
                rotationMatrix,
                true
        );
        if (transformedBitmap != sourceBitmap) {
            sourceBitmap.recycle();
        }
        return transformedBitmap;
    }

    private void bindZoomGestures(@NonNull ImageView imageView) {
        imageView.setScaleType(ImageView.ScaleType.MATRIX);
        Matrix matrix = new Matrix();
        imageView.setImageMatrix(matrix);

        float[] currentScale = {1f};
        float[] baseScale = {1f};
        PointF lastTouch = new PointF();
        boolean[] dragging = {false};

        imageView.post(() -> {
            if (imageView.getDrawable() == null || imageView.getWidth() == 0 || imageView.getHeight() == 0) {
                return;
            }
            float drawableWidth = imageView.getDrawable().getIntrinsicWidth();
            float drawableHeight = imageView.getDrawable().getIntrinsicHeight();
            if (drawableWidth <= 0f || drawableHeight <= 0f) {
                return;
            }
            float viewWidth = imageView.getWidth();
            float viewHeight = imageView.getHeight();
            float fitScale = Math.min(viewWidth / drawableWidth, viewHeight / drawableHeight);
            float dx = (viewWidth - (drawableWidth * fitScale)) / 2f;
            float dy = (viewHeight - (drawableHeight * fitScale)) / 2f;

            matrix.reset();
            matrix.postScale(fitScale, fitScale);
            matrix.postTranslate(dx, dy);
            baseScale[0] = fitScale;
            currentScale[0] = fitScale;
            imageView.setImageMatrix(matrix);
        });

        ScaleGestureDetector scaleDetector = new ScaleGestureDetector(
                requireContext(),
                new ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    @Override
                    public boolean onScale(ScaleGestureDetector detector) {
                        float scaleFactor = detector.getScaleFactor();
                        float targetScale = currentScale[0] * scaleFactor;
                        float maxScale = baseScale[0] * PREVIEW_MAX_SCALE_MULTIPLIER;

                        if (targetScale < baseScale[0]) {
                            scaleFactor = baseScale[0] / currentScale[0];
                            targetScale = baseScale[0];
                        } else if (targetScale > maxScale) {
                            scaleFactor = maxScale / currentScale[0];
                            targetScale = maxScale;
                        }

                        matrix.postScale(scaleFactor, scaleFactor, detector.getFocusX(), detector.getFocusY());
                        constrainMatrixToBounds(imageView, matrix);
                        currentScale[0] = targetScale;
                        imageView.setImageMatrix(matrix);
                        return true;
                    }
                }
        );

        GestureDetector gestureDetector = new GestureDetector(
                requireContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent event) {
                        if (event == null) {
                            return false;
                        }
                        if (Math.abs(currentScale[0] - baseScale[0]) > 0.01f) {
                            resetPreviewMatrix(imageView, matrix, currentScale, baseScale);
                        } else {
                            float targetScale = baseScale[0] * PREVIEW_DOUBLE_TAP_SCALE_MULTIPLIER;
                            float scaleFactor = targetScale / currentScale[0];
                            matrix.postScale(scaleFactor, scaleFactor, event.getX(), event.getY());
                            constrainMatrixToBounds(imageView, matrix);
                            currentScale[0] = targetScale;
                        }
                        imageView.setImageMatrix(matrix);
                        return true;
                    }
                }
        );

        imageView.setOnTouchListener((v, event) -> {
            if (event == null) {
                return false;
            }
            scaleDetector.onTouchEvent(event);
            gestureDetector.onTouchEvent(event);

            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    lastTouch.set(event.getX(), event.getY());
                    dragging[0] = true;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (dragging[0] && !scaleDetector.isInProgress() && currentScale[0] > (baseScale[0] + 0.01f)) {
                        float dx = event.getX() - lastTouch.x;
                        float dy = event.getY() - lastTouch.y;
                        matrix.postTranslate(dx, dy);
                        constrainMatrixToBounds(imageView, matrix);
                        imageView.setImageMatrix(matrix);
                        lastTouch.set(event.getX(), event.getY());
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    imageView.performClick();
                    dragging[0] = false;
                    break;
                case MotionEvent.ACTION_CANCEL:
                    dragging[0] = false;
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    if (event.getPointerCount() > 1) {
                        int actionIndex = event.getActionIndex();
                        int fallbackIndex = actionIndex == 0 ? 1 : 0;
                        lastTouch.set(event.getX(fallbackIndex), event.getY(fallbackIndex));
                    }
                    break;
                default:
                    break;
            }
            return true;
        });
    }

    private void resetPreviewMatrix(@NonNull ImageView imageView,
                                    @NonNull Matrix matrix,
                                    @NonNull float[] currentScale,
                                    @NonNull float[] baseScale) {
        if (imageView.getDrawable() == null || imageView.getWidth() == 0 || imageView.getHeight() == 0) {
            return;
        }
        float drawableWidth = imageView.getDrawable().getIntrinsicWidth();
        float drawableHeight = imageView.getDrawable().getIntrinsicHeight();
        if (drawableWidth <= 0f || drawableHeight <= 0f) {
            return;
        }
        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();
        float fitScale = Math.min(viewWidth / drawableWidth, viewHeight / drawableHeight);
        float dx = (viewWidth - (drawableWidth * fitScale)) / 2f;
        float dy = (viewHeight - (drawableHeight * fitScale)) / 2f;

        matrix.reset();
        matrix.postScale(fitScale, fitScale);
        matrix.postTranslate(dx, dy);
        baseScale[0] = fitScale;
        currentScale[0] = fitScale;
        imageView.setImageMatrix(matrix);
    }

    private void constrainMatrixToBounds(@NonNull ImageView imageView, @NonNull Matrix matrix) {
        if (imageView.getDrawable() == null) {
            return;
        }

        float viewWidth = imageView.getWidth();
        float viewHeight = imageView.getHeight();
        if (viewWidth <= 0f || viewHeight <= 0f) {
            return;
        }

        RectF drawableRect = new RectF(
                0f,
                0f,
                imageView.getDrawable().getIntrinsicWidth(),
                imageView.getDrawable().getIntrinsicHeight()
        );
        matrix.mapRect(drawableRect);

        float deltaX = 0f;
        float deltaY = 0f;

        if (drawableRect.width() <= viewWidth) {
            deltaX = (viewWidth / 2f) - drawableRect.centerX();
        } else if (drawableRect.left > 0f) {
            deltaX = -drawableRect.left;
        } else if (drawableRect.right < viewWidth) {
            deltaX = viewWidth - drawableRect.right;
        }

        if (drawableRect.height() <= viewHeight) {
            deltaY = (viewHeight / 2f) - drawableRect.centerY();
        } else if (drawableRect.top > 0f) {
            deltaY = -drawableRect.top;
        } else if (drawableRect.bottom < viewHeight) {
            deltaY = viewHeight - drawableRect.bottom;
        }

        matrix.postTranslate(deltaX, deltaY);
    }

    private int calculateInSampleSize(@NonNull BitmapFactory.Options options, int reqWidth, int reqHeight) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        while ((height / inSampleSize) > reqHeight || (width / inSampleSize) > reqWidth) {
            inSampleSize *= 2;
        }
        return Math.max(inSampleSize, 1);
    }

    @Override
    public void onDestroyView() {
        clearExpenseCategoryObserver();
        clearReceiptScanWorkObservation();
        loadingHelper.dismiss();
        super.onDestroyView();
        binding.rvReceiptTransactions.setAdapter(null);
        binding = null;
        adapter = null;
    }

    @Override
    public void onDestroy() {
        receiptImageExecutor.shutdownNow();
        super.onDestroy();
    }
}
