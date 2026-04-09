package com.group10.moneymate.workers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.work.Data;
import androidx.work.ListenableWorker;
import androidx.work.testing.TestListenableWorkerBuilder;

import com.group10.moneymate.ai.receipt.ReceiptScanContract;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class AIReceiptScannerWorkerIntegrationTest {

    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    @Test
    public void doWork_withMissingInput_returnsDeterministicFailureOutput() {
        AIReceiptScannerWorker worker = TestListenableWorkerBuilder.from(context, AIReceiptScannerWorker.class)
                .build();

        ListenableWorker.Result result = worker.doWork();
        assertTrue(result instanceof ListenableWorker.Result.Failure);

        Data output = ((ListenableWorker.Result.Failure) result).getOutputData();
        assertEquals(ReceiptScanContract.ERROR_MISSING_IMAGE_INPUT,
                output.getString(ReceiptScanContract.KEY_ERROR_CODE));
        assertEquals(ReceiptScanContract.STAGE_INPUT,
                output.getString(ReceiptScanContract.KEY_ERROR_STAGE));
        assertEquals(ReceiptScanContract.CONFIDENCE_LOW,
                output.getInt(ReceiptScanContract.KEY_CONFIDENCE, -1));
    }

    @Test
    public void doWork_withCorruptInternalImage_returnsDecodeFailureOutput() throws IOException {
        File receiptsDir = new File(context.getFilesDir(), "receipts");
        assertTrue(receiptsDir.exists() || receiptsDir.mkdirs());

        File corruptFile = new File(receiptsDir, "corrupt-receipt.bin");
        try (FileOutputStream outputStream = new FileOutputStream(corruptFile)) {
            outputStream.write(new byte[]{1, 2, 3, 4, 5, 6});
        }

        Data inputData = AIReceiptScannerWorker.buildInputData(
                corruptFile.getAbsolutePath(),
                android.net.Uri.fromFile(corruptFile).toString()
        );

        AIReceiptScannerWorker worker = TestListenableWorkerBuilder.from(context, AIReceiptScannerWorker.class)
                .setInputData(inputData)
                .build();

        ListenableWorker.Result result = worker.doWork();
        assertTrue(result instanceof ListenableWorker.Result.Failure);

        Data output = ((ListenableWorker.Result.Failure) result).getOutputData();
        assertEquals(ReceiptScanContract.ERROR_IMAGE_DECODE_FAILED,
                output.getString(ReceiptScanContract.KEY_ERROR_CODE));
        assertEquals(ReceiptScanContract.STAGE_DECODE,
                output.getString(ReceiptScanContract.KEY_ERROR_STAGE));
        assertEquals(corruptFile.getAbsolutePath(),
                output.getString(ReceiptScanContract.KEY_IMAGE_PATH));
    }
}
