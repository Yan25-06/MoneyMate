package com.group10.moneymate.ai.receipt;

import com.group10.moneymate.ai.receipt.model.ReceiptData;
import com.group10.moneymate.ai.receipt.model.ReceiptItem;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptParser {

    private static final Pattern MONEY_PATTERN = Pattern.compile(
            "(?<!\\d)(\\d{1,3}(?:[.,\\s]\\d{3})+|\\d{3,})(?:\\s?(?:d|đ|vnd|vnđ))?(?!\\d)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DATE_DMY_PATTERN = Pattern.compile(
            "(\\d{1,2})[/-](\\d{1,2})[/-](\\d{2,4})(?:\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?"
    );
    private static final Pattern DATE_YMD_PATTERN = Pattern.compile(
            "(\\d{4})[/-](\\d{1,2})[/-](\\d{1,2})(?:\\s+(\\d{1,2}):(\\d{2})(?::(\\d{2}))?)?"
    );
    private static final Pattern LEADING_QTY_PATTERN = Pattern.compile("^(\\d+\\s*[xX]\\s*|\\d+\\s+)");
    private static final Pattern MULTI_SPACE_PATTERN = Pattern.compile("\\s+");

    private static final List<String> TOTAL_KEYWORDS = Arrays.asList(
            "tong cong",
            "tong thanh toan",
            "tong tien",
            "thanh tien",
            "can thanh toan",
            "phai tra",
            "tong so thanh toan",
            "grand total",
            "total"
    );
    private static final List<String> MONEY_EXCLUSION_KEYWORDS = Arrays.asList(
            "mst",
            "ma so thue",
            "ma gd",
            "ma giao dich",
            "sdt",
            "so dt",
            "dien thoai",
            "tel",
            "stk",
            "tai khoan",
            "bank",
            "fax"
    );
    private static final List<String> ITEM_EXCLUSION_KEYWORDS = Arrays.asList(
            "tong cong",
            "tong thanh toan",
            "thanh tien",
            "vat",
            "thue",
            "giam gia",
            "discount",
            "tien mat",
            "khach dua",
            "tien thua",
            "cash",
            "change",
            "invoice",
            "hoa don"
    );
    private static final List<String> MERCHANT_EXCLUSION_KEYWORDS = Arrays.asList(
            "hoa don",
            "receipt",
            "ban hang",
            "cam on",
            "xin cam on",
            "quy khach",
            "mst",
            "ma so thue",
            "dia chi",
            "address",
            "tel",
            "sdt",
            "ngay",
            "gio"
    );
    private static final List<String> DATE_KEYWORDS = Arrays.asList(
            "ngay",
            "gio",
            "time",
            "date"
    );
    private static final Map<String, String> CATEGORY_HINTS = buildCategoryHints();
    private static final ZoneId RECEIPT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    public ReceiptData parse(String rawText) {
        if (rawText == null || rawText.trim().isEmpty()) {
            return ReceiptData.empty();
        }
        return parse(rawText, Arrays.asList(rawText.split("\\r?\\n")));
    }

    public ReceiptData parse(String rawText, List<String> rawLines) {
        List<String> lines = normalizeLines(rawLines, rawText);
        if (lines.isEmpty()) {
            return ReceiptData.empty();
        }

        AmountCandidate amountCandidate = extractAmount(lines);
        ParsedDate parsedDate = extractDate(lines);
        String merchant = extractMerchant(lines);
        List<ReceiptItem> items = extractItems(lines, amountCandidate);
        String categoryHint = resolveCategoryHint(merchant, items);
        int confidence = resolveConfidence(amountCandidate, parsedDate, items);

        if (amountCandidate == null && parsedDate == null && merchant.isEmpty() && items.isEmpty()) {
            return ReceiptData.empty();
        }

        return new ReceiptData(
                amountCandidate != null ? amountCandidate.normalizedAmount : "",
                parsedDate != null ? parsedDate.epochMillis : ReceiptData.UNKNOWN_TIMESTAMP,
                merchant,
                categoryHint,
                items,
                confidence
        );
    }

    private List<String> normalizeLines(List<String> rawLines, String rawText) {
        List<String> sourceLines = rawLines;
        if ((sourceLines == null || sourceLines.isEmpty()) && rawText != null) {
            sourceLines = Arrays.asList(rawText.split("\\r?\\n"));
        }
        if (sourceLines == null) {
            return Collections.emptyList();
        }

        List<String> normalized = new ArrayList<>();
        for (String rawLine : sourceLines) {
            if (rawLine == null) {
                continue;
            }
            String cleanLine = MULTI_SPACE_PATTERN.matcher(rawLine.replace('\t', ' ')).replaceAll(" ").trim();
            if (!cleanLine.isEmpty()) {
                normalized.add(cleanLine);
            }
        }
        return normalized;
    }

    private AmountCandidate extractAmount(List<String> lines) {
        AmountCandidate bestCandidate = null;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String normalizedLine = normalizeForMatch(line);
            Matcher matcher = MONEY_PATTERN.matcher(line);
            boolean hasTotalKeyword = containsAny(normalizedLine, TOTAL_KEYWORDS);
            boolean hasMoneyExclusion = containsAny(normalizedLine, MONEY_EXCLUSION_KEYWORDS);

            if (hasMoneyExclusion && !hasTotalKeyword) {
                continue;
            }

            while (matcher.find()) {
                String token = matcher.group(1);
                String digitsOnly = normalizeAmountToken(token);
                if (digitsOnly.length() < 3) {
                    continue;
                }

                int score = 0;
                if (hasTotalKeyword) {
                    score += 60;
                }
                if (hasMoneyExclusion) {
                    score -= 50;
                }
                if (normalizedLine.contains("vnd") || normalizedLine.contains("vnđ") || normalizedLine.contains("đ")) {
                    score += 10;
                }
                if (index >= Math.max(0, lines.size() - 4)) {
                    score += 20;
                }
                if (normalizedLine.contains("tam tinh") || normalizedLine.contains("subtotal")) {
                    score -= 10;
                }
                if (normalizedLine.contains("vat") || normalizedLine.contains("thue")) {
                    score -= 5;
                }

                long numericValue = parseLongSafely(digitsOnly);
                AmountCandidate candidate = new AmountCandidate(
                        digitsOnly,
                        line,
                        index,
                        score,
                        numericValue,
                        hasTotalKeyword
                );
                if (isBetterAmountCandidate(candidate, bestCandidate)) {
                    bestCandidate = candidate;
                }
            }
        }
        return bestCandidate;
    }

    private boolean isBetterAmountCandidate(AmountCandidate candidate, AmountCandidate currentBest) {
        if (candidate == null) {
            return false;
        }
        if (currentBest == null) {
            return true;
        }
        if (candidate.score != currentBest.score) {
            return candidate.score > currentBest.score;
        }
        if (candidate.numericValue != currentBest.numericValue) {
            return candidate.numericValue > currentBest.numericValue;
        }
        return candidate.lineIndex > currentBest.lineIndex;
    }

    private ParsedDate extractDate(List<String> lines) {
        ParsedDate bestDate = null;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String normalizedLine = normalizeForMatch(line);

            ParsedDate candidate = matchDate(line, normalizedLine, index, DATE_DMY_PATTERN, false);
            if (candidate == null) {
                candidate = matchDate(line, normalizedLine, index, DATE_YMD_PATTERN, true);
            }
            if (candidate != null && isBetterDateCandidate(candidate, bestDate)) {
                bestDate = candidate;
            }
        }
        return bestDate;
    }

    private ParsedDate matchDate(String line,
                                 String normalizedLine,
                                 int index,
                                 Pattern pattern,
                                 boolean yearFirst) {
        Matcher matcher = pattern.matcher(line);
        while (matcher.find()) {
            try {
                int day;
                int month;
                int year;
                if (yearFirst) {
                    year = parseYear(matcher.group(1));
                    month = Integer.parseInt(matcher.group(2));
                    day = Integer.parseInt(matcher.group(3));
                } else {
                    day = Integer.parseInt(matcher.group(1));
                    month = Integer.parseInt(matcher.group(2));
                    year = parseYear(matcher.group(3));
                }

                int hour = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 0;
                int minute = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;
                int second = matcher.group(6) != null ? Integer.parseInt(matcher.group(6)) : 0;

                LocalDate localDate = LocalDate.of(year, month, day);
                LocalTime localTime = LocalTime.of(hour, minute, second);
                LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime);
                long epochMillis = localDateTime.atZone(RECEIPT_ZONE).toInstant().toEpochMilli();

                int score = containsAny(normalizedLine, DATE_KEYWORDS) ? 30 : 10;
                if (index <= 4) {
                    score += 10;
                }
                return new ParsedDate(epochMillis, index, score);
            } catch (RuntimeException exception) {
                // Skip invalid date fragments and continue scanning.
            }
        }
        return null;
    }

    private boolean isBetterDateCandidate(ParsedDate candidate, ParsedDate currentBest) {
        if (candidate == null) {
            return false;
        }
        if (currentBest == null) {
            return true;
        }
        if (candidate.score != currentBest.score) {
            return candidate.score > currentBest.score;
        }
        return candidate.lineIndex < currentBest.lineIndex;
    }

    private String extractMerchant(List<String> lines) {
        String bestMerchant = "";
        int bestScore = Integer.MIN_VALUE;
        int inspectionLimit = Math.min(lines.size(), 6);

        for (int index = 0; index < inspectionLimit; index++) {
            String line = lines.get(index);
            String normalizedLine = normalizeForMatch(line);
            if (normalizedLine.length() < 3) {
                continue;
            }
            if (containsAny(normalizedLine, MERCHANT_EXCLUSION_KEYWORDS)) {
                continue;
            }
            if (MONEY_PATTERN.matcher(line).find() || DATE_DMY_PATTERN.matcher(line).find() || DATE_YMD_PATTERN.matcher(line).find()) {
                continue;
            }

            int letters = countLetters(line);
            if (letters < 3) {
                continue;
            }

            int score = 50 - (index * 8);
            if (isMostlyUppercase(line)) {
                score += 15;
            }
            if (countDigits(line) > 3) {
                score -= 20;
            }
            if (line.contains(":")) {
                score -= 10;
            }
            if (line.length() > 45) {
                score -= 10;
            }

            if (score > bestScore) {
                bestScore = score;
                bestMerchant = line.trim();
            }
        }

        return bestScore >= 20 ? bestMerchant : "";
    }

    private List<ReceiptItem> extractItems(List<String> lines, AmountCandidate totalCandidate) {
        int limitIndex = totalCandidate != null && totalCandidate.fromTotalKeyword
                ? totalCandidate.lineIndex
                : lines.size();
        if (limitIndex <= 0) {
            limitIndex = lines.size();
        }

        List<ReceiptItem> items = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (int index = 0; index < limitIndex; index++) {
            String line = lines.get(index);
            String normalizedLine = normalizeForMatch(line);
            if (containsAny(normalizedLine, ITEM_EXCLUSION_KEYWORDS)) {
                continue;
            }
            if (containsAny(normalizedLine, MONEY_EXCLUSION_KEYWORDS)) {
                continue;
            }
            if (containsAny(normalizedLine, DATE_KEYWORDS)
                    || DATE_DMY_PATTERN.matcher(line).find()
                    || DATE_YMD_PATTERN.matcher(line).find()) {
                continue;
            }

            Matcher matcher = MONEY_PATTERN.matcher(line);
            AmountSpan lastAmount = null;
            while (matcher.find()) {
                String amountDigits = normalizeAmountToken(matcher.group(1));
                if (amountDigits.length() < 3) {
                    continue;
                }
                lastAmount = new AmountSpan(amountDigits, matcher.start(), matcher.end());
            }

            if (lastAmount == null) {
                continue;
            }

            String itemName = line.substring(0, lastAmount.start).trim();
            itemName = LEADING_QTY_PATTERN.matcher(itemName).replaceFirst("").trim();
            if (itemName.length() < 2) {
                continue;
            }
            if (countLetters(itemName) < 2) {
                continue;
            }

            String dedupeKey = normalizeForMatch(itemName) + "|" + lastAmount.normalizedAmount;
            if (seenKeys.contains(dedupeKey)) {
                continue;
            }
            seenKeys.add(dedupeKey);

            String itemCategoryHint = resolveCategoryHint(itemName, Collections.<ReceiptItem>emptyList());
            items.add(new ReceiptItem(
                    itemName,
                    lastAmount.normalizedAmount,
                    itemCategoryHint,
                    ReceiptData.CONFIDENCE_MEDIUM
            ));

            if (items.size() >= 8) {
                break;
            }
        }

        return items;
    }

    private String resolveCategoryHint(String merchant, List<ReceiptItem> items) {
        List<String> probes = new ArrayList<>();
        if (merchant != null && !merchant.trim().isEmpty()) {
            probes.add(merchant);
        }
        for (ReceiptItem item : items) {
            if (item.getName() != null && !item.getName().isEmpty()) {
                probes.add(item.getName());
            }
        }

        for (String probe : probes) {
            String normalizedProbe = normalizeForMatch(probe);
            for (Map.Entry<String, String> entry : CATEGORY_HINTS.entrySet()) {
                if (normalizedProbe.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return "";
    }

    private int resolveConfidence(AmountCandidate amountCandidate,
                                  ParsedDate parsedDate,
                                  List<ReceiptItem> items) {
        boolean hasAmount = amountCandidate != null;
        boolean hasDate = parsedDate != null;
        boolean hasTotalKeyword = amountCandidate != null && amountCandidate.fromTotalKeyword;

        if (hasAmount && hasDate && hasTotalKeyword) {
            return ReceiptData.CONFIDENCE_HIGH;
        }
        if (hasAmount && hasDate) {
            return ReceiptData.CONFIDENCE_MEDIUM;
        }
        if (hasAmount || !items.isEmpty()) {
            return ReceiptData.CONFIDENCE_LOW;
        }
        return ReceiptData.CONFIDENCE_LOW;
    }

    private static Map<String, String> buildCategoryHints() {
        Map<String, String> hints = new HashMap<>();
        hints.put("ca phe", "Ăn uống");
        hints.put("coffee", "Ăn uống");
        hints.put("tra sua", "Ăn uống");
        hints.put("tra", "Ăn uống");
        hints.put("pho", "Ăn uống");
        hints.put("bun", "Ăn uống");
        hints.put("com", "Ăn uống");
        hints.put("banh", "Ăn uống");
        hints.put("mi", "Ăn uống");
        hints.put("nha hang", "Ăn uống");
        hints.put("grab", "Di chuyển");
        hints.put("taxi", "Di chuyển");
        hints.put("xanh sm", "Di chuyển");
        hints.put("be ", "Di chuyển");
        hints.put("xang", "Di chuyển");
        hints.put("petrol", "Di chuyển");
        hints.put("gui xe", "Di chuyển");
        hints.put("parking", "Di chuyển");
        hints.put("nha thuoc", "Sức khỏe");
        hints.put("pharmacy", "Sức khỏe");
        hints.put("thuoc", "Sức khỏe");
        hints.put("sieu thi", "Mua sắm");
        hints.put("coopmart", "Mua sắm");
        hints.put("co.opmart", "Mua sắm");
        hints.put("winmart", "Mua sắm");
        hints.put("bach hoa xanh", "Mua sắm");
        hints.put("mart", "Mua sắm");
        hints.put("dien", "Hóa đơn");
        hints.put("nuoc", "Hóa đơn");
        hints.put("internet", "Hóa đơn");
        hints.put("wifi", "Hóa đơn");
        return hints;
    }

    private int parseYear(String rawYear) {
        int year = Integer.parseInt(rawYear);
        if (rawYear.length() == 2) {
            int currentCentury = (Year.now(RECEIPT_ZONE).getValue() / 100) * 100;
            year += currentCentury;
        }
        return year;
    }

    private boolean containsAny(String normalizedLine, List<String> keywords) {
        for (String keyword : keywords) {
            if (normalizedLine.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeAmountToken(String token) {
        return token.replaceAll("[^\\d]", "");
    }

    private long parseLongSafely(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private String normalizeForMatch(String value) {
        String normalized = Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}+", "");
        normalized = normalized.replace('đ', 'd').replace('Đ', 'D');
        return MULTI_SPACE_PATTERN.matcher(normalized.toLowerCase(Locale.US)).replaceAll(" ").trim();
    }

    private int countLetters(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (Character.isLetter(value.charAt(index))) {
                count++;
            }
        }
        return count;
    }

    private int countDigits(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (Character.isDigit(value.charAt(index))) {
                count++;
            }
        }
        return count;
    }

    private boolean isMostlyUppercase(String value) {
        int uppercaseCount = 0;
        int letterCount = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isLetter(character)) {
                letterCount++;
                if (Character.isUpperCase(character)) {
                    uppercaseCount++;
                }
            }
        }
        return letterCount > 0 && uppercaseCount >= (letterCount * 0.6f);
    }

    private static final class AmountCandidate {
        private final String normalizedAmount;
        private final String sourceLine;
        private final int lineIndex;
        private final int score;
        private final long numericValue;
        private final boolean fromTotalKeyword;

        private AmountCandidate(String normalizedAmount,
                                String sourceLine,
                                int lineIndex,
                                int score,
                                long numericValue,
                                boolean fromTotalKeyword) {
            this.normalizedAmount = normalizedAmount;
            this.sourceLine = sourceLine;
            this.lineIndex = lineIndex;
            this.score = score;
            this.numericValue = numericValue;
            this.fromTotalKeyword = fromTotalKeyword;
        }
    }

    private static final class ParsedDate {
        private final long epochMillis;
        private final int lineIndex;
        private final int score;

        private ParsedDate(long epochMillis, int lineIndex, int score) {
            this.epochMillis = epochMillis;
            this.lineIndex = lineIndex;
            this.score = score;
        }
    }

    private static final class AmountSpan {
        private final String normalizedAmount;
        private final int start;
        private final int end;

        private AmountSpan(String normalizedAmount, int start, int end) {
            this.normalizedAmount = normalizedAmount;
            this.start = start;
            this.end = end;
        }
    }
}
