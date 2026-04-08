package com.group10.moneymate.ai.receipt;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.text.Text;

public class StubReceiptParserBridge implements ReceiptParserBridge {

    @NonNull
    @Override
    public ParseResult parse(@NonNull String imagePath,
                             @NonNull String imageUri,
                             @NonNull Text recognizedText) {
        return ParseResult.empty();
    }
}
