package com.group10.moneymate.ai.receipt;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OcrPrivacyLoggingSourceTest {

    @Test
    public void workerLogs_shouldNotContainRawOcrTextLogging() throws IOException {
        String workerSource = readProjectSource(
                "app/src/main/java/com/group10/moneymate/workers/AIReceiptScannerWorker.java"
        );

        assertFalse(workerSource.contains("Log.i(TAG, recognizedText.getText())"));
        assertFalse(workerSource.contains("Log.d(TAG, recognizedText.getText())"));
        assertFalse(workerSource.contains("Log.w(TAG, recognizedText.getText())"));
        assertFalse(workerSource.contains("Log.e(TAG, recognizedText.getText())"));
        assertFalse(workerSource.contains("rawText"));
        assertTrue(workerSource.contains("blocks="));
        assertTrue(workerSource.contains("lines="));
        assertTrue(workerSource.contains("durationMs="));
    }

    @Test
    public void parserLayer_shouldNotDependOnAndroidLogging() throws IOException {
        String parserSource = readProjectSource(
                "app/src/main/java/com/group10/moneymate/ai/receipt/ReceiptParser.java"
        );
        String bridgeSource = readProjectSource(
                "app/src/main/java/com/group10/moneymate/ai/receipt/MlKitReceiptParserBridge.java"
        );

        assertFalse(parserSource.contains("android.util.Log"));
        assertFalse(parserSource.contains("Log."));
        assertFalse(bridgeSource.contains("android.util.Log"));
        assertFalse(bridgeSource.contains("Log."));
    }

    private String readProjectSource(String relativePath) throws IOException {
        Path workingDirectory = Paths.get("").toAbsolutePath();
        Path cursor = workingDirectory;
        while (cursor != null) {
            Path candidate = cursor.resolve(relativePath);
            if (Files.exists(candidate)) {
                return new String(Files.readAllBytes(candidate), StandardCharsets.UTF_8);
            }
            cursor = cursor.getParent();
        }

        throw new IOException("Missing source file: " + relativePath);
    }
}
