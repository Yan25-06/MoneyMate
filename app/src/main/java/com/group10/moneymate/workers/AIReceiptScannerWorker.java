package com.group10.moneymate.workers;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;
import androidx.work.BackoffPolicy;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import com.group10.moneymate.BuildConfig;
import com.group10.moneymate.ai.receipt.GeminiService;
import com.group10.moneymate.ai.receipt.MlKitReceiptParserBridge;
import com.group10.moneymate.ai.receipt.ReceiptParser;
import com.group10.moneymate.ai.receipt.ReceiptParserBridge;
import com.group10.moneymate.ai.receipt.ReceiptScanContract;
import com.group10.moneymate.ai.receipt.model.ReceiptData;
import com.group10.moneymate.ai.receipt.model.ReceiptItem;
import com.group10.moneymate.data.repository.AuthRepository;
import com.group10.moneymate.data.repository.CategoryRepository;
import com.group10.moneymate.utils.Constants;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Year;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AIReceiptScannerWorker extends Worker {

    public static final String KEY_IMAGE_PATH = ReceiptScanContract.KEY_IMAGE_PATH;
    public static final String KEY_IMAGE_URI = ReceiptScanContract.KEY_IMAGE_URI;

    private static final String TAG = "AIReceiptScannerWorker";
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_DECODE_BITMAP_WIDTH_PX = 2400;
    private static final int GEMINI_MIN_BITMAP_WIDTH_PX = 1400;
    private static final int GEMINI_MAX_BITMAP_WIDTH_PX = 2200;
    private static final int LOCAL_TARGET_BITMAP_WIDTH_PX = 1600;
    private static final long OCR_TIMEOUT_SECONDS = 30L;
    private static final long RETRY_BACKOFF_SECONDS = 10L;
    private static final long DEFAULT_GEMINI_RATE_LIMIT_COOLDOWN_MS = 60_000L;
    private static final float DESKEW_THRESHOLD_DEGREES = 0.8f;
    private static final float MAX_ACCEPTED_ANGLE_DEGREES = 20f;
    private static final double MIN_PLAUSIBLE_RECEIPT_TOTAL = 1000d;
    private static final double MIN_PLAUSIBLE_ITEM_PRICE = 1000d;
    private static final Pattern OCR_DATE_PATTERN = Pattern.compile(
            "(\\d{1,2})[./-](\\d{1,2})[./-](\\d{2,4})"
    );
    private static final Pattern OCR_DATE_TEXTUAL_MONTH_PATTERN = Pattern.compile(
            "(\\d{1,2})[./-]([A-Za-z]{3,9})[./-](\\d{2,4})",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MONEY_LIKE_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d{4,})(?!\\d)"
    );
    private static final String PREFS_OCR = "ocr_receipt_prefs";
    private static final String PREF_KEY_GEMINI_RATE_LIMIT_UNTIL = "gemini_rate_limit_until";

    private final ReceiptParserBridge receiptParserBridge;
    private final GeminiService geminiService;
    private final CategoryRepository categoryRepository;
    private final AuthRepository authRepository;
    private final ReceiptParser repairReceiptParser;

    public AIReceiptScannerWorker(@NonNull Context context,
                                  @NonNull WorkerParameters workerParameters) {
        this(
                context,
                workerParameters,
                new MlKitReceiptParserBridge(),
                new GeminiService(BuildConfig.GEMINI_API_KEY),
                null,
                null
        );
    }

    public AIReceiptScannerWorker(@NonNull Context context,
                                  @NonNull WorkerParameters workerParameters,
                                  @NonNull ReceiptParserBridge receiptParserBridge,
                                  @NonNull GeminiService geminiService,
                                  @Nullable CategoryRepository categoryRepository,
                                  @Nullable AuthRepository authRepository) {
        super(context, workerParameters);
        this.receiptParserBridge = receiptParserBridge;
        this.geminiService = geminiService;
        this.categoryRepository = categoryRepository;
        this.authRepository = authRepository;
        this.repairReceiptParser = new ReceiptParser();
    }

    @NonNull
    public static Data buildInputData(@NonNull String imagePath, @NonNull String imageUri) {
        return new Data.Builder()
                .putString(KEY_IMAGE_PATH, imagePath)
                .putString(KEY_IMAGE_URI, imageUri)
                .build();
    }

    @NonNull
    public static OneTimeWorkRequest createRequest(@NonNull String imagePath, @NonNull String imageUri) {
        return new OneTimeWorkRequest.Builder(AIReceiptScannerWorker.class)
                .setInputData(buildInputData(imagePath, imageUri))
                .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        RETRY_BACKOFF_SECONDS,
                        TimeUnit.SECONDS
                )
                .build();
    }

    public static boolean shouldPreferCloud(@NonNull Context context) {
        return isNetworkAvailable(context) && !isGeminiRateLimited(context);
    }

    @NonNull
    public static String resolvePreScanProcessingDetail(@NonNull Context context) {
        if (!isNetworkAvailable(context)) {
            return ReceiptScanContract.DETAIL_LOCAL_NO_NETWORK;
        }
        if (isGeminiRateLimited(context)) {
            return ReceiptScanContract.DETAIL_LOCAL_RATE_LIMITED;
        }
        return ReceiptScanContract.DETAIL_CLOUD_PRIMARY;
    }

    @NonNull
    @Override
    public Result doWork() {
        long startedAt = System.currentTimeMillis();
        ResolvedReceiptImage resolvedImage = null;
        Bitmap decodedBitmap = null;
        Bitmap geminiBitmap = null;
        Bitmap geminiEnhancedBitmap = null;
        Bitmap preprocessedBitmap = null;
        Bitmap deskewedBitmap = null;
        TextRecognizer textRecognizer = null;
        String processingSource = ReceiptScanContract.SOURCE_LOCAL;
        String processingDetail = ReceiptScanContract.DETAIL_LOCAL_CLOUD_FAILED;

        try {
            resolvedImage = resolveInputImage();
            decodedBitmap = loadBitmap(resolvedImage.file);
            geminiBitmap = prepareGeminiBitmap(decodedBitmap);
            geminiEnhancedBitmap = prepareGeminiEnhancedBitmap(decodedBitmap);

            ParseExecutionResult parseExecutionResult;
            if (shouldAttemptGemini()) {
                GeminiAttemptResult geminiAttemptResult = tryGeminiVisionParse(
                        geminiBitmap,
                        geminiEnhancedBitmap,
                        resolveAllowedExpenseCategories()
                );
                parseExecutionResult = geminiAttemptResult.parseExecutionResult;
                processingDetail = geminiAttemptResult.fallbackDetail;
                if (parseExecutionResult != null) {
                    GeminiVerificationResult verificationResult = verifyGeminiResultWithLocalOcr(
                            decodedBitmap,
                            parseExecutionResult,
                            resolveAllowedExpenseCategories()
                    );
                    long durationMs = System.currentTimeMillis() - startedAt;
                    Log.i(
                            TAG,
                            "OCR success attempt=" + getCurrentAttemptNumber()
                                    + " source=gemini_vision"
                                    + " width=" + geminiBitmap.getWidth()
                                    + " height=" + geminiBitmap.getHeight()
                                    + " repaired=" + verificationResult.repaired
                                    + " durationMs=" + durationMs
                    );
                    return Result.success(
                            verificationResult.parseResult.toOutputData(
                                    resolvedImage.imagePath,
                                    resolvedImage.imageUri,
                                    verificationResult.rawText,
                                    ReceiptScanContract.SOURCE_CLOUD,
                                    ReceiptScanContract.DETAIL_CLOUD_PRIMARY,
                                    verificationResult.hasOcrText,
                                    verificationResult.ocrBlockCount,
                                    verificationResult.ocrLineCount
                            )
                    );
                }
            } else {
                processingDetail = resolvePreScanProcessingDetail(getApplicationContext());
                Log.i(TAG, "Gemini skipped reason=" + processingDetail + " fallback=local");
            }

            preprocessedBitmap = preprocessBitmap(decodedBitmap);
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            Text firstPassText = runOcr(textRecognizer, preprocessedBitmap);
            Text recognizedText = firstPassText;
            float detectedAngle = estimateDeskewAngle(firstPassText);
            if (Math.abs(detectedAngle) >= DESKEW_THRESHOLD_DEGREES) {
                deskewedBitmap = rotateBitmap(preprocessedBitmap, -detectedAngle);
                Text secondPassText = runOcr(textRecognizer, deskewedBitmap);
                recognizedText = chooseBetterText(firstPassText, secondPassText);
            }
            String rawText = recognizedText.getText();
            List<String> recognizedLines = extractRecognizedLines(recognizedText);

            boolean hasOcrText = !TextUtils.isEmpty(rawText);
            int ocrBlockCount = recognizedText.getTextBlocks().size();
            int ocrLineCount = countRecognizedLines(recognizedText);

            ReceiptParserBridge.ParseResult parseResult = parseWithLocalFallback(
                    resolvedImage,
                    recognizedText
            );
            parseResult = repairLocalParseResult(rawText, recognizedLines, parseResult);

            long durationMs = System.currentTimeMillis() - startedAt;
            Log.i(
                    TAG,
                    "OCR success attempt=" + getCurrentAttemptNumber()
                            + " source=local_fallback"
                            + " width=" + preprocessedBitmap.getWidth()
                            + " height=" + preprocessedBitmap.getHeight()
                            + " deskewAngle=" + detectedAngle
                            + " blocks=" + ocrBlockCount
                            + " lines=" + ocrLineCount
                            + " score=" + evaluateRecognizedTextScore(recognizedText)
                            + " durationMs=" + durationMs
            );

            return Result.success(
                    parseResult.toOutputData(
                            resolvedImage.imagePath,
                            resolvedImage.imageUri,
                            rawText,
                            processingSource,
                            processingDetail,
                            hasOcrText,
                            ocrBlockCount,
                            ocrLineCount
                    )
            );
        } catch (ReceiptParserBridge.ReceiptParsingException exception) {
            logFailure(
                    ReceiptScanContract.STAGE_PARSER,
                    ReceiptScanContract.ERROR_PARSER_FAILED,
                    false,
                    startedAt
            );
            return Result.failure(
                    buildFailureData(
                            resolvedImage,
                            ReceiptScanContract.ERROR_PARSER_FAILED,
                            ReceiptScanContract.STAGE_PARSER
                    )
            );
        } catch (PermanentWorkerException exception) {
            logFailure(exception.errorStage, exception.errorCode, false, startedAt);
            return Result.failure(buildFailureData(resolvedImage, exception.errorCode, exception.errorStage));
        } catch (RetryableWorkerException exception) {
            boolean shouldRetry = shouldRetry();
            logFailure(exception.errorStage, exception.errorCode, shouldRetry, startedAt);
            if (shouldRetry) {
                return Result.retry();
            }
            return Result.failure(buildFailureData(resolvedImage, exception.errorCode, exception.errorStage));
        } finally {
            if (textRecognizer != null) {
                textRecognizer.close();
            }
            recycleBitmap(geminiEnhancedBitmap);
            recycleBitmap(geminiBitmap);
            recycleBitmap(deskewedBitmap);
            recycleBitmap(preprocessedBitmap);
            recycleBitmap(decodedBitmap);
        }
    }

    @Nullable
    private GeminiAttemptResult tryGeminiVisionParse(@NonNull Bitmap geminiBitmap,
                                                     @Nullable Bitmap geminiEnhancedBitmap,
                                                     @NonNull List<String> allowedCategories) {
        long geminiStartedAt = System.currentTimeMillis();
        GeminiService.GeminiResult geminiResult = geminiService.parseReceipt(
                geminiBitmap,
                geminiEnhancedBitmap,
                allowedCategories
        );
        long geminiDurationMs = System.currentTimeMillis() - geminiStartedAt;
        if (geminiResult instanceof GeminiService.GeminiResult.Success) {
            GeminiService.ParsedReceipt parsedReceipt =
                    ((GeminiService.GeminiResult.Success) geminiResult).getParsedReceipt();
            GeminiConversionResult conversionResult = convertGeminiResponseToParseResult(
                    parsedReceipt,
                    allowedCategories
            );
            if (conversionResult != null) {
                Log.i(
                        TAG,
                        "Gemini success fallbackUsed=false durationMs=" + geminiDurationMs
                                + " confidence=" + parsedReceipt.getConfidence()
                                + " merchant=" + truncateForLog(conversionResult.merchant)
                                + " amount=" + conversionResult.amount
                );
                return GeminiAttemptResult.success(
                        new ParseExecutionResult(
                                conversionResult.parseResult,
                                conversionResult.receiptData,
                                conversionResult.itemsJson
                        )
                );
            }
            Log.w(
                    TAG,
                    "Gemini invalid_payload fallbackUsed=true reason=convert_failed durationMs=" + geminiDurationMs
            );
            return GeminiAttemptResult.fallback(ReceiptScanContract.DETAIL_LOCAL_CLOUD_FAILED);
        }

        GeminiService.GeminiResult.Error error = (GeminiService.GeminiResult.Error) geminiResult;
        Log.w(
                TAG,
                "Gemini fallbackUsed=true reason=" + error.getErrorCode()
                        + " retryable=" + error.isRetryable()
                        + " durationMs=" + geminiDurationMs
        );
        if ("http_429".equalsIgnoreCase(error.getErrorCode())) {
            storeGeminiRateLimitCooldown(error.getRetryAfterSeconds());
        }
        return GeminiAttemptResult.fallback(mapGeminiFallbackDetail(error.getErrorCode()));
    }

    private boolean shouldAttemptGemini() {
        return shouldPreferCloud(getApplicationContext());
    }

    @NonNull
    private String mapGeminiFallbackDetail(@Nullable String errorCode) {
        if (!TextUtils.isEmpty(errorCode) && "http_429".equalsIgnoreCase(errorCode.trim())) {
            return ReceiptScanContract.DETAIL_LOCAL_RATE_LIMITED;
        }
        return ReceiptScanContract.DETAIL_LOCAL_CLOUD_FAILED;
    }

    @NonNull
    private ReceiptParserBridge.ParseResult parseWithLocalFallback(
            @NonNull ResolvedReceiptImage resolvedImage,
            @NonNull Text recognizedText
    ) throws ReceiptParserBridge.ReceiptParsingException {
        return receiptParserBridge.parse(
                resolvedImage.imagePath,
                resolvedImage.imageUri,
                recognizedText
        );
    }

    @NonNull
    private ReceiptParserBridge.ParseResult repairLocalParseResult(@NonNull String rawText,
                                                                   @NonNull List<String> recognizedLines,
                                                                   @NonNull ReceiptParserBridge.ParseResult fallbackResult) {
        ReceiptData localReceiptData = repairReceiptParser.parse(rawText, recognizedLines);
        String repairedAmount = extractVerifiedOcrTotal(recognizedLines);
        if (repairedAmount.isEmpty()) {
            repairedAmount = localReceiptData.getAmount();
        }

        Long repairedTimestamp = extractVerifiedOcrDate(recognizedLines);
        long resolvedTimestamp = repairedTimestamp != null
                ? repairedTimestamp
                : ReceiptScanContract.UNKNOWN_TIMESTAMP;

        List<String> allowedCategories = resolveAllowedExpenseCategories();
        String inferredCategory = inferCategoryFromEvidence(
                localReceiptData.getMerchant(),
                localReceiptData.getNoteHint(),
                localReceiptData.getItems(),
                recognizedLines,
                allowedCategories
        );
        String resolvedCategory = reconcileGeminiCategory(
                localReceiptData.getCategoryHint(),
                localReceiptData.getCategoryHint(),
                inferredCategory,
                allowedCategories
        );
        String resolvedNote = reconcileGeminiNote(
                localReceiptData.getMerchant(),
                localReceiptData.getNoteHint(),
                localReceiptData,
                recognizedLines
        );
        int resolvedConfidence = reconcileGeminiConfidence(
                new ReceiptData(
                        repairedAmount,
                        resolvedTimestamp,
                        localReceiptData.getMerchant(),
                        resolvedCategory,
                        resolvedNote,
                        localReceiptData.getItems(),
                        localReceiptData.getConfidence()
                ),
                repairedAmount,
                resolvedTimestamp
        );
        ReceiptData repairedReceiptData = new ReceiptData(
                repairedAmount,
                resolvedTimestamp,
                localReceiptData.getMerchant(),
                resolvedCategory,
                resolvedNote,
                localReceiptData.getItems(),
                resolvedConfidence
        );
        return ReceiptParserBridge.ParseResult.fromReceiptData(
                repairedReceiptData,
                serializeItems(localReceiptData.getItems())
        );
    }

    @Nullable
    private GeminiConversionResult convertGeminiResponseToParseResult(
            @NonNull GeminiService.ParsedReceipt parsedReceipt,
            @NonNull List<String> allowedCategories
    ) {
        List<ReceiptItem> items = new ArrayList<>();
        for (GeminiService.ParsedReceiptItem parsedItem : parsedReceipt.getItems()) {
            String sanitizedItemName = sanitizeGeminiText(parsedItem.getName());
            if (!parsedItem.hasUsablePrice() || sanitizedItemName.isEmpty()) {
                continue;
            }
            String itemCategory = sanitizeGeminiText(parsedItem.getCategory());
            items.add(new ReceiptItem(
                    sanitizedItemName,
                    formatWholeMoney(parsedItem.getPrice()),
                    itemCategory,
                    mapGeminiConfidence(parsedReceipt.getConfidence())
            ));
        }

        String amount = resolveGeminiAmount(parsedReceipt, items);
        if (amount.isEmpty()) {
            return null;
        }

        String merchant = sanitizeGeminiMerchant(parsedReceipt.getMerchant());
        String categoryHint = resolveGeminiCategory(parsedReceipt, items, allowedCategories);
        String noteHint = buildGeminiNoteHint(parsedReceipt, merchant, items);
        if (!isGeminiResultPlausible(parsedReceipt, merchant, amount, items)) {
            Log.w(
                    TAG,
                    "Gemini invalid_payload fallbackUsed=true reason=implausible_result"
                            + " merchant=" + truncateForLog(merchant)
                            + " amount=" + amount
                            + " items=" + items.size()
            );
            return null;
        }
        long timestamp = parseGeminiDate(parsedReceipt.getDate());
        ReceiptData receiptData = new ReceiptData(
                amount,
                timestamp,
                merchant,
                categoryHint,
                noteHint,
                items,
                mapGeminiConfidence(parsedReceipt.getConfidence())
        );
        String itemsJson = serializeItems(items);
        return new GeminiConversionResult(
                ReceiptParserBridge.ParseResult.fromReceiptData(receiptData, itemsJson),
                receiptData,
                itemsJson,
                amount,
                merchant
        );
    }

    @NonNull
    private String resolveGeminiAmount(@NonNull GeminiService.ParsedReceipt parsedReceipt,
                                       @NonNull List<ReceiptItem> items) {
        GeminiService.TotalCandidate bestTotalCandidate = selectBestGeminiTotalCandidate(parsedReceipt);
        if (bestTotalCandidate != null) {
            return formatWholeMoney(bestTotalCandidate.getAmount());
        }

        if (!Double.isNaN(parsedReceipt.getTotal()) && parsedReceipt.getTotal() > 0d) {
            return formatWholeMoney(parsedReceipt.getTotal());
        }

        BigDecimal total = BigDecimal.ZERO;
        for (ReceiptItem item : items) {
            total = total.add(parseWholeMoney(item.getAmount()));
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return "";
        }
        return total.toPlainString();
    }

    @NonNull
    private BigDecimal parseWholeMoney(@NonNull String amount) {
        try {
            return new BigDecimal(amount);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    @NonNull
    private String formatWholeMoney(double amount) {
        BigDecimal decimal = BigDecimal.valueOf(amount).setScale(0, RoundingMode.HALF_UP);
        return decimal.toPlainString();
    }

    private int mapGeminiConfidence(int geminiConfidence) {
        if (geminiConfidence >= 80) {
            return ReceiptScanContract.CONFIDENCE_HIGH;
        }
        if (geminiConfidence >= 45) {
            return ReceiptScanContract.CONFIDENCE_MEDIUM;
        }
        return ReceiptScanContract.CONFIDENCE_LOW;
    }

    @NonNull
    private String resolveGeminiCategory(@NonNull GeminiService.ParsedReceipt parsedReceipt,
                                         @NonNull List<ReceiptItem> items,
                                         @NonNull List<String> allowedCategories) {
        String topLevelCategory = sanitizeGeminiText(parsedReceipt.getCategoryHint());
        String mappedTopLevelCategory = mapToAllowedCategory(topLevelCategory, allowedCategories);
        if (!mappedTopLevelCategory.isEmpty()) {
            return mappedTopLevelCategory;
        }
        for (GeminiService.ParsedReceiptItem parsedItem : parsedReceipt.getItems()) {
            String parsedCategory = sanitizeGeminiText(parsedItem.getCategory());
            String mappedCategory = mapToAllowedCategory(parsedCategory, allowedCategories);
            if (!mappedCategory.isEmpty()) {
                return mappedCategory;
            }
        }
        if (!items.isEmpty()) {
            String mappedFirstItemCategory = mapToAllowedCategory(
                    items.get(0).getCategoryHint(),
                    allowedCategories
            );
            if (!mappedFirstItemCategory.isEmpty()) {
                return mappedFirstItemCategory;
            }
        }
        return allowedCategories.isEmpty() ? "" : allowedCategories.get(allowedCategories.size() - 1);
    }

    @NonNull
    private String buildGeminiNoteHint(@NonNull GeminiService.ParsedReceipt parsedReceipt,
                                       @NonNull String merchant,
                                       @NonNull List<ReceiptItem> items) {
        String explicitNoteHint = sanitizeGeminiText(parsedReceipt.getNoteHint());
        if (!explicitNoteHint.isEmpty()) {
            return ensureMeaningfulVietnameseNote(explicitNoteHint, merchant, items);
        }
        return ensureMeaningfulVietnameseNote("", merchant, items);
    }

    @NonNull
    private String ensureMeaningfulVietnameseNote(@NonNull String explicitNoteHint,
                                                  @NonNull String merchant,
                                                  @NonNull List<ReceiptItem> items) {
        if (!explicitNoteHint.isEmpty()) {
            if (!merchant.isEmpty() && !containsNormalized(explicitNoteHint, merchant)) {
                return "Chi tiêu tại " + merchant + " - " + explicitNoteHint;
            }
            return explicitNoteHint;
        }

        List<String> itemNames = new ArrayList<>();
        for (ReceiptItem item : items) {
            String itemName = sanitizeGeminiText(item.getName());
            if (itemName.isEmpty()) {
                continue;
            }
            boolean duplicated = false;
            for (String existingItemName : itemNames) {
                if (normalizeForScore(existingItemName).equals(normalizeForScore(itemName))) {
                    duplicated = true;
                    break;
                }
            }
            if (!duplicated) {
                itemNames.add(itemName);
            }
            if (itemNames.size() == 2) {
                break;
            }
        }

        if (!merchant.isEmpty() && !itemNames.isEmpty()) {
            return "Chi tiêu tại " + merchant + " - " + TextUtils.join(", ", itemNames);
        }
        if (!merchant.isEmpty()) {
            return "Chi tiêu tại " + merchant;
        }
        if (!itemNames.isEmpty()) {
            return "Mua " + TextUtils.join(", ", itemNames);
        }
        return "";
    }

    @Nullable
    private GeminiService.TotalCandidate selectBestGeminiTotalCandidate(
            @NonNull GeminiService.ParsedReceipt parsedReceipt
    ) {
        GeminiService.TotalCandidate bestCandidate = null;
        int bestPriority = Integer.MIN_VALUE;
        for (GeminiService.TotalCandidate candidate : parsedReceipt.getTotalCandidates()) {
            if (!candidate.hasUsableAmount()) {
                continue;
            }
            int priority = classifyGeminiTotalLabel(candidate.getLabel());
            if (priority < 0) {
                continue;
            }
            if (bestCandidate == null
                    || priority > bestPriority
                    || (priority == bestPriority && candidate.getLineOrder() > bestCandidate.getLineOrder())
                    || (priority == bestPriority
                    && candidate.getLineOrder() == bestCandidate.getLineOrder()
                    && candidate.getAmount() > bestCandidate.getAmount())) {
                bestCandidate = candidate;
                bestPriority = priority;
            }
        }
        return bestCandidate;
    }

    private int classifyGeminiTotalLabel(@Nullable String rawLabel) {
        String label = normalizeForScore(rawLabel == null ? "" : rawLabel);
        if (label.isEmpty()) {
            return 0;
        }
        if (containsAny(
                label,
                "vat",
                "tax",
                "thue",
                "service charge",
                "phi dich vu",
                "surcharge",
                "phu thu",
                "tip",
                "giam gia",
                "discount",
                "tien khach dua",
                "khach dua",
                "cash received",
                "cash",
                "received",
                "tien thua",
                "change"
        )) {
            return -1;
        }
        if (containsAny(
                label,
                "tong thanh toan",
                "thanh toan",
                "tong phai tra",
                "tong tien thanh toan",
                "so tien thanh toan",
                "so tien phai tra",
                "tong cuoi cung",
                "tong sau thue",
                "tong sau vat",
                "grand total",
                "amount due",
                "total due",
                "total payment",
                "payment total",
                "balance due",
                "payable",
                "phai tra",
                "tien mat"
        )) {
            return 5;
        }
        if (containsAny(
                label,
                "tong cong",
                "tong tien",
                "tong",
                "t.cong",
                "t cong"
        )) {
            return 4;
        }
        if (containsAny(
                label,
                "thanh tien",
                "tam tinh",
                "subtotal",
                "sub total",
                "truoc thue",
                "pre tax",
                "before tax"
        )) {
            return 1;
        }
        return 0;
    }

    private boolean isGeminiResultPlausible(@NonNull GeminiService.ParsedReceipt parsedReceipt,
                                            @NonNull String merchant,
                                            @NonNull String amountRaw,
                                            @NonNull List<ReceiptItem> items) {
        BigDecimal amount = parseWholeMoney(amountRaw);
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return false;
        }

        BigDecimal itemSum = BigDecimal.ZERO;
        BigDecimal largestItem = BigDecimal.ZERO;
        int plausibleItemCount = 0;
        for (ReceiptItem item : items) {
            BigDecimal itemAmount = parseWholeMoney(item.getAmount());
            if (itemAmount.compareTo(BigDecimal.valueOf(MIN_PLAUSIBLE_ITEM_PRICE)) >= 0) {
                plausibleItemCount++;
            }
            if (itemAmount.compareTo(largestItem) > 0) {
                largestItem = itemAmount;
            }
            itemSum = itemSum.add(itemAmount);
        }

        if (amount.compareTo(BigDecimal.valueOf(MIN_PLAUSIBLE_RECEIPT_TOTAL)) < 0
                && (!merchant.isEmpty() || !parsedReceipt.getTotalCandidates().isEmpty() || plausibleItemCount > 0)) {
            return false;
        }

        if (largestItem.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(largestItem) < 0) {
            return false;
        }

        if (plausibleItemCount >= 2 && itemSum.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal lowerBound = itemSum.multiply(BigDecimal.valueOf(0.5d));
            if (amount.compareTo(lowerBound) < 0) {
                return false;
            }
        }

        if (!Double.isNaN(parsedReceipt.getTotal())
                && parsedReceipt.getTotal() > 0d
                && amount.compareTo(BigDecimal.valueOf(MIN_PLAUSIBLE_RECEIPT_TOTAL)) < 0
                && parsedReceipt.getTotal() >= MIN_PLAUSIBLE_RECEIPT_TOTAL) {
            return false;
        }

        return true;
    }

    @NonNull
    private String sanitizeGeminiMerchant(@Nullable String merchant) {
        String sanitized = sanitizeGeminiText(merchant);
        if (sanitized.isEmpty()) {
            return "";
        }
        String normalized = normalizeForScore(sanitized);
        if (containsAny(
                normalized,
                "thu ngan",
                "administrator",
                "ban ",
                "ban0",
                "ban1",
                "gio vao",
                "gio ra",
                "mst",
                "dien thoai",
                "sdt"
        )) {
            int separatorIndex = sanitized.indexOf(" - ");
            if (separatorIndex > 0) {
                sanitized = sanitized.substring(0, separatorIndex).trim();
            }
        }
        if (countDigits(sanitized) >= 4) {
            return "";
        }
        return sanitized;
    }

    @NonNull
    private String sanitizeGeminiText(@Nullable String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private int countDigits(@NonNull String value) {
        int digits = 0;
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) {
                digits++;
            }
        }
        return digits;
    }

    @NonNull
    private String truncateForLog(@Nullable String value) {
        String safeValue = sanitizeGeminiText(value);
        if (safeValue.length() <= 48) {
            return safeValue;
        }
        return safeValue.substring(0, 48);
    }

    private long parseGeminiDate(@Nullable String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return ReceiptScanContract.UNKNOWN_TIMESTAMP;
        }

        List<String> dateCandidates = extractDateCandidates(rawDate);
        for (String candidate : dateCandidates) {
            Long parsedTimestamp = tryParseGeminiDateCandidate(candidate);
            if (parsedTimestamp != null) {
                return parsedTimestamp;
            }
        }

        Long directParsedTimestamp = tryParseGeminiDateCandidate(rawDate.trim());
        if (directParsedTimestamp != null) {
            return directParsedTimestamp;
        }
        return ReceiptScanContract.UNKNOWN_TIMESTAMP;
    }

    @NonNull
    private List<String> resolveAllowedExpenseCategories() {
        if (categoryRepository != null && authRepository != null) {
            List<String> categoryNames = categoryRepository.getExpenseCategoryNamesSync(
                    authRepository.getCurrentUserId()
            );
            if (!categoryNames.isEmpty()) {
                return categoryNames;
            }
        }

        List<String> fallbackCategories = new ArrayList<>();
        for (Constants.DefaultCategory defaultCategory : Constants.getDefaultCategories()) {
            if (Constants.TYPE_EXPENSE.equals(defaultCategory.type)) {
                fallbackCategories.add(defaultCategory.name);
            }
        }
        return fallbackCategories;
    }

    @NonNull
    private GeminiVerificationResult verifyGeminiResultWithLocalOcr(
            @NonNull Bitmap decodedBitmap,
            @NonNull ParseExecutionResult geminiResult,
            @NonNull List<String> allowedCategories
    ) {
        Bitmap preprocessedBitmap = null;
        Bitmap deskewedBitmap = null;
        TextRecognizer textRecognizer = null;
        try {
            preprocessedBitmap = preprocessBitmap(decodedBitmap);
            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            Text firstPassText = runOcr(textRecognizer, preprocessedBitmap);
            Text recognizedText = firstPassText;
            float detectedAngle = estimateDeskewAngle(firstPassText);
            if (Math.abs(detectedAngle) >= DESKEW_THRESHOLD_DEGREES) {
                deskewedBitmap = rotateBitmap(preprocessedBitmap, -detectedAngle);
                Text secondPassText = runOcr(textRecognizer, deskewedBitmap);
                recognizedText = chooseBetterText(firstPassText, secondPassText);
            }

            String rawText = recognizedText.getText();
            boolean hasOcrText = !TextUtils.isEmpty(rawText);
            int ocrBlockCount = recognizedText.getTextBlocks().size();
            int ocrLineCount = countRecognizedLines(recognizedText);
            List<String> lines = extractRecognizedLines(recognizedText);
            ReceiptData localReceiptData = repairReceiptParser.parse(rawText, lines);

            String resolvedAmount = reconcileGeminiAmount(
                    geminiResult.receiptData,
                    localReceiptData,
                    lines
            );
            long resolvedTimestamp = reconcileGeminiTimestamp(
                    geminiResult.receiptData.getTimestamp(),
                    lines
            );
            String inferredCategory = inferCategoryFromEvidence(
                    geminiResult.receiptData.getMerchant(),
                    geminiResult.receiptData.getNoteHint(),
                    geminiResult.receiptData.getItems(),
                    lines,
                    allowedCategories
            );
            String resolvedCategory = reconcileGeminiCategory(
                    geminiResult.receiptData.getCategoryHint(),
                    localReceiptData.getCategoryHint(),
                    inferredCategory,
                    allowedCategories
            );
            String resolvedNote = reconcileGeminiNote(
                    geminiResult.receiptData.getMerchant(),
                    geminiResult.receiptData.getNoteHint(),
                    localReceiptData,
                    lines
            );
            int resolvedConfidence = reconcileGeminiConfidence(
                    geminiResult.receiptData,
                    resolvedAmount,
                    resolvedTimestamp
            );

            boolean repaired = !resolvedAmount.equals(geminiResult.receiptData.getAmount())
                    || resolvedTimestamp != geminiResult.receiptData.getTimestamp()
                    || !resolvedCategory.equals(geminiResult.receiptData.getCategoryHint())
                    || !resolvedNote.equals(geminiResult.receiptData.getNoteHint())
                    || resolvedConfidence != geminiResult.receiptData.getConfidence();

            ReceiptData verifiedReceiptData = new ReceiptData(
                    resolvedAmount,
                    resolvedTimestamp,
                    geminiResult.receiptData.getMerchant(),
                    resolvedCategory,
                    resolvedNote,
                    geminiResult.receiptData.getItems(),
                    resolvedConfidence
            );
            ReceiptParserBridge.ParseResult parseResult =
                    ReceiptParserBridge.ParseResult.fromReceiptData(
                            verifiedReceiptData,
                            geminiResult.itemsJson
                    );
            return new GeminiVerificationResult(
                    parseResult,
                    rawText,
                    hasOcrText,
                    ocrBlockCount,
                    ocrLineCount,
                    repaired
            );
        } catch (Exception exception) {
            Log.w(TAG, "Gemini verification skipped reason=" + exception.getClass().getSimpleName());
            return new GeminiVerificationResult(
                    geminiResult.parseResult,
                    "",
                    false,
                    0,
                    0,
                    false
            );
        } finally {
            if (textRecognizer != null) {
                textRecognizer.close();
            }
            recycleBitmap(deskewedBitmap);
            recycleBitmap(preprocessedBitmap);
        }
    }

    @NonNull
    private String reconcileGeminiAmount(@NonNull ReceiptData geminiReceiptData,
                                         @NonNull ReceiptData localReceiptData,
                                         @NonNull List<String> lines) {
        String strongOcrTotal = extractVerifiedOcrTotal(lines);
        if (!strongOcrTotal.isEmpty()) {
            BigDecimal strongTotal = parseWholeMoney(strongOcrTotal);
            BigDecimal geminiTotal = parseWholeMoney(geminiReceiptData.getAmount());
            if (geminiTotal.compareTo(BigDecimal.ZERO) <= 0) {
                return strongOcrTotal;
            }
            if (strongTotal.compareTo(geminiTotal) != 0) {
                return strongOcrTotal;
            }
        }

        if (isAmountLikelyHeaderNoise(geminiReceiptData.getAmount(), lines)) {
            String localAmount = localReceiptData.getAmount();
            if (!localAmount.isEmpty()) {
                return localAmount;
            }
        }

        if (!geminiReceiptData.getAmount().isEmpty()) {
            return geminiReceiptData.getAmount();
        }
        return localReceiptData.getAmount();
    }

    private long reconcileGeminiTimestamp(long geminiTimestamp, @NonNull List<String> lines) {
        Long verifiedOcrTimestamp = extractVerifiedOcrDate(lines);
        if (verifiedOcrTimestamp != null) {
            return verifiedOcrTimestamp;
        }
        if (geminiTimestamp > 0L) {
            return geminiTimestamp;
        }
        return ReceiptScanContract.UNKNOWN_TIMESTAMP;
    }

    @NonNull
    private String reconcileGeminiCategory(@Nullable String geminiCategory,
                                           @Nullable String localCategory,
                                           @Nullable String inferredCategory,
                                           @NonNull List<String> allowedCategories) {
        String mappedInferredCategory = mapToAllowedCategory(inferredCategory, allowedCategories);
        if (!mappedInferredCategory.isEmpty()
                && !normalizeForScore(mappedInferredCategory).contains("khac")) {
            return mappedInferredCategory;
        }

        String mappedGeminiCategory = mapToAllowedCategory(geminiCategory, allowedCategories);
        if (!mappedGeminiCategory.isEmpty()
                && !normalizeForScore(mappedGeminiCategory).contains("khac")) {
            return mappedGeminiCategory;
        }

        String mappedLocalCategory = mapToAllowedCategory(localCategory, allowedCategories);
        if (!mappedLocalCategory.isEmpty()) {
            return mappedLocalCategory;
        }
        if (!mappedInferredCategory.isEmpty()) {
            return mappedInferredCategory;
        }
        if (!mappedGeminiCategory.isEmpty()) {
            return mappedGeminiCategory;
        }
        return allowedCategories.isEmpty() ? "" : allowedCategories.get(allowedCategories.size() - 1);
    }

    @NonNull
    private String reconcileGeminiNote(@NonNull String merchant,
                                       @NonNull String geminiNote,
                                       @NonNull ReceiptData localReceiptData,
                                       @NonNull List<String> lines) {
        String meaningfulGeminiNote = ensureMeaningfulVietnameseNote(
                geminiNote,
                merchant,
                localReceiptData.getItems()
        );
        if (isMeaningfulVietnameseSentence(meaningfulGeminiNote)) {
            return meaningfulGeminiNote;
        }

        List<ReceiptItem> localItems = localReceiptData.getItems();
        if (!localItems.isEmpty()) {
            String localMeaningfulNote = ensureMeaningfulVietnameseNote("", merchant, localItems);
            if (isMeaningfulVietnameseSentence(localMeaningfulNote)) {
                return localMeaningfulNote;
            }
        }

        String fallbackNamedNote = buildFallbackNoteFromLines(merchant, lines);
        if (!fallbackNamedNote.isEmpty()) {
            return fallbackNamedNote;
        }
        return merchant.isEmpty() ? geminiNote : "Chi tiêu tại " + merchant;
    }

    private int reconcileGeminiConfidence(@NonNull ReceiptData geminiReceiptData,
                                          @NonNull String amount,
                                          long timestamp) {
        if (!amount.isEmpty()
                && timestamp > 0L
                && !geminiReceiptData.getMerchant().isEmpty()
                && isMeaningfulVietnameseSentence(geminiReceiptData.getNoteHint())) {
            return ReceiptScanContract.CONFIDENCE_HIGH;
        }
        if (!amount.isEmpty() && !geminiReceiptData.getMerchant().isEmpty()) {
            return ReceiptScanContract.CONFIDENCE_MEDIUM;
        }
        return ReceiptScanContract.CONFIDENCE_LOW;
    }

    @NonNull
    private List<String> extractDateCandidates(@NonNull String rawDate) {
        List<String> candidates = new ArrayList<>();
        String normalizedRawDate = normalizeForScore(rawDate);
        Matcher matcher = Pattern.compile(
                "(\\d{1,4}[./-]\\d{1,2}[./-]\\d{1,4})(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?)?"
        ).matcher(rawDate);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                candidates.add(candidate.trim());
            }
        }
        Matcher textualMatcher = Pattern.compile(
                "(\\d{1,2}[./-][A-Za-z]{3,9}[./-]\\d{2,4})(?:\\s+\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:AM|PM)?)?",
                Pattern.CASE_INSENSITIVE
        ).matcher(rawDate);
        while (textualMatcher.find()) {
            String candidate = textualMatcher.group(1);
            if (candidate != null && !candidate.trim().isEmpty()) {
                candidates.add(candidate.trim());
            }
        }
        Matcher vietnameseMatcher = Pattern.compile(
                "(\\d{1,2})\\s*thang\\s*(\\d{1,2})\\s*nam\\s*(\\d{2,4})"
        ).matcher(normalizedRawDate);
        while (vietnameseMatcher.find()) {
            candidates.add(String.format(
                    Locale.US,
                    "%s/%s/%s",
                    vietnameseMatcher.group(1),
                    vietnameseMatcher.group(2),
                    vietnameseMatcher.group(3)
            ));
        }
        return candidates;
    }

    @Nullable
    private Long extractVerifiedOcrDate(@NonNull List<String> lines) {
        Long bestTimestamp = null;
        int bestScore = Integer.MIN_VALUE;
        int maxAcceptedYear = Year.now(TimeZone.getTimeZone("UTC").toZoneId()).getValue() + 1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String normalizedLine = normalizeForScore(line);
            Matcher matcher = OCR_DATE_PATTERN.matcher(line);
            while (matcher.find()) {
                try {
                    int day = Integer.parseInt(matcher.group(1));
                    int month = Integer.parseInt(matcher.group(2));
                    int year = parseFlexibleYear(matcher.group(3));
                    if (year < 2000 || year > maxAcceptedYear) {
                        continue;
                    }
                    SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
                    dateFormat.setLenient(false);
                    dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
                    Date parsedDate = dateFormat.parse(
                            String.format(Locale.US, "%02d/%02d/%04d", day, month, year)
                    );
                    if (parsedDate == null) {
                        continue;
                    }
                    int score = 0;
                    if (normalizedLine.contains("ngay") || normalizedLine.contains("date")) {
                        score += 30;
                    }
                    if (hasAdjacentDateContext(lines, index)) {
                        score += 18;
                    }
                    if (index <= 10) {
                        score += 10;
                    }
                    if (score > bestScore) {
                        bestScore = score;
                        bestTimestamp = parsedDate.getTime();
                    }
                } catch (Exception ignored) {
                    // Skip invalid OCR date fragments.
                }
            }

            Matcher textualMatcher = OCR_DATE_TEXTUAL_MONTH_PATTERN.matcher(line);
            while (textualMatcher.find()) {
                Long parsedTimestamp = tryParseGeminiDateCandidate(textualMatcher.group(0));
                if (parsedTimestamp == null || !isPlausibleReceiptYear(new Date(parsedTimestamp))) {
                    continue;
                }
                int score = 16;
                if (normalizedLine.contains("ngay") || normalizedLine.contains("date")) {
                    score += 30;
                }
                if (hasAdjacentDateContext(lines, index)) {
                    score += 18;
                }
                if (index <= 10) {
                    score += 10;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestTimestamp = parsedTimestamp;
                }
            }
        }
        return bestTimestamp;
    }

    @NonNull
    private String extractVerifiedOcrTotal(@NonNull List<String> lines) {
        TotalLineCandidate bestCandidate = null;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String normalizedLine = normalizeForScore(line);
            int priority = resolveVerifiedTotalPriority(normalizedLine);
            if (priority < 0) {
                continue;
            }

            List<String> amounts = extractOcrAmounts(line);
            if (amounts.isEmpty() && index + 1 < lines.size()) {
                amounts = extractOcrAmounts(lines.get(index + 1));
            }
            if (amounts.isEmpty()) {
                continue;
            }

            String strongestAmount = selectLargestAmount(amounts);
            if (strongestAmount.isEmpty()) {
                continue;
            }

            TotalLineCandidate candidate = new TotalLineCandidate(
                    strongestAmount,
                    priority,
                    index
            );
            if (bestCandidate == null
                    || candidate.priority > bestCandidate.priority
                    || (candidate.priority == bestCandidate.priority && candidate.lineIndex > bestCandidate.lineIndex)
                    || (candidate.priority == bestCandidate.priority
                    && candidate.lineIndex == bestCandidate.lineIndex
                    && parseWholeMoney(candidate.amount).compareTo(parseWholeMoney(bestCandidate.amount)) > 0)) {
                bestCandidate = candidate;
            }
        }
        return bestCandidate == null ? "" : bestCandidate.amount;
    }

    private int resolveVerifiedTotalPriority(@NonNull String normalizedLine) {
        if (containsAny(normalizedLine, "dien thoai", "sdt", "tel", "mst", "ma so thue")) {
            return -1;
        }
        if (containsAny(normalizedLine, "tong cong", "tong thanh toan", "tong tien", "tong ", "t.cong", "t cong")) {
            return 3;
        }
        if (containsAny(normalizedLine, "thanh tien", "subtotal", "tam tinh")) {
            return 2;
        }
        if (containsAny(normalizedLine, "tien mat", "cash")) {
            return 1;
        }
        return -1;
    }

    @NonNull
    private List<String> extractOcrAmounts(@NonNull String line) {
        List<String> amounts = new ArrayList<>();
        Matcher matcher = MONEY_LIKE_PATTERN.matcher(line);
        while (matcher.find()) {
            String normalized = normalizeOcrMoney(matcher.group(1));
            if (!normalized.isEmpty()) {
                amounts.add(normalized);
            }
        }
        return amounts;
    }

    @NonNull
    private String normalizeOcrMoney(@Nullable String rawAmount) {
        if (rawAmount == null) {
            return "";
        }
        return rawAmount.replaceAll("[^\\d]", "");
    }

    @NonNull
    private String selectLargestAmount(@NonNull List<String> amounts) {
        String bestAmount = "";
        BigDecimal bestValue = BigDecimal.ZERO;
        for (String amount : amounts) {
            BigDecimal value = parseWholeMoney(amount);
            if (value.compareTo(bestValue) > 0) {
                bestValue = value;
                bestAmount = amount;
            }
        }
        return bestAmount;
    }

    private boolean isAmountLikelyHeaderNoise(@Nullable String rawAmount,
                                              @NonNull List<String> lines) {
        if (TextUtils.isEmpty(rawAmount)) {
            return false;
        }
        String amount = rawAmount.trim();
        if (amount.length() < 4) {
            return true;
        }
        int headerScanLimit = Math.min(lines.size(), 4);
        for (int index = 0; index < headerScanLimit; index++) {
            String normalizedLine = normalizeForScore(lines.get(index)).replaceAll("[^a-z0-9]", "");
            if (normalizedLine.contains(amount)) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private List<String> extractRecognizedLines(@NonNull Text recognizedText) {
        List<String> lines = new ArrayList<>();
        for (Text.TextBlock block : recognizedText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                if (line.getText() != null && !line.getText().trim().isEmpty()) {
                    lines.add(line.getText().trim());
                }
            }
        }
        return lines;
    }

    private int parseFlexibleYear(@NonNull String rawYear) {
        int year = Integer.parseInt(rawYear);
        if (rawYear.length() == 2) {
            return 2000 + year;
        }
        return year;
    }

    private boolean isMeaningfulVietnameseSentence(@Nullable String note) {
        if (note == null) {
            return false;
        }
        String trimmed = note.trim();
        if (trimmed.length() < 12) {
            return false;
        }
        return containsAny(
                normalizeForScore(trimmed),
                "chi tieu tai",
                "mua ",
                "thanh toan tai",
                "giao dich tai"
        );
    }

    @NonNull
    private String buildFallbackNoteFromLines(@NonNull String merchant,
                                              @NonNull List<String> lines) {
        List<String> itemNames = new ArrayList<>();
        for (String line : lines) {
            String normalizedLine = normalizeForScore(line);
            if (containsAny(normalizedLine, "tong", "thanh toan", "tien mat", "ngay", "ban", "thu ngan")) {
                continue;
            }
            if (line.matches(".*\\p{L}.*") && line.matches(".*\\d+.*")) {
                String withoutTrailingMoney = line.replaceAll("(\\d+(?:[.,\\s]\\d{3})+|\\d{4,})\\s*$", "").trim();
                withoutTrailingMoney = withoutTrailingMoney.replaceAll("^\\d+\\)\\s*", "").trim();
                withoutTrailingMoney = withoutTrailingMoney.replaceAll("^\\d+\\s*", "").trim();
                if (withoutTrailingMoney.length() >= 2) {
                    itemNames.add(withoutTrailingMoney);
                }
            }
            if (itemNames.size() == 2) {
                break;
            }
        }
        if (!merchant.isEmpty() && !itemNames.isEmpty()) {
            return "Chi tiêu tại " + merchant + " - " + TextUtils.join(", ", itemNames);
        }
        if (!merchant.isEmpty()) {
            return "Chi tiêu tại " + merchant;
        }
        return "";
    }

    @Nullable
    private Long tryParseGeminiDateCandidate(@NonNull String rawCandidate) {
        String candidate = rawCandidate.trim()
                .replace('.', '/')
                .replace('-', '/')
                .replaceAll("(?i)\\s+t\\d.*$", "")
                .replaceAll("\\s+", " ")
                .trim();
        String[] patterns = new String[]{
                "dd/MM/yyyy",
                "yyyy/MM/dd",
                "dd/MM/yy",
                "dd/MMM/yyyy",
                "dd/MMM/yy",
                "dd/MM/yyyy HH:mm:ss",
                "dd/MM/yyyy HH:mm",
                "yyyy/MM/dd HH:mm:ss",
                "yyyy/MM/dd HH:mm",
                "dd/MMM/yyyy hh:mm a",
                "dd/MMM/yy hh:mm a"
        };
        for (String pattern : patterns) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(pattern, Locale.US);
            dateFormat.setLenient(false);
            dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                Date parsedDate = dateFormat.parse(candidate);
                if (parsedDate != null) {
                    if (isPlausibleReceiptYear(parsedDate)) {
                        return parsedDate.getTime();
                    }
                }
            } catch (ParseException ignored) {
                // Try next supported format.
            }
        }
        return null;
    }

    private boolean hasAdjacentDateContext(@NonNull List<String> lines, int index) {
        int from = Math.max(0, index - 1);
        int to = Math.min(lines.size() - 1, index + 1);
        for (int scanIndex = from; scanIndex <= to; scanIndex++) {
            String normalizedLine = normalizeForScore(lines.get(scanIndex));
            if (normalizedLine.contains("ngay") || normalizedLine.contains("date")) {
                return true;
            }
        }
        return false;
    }

    @NonNull
    private String inferCategoryFromEvidence(@Nullable String merchant,
                                             @Nullable String noteHint,
                                             @NonNull List<ReceiptItem> items,
                                             @NonNull List<String> lines,
                                             @NonNull List<String> allowedCategories) {
        if (allowedCategories.isEmpty()) {
            return "";
        }

        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String allowedCategory : allowedCategories) {
            scores.put(allowedCategory, 0);
        }

        bumpCategoryScores(scores, merchant, 8, allowedCategories);
        bumpCategoryScores(scores, noteHint, 4, allowedCategories);
        for (ReceiptItem item : items) {
            bumpCategoryScores(scores, item.getName(), 5, allowedCategories);
            bumpCategoryScores(scores, item.getCategoryHint(), 4, allowedCategories);
        }
        for (String line : lines) {
            bumpCategoryScores(scores, line, 2, allowedCategories);
        }

        String bestCategory = "";
        int bestScore = 0;
        for (Map.Entry<String, Integer> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestCategory = entry.getKey();
            }
        }
        return bestScore >= 4 ? bestCategory : "";
    }

    private void bumpCategoryScores(@NonNull Map<String, Integer> scores,
                                    @Nullable String evidence,
                                    int weight,
                                    @NonNull List<String> allowedCategories) {
        String normalizedEvidence = normalizeForScore(evidence == null ? "" : evidence);
        if (normalizedEvidence.isEmpty()) {
            return;
        }

        incrementCategoryScore(scores, allowedCategories, "an uong", weight, normalizedEvidence,
                "an uong", "am thuc", "nha hang", "quan an", "com", "pho", "bun", "mien", "chao", "lau",
                "nuong", "buffet", "tra sua", "ca phe", "cafe", "coffee", "tea", "tra", "smoothie",
                "sinh to", "ice cream", "kem", "cake", "banh", "banh mi", "ga ran", "pizza", "hamburger",
                "sandwich", "thuc an nhanh", "do an", "hai san", "tiec", "lien hoan", "fast food",
                "restaurant", "milk tea", "bread", "seafood", "party", "kfc", "lotteria", "mcdonald",
                "pizza hut", "domino", "burger king", "jollibee", "popeyes", "starbucks", "highlands coffee",
                "the coffee house", "trung nguyen", "phuc long", "tous les jours", "paris baguette",
                "grabfood", "shopeefood", "now.vn", "baemin", "nuoc ngot", "soda", "sprite", "coca",
                "pepsi", "tonic", "beer");
        incrementCategoryScore(scores, allowedCategories, "di chuyen", weight, normalizedEvidence,
                "di chuyen", "di lai", "xe buyt", "xe khach", "xe lua", "tau hoa", "may bay", "taxi",
                "xe om", "xe dap", "xe may", "o to", "xang", "dau", "nhien lieu", "gui xe", "bai xe",
                "cau duong", "phi cau", "ve tau", "ve xe", "ve may bay", "thue xe", "transport", "bus",
                "coach", "train", "plane", "flight", "motorbike", "bicycle", "car", "petrol", "fuel",
                "parking", "toll", "ticket", "rental", "grab", "gojek", "be", "xanh sm", "mai linh",
                "vinasun", "vietjet", "vietjet air", "vietnam airlines", "bamboo airways",
                "pacific airlines", "uber", "mrt", "lrt", "metro", "be car", "be bike");
        incrementCategoryScore(scores, allowedCategories, "mua sam", weight, normalizedEvidence,
                "mua sam", "sieu thi", "cua hang", "tap hoa", "cho", "trung tam thuong mai", "quan ao",
                "giay dep", "tui xach", "my pham", "dien tu", "dien may", "noi that", "do gia dung",
                "van phong pham", "sach", "bao", "tap chi", "vang bac", "trang suc", "supermarket",
                "store", "market", "mall", "clothing", "shoes", "bag", "cosmetics", "electronics",
                "furniture", "household", "stationery", "book", "newspaper", "jewelry", "coopmart",
                "winmart", "big c", "lotte mart", "aeon", "mm mega market", "circle k", "family mart",
                "ministop", "gs25", "7-eleven", "bach hoa xanh", "shopee", "lazada", "tiki", "sendo",
                "amazon", "ebay", "zalora", "shein", "uniqlo", "zara", "h&m", "nike", "adidas",
                "converse", "the gioi di dong", "fpt shop", "cellphones", "cellphones", "dien may xanh",
                "nguyen kim", "mediamart", "mart", "shop");
        incrementCategoryScore(scores, allowedCategories, "giai tri", weight, normalizedEvidence,
                "giai tri", "rap phim", "ve xem phim", "cgv", "lotte cinema", "galaxy cinema", "bhd",
                "cinestar", "phim", "suat chieu", "phong chieu", "gold class", "2d", "3d", "4dx",
                "imax", "concert", "ca nhac", "su kien", "hoa nhac", "karaoke", "game", "tro choi",
                "the thao dien tu", "thue phim truc tuyen", "netflix", "hbo", "disney+", "entertainment",
                "cinema", "movie", "ticket", "film", "theater", "show", "music", "event", "esports",
                "streaming", "apple tv", "amazon prime", "spotify", "steam", "xbox", "playstation",
                "nintendo", "xem phim", "the vao");
        incrementCategoryScore(scores, allowedCategories, "y te", weight, normalizedEvidence,
                "y te", "suc khoe", "benh vien", "phong kham", "bac si", "y ta", "thuoc", "nha thuoc",
                "kham benh", "chua benh", "dieu tri", "xet nghiem", "tiem chung", "vaccine",
                "bao hiem y te", "nha khoa", "rang", "mat", "tai mui hong", "da lieu", "vat ly tri lieu",
                "health", "hospital", "clinic", "doctor", "nurse", "medicine", "pharmacy", "medical",
                "checkup", "treatment", "test", "vaccination", "insurance", "dental", "dentist",
                "eye", "ent", "dermatology", "physical therapy", "pharmacity", "long chau", "medicare",
                "bao viet", "prudential", "kham");
        incrementCategoryScore(scores, allowedCategories, "giao duc", weight, normalizedEvidence,
                "giao duc", "hoc phi", "truong hoc", "dai hoc", "cao dang", "trung cap", "lop hoc",
                "khoa hoc", "luyen thi", "gia su", "sach giao khoa", "tai lieu", "van phong pham",
                "do dung hoc tap", "thi cu", "bang cap", "hoc bong", "ngoai ngu", "tin hoc",
                "education", "tuition", "school", "college", "university", "class", "course",
                "tutoring", "textbook", "materials", "stationery", "exam", "degree", "scholarship",
                "language", "it", "ielts", "toefl", "toeic", "cambridge", "english center", "aptech",
                "niit", "sach", "book", "truong", "center");
        incrementCategoryScore(scores, allowedCategories, "hoa don", weight, normalizedEvidence,
                "hoa don", "tien ich", "dien", "nuoc", "internet", "wifi", "truyen hinh", "cap",
                "dien thoai", "di dong", "gas", "rac", "phi quan ly", "phi dich vu", "bao tri",
                "bill", "utility", "electricity", "water", "tv", "cable", "phone", "mobile",
                "management fee", "service fee", "maintenance", "evn", "saigon water", "viettel",
                "vnpt", "mobifone", "vinaphone", "fpt telecom", "cmc", "sctv", "k+", "netflix",
                "spotify", "youtube premium", "dien luc", "fpt");
        incrementCategoryScore(scores, allowedCategories, "nha o", weight, normalizedEvidence,
                "nha o", "can ho", "chung cu", "thue nha", "mua nha", "sua nha", "cai tao", "noi that",
                "do go", "giuong tu", "ban ghe", "thiet bi bep", "tu lanh", "may giat", "dieu hoa",
                "may suoi", "binh nong lanh", "quat", "den", "khoa cua", "son tuong", "housing",
                "apartment", "condo", "rent", "buy", "repair", "renovation", "furniture", "bed",
                "table", "kitchen", "fridge", "washer", "ac", "heater", "water heater", "fan",
                "lamp", "lock", "paint", "ikea", "home depot", "kingliving", "boconcept", "room");
        incrementCategoryScore(scores, allowedCategories, "du lich", weight, normalizedEvidence,
                "du lich", "khach san", "hotel", "may bay", "flight", "booking", "resort");
        incrementCategoryScore(scores, allowedCategories, "lam dep", weight, normalizedEvidence,
                "lam dep", "spa", "tham my vien", "salon toc", "cat toc", "goi dau", "uon toc",
                "nhuom toc", "lam mong", "nail", "trang diem", "my pham", "duong da", "cham soc da",
                "xong hoi", "massage", "waxing", "triet long", "beauty", "cosmetics", "makeup",
                "skincare", "haircut", "hairstyle", "manicure", "pedicure", "facial", "laser",
                "korean spa", "american nail", "m.o.i", "l'oreal", "maybelline", "olay", "lancome");
        incrementCategoryScore(scores, allowedCategories, "the thao", weight, normalizedEvidence,
                "the thao", "gym", "tap gym", "the hinh", "yoga", "pilates", "boi loi", "bong da",
                "bong ro", "cau long", "tennis", "chay bo", "dap xe", "vo thuat", "boi",
                "dung cu the thao", "san bong", "ve xem the thao", "cau lac bo the thao", "sports",
                "fitness", "workout", "swimming", "football", "soccer", "basketball", "badminton",
                "running", "cycling", "martial arts", "equipment", "stadium", "club", "membership",
                "peloton", "nike training club", "adidas runtastic");
        incrementCategoryScore(scores, allowedCategories, "thu cung", weight, normalizedEvidence,
                "thu cung", "cho", "meo", "chim", "ca", "hamster", "thuc an thu cung", "phu kien",
                "long", "chuong", "bac si thu y", "tiem phong", "tam", "cat tia long", "huan luyen",
                "pet", "vet", "dog", "cat", "bird", "fish", "food", "accessory", "cage", "kennel",
                "vaccination", "grooming", "training", "petmart", "petcity", "vetpet");
        incrementCategoryScore(scores, allowedCategories, "khac chi", weight, normalizedEvidence,
                "khac", "linh tinh", "chi tieu khac", "misc", "other", "khong xac dinh",
                "miscellaneous", "unknown", "uncategorized");
    }

    private void incrementCategoryScore(@NonNull Map<String, Integer> scores,
                                        @NonNull List<String> allowedCategories,
                                        @NonNull String canonicalCategory,
                                        int weight,
                                        @NonNull String normalizedEvidence,
                                        @NonNull String... keywords) {
        String allowedCategory = findAllowedCategoryExact(canonicalCategory, allowedCategories);
        if (allowedCategory.isEmpty()) {
            return;
        }
        for (String keyword : keywords) {
            if (normalizedEvidence.contains(keyword)) {
                Integer currentScore = scores.get(allowedCategory);
                scores.put(allowedCategory, (currentScore == null ? 0 : currentScore) + weight);
                return;
            }
        }
    }

    private boolean isPlausibleReceiptYear(@NonNull Date parsedDate) {
        SimpleDateFormat yearFormat = new SimpleDateFormat("yyyy", Locale.US);
        yearFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        int year = Integer.parseInt(yearFormat.format(parsedDate));
        int maxAcceptedYear = Year.now(TimeZone.getTimeZone("UTC").toZoneId()).getValue() + 1;
        return year >= 2000 && year <= maxAcceptedYear;
    }

    @NonNull
    private String mapToAllowedCategory(@Nullable String rawCategory,
                                        @NonNull List<String> allowedCategories) {
        String category = sanitizeGeminiText(rawCategory);
        if (category.isEmpty()) {
            return "";
        }

        String normalizedCategory = normalizeForScore(category)
                .replace("hoa don va tien ich", "hoa don")
                .replace("hoa don tien ich", "hoa don")
                .replace("suc khoe", "y te")
                .replace("khac", "khac chi");

        String exactMatch = findAllowedCategoryExact(normalizedCategory, allowedCategories);
        if (!exactMatch.isEmpty()) {
            return exactMatch;
        }

        if (containsAny(normalizedCategory, "an uong", "am thuc", "ca phe", "quan an", "nha hang")) {
            return resolveAllowedCategoryAlias(allowedCategories, "an uong");
        }
        if (containsAny(normalizedCategory, "di chuyen", "taxi", "grab", "xang", "xe")) {
            return resolveAllowedCategoryAlias(allowedCategories, "di chuyen");
        }
        if (containsAny(normalizedCategory, "mua sam", "sieu thi", "tap hoa", "cua hang")) {
            return resolveAllowedCategoryAlias(allowedCategories, "mua sam");
        }
        if (containsAny(normalizedCategory,
                "giai tri", "xem phim", "game", "cinema", "movie", "cgv", "lotte cinema",
                "galaxy cinema", "ticket", "the vao", "phong chieu", "rap phim", "gold class", "phim")) {
            return resolveAllowedCategoryAlias(allowedCategories, "giai tri");
        }
        if (containsAny(normalizedCategory, "y te", "thuoc", "benh vien", "kham")) {
            return resolveAllowedCategoryAlias(allowedCategories, "y te");
        }
        if (containsAny(normalizedCategory, "giao duc", "hoc phi", "sach")) {
            return resolveAllowedCategoryAlias(allowedCategories, "giao duc");
        }
        if (containsAny(normalizedCategory, "hoa don", "dien", "nuoc", "internet")) {
            return resolveAllowedCategoryAlias(allowedCategories, "hoa don");
        }
        if (containsAny(normalizedCategory, "nha o", "thue nha", "phong")) {
            return resolveAllowedCategoryAlias(allowedCategories, "nha o");
        }
        if (containsAny(normalizedCategory, "du lich", "khach san", "may bay")) {
            return resolveAllowedCategoryAlias(allowedCategories, "du lich");
        }
        if (containsAny(normalizedCategory, "lam dep", "spa", "nail", "makeup", "skincare", "salon")) {
            return resolveAllowedCategoryAlias(allowedCategories, "lam dep", "mua sam", "khac chi");
        }
        if (containsAny(normalizedCategory, "the thao", "gym", "fitness", "yoga", "pilates", "stadium")) {
            return resolveAllowedCategoryAlias(allowedCategories, "the thao", "giai tri", "khac chi");
        }
        if (containsAny(normalizedCategory, "thu cung", "pet", "vet", "dog", "cat", "petmart", "petcity")) {
            return resolveAllowedCategoryAlias(allowedCategories, "thu cung", "khac chi");
        }
        if (containsAny(normalizedCategory, "khac")) {
            return resolveAllowedCategoryAlias(allowedCategories, "khac chi");
        }

        for (String allowedCategory : allowedCategories) {
            String normalizedAllowedCategory = normalizeForScore(allowedCategory);
            if (normalizedAllowedCategory.contains(normalizedCategory)
                    || normalizedCategory.contains(normalizedAllowedCategory)) {
                return allowedCategory;
            }
        }
        return "";
    }

    @NonNull
    private String resolveAllowedCategoryAlias(@NonNull List<String> allowedCategories,
                                               @NonNull String primaryCategory,
                                               @NonNull String... fallbackCategories) {
        String resolvedPrimaryCategory = findAllowedCategoryExact(primaryCategory, allowedCategories);
        if (!resolvedPrimaryCategory.isEmpty()) {
            return resolvedPrimaryCategory;
        }
        for (String fallbackCategory : fallbackCategories) {
            String resolvedFallbackCategory = findAllowedCategoryExact(fallbackCategory, allowedCategories);
            if (!resolvedFallbackCategory.isEmpty()) {
                return resolvedFallbackCategory;
            }
        }
        return "";
    }

    @NonNull
    private String findAllowedCategoryExact(@NonNull String normalizedNeedle,
                                            @NonNull List<String> allowedCategories) {
        for (String allowedCategory : allowedCategories) {
            if (normalizeForScore(allowedCategory).equals(normalizedNeedle)) {
                return allowedCategory;
            }
        }
        return "";
    }

    private boolean containsNormalized(@NonNull String text, @NonNull String probe) {
        return normalizeForScore(text).contains(normalizeForScore(probe));
    }

    @NonNull
    private String serializeItems(@NonNull List<ReceiptItem> items) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int index = 0; index < items.size(); index++) {
            ReceiptItem item = items.get(index);
            if (index > 0) {
                builder.append(',');
            }
            builder.append('{')
                    .append("\"name\":\"").append(escapeJson(item.getName())).append("\",")
                    .append("\"note_hint\":\"").append(escapeJson(item.getName())).append("\",")
                    .append("\"amount\":\"").append(escapeJson(item.getAmount())).append("\",")
                    .append("\"category_hint\":\"").append(escapeJson(item.getCategoryHint())).append("\",")
                    .append("\"confidence\":").append(item.getConfidence())
                    .append('}');
        }
        builder.append(']');
        return builder.toString();
    }

    @NonNull
    private String escapeJson(@NonNull String value) {
        StringBuilder builder = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\':
                    builder.append("\\\\");
                    break;
                case '"':
                    builder.append("\\\"");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    builder.append(character);
                    break;
            }
        }
        return builder.toString();
    }

    private static boolean isNetworkAvailable(@NonNull Context context) {
        ConnectivityManager connectivityManager = context.getSystemService(ConnectivityManager.class);
        if (connectivityManager == null) {
            return false;
        }
        Network activeNetwork = connectivityManager.getActiveNetwork();
        if (activeNetwork == null) {
            return false;
        }
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(activeNetwork);
        if (capabilities == null) {
            return false;
        }
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    private static boolean isGeminiRateLimited(@NonNull Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_OCR, Context.MODE_PRIVATE);
        long rateLimitUntil = preferences.getLong(PREF_KEY_GEMINI_RATE_LIMIT_UNTIL, 0L);
        if (rateLimitUntil <= 0L) {
            return false;
        }
        if (System.currentTimeMillis() >= rateLimitUntil) {
            preferences.edit().remove(PREF_KEY_GEMINI_RATE_LIMIT_UNTIL).apply();
            return false;
        }
        return true;
    }

    private void storeGeminiRateLimitCooldown(long retryAfterSeconds) {
        long cooldownMs = retryAfterSeconds > 0L
                ? TimeUnit.SECONDS.toMillis(retryAfterSeconds)
                : DEFAULT_GEMINI_RATE_LIMIT_COOLDOWN_MS;
        long rateLimitUntil = System.currentTimeMillis() + cooldownMs;
        getApplicationContext()
                .getSharedPreferences(PREFS_OCR, Context.MODE_PRIVATE)
                .edit()
                .putLong(PREF_KEY_GEMINI_RATE_LIMIT_UNTIL, rateLimitUntil)
                .apply();
    }

    @NonNull
    private ResolvedReceiptImage resolveInputImage() throws PermanentWorkerException {
        Data inputData = getInputData();
        String imagePath = inputData.getString(KEY_IMAGE_PATH);
        String imageUri = inputData.getString(KEY_IMAGE_URI);

        if (!TextUtils.isEmpty(imagePath)) {
            return resolveFromPath(imagePath, imageUri);
        }

        if (!TextUtils.isEmpty(imageUri)) {
            Uri parsedUri = Uri.parse(imageUri);
            if (parsedUri == null || parsedUri.getScheme() == null) {
                throw new PermanentWorkerException(
                        ReceiptScanContract.ERROR_INVALID_IMAGE_REFERENCE,
                        ReceiptScanContract.STAGE_INPUT
                );
            }
            if ("file".equalsIgnoreCase(parsedUri.getScheme()) && !TextUtils.isEmpty(parsedUri.getPath())) {
                return resolveFromPath(parsedUri.getPath(), imageUri);
            }
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_INVALID_IMAGE_REFERENCE,
                    ReceiptScanContract.STAGE_INPUT
            );
        }

        throw new PermanentWorkerException(
                ReceiptScanContract.ERROR_MISSING_IMAGE_INPUT,
                ReceiptScanContract.STAGE_INPUT
        );
    }

    @NonNull
    private ResolvedReceiptImage resolveFromPath(@Nullable String rawPath,
                                                 @Nullable String providedUri)
            throws PermanentWorkerException {
        if (TextUtils.isEmpty(rawPath)) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_MISSING_IMAGE_INPUT,
                    ReceiptScanContract.STAGE_INPUT
            );
        }

        File file = new File(rawPath);
        try {
            file = file.getCanonicalFile();
        } catch (IOException exception) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_INVALID_IMAGE_REFERENCE,
                    ReceiptScanContract.STAGE_INPUT
            );
        }

        File internalRoot = getApplicationContext().getFilesDir();
        if (!isUnderInternalStorage(file, internalRoot)) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_INVALID_IMAGE_REFERENCE,
                    ReceiptScanContract.STAGE_INPUT
            );
        }
        if (!file.exists() || !file.isFile()) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_NOT_FOUND,
                    ReceiptScanContract.STAGE_INPUT
            );
        }

        String normalizedUri = !TextUtils.isEmpty(providedUri)
                ? providedUri
                : Uri.fromFile(file).toString();
        return new ResolvedReceiptImage(file, file.getAbsolutePath(), normalizedUri);
    }

    private boolean isUnderInternalStorage(@NonNull File candidate, @NonNull File internalRoot)
            throws PermanentWorkerException {
        try {
            String candidatePath = candidate.getCanonicalPath();
            String internalRootPath = internalRoot.getCanonicalPath();
            return candidatePath.equals(internalRootPath)
                    || candidatePath.startsWith(internalRootPath + File.separator);
        } catch (IOException exception) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_INVALID_IMAGE_REFERENCE,
                    ReceiptScanContract.STAGE_INPUT
            );
        }
    }

    @NonNull
    private Bitmap loadBitmap(@NonNull File imageFile) throws PermanentWorkerException {
        BitmapFactory.Options boundsOptions = new BitmapFactory.Options();
        boundsOptions.inJustDecodeBounds = true;
        decodeBounds(imageFile, boundsOptions);

        if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_DECODE_FAILED,
                    ReceiptScanContract.STAGE_DECODE
            );
        }

        BitmapFactory.Options decodeOptions = new BitmapFactory.Options();
        decodeOptions.inPreferredConfig = Bitmap.Config.ARGB_8888;
        decodeOptions.inSampleSize = calculateInSampleSize(
                boundsOptions.outWidth,
                boundsOptions.outHeight
        );

        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeFile(imageFile.getAbsolutePath(), decodeOptions);
        } catch (OutOfMemoryError error) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_DECODE_FAILED,
                    ReceiptScanContract.STAGE_DECODE
            );
        }
        if (bitmap == null) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_DECODE_FAILED,
                    ReceiptScanContract.STAGE_DECODE
            );
        }
        int rotationDegrees = readExifRotationDegrees(imageFile);
        if (rotationDegrees == 0) {
            return bitmap;
        }
        Bitmap rotatedBitmap = rotateBitmap(bitmap, rotationDegrees);
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    private void decodeBounds(@NonNull File imageFile,
                              @NonNull BitmapFactory.Options boundsOptions)
            throws PermanentWorkerException {
        try (InputStream inputStream = new FileInputStream(imageFile)) {
            BitmapFactory.decodeStream(inputStream, null, boundsOptions);
        } catch (FileNotFoundException exception) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_NOT_FOUND,
                    ReceiptScanContract.STAGE_INPUT
            );
        } catch (IOException exception) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_DECODE_FAILED,
                    ReceiptScanContract.STAGE_DECODE
            );
        }
    }

    private int calculateInSampleSize(int width, int height) {
        int sampleSize = 1;
        while ((width / sampleSize) > MAX_DECODE_BITMAP_WIDTH_PX) {
            sampleSize *= 2;
        }
        return Math.max(sampleSize, 1);
    }

    @NonNull
    private Bitmap preprocessBitmap(@NonNull Bitmap sourceBitmap) throws PermanentWorkerException {
        try {
            Bitmap scaledBitmap = resizeBitmapToWidthRange(
                    sourceBitmap,
                    LOCAL_TARGET_BITMAP_WIDTH_PX,
                    LOCAL_TARGET_BITMAP_WIDTH_PX
            );
            Bitmap grayscaleBitmap = Bitmap.createBitmap(
                    scaledBitmap.getWidth(),
                    scaledBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(grayscaleBitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

            ColorMatrix grayscaleMatrix = new ColorMatrix();
            grayscaleMatrix.setSaturation(0f);

            float contrast = 1.18f;
            float translate = (-0.5f * contrast + 0.5f) * 255f;
            ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
            });
            grayscaleMatrix.postConcat(contrastMatrix);

            paint.setColorFilter(new ColorMatrixColorFilter(grayscaleMatrix));
            canvas.drawBitmap(scaledBitmap, 0f, 0f, paint);
            if (scaledBitmap != sourceBitmap) {
                scaledBitmap.recycle();
            }

            int width = grayscaleBitmap.getWidth();
            int height = grayscaleBitmap.getHeight();
            int[] pixels = new int[width * height];
            grayscaleBitmap.getPixels(pixels, 0, width, 0, 0, width, height);

            int[] luminance = extractLuminance(pixels);
            int[] blurred = applyBoxBlur(luminance, width, height, 1);
            int threshold = computeOtsuThreshold(blurred);

            Bitmap binaryBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            applyThreshold(blurred, binaryBitmap, width, height, threshold);
            grayscaleBitmap.recycle();
            return binaryBitmap;
        } catch (IllegalArgumentException exception) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_PREPROCESS_FAILED,
                    ReceiptScanContract.STAGE_PREPROCESS
            );
        } catch (OutOfMemoryError error) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_PREPROCESS_FAILED,
                    ReceiptScanContract.STAGE_PREPROCESS
            );
        }
    }

    @NonNull
    private Bitmap prepareGeminiBitmap(@NonNull Bitmap sourceBitmap) {
        return resizeBitmapToWidthRange(
                sourceBitmap,
                GEMINI_MIN_BITMAP_WIDTH_PX,
                GEMINI_MAX_BITMAP_WIDTH_PX
        );
    }

    @NonNull
    private Bitmap prepareGeminiEnhancedBitmap(@NonNull Bitmap sourceBitmap) throws PermanentWorkerException {
        Bitmap workingBitmap = resizeBitmapToWidthRange(
                sourceBitmap,
                GEMINI_MIN_BITMAP_WIDTH_PX,
                GEMINI_MAX_BITMAP_WIDTH_PX
        );
        try {
            Bitmap enhancedBitmap = Bitmap.createBitmap(
                    workingBitmap.getWidth(),
                    workingBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(enhancedBitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

            ColorMatrix grayscaleMatrix = new ColorMatrix();
            grayscaleMatrix.setSaturation(0f);

            float contrast = 1.35f;
            float translate = (-0.5f * contrast + 0.5f) * 255f;
            ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
            });
            grayscaleMatrix.postConcat(contrastMatrix);

            paint.setColorFilter(new ColorMatrixColorFilter(grayscaleMatrix));
            canvas.drawBitmap(workingBitmap, 0f, 0f, paint);
            return enhancedBitmap;
        } catch (IllegalArgumentException exception) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_PREPROCESS_FAILED,
                    ReceiptScanContract.STAGE_PREPROCESS
            );
        } catch (OutOfMemoryError error) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_PREPROCESS_FAILED,
                    ReceiptScanContract.STAGE_PREPROCESS
            );
        } finally {
            if (workingBitmap != sourceBitmap) {
                workingBitmap.recycle();
            }
        }
    }

    @NonNull
    private Bitmap resizeBitmapToWidthRange(@NonNull Bitmap sourceBitmap,
                                            int minWidth,
                                            int maxWidth) {
        int sourceWidth = sourceBitmap.getWidth();
        if (sourceWidth >= minWidth && sourceWidth <= maxWidth) {
            return sourceBitmap;
        }
        int targetWidth = sourceWidth < minWidth ? minWidth : maxWidth;
        float scale = targetWidth / (float) sourceWidth;
        int targetHeight = Math.max(1, Math.round(sourceBitmap.getHeight() * scale));
        return Bitmap.createScaledBitmap(sourceBitmap, targetWidth, targetHeight, true);
    }

    private int readExifRotationDegrees(@NonNull File imageFile) throws PermanentWorkerException {
        try {
            ExifInterface exifInterface = new ExifInterface(imageFile.getAbsolutePath());
            int orientation = exifInterface.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
            );
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90) {
                return 90;
            }
            if (orientation == ExifInterface.ORIENTATION_ROTATE_180) {
                return 180;
            }
            if (orientation == ExifInterface.ORIENTATION_ROTATE_270) {
                return 270;
            }
            return 0;
        } catch (IOException exception) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_DECODE_FAILED,
                    ReceiptScanContract.STAGE_DECODE
            );
        }
    }

    @NonNull
    private int[] extractLuminance(@NonNull int[] pixels) {
        int[] luminance = new int[pixels.length];
        for (int index = 0; index < pixels.length; index++) {
            int color = pixels[index];
            luminance[index] = (int) (0.299f * Color.red(color)
                    + 0.587f * Color.green(color)
                    + 0.114f * Color.blue(color));
        }
        return luminance;
    }

    @NonNull
    private int[] applyBoxBlur(@NonNull int[] source, int width, int height, int radius) {
        if (radius <= 0) {
            return source.clone();
        }
        int[] blurred = new int[source.length];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int sum = 0;
                int count = 0;
                for (int offsetY = -radius; offsetY <= radius; offsetY++) {
                    int sampleY = clamp(y + offsetY, 0, height - 1);
                    for (int offsetX = -radius; offsetX <= radius; offsetX++) {
                        int sampleX = clamp(x + offsetX, 0, width - 1);
                        sum += source[sampleY * width + sampleX];
                        count++;
                    }
                }
                blurred[y * width + x] = count == 0 ? source[y * width + x] : (sum / count);
            }
        }
        return blurred;
    }

    private int computeOtsuThreshold(@NonNull int[] luminance) {
        int[] histogram = new int[256];
        for (int value : luminance) {
            histogram[clamp(value, 0, 255)]++;
        }

        int total = luminance.length;
        long sum = 0L;
        for (int index = 0; index < histogram.length; index++) {
            sum += (long) index * histogram[index];
        }

        long backgroundSum = 0L;
        int backgroundWeight = 0;
        double bestVariance = -1d;
        int bestThreshold = 127;
        for (int threshold = 0; threshold < histogram.length; threshold++) {
            backgroundWeight += histogram[threshold];
            if (backgroundWeight == 0) {
                continue;
            }

            int foregroundWeight = total - backgroundWeight;
            if (foregroundWeight == 0) {
                break;
            }

            backgroundSum += (long) threshold * histogram[threshold];
            double backgroundMean = backgroundSum / (double) backgroundWeight;
            double foregroundMean = (sum - backgroundSum) / (double) foregroundWeight;
            double variance = backgroundWeight * (double) foregroundWeight
                    * Math.pow(backgroundMean - foregroundMean, 2);
            if (variance > bestVariance) {
                bestVariance = variance;
                bestThreshold = threshold;
            }
        }
        return bestThreshold;
    }

    private void applyThreshold(@NonNull int[] luminance,
                                @NonNull Bitmap targetBitmap,
                                int width,
                                int height,
                                int threshold) {
        int[] output = new int[luminance.length];
        for (int index = 0; index < luminance.length; index++) {
            output[index] = luminance[index] >= threshold ? Color.WHITE : Color.BLACK;
        }
        targetBitmap.setPixels(output, 0, width, 0, 0, width, height);
    }

    private int clamp(int value, int minValue, int maxValue) {
        if (value < minValue) {
            return minValue;
        }
        if (value > maxValue) {
            return maxValue;
        }
        return value;
    }

    private float estimateDeskewAngle(@NonNull Text recognizedText) {
        float weightedSum = 0f;
        float weightTotal = 0f;
        for (Text.TextBlock block : recognizedText.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Float lineAngle = estimateAngleFromCornerPoints(line.getCornerPoints());
                float lineWeight = estimatePolygonWidth(line.getCornerPoints());
                if (lineAngle != null && lineWeight > 0f) {
                    float boundedAngle = normalizeAngle(lineAngle);
                    if (Math.abs(boundedAngle) <= MAX_ACCEPTED_ANGLE_DEGREES) {
                        weightedSum += boundedAngle * lineWeight;
                        weightTotal += lineWeight;
                        continue;
                    }
                }

                for (Text.Element element : line.getElements()) {
                    Float elementAngle = estimateAngleFromCornerPoints(element.getCornerPoints());
                    if (elementAngle == null) {
                        continue;
                    }
                    float boundedAngle = normalizeAngle(elementAngle);
                    if (Math.abs(boundedAngle) > MAX_ACCEPTED_ANGLE_DEGREES) {
                        continue;
                    }
                    float elementWeight = estimatePolygonWidth(element.getCornerPoints());
                    if (elementWeight <= 0f) {
                        elementWeight = Math.max(1f, element.getText().length());
                    }
                    weightedSum += boundedAngle * elementWeight;
                    weightTotal += elementWeight;
                }
            }
        }
        return weightTotal == 0f ? 0f : weightedSum / weightTotal;
    }

    @Nullable
    private Float estimateAngleFromCornerPoints(@Nullable Point[] cornerPoints) {
        if (cornerPoints == null || cornerPoints.length < 2 || cornerPoints[0] == null || cornerPoints[1] == null) {
            return null;
        }
        float deltaX = cornerPoints[1].x - cornerPoints[0].x;
        float deltaY = cornerPoints[1].y - cornerPoints[0].y;
        if (deltaX == 0f && deltaY == 0f) {
            return null;
        }
        return (float) Math.toDegrees(Math.atan2(deltaY, deltaX));
    }

    private float estimatePolygonWidth(@Nullable Point[] cornerPoints) {
        if (cornerPoints == null || cornerPoints.length < 2 || cornerPoints[0] == null || cornerPoints[1] == null) {
            return 0f;
        }
        float deltaX = cornerPoints[1].x - cornerPoints[0].x;
        float deltaY = cornerPoints[1].y - cornerPoints[0].y;
        return (float) Math.hypot(deltaX, deltaY);
    }

    private float normalizeAngle(float angle) {
        while (angle > 90f) {
            angle -= 180f;
        }
        while (angle < -90f) {
            angle += 180f;
        }
        return angle;
    }

    @NonNull
    private Bitmap rotateBitmap(@NonNull Bitmap sourceBitmap, float angleDegrees)
            throws PermanentWorkerException {
        try {
            Matrix matrix = new Matrix();
            matrix.postRotate(angleDegrees);
            return Bitmap.createBitmap(
                    sourceBitmap,
                    0,
                    0,
                    sourceBitmap.getWidth(),
                    sourceBitmap.getHeight(),
                    matrix,
                    true
            );
        } catch (IllegalArgumentException exception) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_PREPROCESS_FAILED,
                    ReceiptScanContract.STAGE_PREPROCESS
            );
        } catch (OutOfMemoryError error) {
            throw new PermanentWorkerException(
                    ReceiptScanContract.ERROR_IMAGE_PREPROCESS_FAILED,
                    ReceiptScanContract.STAGE_PREPROCESS
            );
        }
    }

    @NonNull
    private Text runOcr(@NonNull TextRecognizer textRecognizer,
                        @NonNull Bitmap processedBitmap) throws RetryableWorkerException {
        InputImage inputImage = InputImage.fromBitmap(processedBitmap, 0);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Text> resultReference = new AtomicReference<>();
        AtomicReference<Exception> errorReference = new AtomicReference<>();

        textRecognizer.process(inputImage)
                .addOnSuccessListener(result -> {
                    resultReference.set(result);
                    latch.countDown();
                })
                .addOnFailureListener(exception -> {
                    errorReference.set(exception);
                    latch.countDown();
                })
                .addOnCanceledListener(latch::countDown);

        try {
            boolean completed = latch.await(OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                throw new RetryableWorkerException(
                        ReceiptScanContract.ERROR_OCR_TIMEOUT,
                        ReceiptScanContract.STAGE_OCR
                );
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RetryableWorkerException(
                    ReceiptScanContract.ERROR_OCR_EXECUTION_FAILED,
                    ReceiptScanContract.STAGE_OCR
            );
        }

        if (errorReference.get() != null) {
            throw new RetryableWorkerException(
                    ReceiptScanContract.ERROR_OCR_EXECUTION_FAILED,
                    ReceiptScanContract.STAGE_OCR
            );
        }

        Text recognizedText = resultReference.get();
        if (recognizedText == null) {
            throw new RetryableWorkerException(
                    ReceiptScanContract.ERROR_OCR_EXECUTION_FAILED,
                    ReceiptScanContract.STAGE_OCR
            );
        }
        return recognizedText;
    }

    @NonNull
    private Text chooseBetterText(@NonNull Text firstPassText, @NonNull Text secondPassText) {
        int firstScore = evaluateRecognizedTextScore(firstPassText);
        int secondScore = evaluateRecognizedTextScore(secondPassText);
        return secondScore > firstScore ? secondPassText : firstPassText;
    }

    private int evaluateRecognizedTextScore(@NonNull Text recognizedText) {
        String rawText = recognizedText.getText() == null ? "" : recognizedText.getText().trim();
        if (rawText.isEmpty()) {
            return 0;
        }

        int score = 0;
        int lineCount = countRecognizedLines(recognizedText);
        int blockCount = recognizedText.getTextBlocks().size();
        score += Math.min(rawText.length() / 16, 12);
        score += Math.min(lineCount * 2, 18);
        score += Math.min(blockCount, 6);

        String normalizedText = normalizeForScore(rawText);
        if (containsAny(normalizedText, "tong", "tong cong", "tong thanh toan", "thanh toan", "total")) {
            score += 8;
        }
        if (containsAny(normalizedText, "ngay", "date")) {
            score += 4;
        }
        score += Math.min(countMoneyLikeTokens(rawText) * 2, 10);
        return score;
    }

    private int countMoneyLikeTokens(@NonNull String rawText) {
        int count = 0;
        Matcher matcher = MONEY_LIKE_PATTERN.matcher(rawText);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @NonNull
    private String normalizeForScore(@NonNull String rawText) {
        String normalized = Normalizer.normalize(rawText, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        normalized = normalized.replace('đ', 'd').replace('Đ', 'D');
        return normalized.toLowerCase(Locale.US).replaceAll("\\s+", " ").trim();
    }

    private boolean containsAny(@NonNull String text, @NonNull String... probes) {
        for (String probe : probes) {
            if (text.contains(probe)) {
                return true;
            }
        }
        return false;
    }

    private int countRecognizedLines(@NonNull Text recognizedText) {
        int lineCount = 0;
        for (Text.TextBlock block : recognizedText.getTextBlocks()) {
            lineCount += block.getLines().size();
        }
        return lineCount;
    }

    @NonNull
    private Data buildFailureData(@Nullable ResolvedReceiptImage resolvedImage,
                                  @NonNull String errorCode,
                                  @NonNull String errorStage) {
        String imagePath = resolvedImage != null ? resolvedImage.imagePath : "";
        String imageUri = resolvedImage != null ? resolvedImage.imageUri : "";
        return ReceiptScanContract.buildFailureOutput(imagePath, imageUri, errorCode, errorStage);
    }

    private void logFailure(@NonNull String errorStage,
                            @NonNull String errorCode,
                            boolean willRetry,
                            long startedAt) {
        long durationMs = System.currentTimeMillis() - startedAt;
        Log.w(
                TAG,
                "OCR failure attempt=" + getCurrentAttemptNumber()
                        + " stage=" + errorStage
                        + " code=" + errorCode
                        + " willRetry=" + willRetry
                        + " durationMs=" + durationMs
        );
    }

    private int getCurrentAttemptNumber() {
        return getRunAttemptCount() + 1;
    }

    private boolean shouldRetry() {
        return getRunAttemptCount() < MAX_ATTEMPTS - 1;
    }

    private void recycleBitmap(@Nullable Bitmap bitmap) {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }

    private static final class ResolvedReceiptImage {
        @NonNull
        private final File file;
        @NonNull
        private final String imagePath;
        @NonNull
        private final String imageUri;

        private ResolvedReceiptImage(@NonNull File file,
                                     @NonNull String imagePath,
                                     @NonNull String imageUri) {
            this.file = file;
            this.imagePath = imagePath;
            this.imageUri = imageUri;
        }
    }

    private static final class ParseExecutionResult {
        @NonNull
        private final ReceiptParserBridge.ParseResult parseResult;
        @NonNull
        private final ReceiptData receiptData;
        @NonNull
        private final String itemsJson;

        private ParseExecutionResult(@NonNull ReceiptParserBridge.ParseResult parseResult,
                                     @NonNull ReceiptData receiptData,
                                     @NonNull String itemsJson) {
            this.parseResult = parseResult;
            this.receiptData = receiptData;
            this.itemsJson = itemsJson;
        }
    }

    private static final class GeminiAttemptResult {
        @Nullable
        private final ParseExecutionResult parseExecutionResult;
        @NonNull
        private final String fallbackDetail;

        private GeminiAttemptResult(@Nullable ParseExecutionResult parseExecutionResult,
                                    @NonNull String fallbackDetail) {
            this.parseExecutionResult = parseExecutionResult;
            this.fallbackDetail = fallbackDetail;
        }

        @NonNull
        private static GeminiAttemptResult success(@NonNull ParseExecutionResult parseExecutionResult) {
            return new GeminiAttemptResult(
                    parseExecutionResult,
                    ReceiptScanContract.DETAIL_CLOUD_PRIMARY
            );
        }

        @NonNull
        private static GeminiAttemptResult fallback(@NonNull String fallbackDetail) {
            return new GeminiAttemptResult(null, fallbackDetail);
        }
    }

    private static final class GeminiConversionResult {
        @NonNull
        private final ReceiptParserBridge.ParseResult parseResult;
        @NonNull
        private final ReceiptData receiptData;
        @NonNull
        private final String itemsJson;
        @NonNull
        private final String amount;
        @NonNull
        private final String merchant;

        private GeminiConversionResult(@NonNull ReceiptParserBridge.ParseResult parseResult,
                                       @NonNull ReceiptData receiptData,
                                       @NonNull String itemsJson,
                                       @NonNull String amount,
                                       @NonNull String merchant) {
            this.parseResult = parseResult;
            this.receiptData = receiptData;
            this.itemsJson = itemsJson;
            this.amount = amount;
            this.merchant = merchant;
        }
    }

    private static final class GeminiVerificationResult {
        @NonNull
        private final ReceiptParserBridge.ParseResult parseResult;
        @NonNull
        private final String rawText;
        private final boolean hasOcrText;
        private final int ocrBlockCount;
        private final int ocrLineCount;
        private final boolean repaired;

        private GeminiVerificationResult(@NonNull ReceiptParserBridge.ParseResult parseResult,
                                         @NonNull String rawText,
                                         boolean hasOcrText,
                                         int ocrBlockCount,
                                         int ocrLineCount,
                                         boolean repaired) {
            this.parseResult = parseResult;
            this.rawText = rawText;
            this.hasOcrText = hasOcrText;
            this.ocrBlockCount = ocrBlockCount;
            this.ocrLineCount = ocrLineCount;
            this.repaired = repaired;
        }
    }

    private static final class TotalLineCandidate {
        @NonNull
        private final String amount;
        private final int priority;
        private final int lineIndex;

        private TotalLineCandidate(@NonNull String amount, int priority, int lineIndex) {
            this.amount = amount;
            this.priority = priority;
            this.lineIndex = lineIndex;
        }
    }

    private static class PermanentWorkerException extends Exception {
        @NonNull
        private final String errorCode;
        @NonNull
        private final String errorStage;

        private PermanentWorkerException(@NonNull String errorCode, @NonNull String errorStage) {
            this.errorCode = errorCode;
            this.errorStage = errorStage;
        }
    }

    private static class RetryableWorkerException extends Exception {
        @NonNull
        private final String errorCode;
        @NonNull
        private final String errorStage;

        private RetryableWorkerException(@NonNull String errorCode, @NonNull String errorStage) {
            this.errorCode = errorCode;
            this.errorStage = errorStage;
        }
    }
}
