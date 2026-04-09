package com.group10.moneymate.ai.receipt;

import com.group10.moneymate.ai.receipt.model.ReceiptData;
import com.group10.moneymate.ai.receipt.model.ReceiptItem;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ReceiptParser {

    private static final Pattern MONEY_PATTERN = Pattern.compile(
            "(?i)(?<!\\d)(\\d+(?:[.,\\s]\\d{3})+|\\d+(?:[.,]\\d{1,2})?)(?:\\s?(?:đ|d|vnd|vnđ))?(?!\\d)"
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
            "tong",
            "tong cong",
            "t cong",
            "t.cong",
            "tong tien",
            "tong thanh toan",
            "tong so thanh toan",
            "tien thanh toan",
            "thanh toan",
            "thanh tien",
            "tong thanh tien",
            "phai tra",
            "can thanh toan",
            "total",
            "grand total",
            "amount due"
    );
    private static final List<String> EXCLUDED_AMOUNT_CONTEXT = Arrays.asList(
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
            "fax",
            "giam gia",
            "discount",
            "vat",
            "thue",
            "tien mat",
            "khach dua",
            "tien thua",
            "cash",
            "change"
    );
    private static final List<String> ITEM_EXCLUSION_KEYWORDS = Arrays.asList(
            "tong",
            "total",
            "thanh toan",
            "thanh tien",
            "ngay",
            "date",
            "gio",
            "time",
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
            "date",
            "time"
    );
    private static final Map<String, String> CATEGORY_HINTS = buildCategoryHints();
    private static final ZoneId RECEIPT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final String FALLBACK_CATEGORY = "Khác";

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

        String merchant = extractMerchant(lines);
        List<ReceiptItem> items = extractItems(lines);
        AmountCandidate totalAmount = extractTotalAmount(lines);
        if (totalAmount == null) {
            totalAmount = buildSummedItemsAmount(items);
        }
        if (totalAmount == null) {
            totalAmount = extractLargestAmount(lines);
        }

        ParsedDate parsedDate = extractDate(lines);
        long timestamp = parsedDate != null
                ? parsedDate.epochMillis
                : ReceiptData.UNKNOWN_TIMESTAMP;
        String categoryHint = resolveCategoryHint(merchant, lines, items);
        String noteHint = buildNoteHint(lines, merchant);
        int confidence = resolveConfidence(totalAmount, parsedDate, merchant, items);

        return new ReceiptData(
                totalAmount != null ? totalAmount.normalizedAmount : "",
                timestamp,
                merchant,
                categoryHint,
                noteHint,
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
            String cleanLine = sanitizeLineText(rawLine);
            if (!cleanLine.isEmpty()) {
                normalized.add(cleanLine);
            }
        }
        return normalized;
    }

    private AmountCandidate extractTotalAmount(List<String> lines) {
        AmountCandidate bestCandidate = null;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String normalizedLine = normalizeForMatch(line);
            if (!containsAny(normalizedLine, TOTAL_KEYWORDS)) {
                continue;
            }

            List<String> amounts = extractAmounts(line);
            boolean amountFromNextLine = false;
            if (amounts.isEmpty() && index + 1 < lines.size()) {
                String nextLine = lines.get(index + 1);
                String normalizedNextLine = normalizeForMatch(nextLine);
                if (!containsAny(normalizedNextLine, EXCLUDED_AMOUNT_CONTEXT)
                        && !containsAny(normalizedNextLine, ITEM_EXCLUSION_KEYWORDS)) {
                    amounts = extractAmounts(nextLine);
                    amountFromNextLine = !amounts.isEmpty();
                }
            }
            if (amounts.isEmpty()) {
                continue;
            }

            String bestAmount = "";
            BigDecimal bestValue = BigDecimal.ZERO;
            for (String amount : amounts) {
                BigDecimal value = parseMoney(amount);
                if (value.compareTo(bestValue) > 0) {
                    bestValue = value;
                    bestAmount = amount;
                }
            }
            if (bestAmount.isEmpty()) {
                continue;
            }

            int score = 100 + resolveTotalKeywordPriority(normalizedLine);
            if (normalizedLine.contains("vat") || normalizedLine.contains("thue")) {
                score -= 30;
            }
            if (normalizedLine.contains("giam gia") || normalizedLine.contains("discount")) {
                score -= 30;
            }
            if (index >= Math.max(0, lines.size() - 4)) {
                score += 10;
            }

            int resolvedLineIndex = amountFromNextLine ? index + 1 : index;
            AmountCandidate candidate = new AmountCandidate(bestAmount, score, true, resolvedLineIndex);
            if (isBetterKeywordTotalCandidate(candidate, bestCandidate)) {
                bestCandidate = candidate;
            }
        }
        return bestCandidate;
    }

    private boolean isBetterKeywordTotalCandidate(AmountCandidate candidate, AmountCandidate currentBest) {
        if (candidate == null) {
            return false;
        }
        if (currentBest == null) {
            return true;
        }
        if (candidate.score != currentBest.score) {
            if (Math.abs(candidate.score - currentBest.score) >= 25) {
                return candidate.score > currentBest.score;
            }
        }
        if (candidate.lineIndex != currentBest.lineIndex) {
            return candidate.lineIndex > currentBest.lineIndex;
        }
        if (candidate.score != currentBest.score) {
            return candidate.score > currentBest.score;
        }
        return parseMoney(candidate.normalizedAmount).compareTo(parseMoney(currentBest.normalizedAmount)) > 0;
    }

    private AmountCandidate buildSummedItemsAmount(List<ReceiptItem> items) {
        if (items.isEmpty()) {
            return null;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ReceiptItem item : items) {
            total = total.add(parseMoney(item.getAmount()));
        }
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return new AmountCandidate(formatMoney(total), 40, false, -1);
    }

    private AmountCandidate extractLargestAmount(List<String> lines) {
        BigDecimal bestValue = BigDecimal.ZERO;
        String bestAmount = "";
        int bestScore = Integer.MIN_VALUE;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String normalizedLine = normalizeForMatch(line);
            if (containsAny(normalizedLine, EXCLUDED_AMOUNT_CONTEXT) || containsDatePattern(line)) {
                continue;
            }

            List<String> amounts = extractAmounts(line);
            for (String amount : amounts) {
                BigDecimal value = parseMoney(amount);
                if (value.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                int score = value.compareTo(new BigDecimal("1000")) >= 0 ? 20 : 0;
                if (index >= Math.max(0, lines.size() - 5)) {
                    score += 20;
                }
                if (containsAny(normalizedLine, TOTAL_KEYWORDS)) {
                    score += 30;
                }
                if (containsAny(normalizedLine, ITEM_EXCLUSION_KEYWORDS)) {
                    score -= 20;
                }
                if (score > bestScore || (score == bestScore && value.compareTo(bestValue) > 0)) {
                    bestScore = score;
                    bestValue = value;
                    bestAmount = amount;
                }
            }
        }
        if (bestAmount.isEmpty()) {
            return null;
        }
        return new AmountCandidate(bestAmount, 10, false, -1);
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
        return parseMoney(candidate.normalizedAmount).compareTo(parseMoney(currentBest.normalizedAmount)) > 0;
    }

    private ParsedDate extractDate(List<String> lines) {
        ParsedDate bestDate = null;
        int maxAcceptedYear = Year.now(RECEIPT_ZONE).getValue() + 1;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String normalizedLine = normalizeForMatch(line);

            ParsedDate candidate = matchDate(line, normalizedLine, index, DATE_DMY_PATTERN, false, maxAcceptedYear);
            if (candidate == null) {
                candidate = matchDate(line, normalizedLine, index, DATE_YMD_PATTERN, true, maxAcceptedYear);
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
                                 boolean yearFirst,
                                 int maxAcceptedYear) {
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
                if (year < 2000 || year > maxAcceptedYear) {
                    continue;
                }

                int hour = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 0;
                int minute = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;
                int second = matcher.group(6) != null ? Integer.parseInt(matcher.group(6)) : 0;

                LocalDate localDate = LocalDate.of(year, month, day);
                LocalTime localTime = LocalTime.of(hour, minute, second);
                long epochMillis = LocalDateTime.of(localDate, localTime)
                        .atZone(RECEIPT_ZONE)
                        .toInstant()
                        .toEpochMilli();

                int score = containsAny(normalizedLine, DATE_KEYWORDS) ? 30 : 10;
                if (index <= 4) {
                    score += 10;
                }
                return new ParsedDate(epochMillis, score);
            } catch (RuntimeException ignored) {
                // Skip invalid date fragments.
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
        return candidate.score > currentBest.score;
    }

    private String extractMerchant(List<String> lines) {
        int inspectionLimit = Math.min(lines.size(), 8);
        for (int index = 0; index < inspectionLimit; index++) {
            String line = lines.get(index);
            String normalizedLine = normalizeForMatch(line);
            if (normalizedLine.length() < 3) {
                continue;
            }
            if (containsAny(normalizedLine, MERCHANT_EXCLUSION_KEYWORDS)) {
                continue;
            }
            if (countDigits(line) > 0 && !isLikelyMerchantBrand(line)) {
                continue;
            }
            if (line.length() > 100) {
                continue;
            }
            if (countWords(line) >= 2 || isLikelyMerchantBrand(line)) {
                return line.trim();
            }
        }
        return "";
    }

    private List<ReceiptItem> extractItems(List<String> lines) {
        List<ReceiptItem> items = new ArrayList<>();
        for (String line : lines) {
            String normalizedLine = normalizeForMatch(line);
            if (containsAny(normalizedLine, ITEM_EXCLUSION_KEYWORDS)
                    || containsAny(normalizedLine, EXCLUDED_AMOUNT_CONTEXT)
                    || containsDatePattern(line)) {
                continue;
            }
            if (!containsLetterAndDigit(line)) {
                continue;
            }

            Matcher matcher = MONEY_PATTERN.matcher(line);
            String trailingAmount = "";
            int trailingAmountStart = -1;
            int trailingAmountEnd = -1;
            while (matcher.find()) {
                trailingAmount = normalizeMoneyToken(matcher.group(1));
                trailingAmountStart = matcher.start();
                trailingAmountEnd = matcher.end();
            }
            if (trailingAmount.isEmpty() || trailingAmountStart < 0 || trailingAmountEnd < 0) {
                continue;
            }
            if (!line.substring(trailingAmountEnd).trim().isEmpty()) {
                continue;
            }
            if (parseMoney(trailingAmount).compareTo(new BigDecimal("1000")) < 0) {
                continue;
            }

            String itemName = line.substring(0, trailingAmountStart).trim();
            itemName = LEADING_QTY_PATTERN.matcher(itemName).replaceFirst("").trim();
            if (itemName.length() < 2 || countLetters(itemName) < 2) {
                continue;
            }

            items.add(new ReceiptItem(
                    itemName,
                    trailingAmount,
                    resolveCategoryHint("", Collections.singletonList(line), Collections.<ReceiptItem>emptyList()),
                    ReceiptData.CONFIDENCE_MEDIUM
            ));
        }
        return deduplicateItems(items);
    }

    private List<ReceiptItem> deduplicateItems(List<ReceiptItem> items) {
        List<ReceiptItem> deduped = new ArrayList<>();
        Map<String, Boolean> seen = new HashMap<>();
        for (ReceiptItem item : items) {
            String key = normalizeForMatch(item.getName()) + "|" + item.getAmount();
            if (seen.containsKey(key)) {
                continue;
            }
            seen.put(key, Boolean.TRUE);
            deduped.add(item);
        }
        return deduped;
    }

    private String resolveCategoryHint(String merchant, List<String> lines, List<ReceiptItem> items) {
        Map<String, Integer> categoryScores = new LinkedHashMap<>();
        String normalizedMerchant = normalizeForMatch(merchant);
        String normalizedCorpus = normalizeForMatch(joinText(lines));

        scoreCategoryMatches(categoryScores, normalizedMerchant, 10);
        scoreCategoryMatches(categoryScores, normalizedCorpus, 1);
        for (String line : lines) {
            scoreCategoryMatches(categoryScores, normalizeForMatch(line), 1);
        }
        for (ReceiptItem item : items) {
            scoreCategoryMatches(categoryScores, normalizeForMatch(item.getName()), 1);
        }

        String bestCategory = FALLBACK_CATEGORY;
        int bestScore = 0;
        for (Map.Entry<String, Integer> entry : categoryScores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                bestCategory = entry.getKey();
            }
        }
        return bestCategory;
    }

    private void scoreCategoryMatches(Map<String, Integer> categoryScores,
                                      String normalizedText,
                                      int weight) {
        if (normalizedText == null || normalizedText.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : CATEGORY_HINTS.entrySet()) {
            if (!normalizedText.contains(entry.getKey())) {
                continue;
            }
            String category = entry.getValue();
            int currentScore = categoryScores.containsKey(category)
                    ? categoryScores.get(category)
                    : 0;
            categoryScores.put(category, currentScore + weight);
        }
    }

    private String joinText(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(line);
        }
        return builder.toString();
    }

    private String buildNoteHint(List<String> lines, String merchant) {
        String primary = merchant == null ? "" : merchant.trim();
        for (String line : lines) {
            String candidate = line.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (!primary.isEmpty() && candidate.equalsIgnoreCase(primary)) {
                continue;
            }
            if (countDigits(candidate) > 0) {
                continue;
            }
            if (containsAny(normalizeForMatch(candidate), MERCHANT_EXCLUSION_KEYWORDS)) {
                continue;
            }
            return primary.isEmpty() ? candidate : primary + " - " + candidate;
        }
        return primary;
    }

    private int resolveConfidence(AmountCandidate amountCandidate,
                                  ParsedDate parsedDate,
                                  String merchant,
                                  List<ReceiptItem> items) {
        boolean hasAmount = amountCandidate != null && !amountCandidate.normalizedAmount.isEmpty();
        boolean hasDate = parsedDate != null;
        boolean hasMerchant = merchant != null && !merchant.trim().isEmpty();
        boolean hasItems = !items.isEmpty();

        if (hasAmount && amountCandidate != null && amountCandidate.fromKeyword && hasDate && (hasMerchant || hasItems)) {
            return ReceiptData.CONFIDENCE_HIGH;
        }
        if (hasAmount && (hasDate || hasMerchant || hasItems)) {
            return ReceiptData.CONFIDENCE_MEDIUM;
        }
        return ReceiptData.CONFIDENCE_LOW;
    }

    private boolean containsDatePattern(String line) {
        return DATE_DMY_PATTERN.matcher(line).find() || DATE_YMD_PATTERN.matcher(line).find();
    }

    private List<String> extractAmounts(String line) {
        List<String> amounts = new ArrayList<>();
        Matcher matcher = MONEY_PATTERN.matcher(line);
        while (matcher.find()) {
            String normalized = normalizeMoneyToken(matcher.group(1));
            if (!normalized.isEmpty()) {
                amounts.add(normalized);
            }
        }
        return amounts;
    }

    private String sanitizeLineText(String rawLine) {
        if (rawLine == null) {
            return "";
        }
        return MULTI_SPACE_PATTERN.matcher(rawLine.replace('\t', ' ')).replaceAll(" ").trim();
    }

    private String normalizeMoneyToken(String token) {
        if (token == null) {
            return "";
        }
        String candidate = token.trim()
                .replace("đ", "")
                .replace("Đ", "")
                .replaceAll("(?i)vnd|vnđ", "")
                .replace(" ", "");
        if (candidate.isEmpty()) {
            return "";
        }

        String originalCandidate = token.trim()
                .replace("đ", "")
                .replace("Đ", "")
                .replaceAll("(?i)vnd|vnđ", "")
                .trim();
        if (originalCandidate.contains(" ")) {
            String[] parts = originalCandidate.split("\\s+");
            if (parts.length >= 2) {
                String trailingPart = parts[parts.length - 1];
                String leadingJoined = String.join("", Arrays.copyOf(parts, parts.length - 1))
                        .replaceAll("[^\\d]", "");
                if ((trailingPart.contains(",") || trailingPart.contains("."))
                        && !leadingJoined.isEmpty()
                        && leadingJoined.length() <= 2) {
                    return normalizeMoneyToken(trailingPart);
                }
            }
        }

        int lastDot = candidate.lastIndexOf('.');
        int lastComma = candidate.lastIndexOf(',');
        int lastSeparator = Math.max(lastDot, lastComma);
        if (lastSeparator < 0) {
            return candidate.replaceAll("[^\\d]", "");
        }

        String integerPart = candidate.substring(0, lastSeparator).replaceAll("[^\\d]", "");
        String fractionPart = candidate.substring(lastSeparator + 1).replaceAll("[^\\d]", "");
        boolean decimalLike = fractionPart.length() > 0
                && fractionPart.length() <= 2
                && candidate.indexOf('.') == lastSeparator
                && candidate.lastIndexOf('.') == lastSeparator
                && candidate.indexOf(',') < 0;
        if (!decimalLike) {
            return candidate.replaceAll("[^\\d]", "");
        }
        if (integerPart.isEmpty()) {
            integerPart = "0";
        }
        return integerPart + "." + fractionPart;
    }

    private int resolveTotalKeywordPriority(String normalizedLine) {
        if (normalizedLine.contains("grand total")
                || normalizedLine.contains("tong cong")
                || normalizedLine.contains("tong thanh toan")
                || normalizedLine.contains("tong so thanh toan")
                || normalizedLine.contains("amount due")
                || normalizedLine.contains("phai tra")
                || normalizedLine.contains("can thanh toan")) {
            return 50;
        }
        if (normalizedLine.contains("tong tien")
                || normalizedLine.contains("tien thanh toan")
                || normalizedLine.contains("thanh toan")
                || normalizedLine.equals("tong")
                || normalizedLine.startsWith("tong ")) {
            return 35;
        }
        if (normalizedLine.contains("tong thanh tien") || normalizedLine.contains("thanh tien")) {
            return 15;
        }
        return 0;
    }

    private BigDecimal parseMoney(String normalizedAmount) {
        if (normalizedAmount == null || normalizedAmount.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(normalizedAmount);
        } catch (NumberFormatException exception) {
            return BigDecimal.ZERO;
        }
    }

    private String formatMoney(BigDecimal amount) {
        BigDecimal normalized = amount.stripTrailingZeros();
        if (normalized.scale() < 0) {
            normalized = normalized.setScale(0, RoundingMode.UNNECESSARY);
        }
        return normalized.toPlainString();
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

    private String normalizeForMatch(String value) {
        String normalized = Normalizer.normalize(Objects.toString(value, ""), Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{M}+", "");
        normalized = normalized.replace('đ', 'd').replace('Đ', 'D');
        normalized = MULTI_SPACE_PATTERN.matcher(normalized.toLowerCase(Locale.US)).replaceAll(" ").trim();
        normalized = normalized
                .replace("t0ng", "tong")
                .replace("to'ng", "tong")
                .replace("to ng", "tong")
                .replace("t.cong", "tong cong")
                .replace("t cong", "tong cong")
                .replace("thah toan", "thanh toan")
                .replace("thanhtoan", "thanh toan")
                .replace("thanh toan:", "thanh toan")
                .replace("tong cong:", "tong cong")
                .replace("tong thanh toan:", "tong thanh toan");
        return normalized;
    }

    private boolean containsLetterAndDigit(String value) {
        return value.matches(".*\\p{L}.*") && value.matches(".*\\d+.*");
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

    private int countWords(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    private boolean isLikelyMerchantBrand(String line) {
        int letters = countLetters(line);
        String trimmed = line == null ? "" : line.trim();
        return letters >= 2
                && trimmed.length() >= 4
                && trimmed.equals(trimmed.toUpperCase(Locale.US));
    }

    private static Map<String, String> buildCategoryHints() {
        Map<String, String> hints = new HashMap<>();
        hints.put("an uong", "Ăn uống");
        hints.put("nha hang", "Ăn uống");
        hints.put("coffee", "Ăn uống");
        hints.put("restaurant", "Ăn uống");
        hints.put("res", "Ăn uống");
        hints.put("highlands", "Ăn uống");
        hints.put("pho", "Ăn uống");
        hints.put("bun", "Ăn uống");
        hints.put("com", "Ăn uống");
        hints.put("banh", "Ăn uống");
        hints.put("tra sua", "Ăn uống");
        hints.put("ca phe", "Ăn uống");
        hints.put("coca", "Ăn uống");
        hints.put("sprite", "Ăn uống");
        hints.put("soda", "Ăn uống");
        hints.put("tonic", "Ăn uống");
        hints.put("nuoc ngot", "Ăn uống");
        hints.put("sieu thi", "Mua sắm");
        hints.put("circle k", "Mua sắm");
        hints.put("gs25", "Mua sắm");
        hints.put("mart", "Mua sắm");
        hints.put("coopmart", "Mua sắm");
        hints.put("co opmart", "Mua sắm");
        hints.put("winmart", "Mua sắm");
        hints.put("bach hoa xanh", "Mua sắm");
        hints.put("grab", "Di chuyển");
        hints.put("taxi", "Di chuyển");
        hints.put("xe bus", "Di chuyển");
        hints.put("xanh sm", "Di chuyển");
        hints.put("be ", "Di chuyển");
        hints.put("dien", "Hóa đơn & Tiện ích");
        hints.put("nuoc", "Hóa đơn & Tiện ích");
        hints.put("internet", "Hóa đơn & Tiện ích");
        hints.put("wifi", "Hóa đơn & Tiện ích");
        hints.put("thuoc", "Sức khỏe");
        hints.put("benh vien", "Sức khỏe");
        hints.put("nha thuoc", "Sức khỏe");
        return hints;
    }

    private static final class AmountCandidate {
        private final String normalizedAmount;
        private final int score;
        private final boolean fromKeyword;
        private final int lineIndex;

        private AmountCandidate(String normalizedAmount, int score, boolean fromKeyword, int lineIndex) {
            this.normalizedAmount = normalizedAmount;
            this.score = score;
            this.fromKeyword = fromKeyword;
            this.lineIndex = lineIndex;
        }
    }

    private static final class ParsedDate {
        private final long epochMillis;
        private final int score;

        private ParsedDate(long epochMillis, int score) {
            this.epochMillis = epochMillis;
            this.score = score;
        }
    }
}
