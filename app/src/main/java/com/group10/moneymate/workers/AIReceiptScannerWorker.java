package com.group10.moneymate.workers;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class AIReceiptScannerWorker extends Worker {

    public static final String KEY_IMAGE_URI = "image_uri";

    private static final String TAG = "AIReceiptScannerWorker";

    public AIReceiptScannerWorker(@NonNull Context context,
                                  @NonNull WorkerParameters workerParameters) {
        super(context, workerParameters);
    }

    @NonNull
    @Override
    public Result doWork() {
        String imageUriString = getInputData().getString(KEY_IMAGE_URI);
        if (imageUriString == null || imageUriString.trim().isEmpty()) {
            Log.w(TAG, "Missing image_uri in input data");
            return Result.failure();
        }

        Uri imageUri = Uri.parse(imageUriString);
        if (imageUri == null || imageUri.getScheme() == null) {
            Log.w(TAG, "Invalid image_uri: " + imageUriString);
            return Result.failure();
        }

        // Phase 5 scaffold: validation only. OCR parsing will be implemented in later AI phase.
        Log.i(TAG, "Validated receipt image URI for background pipeline: " + imageUriString);

        Data output = new Data.Builder()
                .putString(KEY_IMAGE_URI, imageUriString)
                .putBoolean("validated", true)
                .build();
        return Result.success(output);
    }
}

