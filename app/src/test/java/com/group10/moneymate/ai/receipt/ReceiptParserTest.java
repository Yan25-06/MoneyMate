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
        assertEquals("Mua sắm", receiptData.getCategoryHint());
        assertEquals("CIRCLE K VIET NAM", receiptData.getNoteHint());
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
        assertNotEquals(ReceiptData.UNKNOWN_TIMESTAMP, receiptData.getTimestamp());
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
        assertEquals("Khác", receiptData.getCategoryHint());
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
        assertEquals("Tap hoa Minh Anh", receiptData.getNoteHint());
        assertEquals(ReceiptData.CONFIDENCE_MEDIUM, receiptData.getConfidence());
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
        assertEquals(ReceiptData.CONFIDENCE_MEDIUM, receiptData.getConfidence());
    }

    @Test
    public void parse_shouldRecognizeAccentedTotalKeywords() {
        String rawText = ""
                + "CO.OPMART\n"
                + "Ngày: 09/04/2026\n"
                + "Sữa tươi 35.000\n"
                + "Bánh quy 20.000\n"
                + "Tổng thanh toán: 55.000đ\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("55000", receiptData.getAmount());
        assertEquals("Mua sắm", receiptData.getCategoryHint());
        assertEquals(ReceiptData.CONFIDENCE_HIGH, receiptData.getConfidence());
    }

    @Test
    public void parse_shouldFallbackToSummedItemsWhenTotalLineIsMissing() {
        String rawText = ""
                + "HIGHLANDS COFFEE\n"
                + "09/04/2026\n"
                + "Tra sen vang 45000\n"
                + "Banh mi 30000\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("75000", receiptData.getAmount());
        assertEquals(2, receiptData.getItems().size());
        assertEquals("Ăn uống", receiptData.getCategoryHint());
        assertEquals(ReceiptData.CONFIDENCE_MEDIUM, receiptData.getConfidence());
    }

    @Test
    public void parse_shouldReadTotalFromNextLineAfterKeywordLine() {
        String rawText = ""
                + "GS25\n"
                + "Ngay 09/04/2026\n"
                + "Mi tron 25.000\n"
                + "Nuoc suoi 10.000\n"
                + "Tong thanh toan\n"
                + "35.000đ\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("35000", receiptData.getAmount());
        assertEquals("Mua sắm", receiptData.getCategoryHint());
        assertEquals(ReceiptData.CONFIDENCE_HIGH, receiptData.getConfidence());
    }

    @Test
    public void parse_shouldHandleCommonOcrMisspellingForTotalKeyword() {
        String rawText = ""
                + "WINMART+\n"
                + "Ngay 09/04/2026\n"
                + "Sua tuoi 42.000\n"
                + "Banh quy 18.000\n"
                + "T0ng thanh toan 60.000\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("60000", receiptData.getAmount());
        assertEquals("Mua sắm", receiptData.getCategoryHint());
        assertEquals(ReceiptData.CONFIDENCE_HIGH, receiptData.getConfidence());
    }

    @Test
    public void parse_shouldUseLastTotalLineWhenMultipleTotalLinesExist() {
        String rawText = ""
                + "BUN BO CO BA\n"
                + "Ngay 09/04/2026\n"
                + "Tam tinh 45.000\n"
                + "Tong cong 50.000\n"
                + "Tong thanh toan 55.000\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("55000", receiptData.getAmount());
        assertEquals("Ăn uống", receiptData.getCategoryHint());
        assertEquals(ReceiptData.CONFIDENCE_HIGH, receiptData.getConfidence());
    }

    @Test
    public void parse_shouldInferCategoryFromWholeReceiptText() {
        String rawText = ""
                + "CUA HANG A12\n"
                + "Ngay 09/04/2026\n"
                + "Chuyen xe GrabCar 85.000\n"
                + "Tong thanh toan 85.000\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("85000", receiptData.getAmount());
        assertEquals("Di chuyển", receiptData.getCategoryHint());
        assertEquals(ReceiptData.CONFIDENCE_HIGH, receiptData.getConfidence());
    }

    @Test
    public void parse_shouldPreferGrandTotalOverThanhTienAndVat() {
        String rawText = ""
                + "NHA HANG CO BA\n"
                + "Ngay 09/04/2026\n"
                + "Tong thanh tien 167.000\n"
                + "VAT 167.000\n"
                + "Tong cong 334.000\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("334000", receiptData.getAmount());
        assertEquals("Ăn uống", receiptData.getCategoryHint());
        assertEquals(ReceiptData.CONFIDENCE_HIGH, receiptData.getConfidence());
    }

    @Test
    public void parse_shouldSupportCommaAndDotMoneyFormats() {
        String rawText = ""
                + "NHA THUOC ABC\n"
                + "Ngay 09/04/2026\n"
                + "Tong cong 140,000\n"
                + "Thanh tien 4334.093\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("140000", receiptData.getAmount());
        assertEquals("Sức khỏe", receiptData.getCategoryHint());

        ReceiptData fallbackReceipt = parser.parse(
                "NHA THUOC ABC\n"
                        + "Ngay 09/04/2026\n"
                        + "Thuoc cam 4334,093\n"
        );
        assertEquals("4334093", fallbackReceipt.getAmount());
    }

    @Test
    public void parse_shouldExtractRestaurantReceiptTotalDateAndFoodCategory() {
        String rawText = ""
                + "VINH NGUYEN RES\n"
                + "355 Su Van Hanh, P.12, Q.10\n"
                + "DT: 090.126.9955 - 090.126.9933\n"
                + "HOA DON THANH TOAN\n"
                + "Ngay in: 29/03/2019 Gio in: 23:59:00\n"
                + "1) Coca 2 25,000 50,000\n"
                + "2) Sprite 2 25,000 50,000\n"
                + "3) Coca 2 25,000 50,000\n"
                + "4) Tonic 2 25,000 50,000\n"
                + "5) Soda 1 25,000 25,000\n"
                + "T.Cong 9 225,000\n"
                + "TIEN MAT 225,000\n";

        ReceiptData receiptData = parser.parse(rawText);

        assertEquals("225000", receiptData.getAmount());
        assertEquals("VINH NGUYEN RES", receiptData.getMerchant());
        assertEquals("Ăn uống", receiptData.getCategoryHint());
        assertTrue(receiptData.getNoteHint().contains("VINH NGUYEN RES"));
        assertNotEquals(ReceiptData.UNKNOWN_TIMESTAMP, receiptData.getTimestamp());
    }
}
