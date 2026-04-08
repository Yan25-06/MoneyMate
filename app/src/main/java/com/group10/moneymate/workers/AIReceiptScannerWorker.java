package com.group10.moneymate.workers;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
import com.group10.moneymate.ai.receipt.MlKitReceiptParserBridge;
import com.group10.moneymate.ai.receipt.ReceiptParserBridge;
import com.group10.moneymate.ai.receipt.ReceiptScanContract;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class AIReceiptScannerWorker extends Worker {

    public static final String KEY_IMAGE_PATH = ReceiptScanContract.KEY_IMAGE_PATH;
    public static final String KEY_IMAGE_URI = ReceiptScanContract.KEY_IMAGE_URI;

    private static final String TAG = "AIReceiptScannerWorker";
    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_BITMAP_DIMENSION_PX = 1600;
    private static final long OCR_TIMEOUT_SECONDS = 20L;
    private static final long RETRY_BACKOFF_SECONDS = 10L;

    private final ReceiptParserBridge receiptParserBridge;

    public AIReceiptScannerWorker(@NonNull Context context,
                                  @NonNull WorkerParameters workerParameters) {
        this(context, workerParameters, new MlKitReceiptParserBridge());
    }

    public AIReceiptScannerWorker(@NonNull Context context,
                                  @NonNull WorkerParameters workerParameters,
                                  @NonNull ReceiptParserBridge receiptParserBridge) {
        super(context, workerParameters);
        this.receiptParserBridge = receiptParserBridge;
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

    @NonNull
    @Override
    public Result doWork() {
        long startedAt = System.currentTimeMillis();
        ResolvedReceiptImage resolvedImage = null;
        Bitmap decodedBitmap = null;
        Bitmap preprocessedBitmap = null;
        TextRecognizer textRecognizer = null;

        try {
            resolvedImage = resolveInputImage();
            decodedBitmap = loadBitmap(resolvedImage.file);
            preprocessedBitmap = preprocessBitmap(decodedBitmap);

            textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            Text recognizedText = runOcr(textRecognizer, preprocessedBitmap);

            boolean hasOcrText = !TextUtils.isEmpty(recognizedText.getText());
            int ocrBlockCount = recognizedText.getTextBlocks().size();
            int ocrLineCount = countRecognizedLines(recognizedText);

            ReceiptParserBridge.ParseResult parseResult = receiptParserBridge.parse(
                    resolvedImage.imagePath,
                    resolvedImage.imageUri,
                    recognizedText
            );

            long durationMs = System.currentTimeMillis() - startedAt;
            Log.i(
                    TAG,
                    "OCR success attempt=" + getCurrentAttemptNumber()
                            + " width=" + preprocessedBitmap.getWidth()
                            + " height=" + preprocessedBitmap.getHeight()
                            + " blocks=" + ocrBlockCount
                            + " lines=" + ocrLineCount
                            + " durationMs=" + durationMs
            );

            return Result.success(
                    parseResult.toOutputData(
                            resolvedImage.imagePath,
                            resolvedImage.imageUri,
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
            recycleBitmap(preprocessedBitmap);
            recycleBitmap(decodedBitmap);
        }
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
        return bitmap;
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
        while ((width / sampleSize) > MAX_BITMAP_DIMENSION_PX
                || (height / sampleSize) > MAX_BITMAP_DIMENSION_PX) {
            sampleSize *= 2;
        }
        return Math.max(sampleSize, 1);
    }

    @NonNull
    private Bitmap preprocessBitmap(@NonNull Bitmap sourceBitmap) throws PermanentWorkerException {
        try {
            Bitmap processedBitmap = Bitmap.createBitmap(
                    sourceBitmap.getWidth(),
                    sourceBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888
            );
            Canvas canvas = new Canvas(processedBitmap);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

            ColorMatrix grayscaleMatrix = new ColorMatrix();
            grayscaleMatrix.setSaturation(0f);

            float contrast = 1.1f;
            float translate = (-0.5f * contrast + 0.5f) * 255f;
            ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f
            });
            grayscaleMatrix.postConcat(contrastMatrix);

            paint.setColorFilter(new ColorMatrixColorFilter(grayscaleMatrix));
            canvas.drawBitmap(sourceBitmap, 0f, 0f, paint);
            return processedBitmap;
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
