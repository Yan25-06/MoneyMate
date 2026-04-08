package com.group10.moneymate.ui.transaction;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.group10.moneymate.R;
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
import com.group10.moneymate.utils.LoadingHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class TransactionConfirmationFragment extends Fragment {

    private static final int HIGH_CONFIDENCE = 2;
    private static final int MAX_THUMBNAIL_SIZE_PX = 320;

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
        observeSaveDependencies();
        renderConfirmationState(confirmationArgs);
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

        if (confirmationArgs != null) {
            updateSummary(confirmationArgs);
        }
        updateActionState();
    }

    private void observeExpenseCategories(@Nullable String walletId) {
        clearExpenseCategoryObserver();
        expenseCategoriesSource = viewModel.getExpenseCategoriesForWallet(walletId);
        expenseCategoriesObserver = categories -> {
            availableExpenseCategories.clear();
            if (categories != null) {
                availableExpenseCategories.addAll(categories);
            }
            if (confirmationArgs != null) {
                updateSummary(confirmationArgs);
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

    private void renderConfirmationState(@NonNull TransactionConfirmationFragmentArgs args) {
        pendingItems.clear();
        pendingItems.addAll(buildPendingItems(args));
        adapter.submitList(new ArrayList<>(pendingItems));

        updateThumbnail(args.getImagePath());
        updateSummary(args);
        updateEmptyState();
    }

    @NonNull
    private List<ReceiptTransactionAdapter.PendingReceiptItem> buildPendingItems(
            @NonNull TransactionConfirmationFragmentArgs args
    ) {
        List<ReceiptTransactionAdapter.PendingReceiptItem> items = parseItemPayload(args);
        if (!items.isEmpty()) {
            return items;
        }

        List<ReceiptTransactionAdapter.PendingReceiptItem> fallbackItems = new ArrayList<>();
        fallbackItems.add(createPendingItem(
                "ocr_root_0",
                args.getAmount(),
                resolveFallbackNote(args.getMerchant()),
                args.getCategoryHint(),
                args.getTimestamp(),
                args.getConfidence()
        ));
        return fallbackItems;
    }

    @NonNull
    private List<ReceiptTransactionAdapter.PendingReceiptItem> parseItemPayload(
            @NonNull TransactionConfirmationFragmentArgs args
    ) {
        String itemsJson = args.getItemsJson();
        if (TextUtils.isEmpty(itemsJson)) {
            return new ArrayList<>();
        }

        List<ReceiptTransactionAdapter.PendingReceiptItem> parsedItems = new ArrayList<>();
        try {
            JSONArray jsonArray = new JSONArray(itemsJson);
            for (int index = 0; index < jsonArray.length(); index++) {
                JSONObject itemObject = jsonArray.optJSONObject(index);
                if (itemObject == null) {
                    continue;
                }
                parsedItems.add(createPendingItem(
                        "ocr_item_" + index,
                        itemObject.optString("amount", ""),
                        resolveItemNote(itemObject.optString("name", ""), args.getMerchant()),
                        resolveCategoryHint(itemObject.optString("category_hint", ""), args.getCategoryHint()),
                        args.getTimestamp(),
                        itemObject.optInt("confidence", args.getConfidence())
                ));
            }
        } catch (JSONException exception) {
            return new ArrayList<>();
        }
        return parsedItems;
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
        long safeTimestamp = timestamp > 0L ? timestamp : System.currentTimeMillis();

        return new ReceiptTransactionAdapter.PendingReceiptItem(
                draftId,
                safeAmountRaw,
                resolveAmountLabel(safeAmountRaw),
                safeNote,
                safeCategoryHint,
                resolveCategoryLabel(safeCategoryHint),
                safeTimestamp,
                resolveDateLabel(timestamp),
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
    private String resolveFallbackNote(@Nullable String merchant) {
        if (!TextUtils.isEmpty(merchant)) {
            return merchant.trim();
        }
        return getString(R.string.transaction_scan_confirmation_note_fallback);
    }

    @NonNull
    private String resolveItemNote(@Nullable String itemName, @Nullable String merchant) {
        if (!TextUtils.isEmpty(itemName)) {
            return itemName.trim();
        }
        return resolveFallbackNote(merchant);
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

    private void updateThumbnail(@Nullable String imagePath) {
        if (TextUtils.isEmpty(imagePath)) {
            binding.ivReceiptThumbnail.setImageResource(R.drawable.outline_receipt_24);
            binding.ivReceiptThumbnail.setImageTintList(ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.transaction_income_accent)
            ));
            return;
        }

        Bitmap thumbnailBitmap = decodeSampledBitmap(imagePath, MAX_THUMBNAIL_SIZE_PX, MAX_THUMBNAIL_SIZE_PX);
        if (thumbnailBitmap == null) {
            binding.ivReceiptThumbnail.setImageResource(R.drawable.outline_receipt_24);
            binding.ivReceiptThumbnail.setImageTintList(ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.transaction_income_accent)
            ));
            return;
        }
        binding.ivReceiptThumbnail.setImageBitmap(thumbnailBitmap);
        binding.ivReceiptThumbnail.setImageTintList(null);
    }

    private void updateSummary(@NonNull TransactionConfirmationFragmentArgs args) {
        String merchant = args.getMerchant();
        int itemCount = pendingItems.size();
        if (!TextUtils.isEmpty(merchant)) {
            binding.tvConfirmationSummaryTitle.setText(merchant);
        } else {
            binding.tvConfirmationSummaryTitle.setText(R.string.transaction_scan_confirmation_summary_title);
        }
        binding.tvConfirmationSummarySubtitle.setText(
                getString(R.string.transaction_scan_confirmation_summary_count, itemCount)
        );

        boolean hasPendingWarnings = args.getConfidence() < HIGH_CONFIDENCE || hasAnyPendingWarning();
        boolean hasSummaryWarning = hasPendingWarnings || saveAllWalletId == null;
        binding.layoutConfirmationSummaryWarning.setVisibility(hasSummaryWarning ? View.VISIBLE : View.GONE);
        binding.tvConfirmationSummaryWarning.setText(resolveSummaryWarningMessage(hasPendingWarnings));
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
        action.setOcrDraftImagePath(confirmationArgs != null ? confirmationArgs.getImagePath() : null);
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
        transaction.setImagePath(confirmationArgs != null ? confirmationArgs.getImagePath() : null);
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
        String imagePath = confirmationArgs != null ? confirmationArgs.getImagePath() : null;
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
        if (confirmationArgs != null) {
            updateSummary(confirmationArgs);
        }
        updateEmptyState();
        Toast.makeText(requireContext(), R.string.transaction_scan_confirmation_item_saved_removed, Toast.LENGTH_SHORT).show();
    }

    @Nullable
    private Bitmap decodeSampledBitmap(@NonNull String imagePath, int reqWidth, int reqHeight) {
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
        return BitmapFactory.decodeFile(imagePath, decodeOptions);
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
        loadingHelper.dismiss();
        super.onDestroyView();
        binding.rvReceiptTransactions.setAdapter(null);
        binding = null;
        adapter = null;
    }
}
