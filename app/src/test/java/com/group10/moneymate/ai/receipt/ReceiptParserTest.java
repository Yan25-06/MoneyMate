package com.group10.moneymate.ai.receipt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.group10.moneymate.ai.receipt.model.ReceiptData;
import com.group10.moneymate.ai.receipt.model.ReceiptItem;

import org.junit.Test;

import java.util.List;

public class ReceiptParserTest {

    private final ReceiptParser parser = new ReceiptParser();

    @Test
    public void parse_shouldExtractHighConfidenceReceiptForTypicalVnStore() {
        String rawText = ""
                + "CIRCLE K VIET NAM\n"
                + "123 Nguyen Trai, Q1\n"
                + "Ngay: 09/04/2026 18:35\n"
                + "Ca phe sua 25.000\n"
                + "Banh mi dac biet 30.000\n"
                + "Tong cong 55.000\n"
                + "Tien mat 60.000\n"
                + "Tien thua 5.000\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("55000", receiptData.getAmount());
        assertNotEquals(ReceiptData.UNKNOWN_TIMESTAMP, receiptData.getTimestamp());
        assertEquals("CIRCLE K VIET NAM", receiptData.getMerchant());
        assertEquals("Ăn uống", receiptData.getCategoryHint());
        assertEquals(ReceiptData.CONFIDENCE_HIGH, receiptData.getConfidence());

        List<ReceiptItem> items = receiptData.getItems();
        assertEquals(2, items.size());
        assertEquals("Ca phe sua", items.get(0).getName());
        assertEquals("25000", items.get(0).getAmount());
        assertEquals("Banh mi dac biet", items.get(1).getName());
        assertEquals("30000", items.get(1).getAmount());
    }

    @Test
    public void parse_shouldReturnMediumConfidenceWhenDateAndAmountExistWithoutTotalKeyword() {
        String rawText = ""
                + "HIGHLANDS COFFEE\n"
                + "Ngay 09/04/2026\n"
                + "Freeze Tra Xanh 49000\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("49000", receiptData.getAmount());
        assertEquals("HIGHLANDS COFFEE", receiptData.getMerchant());
        assertEquals(ReceiptData.CONFIDENCE_MEDIUM, receiptData.getConfidence());
        assertEquals(1, receiptData.getItems().size());
        assertEquals("Ăn uống", receiptData.getCategoryHint());
    }

    @Test
    public void parse_shouldFallbackDeterministicallyForUnstructuredText() {
        String rawText = ""
                + "Cam on quy khach\n"
                + "MST 0312345678\n"
                + "SDT 0909123456\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("", receiptData.getAmount());
        assertEquals(ReceiptData.UNKNOWN_TIMESTAMP, receiptData.getTimestamp());
        assertEquals("", receiptData.getMerchant());
        assertTrue(receiptData.getItems().isEmpty());
        assertEquals(ReceiptData.CONFIDENCE_LOW, receiptData.getConfidence());
        assertFalse(receiptData.hasAmount());
    }

    @Test
    public void parse_shouldStayLowConfidenceWhenOnlyAmountIsDetected() {
        String rawText = ""
                + "Tap hoa Minh Anh\n"
                + "Snack 15000\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("15000", receiptData.getAmount());
        assertEquals(ReceiptData.UNKNOWN_TIMESTAMP, receiptData.getTimestamp());
        assertEquals(ReceiptData.CONFIDENCE_LOW, receiptData.getConfidence());
        assertEquals(1, receiptData.getItems().size());
    }

    @Test
    public void parse_shouldIgnorePhoneAndTaxNumbersWhenChoosingTotalAmount() {
        String rawText = ""
                + "NHA THUOC ABC\n"
                + "SDT 0909123456\n"
                + "MST 0312345678\n"
                + "Thuoc cam 45.000\n"
                + "Tong thanh toan 45.000\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("45000", receiptData.getAmount());
        assertEquals("Sức khỏe", receiptData.getCategoryHint());
        assertEquals(ReceiptData.CONFIDENCE_LOW, receiptData.getConfidence());
    }
}
