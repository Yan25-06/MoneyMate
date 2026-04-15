package com.group10.moneymate.ai.receipt;

import androidx.annotation.NonNull;

import com.google.mlkit.vision.text.Text;
import com.group10.moneymate.ai.receipt.model.ReceiptData;
import com.group10.moneymate.ai.receipt.model.ReceiptItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MlKitReceiptParserBridge implements ReceiptParserBridge {

    private final ReceiptParser receiptParser;

    public MlKitReceiptParserBridge() {
        this(new ReceiptParser());
    }

    public MlKitReceiptParserBridge(@NonNull ReceiptParser receiptParser) {
        this.receiptParser = receiptParser;
    }

    @NonNull
    @Override
    public ParseResult parse(@NonNull String imagePath,
                             @NonNull String imageUri,
                             @NonNull Text recognizedText) throws ReceiptParsingException {
        try {
            List<String> lines = extractLines(recognizedText);
            String rawText = recognizedText.getText();
            if (rawText == null) {
                rawText = "";
            }
            ReceiptData receiptData = receiptParser.parse(rawText, lines);
            return ParseResult.fromReceiptData(receiptData, serializeItems(receiptData.getItems()));
        } catch (RuntimeException exception) {
            throw new ReceiptParsingException(exception);
        }
    }

    @NonNull
    private List<String> extractLines(@NonNull Text recognizedText) {
        List<String> lines = new ArrayList<>();
        for (Text.TextBlock textBlock : recognizedText.getTextBlocks()) {
            for (Text.Line line : textBlock.getLines()) {
                String lineText = line.getText();
                if (lineText != null && !lineText.trim().isEmpty()) {
                    lines.add(lineText);
                }
            }
        }
        if (!lines.isEmpty()) {
            return lines;
        }

        String rawText = recognizedText.getText();
        if (rawText == null || rawText.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.asList(rawText.split("\\r?\\n"));
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
}
