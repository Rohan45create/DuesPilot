package com.duespilot.api.service.notification;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Enforces Rule R-77: Masking sensitive numbers.
 * Bank account numbers, PAN, and Aadhaar numbers are masked in all outbound
 * notification drafts, payloads, and preview panes (e.g., ••••1234).
 */
public final class SensitiveDataMasker {

    private SensitiveDataMasker() {}

    // Indian PAN pattern: 5 uppercase letters, 4 digits, 1 letter (e.g. ABCDE1234F)
    private static final Pattern PAN_PATTERN = Pattern.compile("\\b([A-Z]{5}[0-9]{4}[A-Z])\\b");

    // Aadhaar number pattern: 12 digits separated by spaces or hyphens (e.g. 1234 5678 9012 or 1234-5678-9012)
    private static final Pattern AADHAAR_PATTERN = Pattern.compile("\\b([0-9]{4})[\\s-]+([0-9]{4})[\\s-]+([0-9]{4})\\b");

    // Indian bank account number pattern: 9 to 18 consecutive digits
    private static final Pattern BANK_ACCOUNT_PATTERN = Pattern.compile("\\b([0-9]{9,18})\\b");

    public static String mask(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }

        String result = maskPan(input);
        result = maskAadhaar(result);
        result = maskBankAccount(result);
        return result;
    }

    public static String maskPan(String input) {
        if (input == null) return null;
        Matcher matcher = PAN_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String pan = matcher.group(1);
            // Mask first 6 characters, leave last 4 (e.g. ••••••1234F -> ••••234F or ••••1234)
            String masked = "••••" + pan.substring(pan.length() - 4);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String maskAadhaar(String input) {
        if (input == null) return null;
        Matcher matcher = AADHAAR_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String lastFour = matcher.group(3);
            String masked = "•••• •••• " + lastFour;
            matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String maskBankAccount(String input) {
        if (input == null) return null;
        Matcher matcher = BANK_ACCOUNT_PATTERN.matcher(input);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String acct = matcher.group(1);
            String masked = "••••" + acct.substring(acct.length() - 4);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(masked));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
