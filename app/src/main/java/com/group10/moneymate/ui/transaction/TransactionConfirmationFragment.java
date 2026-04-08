package com.group10.moneymate.ui.transaction;

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
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.group10.moneymate.R;
import com.group10.moneymate.databinding.FragmentTransactionConfirmationBinding;
import com.group10.moneymate.ui.transaction.adapter.ReceiptTransactionAdapter;
import com.group10.moneymate.utils.CurrencyFormatter;
import com.group10.moneymate.utils.DateUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class TransactionConfirmationFragment extends Fragment {

    private static final long UNKNOWN_TIMESTAMP = -1L;
    private static final int LOW_CONFIDENCE = 0;
    private static final int HIGH_CONFIDENCE = 2;
    private static final int MAX_THUMBNAIL_SIZE_PX = 320;

    private FragmentTransactionConfirmationBinding binding;
    private ReceiptTransactionAdapter adapter;
    private final List<ReceiptTransactionAdapter.PendingReceiptItem> pendingItems = new ArrayList<>();

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
        TransactionConfirmationFragmentArgs args = TransactionConfirmationFragmentArgs.fromBundle(
                getArguments() != null ? getArguments() : new Bundle()
        );

        setupToolbar();
        setupRecyclerView();
        setupActions();
        observeDraftSaveResults();
        renderConfirmationState(args);
    }

    private void setupToolbar() {
        binding.btnConfirmationBack.setOnClickListener(v -> Navigation.findNavController(v).navigateUp());
    }

    private void setupRecyclerView() {
        adapter = new ReceiptTransactionAdapter();
        adapter.setOnReceiptItemClickListener(this::openDraftInAddEditScreen);
        binding.rvReceiptTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvReceiptTransactions.setAdapter(adapter);
    }

    private void setupActions() {
        binding.btnSaveAllReceiptTransactions.setOnClickListener(v ->
                Toast.makeText(requireContext(), R.string.transaction_scan_confirmation_save_all_placeholder, Toast.LENGTH_SHORT).show()
        );
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
            binding.ivReceiptThumbnail.setImageTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(requireContext(), R.color.transaction_income_accent)
            ));
            return;
        }

        Bitmap thumbnailBitmap = decodeSampledBitmap(imagePath, MAX_THUMBNAIL_SIZE_PX, MAX_THUMBNAIL_SIZE_PX);
        if (thumbnailBitmap == null) {
            binding.ivReceiptThumbnail.setImageResource(R.drawable.outline_receipt_24);
            binding.ivReceiptThumbnail.setImageTintList(android.content.res.ColorStateList.valueOf(
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

        boolean hasSummaryWarning = args.getConfidence() < HIGH_CONFIDENCE || hasAnyPendingWarning();
        binding.layoutConfirmationSummaryWarning.setVisibility(hasSummaryWarning ? View.VISIBLE : View.GONE);
        binding.tvConfirmationSummaryWarning.setText(
                hasSummaryWarning
                        ? getString(R.string.transaction_scan_confirmation_summary_warning)
                        : ""
        );
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
        binding.btnSaveAllReceiptTransactions.setEnabled(!isEmpty);
    }

    private void openDraftInAddEditScreen(@NonNull ReceiptTransactionAdapter.PendingReceiptItem item) {
        TransactionConfirmationFragmentDirections.ActionTransactionConfirmationFragmentToAddEditTransactionFragment action =
                TransactionConfirmationFragmentDirections.actionTransactionConfirmationFragmentToAddEditTransactionFragment();
        action.setTransactionId(null);
        action.setOcrDraftId(item.getDraftId());
        action.setOcrDraftAmount(item.getAmountRaw());
        action.setOcrDraftNote(item.getNote());
        action.setOcrDraftTimestamp(item.getTimestamp());
        action.setOcrDraftCategoryHint(item.getCategoryHint());
        action.setOcrDraftImagePath(TransactionConfirmationFragmentArgs.fromBundle(
                getArguments() != null ? getArguments() : new Bundle()
        ).getImagePath());
        Navigation.findNavController(binding.getRoot()).navigate(action);
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
        updateSummary(TransactionConfirmationFragmentArgs.fromBundle(
                getArguments() != null ? getArguments() : new Bundle()
        ));
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
        super.onDestroyView();
        binding.rvReceiptTransactions.setAdapter(null);
        binding = null;
        adapter = null;
    }
}
