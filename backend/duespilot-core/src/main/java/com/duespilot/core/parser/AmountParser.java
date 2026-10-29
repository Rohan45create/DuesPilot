package com.duespilot.core.parser;

import com.duespilot.core.model.Known;
import com.duespilot.core.model.Paise;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * Parser for monetary amounts in Indian commercial contexts.
 *
 * Per architecture.md section 10.2 and Rule R-1:
 * - Accepts ₹, Rs, Rs., INR, Indian digit grouping (1,18,000.00), and Devanagari digits (०–९).
 * - Rejects ambiguous amounts (two different totals in one quote) → returns Known.Unknown.
 * - NEVER uses float/double anywhere.
 */
public final class AmountParser {

    // Matches numbers with optional currency symbols, commas, decimals, and suffixes
    // Excludes numbers embedded in dates (e.g. 12/03/2024) or alphanumeric IDs (e.g. INV-2024)
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
        "(?i)(?:(?<prefix>₹|₨|INR|RS\\.?|रू\\.?|रु\\.?)\\s*)?"
        + "(?<neg>-|\\()?\\s*"
        + "(?<num>[0-9]{1,3}(?:,[0-9]{2,3})*(?:\\.[0-9]+)?|[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)"
        + "\\)?(?<suffixGroup>\\s*(?<suffix>/-|INR|Rs\\.?|only))?"
    );

    private AmountParser() {
    }

    /**
     * Parses a string containing an amount or extracts an unambiguous amount from a quote.
     *
     * @param text input text or quote snippet
     * @return Known with the parsed {@link Paise}, or Known.Unknown if missing, malformed, or ambiguous.
     */
    public static Known<Paise> parse(@Nullable String text) {
        if (text == null || text.isBlank()) {
            return Known.unknown("Amount text is empty or null");
        }

        String normalized = normalizeDevanagariDigits(text.trim());

        // 1. First attempt: Treat the entire trimmed text as a single amount specification
        Known<Paise> directParse = tryParseExactAmount(normalized);
        if (directParse.isValue()) {
            return directParse;
        }

        // 2. Scan text for amount candidates (e.g. from an extracted sentence or quote)
        List<FoundAmount> candidates = extractCandidates(normalized);

        if (candidates.isEmpty()) {
            return directParse.isUnknown() ? directParse : Known.unknown("Cannot parse amount: \"" + text + "\"");
        }

        // Separate explicit currency-tagged candidates from plain numbers
        List<FoundAmount> currencyTagged = candidates.stream().filter(FoundAmount::hasCurrencyTag).toList();
        List<FoundAmount> poolToEvaluate = !currencyTagged.isEmpty() ? currencyTagged : candidates;

        Set<Paise> uniqueAmounts = new LinkedHashSet<>();
        for (FoundAmount fa : poolToEvaluate) {
            uniqueAmounts.add(fa.amount());
        }

        if (uniqueAmounts.size() == 1) {
            return Known.value(uniqueAmounts.iterator().next());
        }

        // Multiple differing amounts in one quote -> Ambiguous per Section 10.2
        return Known.unknown("Ambiguous amount: multiple conflicting totals found in quote: " + uniqueAmounts);
    }

    /**
     * Attempts to parse a clean, isolated amount string (e.g. "₹ 1,18,000.00" or "Rs. 50000/-").
     */
    private static Known<Paise> tryParseExactAmount(String text) {
        String s = text.trim();

        // Check for accounting parentheses e.g. "(₹50,000.00)" or "(50,000)"
        boolean negative = false;
        if (s.startsWith("(") && s.endsWith(")")) {
            negative = true;
            s = s.substring(1, s.length() - 1).trim();
        }

        // Strip leading minus
        if (s.startsWith("-")) {
            negative = true;
            s = s.substring(1).trim();
        }

        // Strip currency prefixes
        s = stripPrefix(s);

        // Strip leading minus again if was after prefix: e.g. "₹ -50,000"
        if (s.startsWith("-")) {
            negative = true;
            s = s.substring(1).trim();
        }

        // Strip currency suffixes
        s = stripSuffix(s);

        s = s.trim();
        if (s.isEmpty()) {
            return Known.unknown("No numeric value in amount: \"" + text + "\"");
        }

        return parseCleanNumberToPaise(s, negative);
    }

    private static Known<Paise> parseCleanNumberToPaise(String numStr, boolean negative) {
        int dotIndex = numStr.indexOf('.');
        String intPart;
        String decPart = "";

        if (dotIndex >= 0) {
            if (numStr.indexOf('.', dotIndex + 1) >= 0) {
                return Known.unknown("Invalid amount format: multiple decimal points in \"" + numStr + "\"");
            }
            intPart = numStr.substring(0, dotIndex);
            decPart = numStr.substring(dotIndex + 1);
        } else {
            intPart = numStr;
        }

        // Validate comma grouping syntax if commas exist
        if (intPart.contains(",")) {
            if (!isValidIndianOrStandardGrouping(intPart)) {
                return Known.unknown("Invalid digit grouping in amount: \"" + numStr + "\"");
            }
        }

        String rawInt = intPart.replace(",", "").trim();
        long rupees = 0L;
        if (!rawInt.isEmpty()) {
            for (int i = 0; i < rawInt.length(); i++) {
                char c = rawInt.charAt(i);
                if (c < '0' || c > '9') {
                    return Known.unknown("Non-digit character in amount: \"" + numStr + "\"");
                }
            }
            try {
                rupees = Long.parseLong(rawInt);
            } catch (NumberFormatException ex) {
                return Known.unknown("Amount overflow: \"" + numStr + "\"");
            }
        }

        long paise = 0L;
        if (!decPart.isEmpty()) {
            for (int i = 0; i < decPart.length(); i++) {
                char c = decPart.charAt(i);
                if (c < '0' || c > '9') {
                    return Known.unknown("Non-digit character in decimal part: \"" + numStr + "\"");
                }
            }
            if (decPart.length() == 1) {
                paise = (decPart.charAt(0) - '0') * 10L;
            } else if (decPart.length() == 2) {
                paise = (decPart.charAt(0) - '0') * 10L + (decPart.charAt(1) - '0');
            } else {
                // More than 2 decimal digits: check if trailing digits are non-zero
                boolean nonZeroFraction = false;
                for (int i = 2; i < decPart.length(); i++) {
                    if (decPart.charAt(i) != '0') {
                        nonZeroFraction = true;
                        break;
                    }
                }
                if (nonZeroFraction) {
                    return Known.unknown("Ambiguous or unsupported fractional paise in amount: \"" + numStr + "\"");
                }
                paise = (decPart.charAt(0) - '0') * 10L + (decPart.charAt(1) - '0');
            }
        }

        try {
            long totalPaise = Math.addExact(Math.multiplyExact(rupees, 100L), paise);
            if (negative) {
                totalPaise = -totalPaise;
            }
            return Known.value(Paise.ofPaise(totalPaise));
        } catch (ArithmeticException ex) {
            return Known.unknown("Arithmetic overflow calculating paise for \"" + numStr + "\"");
        }
    }

    private static boolean isValidIndianOrStandardGrouping(String intPart) {
        String[] parts = intPart.split(",");
        if (parts.length <= 1) {
            return true;
        }
        // First part can have 1 to 3 digits
        if (parts[0].isEmpty() || parts[0].length() > 3) {
            return false;
        }
        // Last part before decimal must have exactly 3 digits
        if (parts[parts.length - 1].length() != 3) {
            return false;
        }
        // Middle parts: in Indian grouping they have 2 digits (e.g. 1,18,000);
        // in Western grouping they have 3 digits (e.g. 118,000).
        boolean isIndian = true;
        boolean isWestern = true;
        for (int i = 1; i < parts.length - 1; i++) {
            if (parts[i].length() != 2) {
                isIndian = false;
            }
            if (parts[i].length() != 3) {
                isWestern = false;
            }
        }
        return isIndian || isWestern;
    }

    private static List<FoundAmount> extractCandidates(String text) {
        List<FoundAmount> results = new ArrayList<>();
        Matcher matcher = AMOUNT_PATTERN.matcher(text);

        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();

            // Guard against matching date segments e.g. "12/03/2024"
            if (start > 0 && text.charAt(start - 1) == '/') {
                continue;
            }
            if (end < text.length() && text.charAt(end) == '/') {
                continue;
            }
            // Guard against alphanumeric identifier e.g. "INV-001" or "PO123"
            if (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) {
                continue;
            }
            if (start > 1 && text.charAt(start - 1) == '-' && Character.isLetter(text.charAt(start - 2))) {
                continue;
            }
            if (end < text.length() && Character.isLetterOrDigit(text.charAt(end))) {
                continue;
            }

            String prefix = matcher.group("prefix");
            String num = matcher.group("num");
            String suffix = matcher.group("suffix");
            String neg = matcher.group("neg");

            boolean hasCurrency = (prefix != null && !prefix.isBlank())
                || (suffix != null && !suffix.isBlank());

            boolean isNeg = "-".equals(neg) || "(".equals(neg);

            Known<Paise> parsed = parseCleanNumberToPaise(num, isNeg);
            if (parsed.isValue()) {
                results.add(new FoundAmount(parsed.getOrThrow(), hasCurrency));
            }
        }
        return results;
    }

    private static String stripPrefix(String s) {
        String lower = s.toLowerCase();
        if (s.startsWith("₹") || s.startsWith("₨")) {
            return s.substring(1).trim();
        }
        if (lower.startsWith("inr")) {
            return s.substring(3).trim();
        }
        if (lower.startsWith("rs.") || lower.startsWith("rs ")) {
            return s.substring(3).trim();
        }
        if (lower.startsWith("rs")) {
            return s.substring(2).trim();
        }
        if (s.startsWith("रु.") || s.startsWith("रू.")) {
            return s.substring(3).trim();
        }
        if (s.startsWith("रु") || s.startsWith("रू")) {
            return s.substring(2).trim();
        }
        return s;
    }

    private static String stripSuffix(String s) {
        String lower = s.toLowerCase();
        if (s.endsWith("/-")) {
            return s.substring(0, s.length() - 2).trim();
        }
        if (lower.endsWith("only")) {
            return s.substring(0, s.length() - 4).trim();
        }
        if (lower.endsWith("inr")) {
            return s.substring(0, s.length() - 3).trim();
        }
        if (lower.endsWith("rs.")) {
            return s.substring(0, s.length() - 3).trim();
        }
        if (lower.endsWith("rs")) {
            return s.substring(0, s.length() - 2).trim();
        }
        return s;
    }

    /**
     * Converts Devanagari numerals (०–९) to standard ASCII Arabic digits (0–9).
     */
    public static String normalizeDevanagariDigits(String input) {
        Objects.requireNonNull(input, "input cannot be null");
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c >= '\u0966' && c <= '\u096F') {
                sb.append((char) ('0' + (c - '\u0966')));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private record FoundAmount(Paise amount, boolean hasCurrencyTag) {}
}
