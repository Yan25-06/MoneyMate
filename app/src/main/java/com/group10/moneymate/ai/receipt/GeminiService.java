package com.group10.moneymate.ai.receipt;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import javax.net.ssl.HttpsURLConnection;

public class GeminiService {

    private static final String MODEL_NAME = "gemini-2.5-flash-lite";
    private static final String ENDPOINT_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";
    private static final int REQUEST_TIMEOUT_MS = 10_000;
    private static final String HEADER_RETRY_AFTER = "Retry-After";
    private static final Pattern FENCE_PATTERN = Pattern.compile("^```(?:json)?\\s*|\\s*```$", Pattern.MULTILINE);
    private static final String IMAGE_MIME_TYPE = "image/jpeg";

    private final String apiKey;
    private final GeminiTransport transport;

    public GeminiService(@Nullable String apiKey) {
        this(apiKey, new HttpGeminiTransport());
    }

    GeminiService(@Nullable String apiKey, @NonNull GeminiTransport transport) {
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.transport = transport;
    }

    @NonNull
    public GeminiResult parseReceipt(@NonNull Bitmap bitmap) {
        if (bitmap.isRecycled() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) {
            return GeminiResult.error("invalid_bitmap", "Bitmap is invalid", false);
        }
        return parseReceipt(bitmap, null, Collections.emptyList());
    }

    @NonNull
    public GeminiResult parseReceipt(@NonNull Bitmap primaryBitmap, @Nullable Bitmap enhancedBitmap) {
        return parseReceipt(primaryBitmap, enhancedBitmap, Collections.emptyList());
    }

    @NonNull
    public GeminiResult parseReceipt(@NonNull Bitmap primaryBitmap,
                                     @Nullable Bitmap enhancedBitmap,
                                     @NonNull List<String> allowedCategories) {
        if (primaryBitmap.isRecycled() || primaryBitmap.getWidth() <= 0 || primaryBitmap.getHeight() <= 0) {
            return GeminiResult.error("invalid_bitmap", "Primary bitmap is invalid", false);
        }

        List<GeminiReceiptSchema.InlineImagePayload> images = new ArrayList<>();
        byte[] primaryBytes = compressBitmap(primaryBitmap);
        if (primaryBytes.length == 0) {
            return GeminiResult.error("empty_bitmap", "Primary bitmap compression produced empty bytes", false);
        }
        images.add(new GeminiReceiptSchema.InlineImagePayload(
                IMAGE_MIME_TYPE,
                Base64.getEncoder().encodeToString(primaryBytes)
        ));

        if (enhancedBitmap != null
                && !enhancedBitmap.isRecycled()
                && enhancedBitmap.getWidth() > 0
                && enhancedBitmap.getHeight() > 0) {
            byte[] enhancedBytes = compressBitmap(enhancedBitmap);
            if (enhancedBytes.length > 0) {
                images.add(new GeminiReceiptSchema.InlineImagePayload(
                        IMAGE_MIME_TYPE,
                        Base64.getEncoder().encodeToString(enhancedBytes)
                ));
            }
        }
        return executeParseReceipt(images, allowedCategories);
    }

    @NonNull
    GeminiResult parseReceipt(@NonNull byte[] imageBytes, @NonNull String mimeType) {
        if (imageBytes.length == 0) {
            return GeminiResult.error("empty_bitmap", "Image bytes are empty", false);
        }
        if (apiKey.isEmpty()) {
            return GeminiResult.error("missing_api_key", "Gemini API key is missing", false);
        }
        List<GeminiReceiptSchema.InlineImagePayload> images = new ArrayList<>();
        images.add(new GeminiReceiptSchema.InlineImagePayload(
                mimeType,
                Base64.getEncoder().encodeToString(imageBytes)
        ));
        return executeParseReceipt(images, Collections.emptyList());
    }

    @NonNull
    private GeminiResult executeParseReceipt(@NonNull List<GeminiReceiptSchema.InlineImagePayload> images) {
        return executeParseReceipt(images, Collections.emptyList());
    }

    @NonNull
    private GeminiResult executeParseReceipt(@NonNull List<GeminiReceiptSchema.InlineImagePayload> images,
                                             @NonNull List<String> allowedCategories) {
        try {
            String requestBody = GeminiReceiptSchema.buildRequestBody(images, allowedCategories);
            TransportResponse response = transport.generateContent(apiKey, requestBody);
            if (!response.isSuccessful()) {
                return GeminiResult.error(
                        "http_" + response.getStatusCode(),
                        "Gemini HTTP error",
                        true,
                        response.getRetryAfterSeconds()
                );
            }

            ParsedReceipt parsedReceipt = parseResponseBody(response.getBody());
            ValidationResult validation = validateParsedReceipt(parsedReceipt);
            if (!validation.isValid()) {
                return GeminiResult.error(validation.errorCode, validation.message, false);
            }
            return GeminiResult.success(parsedReceipt);
        } catch (JSONException exception) {
            return GeminiResult.error("invalid_json", "Gemini returned invalid JSON", true, -1L);
        } catch (IOException exception) {
            return GeminiResult.error("io_exception", exception.getClass().getSimpleName(), true, -1L);
        } catch (RuntimeException exception) {
            return GeminiResult.error("runtime_exception", exception.getClass().getSimpleName(), true, -1L);
        }
    }

    @NonNull
    private byte[] compressBitmap(@NonNull Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        boolean compressed = bitmap.compress(Bitmap.CompressFormat.JPEG, 92, outputStream);
        return compressed ? outputStream.toByteArray() : new byte[0];
    }

    @NonNull
    ParsedReceipt parseResponseBody(@NonNull String responseBody) throws JSONException {
        JSONObject envelope = new JSONObject(responseBody);
        JSONArray candidates = envelope.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            throw new JSONException("Missing candidates");
        }

        JSONObject firstCandidate = candidates.optJSONObject(0);
        if (firstCandidate == null) {
            throw new JSONException("Missing candidate object");
        }

        JSONObject content = firstCandidate.optJSONObject("content");
        if (content == null) {
            throw new JSONException("Missing content");
        }

        JSONArray parts = content.optJSONArray("parts");
        if (parts == null || parts.length() == 0) {
            throw new JSONException("Missing parts");
        }

        JSONObject firstPart = parts.optJSONObject(0);
        if (firstPart == null) {
            throw new JSONException("Missing part object");
        }

        String modelJson = sanitizeModelJson(firstPart.optString("text", ""));
        if (modelJson.isEmpty()) {
            throw new JSONException("Missing model JSON payload");
        }

        JSONObject receiptJson = new JSONObject(modelJson);
        String merchant = receiptJson.optString("merchant", "").trim();
        String date = receiptJson.optString("date", "").trim();
        double total = receiptJson.optDouble("total", Double.NaN);
        String categoryHint = receiptJson.optString("category_hint", "").trim();
        String noteHint = receiptJson.optString("note_hint", "").trim();
        int confidence = normalizeConfidence(receiptJson.optInt("confidence", 0));

        List<ParsedReceiptItem> items = new ArrayList<>();
        JSONArray itemArray = receiptJson.optJSONArray("items");
        if (itemArray != null) {
            for (int index = 0; index < itemArray.length(); index++) {
                JSONObject itemObject = itemArray.optJSONObject(index);
                if (itemObject == null) {
                    continue;
                }
                String name = itemObject.optString("name", "").trim();
                double price = itemObject.optDouble("price", Double.NaN);
                double quantity = itemObject.optDouble("quantity", Double.NaN);
                String category = itemObject.optString("category", "").trim();
                items.add(new ParsedReceiptItem(name, price, quantity, category));
            }
        }

        List<TotalCandidate> totalCandidates = new ArrayList<>();
        JSONArray totalCandidateArray = receiptJson.optJSONArray("total_candidates");
        if (totalCandidateArray != null) {
            for (int index = 0; index < totalCandidateArray.length(); index++) {
                JSONObject candidateObject = totalCandidateArray.optJSONObject(index);
                if (candidateObject == null) {
                    continue;
                }
                String label = candidateObject.optString("label", "").trim();
                double amount = candidateObject.optDouble("amount", Double.NaN);
                int lineOrder = candidateObject.optInt("line_order", index);
                totalCandidates.add(new TotalCandidate(label, amount, lineOrder));
            }
        }

        return new ParsedReceipt(
                merchant,
                date,
                total,
                categoryHint,
                noteHint,
                items,
                totalCandidates,
                confidence
        );
    }

    @NonNull
    ValidationResult validateParsedReceipt(@NonNull ParsedReceipt parsedReceipt) {
        boolean hasTotal = !Double.isNaN(parsedReceipt.getTotal()) && parsedReceipt.getTotal() > 0d;
        boolean hasTotalCandidates = false;
        for (TotalCandidate candidate : parsedReceipt.getTotalCandidates()) {
            if (candidate.hasUsableAmount()) {
                hasTotalCandidates = true;
                break;
            }
        }
        boolean hasItemPrices = false;
        for (ParsedReceiptItem item : parsedReceipt.getItems()) {
            if (item.hasUsablePrice()) {
                hasItemPrices = true;
                break;
            }
        }

        if (!hasTotal && !hasTotalCandidates && !hasItemPrices) {
            return ValidationResult.invalid("missing_amount", "Missing total, total candidates and item prices");
        }

        if (parsedReceipt.getMerchant().isEmpty() && parsedReceipt.getDate().isEmpty()) {
            return ValidationResult.invalid("missing_context", "Missing merchant and date");
        }

        return ValidationResult.valid();
    }

    @NonNull
    static String sanitizeModelJson(@Nullable String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String sanitized = rawValue.trim();
        sanitized = FENCE_PATTERN.matcher(sanitized).replaceAll("");
        return sanitized.trim();
    }

    private int normalizeConfidence(int confidence) {
        if (confidence < 0) {
            return 0;
        }
        return Math.min(confidence, 100);
    }

    interface GeminiTransport {
        @NonNull
        TransportResponse generateContent(@NonNull String apiKey, @NonNull String requestBody) throws IOException;
    }

    static final class HttpGeminiTransport implements GeminiTransport {

        @NonNull
        @Override
        public TransportResponse generateContent(@NonNull String apiKey,
                                                 @NonNull String requestBody) throws IOException {
            String endpoint = String.format(Locale.US, ENDPOINT_TEMPLATE, MODEL_NAME, apiKey);
            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setConnectTimeout(REQUEST_TIMEOUT_MS);
            connection.setReadTimeout(REQUEST_TIMEOUT_MS);
            connection.setDoOutput(true);

            try (OutputStream outputStream = connection.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(
                         new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {
                writer.write(requestBody);
                writer.flush();
            }

            int responseCode = connection.getResponseCode();
            long retryAfterSeconds = parseRetryAfterSeconds(connection.getHeaderField(HEADER_RETRY_AFTER));
            InputStream stream = responseCode >= HttpsURLConnection.HTTP_BAD_REQUEST
                    ? connection.getErrorStream()
                    : connection.getInputStream();
            String body = readStream(stream);
            connection.disconnect();
            return new TransportResponse(responseCode, body, retryAfterSeconds);
        }

        private long parseRetryAfterSeconds(@Nullable String rawValue) {
            if (rawValue == null) {
                return -1L;
            }
            try {
                long parsedValue = Long.parseLong(rawValue.trim());
                return Math.max(parsedValue, -1L);
            } catch (NumberFormatException exception) {
                return -1L;
            }
        }

        @NonNull
        private String readStream(@Nullable InputStream stream) throws IOException {
            if (stream == null) {
                return "";
            }
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            return builder.toString();
        }
    }

    static final class TransportResponse {
        private final int statusCode;
        @NonNull
        private final String body;
        private final long retryAfterSeconds;

        TransportResponse(int statusCode, @NonNull String body, long retryAfterSeconds) {
            this.statusCode = statusCode;
            this.body = body;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        int getStatusCode() {
            return statusCode;
        }

        @NonNull
        String getBody() {
            return body;
        }

        long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }

        boolean isSuccessful() {
            return statusCode >= 200 && statusCode < 300;
        }
    }

    public static abstract class GeminiResult {

        @NonNull
        public static GeminiResult success(@NonNull ParsedReceipt parsedReceipt) {
            return new Success(parsedReceipt);
        }

        @NonNull
        public static GeminiResult error(@NonNull String errorCode,
                                         @NonNull String detail,
                                         boolean retryable) {
            return new Error(errorCode, detail, retryable, -1L);
        }

        @NonNull
        public static GeminiResult error(@NonNull String errorCode,
                                         @NonNull String detail,
                                         boolean retryable,
                                         long retryAfterSeconds) {
            return new Error(errorCode, detail, retryable, retryAfterSeconds);
        }

        public static final class Success extends GeminiResult {
            @NonNull
            private final ParsedReceipt parsedReceipt;

            Success(@NonNull ParsedReceipt parsedReceipt) {
                this.parsedReceipt = parsedReceipt;
            }

            @NonNull
            public ParsedReceipt getParsedReceipt() {
                return parsedReceipt;
            }
        }

        public static final class Error extends GeminiResult {
            @NonNull
            private final String errorCode;
            @NonNull
            private final String detail;
            private final boolean retryable;
            private final long retryAfterSeconds;

            Error(@NonNull String errorCode,
                  @NonNull String detail,
                  boolean retryable,
                  long retryAfterSeconds) {
                this.errorCode = errorCode;
                this.detail = detail;
                this.retryable = retryable;
                this.retryAfterSeconds = retryAfterSeconds;
            }

            @NonNull
            public String getErrorCode() {
                return errorCode;
            }

            @NonNull
            public String getDetail() {
                return detail;
            }

            public boolean isRetryable() {
                return retryable;
            }

            public long getRetryAfterSeconds() {
                return retryAfterSeconds;
            }
        }
    }

    public static final class ParsedReceipt {
        @NonNull
        private final String merchant;
        @NonNull
        private final String date;
        private final double total;
        @NonNull
        private final String categoryHint;
        @NonNull
        private final String noteHint;
        @NonNull
        private final List<ParsedReceiptItem> items;
        @NonNull
        private final List<TotalCandidate> totalCandidates;
        private final int confidence;

        ParsedReceipt(@NonNull String merchant,
                      @NonNull String date,
                      double total,
                      @NonNull String categoryHint,
                      @NonNull String noteHint,
                      @NonNull List<ParsedReceiptItem> items,
                      @NonNull List<TotalCandidate> totalCandidates,
                      int confidence) {
            this.merchant = merchant;
            this.date = date;
            this.total = total;
            this.categoryHint = categoryHint;
            this.noteHint = noteHint;
            this.items = items;
            this.totalCandidates = totalCandidates;
            this.confidence = confidence;
        }

        @NonNull
        public String getMerchant() {
            return merchant;
        }

        @NonNull
        public String getDate() {
            return date;
        }

        public double getTotal() {
            return total;
        }

        @NonNull
        public String getCategoryHint() {
            return categoryHint;
        }

        @NonNull
        public String getNoteHint() {
            return noteHint;
        }

        @NonNull
        public List<ParsedReceiptItem> getItems() {
            return items;
        }

        @NonNull
        public List<TotalCandidate> getTotalCandidates() {
            return totalCandidates;
        }

        public int getConfidence() {
            return confidence;
        }
    }

    public static final class ParsedReceiptItem {
        @NonNull
        private final String name;
        private final double price;
        private final double quantity;
        @NonNull
        private final String category;

        ParsedReceiptItem(@NonNull String name, double price, double quantity, @NonNull String category) {
            this.name = name;
            this.price = price;
            this.quantity = quantity;
            this.category = category;
        }

        @NonNull
        public String getName() {
            return name;
        }

        public double getPrice() {
            return price;
        }

        public double getQuantity() {
            return quantity;
        }

        @NonNull
        public String getCategory() {
            return category;
        }

        public boolean hasUsablePrice() {
            return !Double.isNaN(price) && price > 0d;
        }
    }

    public static final class TotalCandidate {
        @NonNull
        private final String label;
        private final double amount;
        private final int lineOrder;

        TotalCandidate(@NonNull String label, double amount, int lineOrder) {
            this.label = label;
            this.amount = amount;
            this.lineOrder = lineOrder;
        }

        @NonNull
        public String getLabel() {
            return label;
        }

        public double getAmount() {
            return amount;
        }

        public int getLineOrder() {
            return lineOrder;
        }

        public boolean hasUsableAmount() {
            return !Double.isNaN(amount) && amount > 0d;
        }
    }

    static final class ValidationResult {
        private final boolean valid;
        @NonNull
        private final String errorCode;
        @NonNull
        private final String message;

        private ValidationResult(boolean valid, @NonNull String errorCode, @NonNull String message) {
            this.valid = valid;
            this.errorCode = errorCode;
            this.message = message;
        }

        @NonNull
        static ValidationResult valid() {
            return new ValidationResult(true, "", "");
        }

        @NonNull
        static ValidationResult invalid(@NonNull String errorCode, @NonNull String message) {
            return new ValidationResult(false, errorCode, message);
        }

        boolean isValid() {
            return valid;
        }
    }
}
